package cythen.hyperdrop.device

import cythen.hyperdrop.config.ConfigManager
import cythen.hyperdrop.device.protocol.AncPathKind
import cythen.hyperdrop.device.protocol.GaiaFeature
import cythen.hyperdrop.device.protocol.ObservedFeatures
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

    /** Dotted-line accessor for UI code that only needs the shape, not the whole profile. */
    fun formFactorFor(name: String?): DeviceFormFactor = profileFor(name).formFactor

    /** The shared wire vocabulary this device speaks; shown in the adaptation page. */
    fun controlFor(name: String?): ControlProfile = profileFor(name).control

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

    /**
     * Capabilities for [deviceName] after folding in what the earbud itself reported through
     * `GET_SUPPORTED_FEATURES`.
     *
     * Gating, never overriding (see [ObservedFeatures]):
     *  * user overrides still win, because they are applied first and are never undone here;
     *  * a declared capability is never switched *off* — a partial or empty feature list is
     *    normal and must not hide controls that already work;
     *  * a control that the profile left off may be switched *on*, but only when the device
     *    reports the GAIA feature that carries it and the profile carries the translation
     *    table that makes the control meaningful;
     *  * the ANC path may move to another candidate feature only for unverified profiles, so
     *    a model confirmed on real hardware keeps the path that was confirmed.
     *
     * An empty [observed] returns the plain [detectDeviceCapabilities] result.
     */
    fun applyObservedFeatures(
        deviceName: String,
        observed: ObservedFeatures,
        adaptiveOverride: Int = ConfigManager.CAPABILITY_OVERRIDE_AUTO,
        spatialAudioOverride: Int = ConfigManager.CAPABILITY_OVERRIDE_AUTO,
        spatialSoundSwitchOverride: Int = ConfigManager.CAPABILITY_OVERRIDE_AUTO,
        ancImplementationOverride: Int = ConfigManager.CAPABILITY_OVERRIDE_AUTO,
    ): DeviceCapabilities {
        val profile = profileFor(deviceName)
        val base = detectDeviceCapabilities(
            deviceName = deviceName,
            adaptiveOverride = adaptiveOverride,
            spatialAudioOverride = spatialAudioOverride,
            spatialSoundSwitchOverride = spatialSoundSwitchOverride,
            ancImplementationOverride = ancImplementationOverride,
        )
        if (observed.isEmpty) return base
        // An unrecognised device has no translation tables, so a reported feature says nothing
        // about how to drive it. Gate only models we have a profile for.
        if (!isSupported(deviceName)) return base

        val ancPath = resolveAncPath(profile, base, observed)
        return base.copy(
            adaptiveSupported = base.adaptiveSupported || observed.supports(GaiaFeature.AUDIO_CURATION),
            spatialAudioSupported = base.spatialAudioSupported || observed.supports(GaiaFeature.SPATIAL_AUDIO),
            // Head tracking is a sub-command of the spatial-audio feature, so it needs both.
            headTrackingSupported = base.headTrackingSupported ||
                (observed.supports(GaiaFeature.SPATIAL_AUDIO) && base.spatialSoundSwitchSupported),
            gainSupported = base.gainSupported || (observed.supports(GaiaFeature.DAC_GAIN) && base.gainMap.isNotEmpty()),
            ledSupported = base.ledSupported || observed.supports(GaiaFeature.LED),
            promptToneSupported = base.promptToneSupported || observed.supports(GaiaFeature.VOICE),
            promptVolumeSupported = base.promptVolumeSupported ||
                (observed.supports(GaiaFeature.VOICE) && base.promptToneSupported),
            lhdcSupported = base.lhdcSupported || observed.supports(GaiaFeature.CODEC_TYPE),
            dualConnectionSupported = base.dualConnectionSupported || observed.supports(GaiaFeature.ONEBRINGTWO),
            ancPath = ancPath,
        )
    }

    /**
     * First ANC path candidate whose feature the device reported, falling back to the
     * profile's own path. Verified profiles keep their declared path unconditionally — a
     * model confirmed on real hardware must not regress because a firmware build reports a
     * different feature set.
     */
    private fun resolveAncPath(
        profile: DeviceProfile,
        base: DeviceCapabilities,
        observed: ObservedFeatures,
    ): AncPathKind {
        val candidates = base.ancPathCandidates
        if (candidates.isEmpty()) return base.ancPath
        if (profile.verified) return base.ancPath
        return candidates.firstOrNull { observed.supports(it.feature) } ?: base.ancPath
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
