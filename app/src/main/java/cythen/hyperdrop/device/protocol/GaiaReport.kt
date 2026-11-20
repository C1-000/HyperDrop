package cythen.hyperdrop.device.protocol

/**
 * Typed interpretation of one received GAIA payload.
 *
 * A [GaiaCommandTable] turns model-specific response bytes into one of these reports; the
 * session layer only ever sees semantics, never raw vendor bytes.
 */
sealed interface GaiaReport {
    /** Noise-control mode changed (or was reported). [uiStatus] is the HyperOS status index. */
    data class AncMode(val uiStatus: Int, val wire: Int) : GaiaReport

    /** Battery levels, each null when that component was not reported / out of range. */
    data class Battery(val left: Int?, val right: Int?, val case: Int?) : GaiaReport

    data class GainLevel(val uiValue: Int, val wire: Int) : GaiaReport

    data class LedEnabled(val enabled: Boolean) : GaiaReport

    /** Spatial audio on/off report (feature 0x18). */
    data class SpatialAudio(val enabled: Boolean) : GaiaReport

    /** Head-tracking on/off report (feature 0x18). */
    data class HeadTracking(val enabled: Boolean) : GaiaReport

    /** Voice-prompt configuration ([enabled], [volume] 0..100, [index]). */
    data class VoiceConfig(val enabled: Boolean, val volume: Int, val index: Int) : GaiaReport

    /** LHDC codec toggle report (feature 0x10). */
    data class Lhdc(val enabled: Boolean) : GaiaReport

    /** Dual-connection toggle report (feature 0x14). */
    data class DualConnection(val enabled: Boolean) : GaiaReport

    /** Bitmask of supported GAIA feature ids. */
    data class SupportedFeatures(val features: Set<Int>) : GaiaReport

    /** GAIA v1 API-version response; negotiation only, no state to publish. */
    object GaiaNegotiation : GaiaReport

    /** Payload that this command table does not recognise. */
    object Unknown : GaiaReport
}
