package cythen.hyperdrop.device

import cythen.hyperdrop.config.ConfigManager
import cythen.hyperdrop.device.CapabilityProfile.Companion.OBSERVATION_RULES
import cythen.hyperdrop.pods.AncImplementation

/**
 * Single source of truth for "which device is this, and what can it do".
 *
 * Matching is data-driven via [DeviceModels]: the longest alias that is a substring of the
 * Bluetooth name wins; a MOONDROP-branded name that matches no model falls back to a
 * conservative inferred profile that probes capabilities on connect (layer-2 fallback);
 * anything else falls back to [StandardBluetoothProfile], which claims no capabilities.
 *
 * Runtime observation (GET_SUPPORTED_FEATURES) may OPEN capabilities that the factory
 * declaration left closed, but never closes a declared one — the declaration is
 * authoritative on conflict. See [resolveCapabilities]. Observations are cached per
 * device name so the UI reflects them even before the next query cycle.
 *
 * Adding a MOONDROP model = one entry in [DeviceModels.models]. No change needed in
 * pods/, hook/ or ui/.
 */
object DeviceRegistry {

    /** Registered real models (verified + protocol-inferred). */
    val registeredProfiles: List<DeviceProfile> = DeviceModels.models

    /** Conservative fallback for a non-MOONDROP device. */
    val fallbackProfile: DeviceProfile = StandardBluetoothProfile

    /** Per-device feature ids observed from GET_SUPPORTED_FEATURES at runtime. */
    private val observedFeatures = HashMap<String, Set<Int>>()

    /** Normalised key so the writer (session) and reader (UI) always agree. */
    private fun featureKey(deviceName: String): String = DeviceModels.normalize(deviceName)

    fun profileFor(name: String?): DeviceProfile =
        DeviceModels.match(name) ?: fallbackProfile

    /** True when a concrete model (verified or inferred MOONDROP) owns this name. */
    fun isSupported(name: String?): Boolean =
        name != null && DeviceModels.match(name) != null

    /** Lookup by stable id, for IPC payloads that carry a device id. */
    fun profileById(id: String): DeviceProfile =
        registeredProfiles.firstOrNull { it.id == id } ?: fallbackProfile

    /**
     * Record features reported by the device and keep only the union — observation can
     * accumulate but never retract a feature within a session.
     */
    fun recordObservedFeatures(deviceName: String, features: Set<Int>) {
        val key = featureKey(deviceName)
        val current = observedFeatures[key].orEmpty()
        observedFeatures[key] = current + features
    }

    fun clearObservedFeatures(deviceName: String) {
        observedFeatures.remove(featureKey(deviceName))
    }

    /** Whether this device's control profile asks for GET_SUPPORTED_FEATURES on connect. */
    fun shouldProbeOnConnect(name: String?): Boolean = profileFor(name).controlProfile.probeOnConnect

    /**
     * Apply observed features to a declared capability view. Two-state semantics:
     * a declared capability stays authoritative; each observed feature opens the
     * capability it governs. Never closes anything.
     */
    internal fun resolveCapabilities(
        declared: DeviceCapabilities,
        features: Set<Int>,
    ): DeviceCapabilities {
        if (features.isEmpty()) return declared
        var result = declared
        OBSERVATION_RULES.forEach { rule ->
            if (features.contains(rule.featureId)) result = rule.open(result)
        }
        return result
    }

    /**
     * Capabilities for [deviceName]: factory declaration + runtime observation + the
     * per-feature user overrides applied on top.
     */
    fun detectDeviceCapabilities(
        deviceName: String,
        adaptiveOverride: Int = ConfigManager.CAPABILITY_OVERRIDE_AUTO,
        spatialAudioOverride: Int = ConfigManager.CAPABILITY_OVERRIDE_AUTO,
        spatialSoundSwitchOverride: Int = ConfigManager.CAPABILITY_OVERRIDE_AUTO,
        ancImplementationOverride: Int = ConfigManager.CAPABILITY_OVERRIDE_AUTO,
    ): DeviceCapabilities {
        val declared = profileFor(deviceName).capabilities
        val observed = observedFeatures[featureKey(deviceName)].orEmpty()
        val base = resolveCapabilities(declared, observed)
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
