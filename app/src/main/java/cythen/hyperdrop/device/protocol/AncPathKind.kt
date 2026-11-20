package cythen.hyperdrop.device.protocol

/** Which GAIA feature carries ANC on a given model. */
enum class AncPathKind(val feature: Int) {
    ANC_V1(2),
    AUDIO_CURATION(8),
    ANC_V2(32),
}
