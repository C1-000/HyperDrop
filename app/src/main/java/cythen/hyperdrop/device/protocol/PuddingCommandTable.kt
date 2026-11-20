package cythen.hyperdrop.device.protocol

/**
 * GAIA command table for MOONDROP PUDDING / MD-TWS-056.
 *
 * All PUDDING-specific byte constants, wire encodings and response parsing live here.
 * Supporting another GAIA-based MOONDROP model means writing one more [GaiaCommandTable]
 * and referencing it from that model's DeviceProfile.
 */
object PuddingCommandTable : GaiaCommandTable {

    override val sppUuid: String = GaiaTransport.SPP_UUID

    // GAIA v1 API-version request used by the official app before switching to v4.
    override val queryGaiaVersion: ByteArray = byteArrayOf(0x00, 0x0A, 0x03, 0x00)

    // Qualcomm GAIA v3 command words encode feature + packet type + command.
    // The official app logs are RESPONSE packets; TX must use COMMAND words instead.
    // ANC_V2 feature 0x20: COMMAND 0x4000, RESPONSE 0x4100.
    override val queryNoiseMode: ByteArray = byteArrayOf(0x00, 0x1D, 0x40, 0x03)

    // DAC_GAIN feature 0x0F: COMMAND 0x1E00, RESPONSE 0x1F00.
    // LED feature 0x13: COMMAND 0x2600, RESPONSE 0x2700.
    // Sub-command 0x01 = GET, 0x02 = SET.
    override val queryGainLevel: ByteArray = byteArrayOf(0x00, 0x1D, 0x1E, 0x01)
    override val queryLedState: ByteArray = byteArrayOf(0x00, 0x1D, 0x26, 0x01)

    // BATTERY feature 0x0D: COMMAND 0x1A00, RESPONSE 0x1B00.
    // PUDDING also returns charging-case id 3 in the level response.
    override val querySupportedBatteries: ByteArray = byteArrayOf(0x00, 0x1D, 0x1A, 0x00)
    override val queryBatteryLevels: ByteArray = byteArrayOf(
        0x00, 0x1D, 0x1A, 0x01, 0x01, 0x02
    )

    // Extended features PUDDING supports per MiuixMoondrop's ADAPTATION.md matrix.
    // VOICE (0x0E), CODEC_TYPE/LHDC (0x10) and ONEBRINGTWO (0x14).
    override val queryVoiceConfig: ByteArray = byteArrayOf(0x00, 0x1D, 0x1C, 0x01)
    override val queryLhdc: ByteArray = byteArrayOf(0x00, 0x1D, 0x20, 0x05)
    override val queryDualConnection: ByteArray = byteArrayOf(0x00, 0x1D, 0x28, 0x01)

    override val ancCycle: List<Int> = listOf(2, 4, 5, 3, 1)

    /**
     * PUDDING's noise-control wire values follow the Qualcomm ANC_V2 standard order
     * (OFF / ON / TRANSPARENT / ANTI_WIND / ADAPTIVE), not the order the UI presents.
     * [wireValue] is what the earbud understands, [uiStatus] is the index used by
     * HyperOS settings and must stay stable.
     */
    private enum class NoiseMode(val wireValue: Int, val uiStatus: Int) {
        OFF(0x00, 1),
        BASIC(0x01, 2),
        TRANSPARENCY(0x02, 3),
        ADAPTIVE(0x04, 4),
        WIND_REDUCTION(0x03, 5);

        companion object {
            fun fromWire(value: Int): NoiseMode? = entries.firstOrNull { it.wireValue == value }
            fun fromUiStatus(status: Int): NoiseMode? = entries.firstOrNull { it.uiStatus == status }
        }
    }

    // PUDDING's DAC gain wire direction is reversed from the UI order:
    // UI low/medium/high = 0/1/2, while the earbud expects wire 2/1/0.
    private enum class GainLevel(val uiValue: Int, val wireValue: Int) {
        LOW(0, 0x02),
        MEDIUM(1, 0x01),
        HIGH(2, 0x00);

        companion object {
            fun fromUi(value: Int): GainLevel? = entries.firstOrNull { it.uiValue == value }
            fun fromWire(value: Int): GainLevel? = entries.firstOrNull { it.wireValue == value }
        }
    }

    override fun setNoiseMode(uiStatus: Int): ByteArray? {
        val mode = NoiseMode.fromUiStatus(uiStatus) ?: return null
        return byteArrayOf(0x00, 0x1D, 0x40, 0x04, mode.wireValue.toByte())
    }

    override fun setGainLevel(uiValue: Int): ByteArray? {
        val gain = GainLevel.fromUi(uiValue) ?: return null
        return byteArrayOf(0x00, 0x1D, 0x1E, 0x02, gain.wireValue.toByte())
    }

    override fun setLedEnabled(enabled: Boolean): ByteArray = byteArrayOf(
        0x00, 0x1D, 0x26, 0x02, if (enabled) 0x01 else 0x00
    )

    override fun setPromptTone(enabled: Boolean, volume: Int, index: Int): ByteArray =
        byteArrayOf(
            0x00, 0x1D, 0x1C, 0x02,
            if (enabled) 0x01 else 0x00,
            volume.coerceIn(0, 100).toByte(),
            (index and 0xFF).toByte(),
        )

    override fun setLhdc(enabled: Boolean): ByteArray =
        byteArrayOf(0x00, 0x1D, 0x20, 0x06, if (enabled) 0x01 else 0x00)

    override fun setDualConnection(enabled: Boolean): ByteArray =
        byteArrayOf(0x00, 0x1D, 0x28, 0x02, if (enabled) 0x01 else 0x00)

    override fun gainUiToWire(uiValue: Int): Int? = GainLevel.fromUi(uiValue)?.wireValue
    override fun gainWireToUi(wire: Int): Int? = GainLevel.fromWire(wire)?.uiValue
    override fun ancUiToWire(uiStatus: Int): Int? = NoiseMode.fromUiStatus(uiStatus)?.wireValue

    override fun parse(payload: ByteArray): GaiaReport {
        // SET mode echo/notification: 00 1D 41 04 <mode>
        // GET mode response observed during official-app initialisation: 00 1D 41 03 <mode>
        if (matchesAny(payload, 0x41, 0x04, 0x03)) {
            val wire = payload[4].toInt() and 0xFF
            val mode = NoiseMode.fromWire(wire)
            if (mode != null) return GaiaReport.AncMode(mode.uiStatus, wire)
        }

        // Qualcomm Battery feature RESPONSE: 00 1D 1B 01 [batteryId, level]...
        // Battery ids: 1=left, 2=right, 3=charging case.
        if (payload.size >= 6 && matches(payload, 0x1B, 0x01)) {
            var left: Int? = null
            var right: Int? = null
            var case: Int? = null
            var i = 4
            while (i + 1 < payload.size) {
                val id = payload[i].toInt() and 0xFF
                val level = payload[i + 1].toInt() and 0xFF
                if (level in 0..100) {
                    when (id) {
                        1 -> left = level
                        2 -> right = level
                        3 -> case = level
                    }
                }
                i += 2
            }
            if (left != null || right != null || case != null) {
                return GaiaReport.Battery(left, right, case)
            }
        }

        // QTIL DAC gain: 00 1D 1F 01/02 <wire>.
        if (matchesAny(payload, 0x1F, 0x01, 0x02)) {
            val wire = payload[4].toInt() and 0xFF
            val ui = GainLevel.fromWire(wire)?.uiValue
            if (ui != null) return GaiaReport.GainLevel(ui, wire)
        }

        // QTIL LED: 00 1D 27 01/02 <0|1>.
        if (matchesAny(payload, 0x27, 0x01, 0x02)) {
            val value = payload[4].toInt() and 0xFF
            if (value == 0 || value == 1) return GaiaReport.LedEnabled(value == 1)
        }

        // VOICE: 00 1D 1D 01/02 <enabled, volume, index>.
        if (matchesAny(payload, 0x1D, 0x01, 0x02) && payload.size >= 7) {
            return GaiaReport.VoiceConfig(
                enabled = payload[4].toInt() and 0xFF == 1,
                volume = payload[5].toInt() and 0xFF,
                index = payload[6].toInt() and 0xFF,
            )
        }

        // CODEC_TYPE / LHDC: 00 1D 21 05/06 <0|1>.
        if (matchesAny(payload, 0x21, 0x05, 0x06)) {
            val value = payload[4].toInt() and 0xFF
            if (value == 0 || value == 1) return GaiaReport.Lhdc(value == 1)
        }

        // ONEBRINGTWO / dual connection: 00 1D 29 01/02 <0|1>.
        if (matchesAny(payload, 0x29, 0x01, 0x02)) {
            val value = payload[4].toInt() and 0xFF
            if (value == 0 || value == 1) return GaiaReport.DualConnection(value == 1)
        }

        // The first v1 response is the GAIA API-version response (feature 0x0A).
        if (payload.size >= 4 && payload[0] == 0x00.toByte() && payload[1] == 0x0A.toByte()) {
            return GaiaReport.GaiaNegotiation
        }

        return GaiaReport.Unknown
    }

    private fun matches(p: ByteArray, cmd: Int, sub: Int): Boolean =
        p.size >= 5 && p[0] == 0x00.toByte() && p[1] == 0x1D.toByte() &&
            p[2] == cmd.toByte() && p[3] == sub.toByte()

    private fun matchesAny(p: ByteArray, cmd: Int, vararg subs: Int): Boolean =
        p.size >= 5 && p[0] == 0x00.toByte() && p[1] == 0x1D.toByte() &&
            p[2] == cmd.toByte() && subs.any { p[3] == it.toByte() }
}
