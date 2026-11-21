package cythen.hyperdrop.pods.link

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import cythen.hyperdrop.device.PodTransport
import cythen.hyperdrop.device.protocol.GaiaTransport
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * GAIA over Classic Bluetooth RFCOMM/SPP.
 *
 * This is the transport the official app uses for every CLASSIC-mode device
 * (`BluetoothConnectionHandler.resolveBluetoothType()` returns CLASSIC unless the paired-device
 * map says `transportType = LOW_ENERGY`), and the one PUDDING/MIRAGE are verified on.
 *
 * Framing is the v4 short frame from [GaiaTransport]; the socket carries a byte stream, so a
 * [GaiaTransport.StreamDecoder] reassembles split/merged frames before the listener sees them.
 */
@SuppressLint("MissingPermission")
class RfcommPodLink(
    private val device: BluetoothDevice,
    private val sppUuid: String = GaiaTransport.SPP_UUID,
    private val logger: LinkLogger = LinkLogger.NONE,
    private val isCancelled: () -> Boolean = { false },
) : PodLink {

    override val transport: PodTransport = PodTransport.RFCOMM_GAIA
    override var listener: PodLink.Listener? = null

    private var socket: BluetoothSocket? = null
    private var readerThread: Thread? = null
    private val decoder = GaiaTransport.StreamDecoder()

    @Volatile
    private var closed = false

    override val isOpen: Boolean
        get() = socket?.isConnected == true && !closed

    override fun open() {
        closed = false
        decoder.reset()
        val newSocket = device.createRfcommSocketToServiceRecord(UUID.fromString(sppUuid))
        newSocket.connect()
        // A yield may land while connect() is blocking; cancellation cannot interrupt it, so
        // re-check before publishing the socket to avoid handing the official app a conflict.
        if (isCancelled()) {
            runCatching { newSocket.close() }
            throw IOException("cancelled during connect")
        }
        socket = newSocket
        startReader(newSocket.inputStream)
    }

    override fun close() {
        if (closed) return
        closed = true
        readerThread?.interrupt()
        readerThread = null
        val s = socket
        socket = null
        runCatching { s?.close() }
    }

    override fun send(payload: ByteArray): Boolean = write(GaiaTransport.v4(payload))

    override fun sendRaw(frame: ByteArray): Boolean = write(frame)

    private fun write(frame: ByteArray): Boolean {
        val s = socket ?: return false
        logger.log(LinkLogger.Direction.TX, frame, "gaia")
        return try {
            s.outputStream.write(frame)
            s.outputStream.flush()
            true
        } catch (e: IOException) {
            handleLoss("write failed: ${e.message.orEmpty()}")
            false
        }
    }

    private fun startReader(inputStream: InputStream) {
        readerThread = Thread({
            val buffer = ByteArray(1024)
            try {
                while (!closed) {
                    val read = inputStream.read(buffer)
                    if (read < 0) {
                        handleLoss("stream ended")
                        return@Thread
                    }
                    if (read == 0) continue
                    val chunk = buffer.copyOfRange(0, read)
                    logger.log(LinkLogger.Direction.RX, chunk, "gaia")
                    decoder.feed(chunk).forEach { listener?.onPayload(it) }
                }
            } catch (e: IOException) {
                if (!closed) handleLoss("read error: ${e.message.orEmpty()}")
            }
        }, "HyperDrop-RfcommReader").apply {
            isDaemon = true
            start()
        }
    }

    private fun handleLoss(reason: String) {
        if (closed) return
        closed = true
        runCatching { socket?.close() }
        socket = null
        listener?.onClosed(reason)
    }
}
