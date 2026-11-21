package cythen.hyperdrop.pods.link

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.util.Log
import cythen.hyperdrop.device.PodTransport
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * GAIA over BLE GATT.
 *
 * The official app picks this transport when `resolveBluetoothType()` reads
 * `transportType = LOW_ENERGY` from its paired-device map, and the GAIA client SDK it bundles
 * carries the GATT profile in `ServiceUuid$Gaia` / `CharacteristicUuid$Gaia*`:
 *
 * - service            `00001100-d102-11e1-9b23-00025b00a5a5`
 * - command (write)    `00001101-d102-11e1-9b23-00025b00a5a5`
 * - response (notify)  `00001102-d102-11e1-9b23-00025b00a5a5`
 * - data (notify)      `00001103-d102-11e1-9b23-00025b00a5a5`
 *
 * Two things differ from [RfcommPodLink] and matter for every caller:
 *
 * 1. **No `0xFF` framing.** GAIA packets travel as the bare `[vendor][command word][params]`
 *    bytes. [PodLink] already asks callers for an unframed payload, so [send] writes it
 *    directly instead of running it through the RFCOMM v4 framer.
 * 2. **Notification boundaries are packet boundaries.** A GAIA v3 packet does not encode its
 *    own length, so each notification is treated as exactly one packet. That holds while the
 *    packet fits the negotiated MTU; longer payloads use the official SDK's data-transfer
 *    handshake, which this build does not implement yet and logs instead of guessing.
 */
@SuppressLint("MissingPermission")
class BleGaiaPodLink(
    private val context: Context,
    private val device: BluetoothDevice,
    private val logger: LinkLogger = LinkLogger.NONE,
    private val isCancelled: () -> Boolean = { false },
) : PodLink {

    companion object {
        const val TAG = "HyperDrop-BleGaia"

        const val GAIA_SERVICE: String = "00001100-d102-11e1-9b23-00025b00a5a5"
        const val GAIA_COMMAND: String = "00001101-d102-11e1-9b23-00025b00a5a5"
        const val GAIA_RESPONSE: String = "00001102-d102-11e1-9b23-00025b00a5a5"
        const val GAIA_DATA: String = "00001103-d102-11e1-9b23-00025b00a5a5"
        const val CCCD: String = "00002902-0000-1000-8000-00805f9b34fb"

        /** The official client asks for a 247-byte MTU; the stack may grant less. */
        const val REQUESTED_MTU = 247

        private const val DISCOVER_TIMEOUT_MS = 8_000L
    }

    override val transport: PodTransport = PodTransport.BLE_GAIA
    override var listener: PodLink.Listener? = null

    private var gatt: BluetoothGatt? = null
    private var commandChar: BluetoothGattCharacteristic? = null
    private var responseChar: BluetoothGattCharacteristic? = null
    private var dataChar: BluetoothGattCharacteristic? = null

    private val closed = AtomicBoolean(false)

    /** Signalled once the GAIA service has been resolved (or the attempt failed). */
    @Volatile
    private var readyLatch: CountDownLatch? = null

    @Volatile
    private var negotiatedMtu = 23

    override val isOpen: Boolean
        get() = !closed.get() && commandChar != null

    override fun open() {
        closed.set(false)
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if (manager.adapter?.isEnabled != true) throw IOException("bluetooth is off")

        val latch = CountDownLatch(1)
        readyLatch = latch
        val newGatt = device.connectGatt(context, false, createCallback(), BluetoothDevice.TRANSPORT_LE)
            ?: throw IOException("connectGatt returned null")
        gatt = newGatt

        if (!latch.await(DISCOVER_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            close()
            throw IOException("GATT service discovery timed out")
        }
        if (isCancelled()) {
            // A yield may land while connectGatt is settling; never hold a channel the official
            // app is trying to own.
            close()
            throw IOException("cancelled during connect")
        }
        if (commandChar == null) {
            close()
            throw IOException("GAIA service not usable")
        }
        Log.d(TAG, "gatt ready mtu=$negotiatedMtu")
        logger.log(LinkLogger.Direction.RX, ByteArray(0), "gatt ready mtu=$negotiatedMtu")
    }

    private fun createCallback(): BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    handleLoss("gatt disconnected status=$status")
                    readyLatch?.countDown()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "service discovery failed status=$status")
                readyLatch?.countDown()
                return
            }
            val service = g.getService(UUID.fromString(GAIA_SERVICE))
            if (service == null) {
                Log.w(TAG, "GAIA service missing")
                readyLatch?.countDown()
                return
            }
            commandChar = service.getCharacteristic(UUID.fromString(GAIA_COMMAND))
            responseChar = service.getCharacteristic(UUID.fromString(GAIA_RESPONSE))
            dataChar = service.getCharacteristic(UUID.fromString(GAIA_DATA))
            if (commandChar == null) {
                Log.w(TAG, "GAIA command characteristic missing")
                readyLatch?.countDown()
                return
            }
            // Ask for the larger MTU first: a 20-byte default would truncate any response
            // longer than the command header. The reply arrives later via onMtuChanged and is
            // only informational for the notification path, so it does not gate readiness.
            runCatching { g.requestMtu(REQUESTED_MTU) }
            enableNotifications(g)
            readyLatch?.countDown()
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            negotiatedMtu = mtu
            Log.d(TAG, "mtu=$mtu status=$status")
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            deliver(characteristic.uuid, value)
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) Log.w(TAG, "write failed status=$status")
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) Log.w(TAG, "descriptor write failed status=$status")
        }
    }

    private fun deliver(characteristicUuid: UUID?, value: ByteArray) {
        if (value.isEmpty()) return
        val isData = characteristicUuid == UUID.fromString(GAIA_DATA)
        logger.log(LinkLogger.Direction.RX, value, if (isData) "data" else "response")
        if (isData) {
            // Large payloads are split through the official SDK's data-transfer handshake.
            // Until that lands, surface the truncation rather than hand a partial packet up.
            Log.w(TAG, "data characteristic payload (${value.size} bytes) not reassembled yet")
            return
        }
        listener?.onPayload(value)
    }

    private fun enableNotifications(g: BluetoothGatt) {
        listOfNotNull(responseChar, dataChar).forEach { characteristic ->
            g.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(UUID.fromString(CCCD)) ?: return@forEach
            val isIndicate = characteristic.properties and
                BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0
            val value = if (isIndicate) {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            }
            // minSdk is 35, so the descriptor.value + writeDescriptor(descriptor) pair is
            // deprecated in favour of the value-carrying overload.
            runCatching { g.writeDescriptor(descriptor, value) }
                .onFailure { Log.w(TAG, "writeDescriptor threw: ${it.message}") }
        }
    }

    /** Writes an unframed GAIA payload: no `0xFF` frame on BLE. */
    override fun send(payload: ByteArray): Boolean = write(payload)

    override fun sendRaw(frame: ByteArray): Boolean = write(frame)

    private fun write(bytes: ByteArray): Boolean {
        if (closed.get()) return false
        val g = gatt ?: return false
        val characteristic = commandChar ?: return false
        logger.log(LinkLogger.Direction.TX, bytes, "gaia")

        // minSdk is 35, so the Tiramisu overload is always available and the deprecated
        // characteristic.value / writeType path is dead code that would only add a lint debt.
        val started = g.writeCharacteristic(
            characteristic,
            bytes,
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
        ) == BluetoothStatusCodes.SUCCESS
        if (!started) {
            handleLoss("writeCharacteristic rejected")
            return false
        }
        // Fire-and-forget on purpose: the session already spaces its queries, and blocking on
        // every onCharacteristicWrite would stall the UI coroutine for no benefit.
        return true
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        teardown()
    }

    private fun handleLoss(reason: String) {
        if (!closed.compareAndSet(false, true)) return
        Log.d(TAG, "link lost: $reason")
        teardown()
        listener?.onClosed(reason)
    }

    private fun teardown() {
        commandChar = null
        responseChar = null
        dataChar = null
        val g = gatt
        gatt = null
        runCatching { g?.disconnect() }
        runCatching { g?.close() }
        readyLatch?.countDown()
    }
}
