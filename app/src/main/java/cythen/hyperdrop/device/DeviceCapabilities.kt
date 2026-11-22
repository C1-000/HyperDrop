package cythen.hyperdrop.device

import cythen.hyperdrop.pods.AncImplementation

/**
 * Runtime capability view for one device.
 *
 * Produced from a [CapabilityProfile] declaration via [CapabilityProfile.toCapabilities],
 * then enriched by runtime observation in [DeviceRegistry.resolveCapabilities] and finally
 * overlaid with the user's per-feature overrides. UI reads this type exclusively.
 *
 * Wire-level ANC / gain maps are NOT part of this view — they live in the control profile,
 * so changing a model's ANC path no longer requires duplicating its capabilities.
 */
data class DeviceCapabilities(
    val adaptiveSupported: Boolean,
    val spatialAudioSupported: Boolean,
    val spatialSoundSwitchSupported: Boolean,
    val ancImplementation: AncImplementation,
    /** Selectable ANC strength tabs, in UI order. Empty means "no strength picker". */
    val ancModes: List<AncModeOption> = emptyList(),

    // ---- Device control capabilities (DcProfile / FeatureProfile) ----------------

    val gainSupported: Boolean = false,
    /** User-facing labels for each gain index. */
    val gainLabels: List<String> = emptyList(),
    val ledSupported: Boolean = false,
    val headTrackingSupported: Boolean = false,
    val promptToneSupported: Boolean = false,
    val promptVolumeSupported: Boolean = false,
    val lhdcSupported: Boolean = false,
    val lc3Supported: Boolean = false,
    val dualConnectionSupported: Boolean = false,
    /** How the battery card renders. Display-only; the battery query is unchanged. */
    val batteryForm: BatteryForm = BatteryForm.TWS_THREE_WAY,
) {
    companion object {
        /** Conservative baseline for a name that matches no known model. */
        val NONE = DeviceCapabilities(
            adaptiveSupported = false,
            spatialAudioSupported = false,
            spatialSoundSwitchSupported = false,
            ancImplementation = AncImplementation.STANDARD,
            ancModes = emptyList(),
        )
    }
}
