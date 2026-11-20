package cythen.hyperdrop.device

import kotlinx.serialization.Serializable

/**
 * A fake device created from an existing [DeviceProfile] for debugging.
 *
 * Debug-only concept: [address] is a locally administered MAC (02:00:00:00:00:xx) that never
 * exists on the Bluetooth stack, and [displayName] is the profile name snapshot so the normal
 * name-based capability lookup ([DeviceRegistry.detectDeviceCapabilities]) resolves the very
 * same profile when the simulated session reports the device as connected.
 *
 * Persisted in [cythen.hyperdrop.config.AppConfig]; connection state itself is intentionally
 * in-memory only (see VirtualDeviceSession), because a simulated link cannot survive process death.
 */
@Serializable
data class SimulatedDevice(
    val profileId: String,
    val address: String,
    val displayName: String,
    val createdAt: Long = 0L,
)
