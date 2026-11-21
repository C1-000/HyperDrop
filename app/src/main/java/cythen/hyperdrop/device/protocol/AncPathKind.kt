package cythen.hyperdrop.device.protocol

/**
 * Which GAIA feature carries ANC on a given model.
 *
 * [feature] is the `QTILFeature.getValue()` value from the official APK, not the enum
 * ordinal — the two diverge above 0x1b (`QTILFeature.smali:315-360`): AUDIO_FILE_STORAGE
 * is ordinal 0x1c / value 0x1d, LR_CHANNEL 0x1d / 0x1e, TOUCHV4 0x1e / 0x1f and
 * ANC_V2 0x1f / **0x20**. Getting this wrong shifts every ANC command word.
 */
enum class AncPathKind(val feature: Int) {
    /**
     * GAIA ANC V1 (feature 2).
     *
     * Not usable as-is and kept only so stored profiles keep compiling:
     *  - The official app never pre-registers `V3ANCPlugin`; it is built lazily by
     *    `QTILV3Vendor.buildPlugin()` and only when a device *reports* feature 2 in
     *    `GET_SUPPORTED_FEATURES` (`QTILV3Vendor.smali:1140-1152`).
     *  - The command words here were assumed (`0x04 0x01` read / `0x04 0x02` write) but
     *    the official V1 commands are GET_ANC_STATE=0x01 / SET_ANC_STATE=0x02, whose
     *    response command word is 0x05 — which the response matcher requires sub 0x03/0x04,
     *    so a real reply could never be recognised.
     * No profile in this build selects it; [DeviceModels] uses AUDIO_CURATION or ANC_V2.
     */
    @Deprecated(
        "Broken wire semantics and unreachable response parsing; use AUDIO_CURATION or ANC_V2.",
        ReplaceWith("AncPathKind.AUDIO_CURATION"),
    )
    ANC_V1(2),

    /** GAIA Audio Curation (feature 8): get/set current mode, gain, toggle config. */
    AUDIO_CURATION(8),

    /** GAIA ANC_V2 (feature 0x20): get/set current mode plus ANC switch configuration. */
    ANC_V2(32),
}
