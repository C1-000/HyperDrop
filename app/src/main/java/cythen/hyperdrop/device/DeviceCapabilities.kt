package cythen.hyperdrop.device

import cythen.hyperdrop.device.protocol.AncPathKind
import cythen.hyperdrop.pods.AncImplementation

/**
 * Feature availability for one device profile.
 *
 * Values are declarative and live on the [DeviceProfile]; user overrides are applied
 * on top by [DeviceRegistry.detectDeviceCapabilities]. No model name is inspected here.
 *
 * ANC / gain translation tables are carried here so the command layer and UI read the
 * same source of truth instead of each model hard-coding its own mapping.
 */
data class DeviceCapabilities(
    val adaptiveSupported: Boolean,
    val spatialAudioSupported: Boolean,
    val spatialSoundSwitchSupported: Boolean,
    val ancImplementation: AncImplementation,
    /** Selectable ANC strength tabs, in UI order. Empty means "no strength picker". */
    val ancModes: List<AncModeOption> = emptyList(),

    // ---- GAIA ANC path + value translation ---------------------------------------

    /** Which GAIA feature carries ANC for this model. */
    val ancPath: AncPathKind = AncPathKind.ANC_V2,
    /** UI-status -> wire-value table for ANC, parallel to [ancModes]. */
    val ancSetMap: IntArray = intArrayOf(),
    /** Device wire-value -> UI-status table; null means "use the inverse of ancSetMap". */
    val ancGetMap: IntArray? = null,

    // ---- Device control capabilities (DcProfile / FeatureProfile) ----------------

    val gainSupported: Boolean = false,
    /** UI gain index (0..n-1) -> wire value. */
    val gainMap: IntArray = intArrayOf(0, 1, 2),
    /** User-facing labels for each gain index. */
    val gainLabels: List<String> = emptyList(),
    val ledSupported: Boolean = false,
    val headTrackingSupported: Boolean = false,
    val promptToneSupported: Boolean = false,
    val promptVolumeSupported: Boolean = false,
    val lhdcSupported: Boolean = false,
    val dualConnectionSupported: Boolean = false,
    val singleDeviceBattery: Boolean = false,
) {
    /**
     * ANC paths worth trying for this model, most likely first.
     *
     * The profile's own [ancPath] always leads. The remaining GAIA features that can carry an
     * ANC mode follow as fallbacks, so a device whose firmware answers on a different path
     * than the one we inferred still has a chance. Multiple paths have to be tried in order
     * because the feature that actually answers is only knowable from the device's
     * `GET_SUPPORTED_FEATURES` reply.
     *
     * Empty when the model declares no ANC at all (no [ancSetMap]), and empty for the
     * deprecated [AncPathKind.ANC_V1] path, whose command words do not match the wire format
     * this codebase speaks.
     */
    val ancPathCandidates: List<AncPathKind>
        get() {
            if (ancSetMap.isEmpty()) return emptyList()
            if (ancPath == AncPathKind.ANC_V1) return emptyList()
            return listOf(ancPath) + FALLBACK_ANC_PATHS.filter { it != ancPath }
        }

    companion object {
        /**
         * GAIA features that can carry an ANC mode, in preference order. ANC_V2 is the modern
         * numeric-mode feature; AUDIO_CURATION is the older semantic one. ANC_V1 is excluded
         * on purpose — see [AncPathKind].
         */
        val FALLBACK_ANC_PATHS: List<AncPathKind> =
            listOf(AncPathKind.ANC_V2, AncPathKind.AUDIO_CURATION)

        /** Conservative baseline for a name that matches no known model. */
        val NONE = DeviceCapabilities(
            adaptiveSupported = false,
            spatialAudioSupported = false,
            spatialSoundSwitchSupported = false,
            ancImplementation = AncImplementation.STANDARD,
            ancModes = emptyList(),
            ancPath = AncPathKind.ANC_V2,
            ancSetMap = intArrayOf(),
            ancGetMap = null,
        )
    }
}
