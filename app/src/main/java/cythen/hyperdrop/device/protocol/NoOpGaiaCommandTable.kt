package cythen.hyperdrop.device.protocol

/**
 * Command table for the conservative fallback profile. It never produces a command and
 * never claims to understand a payload, so an unknown device can never be driven with
 * PUDDING-specific bytes by accident.
 */
object NoOpGaiaCommandTable : GaiaCommandTable {
    override val sppUuid: String = GaiaTransport.SPP_UUID
    override val queryGaiaVersion: ByteArray = ByteArray(0)
    override val queryNoiseMode: ByteArray = ByteArray(0)
    override val queryGainLevel: ByteArray = ByteArray(0)
    override val queryLedState: ByteArray = ByteArray(0)
    override val querySupportedBatteries: ByteArray = ByteArray(0)
    override val queryBatteryLevels: ByteArray = ByteArray(0)
    override val ancCycle: List<Int> = emptyList()

    override fun setNoiseMode(uiStatus: Int): ByteArray? = null
    override fun setGainLevel(uiValue: Int): ByteArray? = null
    override fun setLedEnabled(enabled: Boolean): ByteArray = ByteArray(0)
    override fun gainUiToWire(uiValue: Int): Int? = null
    override fun gainWireToUi(wire: Int): Int? = null
    override fun ancUiToWire(uiStatus: Int): Int? = null
    override fun parse(payload: ByteArray): GaiaReport = GaiaReport.Unknown
}
