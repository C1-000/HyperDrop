package cythen.hyperdrop.device

import cythen.hyperdrop.pods.AncImplementation

/**
 * Feature availability for one device profile.
 *
 * Values are declarative and live on the [DeviceProfile]; user overrides are applied
 * on top by [DeviceRegistry.detectDeviceCapabilities]. No model name is inspected here.
 */
data class DeviceCapabilities(
    val adaptiveSupported: Boolean,
    val spatialAudioSupported: Boolean,
    val spatialSoundSwitchSupported: Boolean,
    val ancImplementation: AncImplementation,
    /** Selectable ANC strength tabs, in UI order. Empty means "no strength picker". */
    val ancModes: List<AncModeOption> = emptyList(),
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
