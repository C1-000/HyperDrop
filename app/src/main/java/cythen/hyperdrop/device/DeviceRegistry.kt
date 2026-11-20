package cythen.hyperdrop.device

import cythen.hyperdrop.config.ConfigManager
import cythen.hyperdrop.pods.AncImplementation

/**
 * Single source of truth for "which device is this, and what can it do".
 *
 * Matching is data-driven via [DeviceModels]: the longest alias that is a substring of the
 * Bluetooth name wins; a MOONDROP-branded name that matches no model falls back to a
 * conservative inferred profile; anything else falls back to [StandardBluetoothProfile],
 * which claims no capabilities.
 *
 * Adding a MOONDROP model = one entry in [DeviceModels.models]. No change needed in
 * pods/, hook/ or ui/.
 */
object DeviceRegistry {

    /** Registered real models (verified + protocol-inferred). */
    val registeredProfiles: List<DeviceProfile> = DeviceModels.models

    /** Conservative fallback for a non-MOONDROP device. */
    val fallbackProfile: DeviceProfile = StandardBluetoothProfile

    fun profileFor(name: String?): DeviceProfile =
        DeviceModels.match(name) ?: fallbackProfile

    /** True when a concrete model (verified or inferred MOONDROP) owns this name. */
    fun isSupported(name: String?): Boolean =
        name != null && DeviceModels.match(name) != null

    /** Lookup by stable id, for IPC payloads that carry a device id (phase F). */
    fun profileById(id: String): DeviceProfile =
        registeredProfiles.firstOrNull { it.id == id } ?: fallbackProfile

    /**
     * Capabilities for [deviceName] with per-feature user overrides applied on top of the
     * profile's auto-detected values.
     */
    fun detectDeviceCapabilities(
        deviceName: String,
        adaptiveOverride: Int = ConfigManager.CAPABILITY_OVERRIDE_AUTO,
        spatialAudioOverride: Int = ConfigManager.CAPABILITY_OVERRIDE_AUTO,
        spatialSoundSwitchOverride: Int = ConfigManager.CAPABILITY_OVERRIDE_AUTO,
        ancImplementationOverride: Int = ConfigManager.CAPABILITY_OVERRIDE_AUTO,
    ): DeviceCapabilities {
        val base = profileFor(deviceName).capabilities
        return base.copy(
            adaptiveSupported = resolveCapability(adaptiveOverride, base.adaptiveSupported),
            spatialAudioSupported = resolveCapability(spatialAudioOverride, base.spatialAudioSupported),
            spatialSoundSwitchSupported = resolveCapability(spatialSoundSwitchOverride, base.spatialSoundSwitchSupported),
            ancImplementation = resolveAncImplementation(ancImplementationOverride, base.ancImplementation),
        )
    }

    private fun resolveCapability(override: Int, autoDetected: Boolean): Boolean = when (override) {
        ConfigManager.CAPABILITY_OVERRIDE_FORCE_ENABLED -> true
        ConfigManager.CAPABILITY_OVERRIDE_FORCE_DISABLED -> false
        else -> autoDetected
    }

    private fun resolveAncImplementation(
        override: Int,
        autoDetected: AncImplementation,
    ): AncImplementation = when (override) {
        ConfigManager.CAPABILITY_OVERRIDE_FORCE_ENABLED -> AncImplementation.COMPATIBLE
        ConfigManager.CAPABILITY_OVERRIDE_FORCE_DISABLED -> AncImplementation.STANDARD
        else -> autoDetected
    }
}
