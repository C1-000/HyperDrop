package cythen.hyperdrop.pods.link

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import cythen.hyperdrop.device.PodTransport

/**
 * Picks the transport for a device the way the official app does: by asking the device.
 *
 * The official client never derives the transport from the model — `connectDeviceBT` calls
 * `resolveBluetoothType(device)` which reads a per-device `transportType` flag, then branches
 * LOW_ENERGY → GATT / CLASSIC → RFCOMM. We have no such flag, so we reproduce the *behaviour*
 * by checking which services the device actually advertises before committing to a socket.
 *
 * Probe order is deliberate: BLE GAIA first (a GATT service can be inspected without
 * connecting and without disturbing audio), then RFCOMM (the verified path, but opening it
 * takes the channel), then the 9ECA BLE service (which can also be probed cheaply, but is
 * only useful on devices that expose it, and cannot carry ANC).
 */
object LinkProbe {

    /** GAIA over BLE GATT service, from the official `ServiceUuid$Gaia` constant. */
    const val GAIA_GATT_SERVICE: String = "00001100-d102-11e1-9b23-00025b00a5a5"

    /** Vendor 9ECA service (audio source switch / PEQ), lower-cased for comparison. */
    const val SRC_9ECA_SERVICE: String = "9eca0000-7f3a-4f32-9a38-a91b2c6e0100"

    /** Standard SPP service the GAIA RFCOMM transport connects to. */
    const val SPP_SERVICE: String = "00001101-0000-1000-8000-00805f9b34fb"

    @SuppressLint("MissingPermission")
    fun probe(context: Context, device: BluetoothDevice): ProbeResult {
        val manager = context.getSystemService(BluetoothManager::class.java)
        val adapter = manager?.adapter ?: return ProbeResult.Unavailable("no bluetooth adapter")

        val uuids = device.uuids?.map { it.uuid.toString().lowercase() }.orEmpty()
        if (uuids.isNotEmpty()) {
            return classify(device.address.orEmpty(), uuids)
        }

        // Service discovery has not run for this device yet. Ask the stack to fetch SDP now;
        // a fresh result usually lands within a second, which is why the caller retries.
        val bonded = adapter.bondedDevices?.any { it.address == device.address } == true
        if (bonded) {
            runCatching { device.fetchUuidsWithSdp() }
            return ProbeResult.Pending("SDP fetch requested")
        }
        return ProbeResult.Pending("device not bonded; SDP unavailable")
    }

    /** Map a discovered UUID set onto the transport priority the session should try. */
    fun classify(address: String, uuids: Collection<String>): ProbeResult {
        val normalized = uuids.map { it.lowercase() }.toSet()
        val candidates = buildList {
            if (GAIA_GATT_SERVICE in normalized) add(PodTransport.BLE_GAIA)
            if (SPP_SERVICE in normalized) add(PodTransport.RFCOMM_GAIA)
            if (SRC_9ECA_SERVICE in normalized) add(PodTransport.BLE_SRC_9ECA)
        }
        return if (candidates.isEmpty()) {
            ProbeResult.NoKnownService(address)
        } else {
            ProbeResult.Candidates(candidates)
        }
    }

    /**
     * Merge a profile's declared hint with what the device advertises.
     *
     * Advertised services win: they describe this specific unit's firmware, whereas the
     * profile hint is a compile-time guess. The hint is only used to order de-duplicated
     * candidates and to keep supporting a device whose SDP is not readable.
     */
    fun resolveOrder(declared: List<PodTransport>, probed: List<PodTransport>): List<PodTransport> {
        if (probed.isEmpty()) return declared
        val ordered = buildList {
            addAll(probed)
            declared.forEach { if (it !in probed) add(it) }
        }
        return ordered.distinct()
    }

    sealed interface ProbeResult {
        /** Advertised services identified at least one known transport. */
        data class Candidates(val transports: List<PodTransport>) : ProbeResult

        /** Service discovery is not done yet; retry shortly. */
        data class Pending(val why: String) : ProbeResult

        /** The device advertises none of the services we know how to talk to. */
        data class NoKnownService(val address: String) : ProbeResult

        /** Bluetooth is not usable at all on this device/system. */
        data class Unavailable(val why: String) : ProbeResult
    }

    /** Convenience for logging a probe result without repeating the `when` at call sites. */
    fun describe(result: ProbeResult): String = when (result) {
        is ProbeResult.Candidates -> "candidates=${result.transports.joinToString { it.name }}"
        is ProbeResult.Pending -> "pending: ${result.why}"
        is ProbeResult.NoKnownService -> "no known GAIA/9ECA service for ${result.address}"
        is ProbeResult.Unavailable -> "unavailable: ${result.why}"
    }

    /** True when the stack reports the device as connected over any profile. */
    @SuppressLint("MissingPermission")
    fun isA2dpConnected(context: Context, device: BluetoothDevice): Boolean {
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return false
        val adapter = manager.adapter ?: return false
        return adapter.getProfileConnectionState(BluetoothProfile.A2DP) ==
            BluetoothProfile.STATE_CONNECTED
    }
}
