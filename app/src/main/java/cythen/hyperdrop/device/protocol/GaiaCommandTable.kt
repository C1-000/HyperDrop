package cythen.hyperdrop.device.protocol

/**
 * Everything that varies between models on the GAIA command layer.
 *
 * A command table owns the byte-level vocabulary of one device: which control commands
 * exist, how ANC / gain / LED values are encoded on the wire, and how to turn a received
 * payload into a [GaiaReport]. Framing itself is model-independent and lives in
 * [GaiaTransport].
 *
 * Newer controls (spatial audio, voice prompts, LHDC, dual-connection, feature discovery)
 * have default no-op implementations so existing tables keep compiling; a capable model
 * overrides them (see [GenericGaiaCommandTable]).
 */
interface GaiaCommandTable {
    /** SPP UUID used to open the RFCOMM socket. */
    val sppUuid: String

    // ---- Negotiation / handshake -------------------------------------------------

    val queryGaiaVersion: ByteArray

    // ---- Status GET commands -----------------------------------------------------

    val queryNoiseMode: ByteArray
    val queryGainLevel: ByteArray
    val queryLedState: ByteArray
    val querySupportedBatteries: ByteArray
    val queryBatteryLevels: ByteArray

    // ---- Control SET commands ----------------------------------------------------

    fun setNoiseMode(uiStatus: Int): ByteArray?
    fun setGainLevel(uiValue: Int): ByteArray?
    fun setLedEnabled(enabled: Boolean): ByteArray

    // ---- Value translation (for optimistic UI updates before the echo arrives) ----

    fun gainUiToWire(uiValue: Int): Int?
    fun gainWireToUi(wire: Int): Int?
    fun ancUiToWire(uiStatus: Int): Int?

    // ---- Optional extended controls ---------------------------------------------

    /** GAIA feature that carries ANC. Default: ANC_V2. */
    val ancPath: AncPathKind get() = AncPathKind.ANC_V2

    val querySupportedFeatures: ByteArray? get() = null
    val querySpatialAudio: ByteArray? get() = null
    val queryHeadTracking: ByteArray? get() = null
    val queryVoiceConfig: ByteArray? get() = null
    val queryLhdc: ByteArray? get() = null
    val queryDualConnection: ByteArray? get() = null

    fun setSpatialAudio(enabled: Boolean): ByteArray? = null
    fun setHeadTracking(enabled: Boolean): ByteArray? = null
    fun setTransparencyVocal(enabled: Boolean): ByteArray? = null
    fun setPromptTone(enabled: Boolean, volume: Int = 50, index: Int = 0): ByteArray? = null
    fun setLhdc(enabled: Boolean): ByteArray? = null
    fun setDualConnection(enabled: Boolean): ByteArray? = null

    // ---- Receive path ------------------------------------------------------------

    fun parse(payload: ByteArray): GaiaReport

    /** Ordered ANC uiStatus cycle used by the "cycle ANC" action. */
    val ancCycle: List<Int>
}
