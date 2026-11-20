package cythen.hyperdrop.device.protocol

/**
 * GAIA command table for protocol-inferred MOONDROP models.
 *
 * All command bytes are the canonical Qualcomm GAIA constants verified against
 * MiuixMoondrop's core/Gaia.kt. ANC and gain wire values are supplied per-model via
 * translation tables, so one class serves every inferred model. When a command is not
 * supported by a given model the corresponding query/set is simply null/no-op and the
 * session layer degrades silently.
 */
class GenericGaiaCommandTable(
    private val ancPathKind: AncPathKind = AncPathKind.ANC_V2,
    private val ancSetMap: IntArray = intArrayOf(),
    private val ancGetMap: IntArray = intArrayOf(),
    private val gainMap: IntArray = intArrayOf(0, 1, 2),
) : GaiaCommandTable {

    override val sppUuid: String = GaiaTransport.SPP_UUID
    override val queryGaiaVersion: ByteArray = byteArrayOf(0x00, 0x0A, 0x03, 0x00)

    override val ancPath: AncPathKind get() = ancPathKind

    override val queryNoiseMode: ByteArray = when (ancPathKind) {
        AncPathKind.ANC_V2 -> byteArrayOf(0x00, 0x1D, 0x40, 0x03)
        AncPathKind.AUDIO_CURATION -> byteArrayOf(0x00, 0x1D, 0x10, 0x03)
        AncPathKind.ANC_V1 -> byteArrayOf(0x00, 0x1D, 0x04, 0x01)
    }

    override val queryGainLevel: ByteArray = byteArrayOf(0x00, 0x1D, 0x1E, 0x01)
    override val queryLedState: ByteArray = byteArrayOf(0x00, 0x1D, 0x26, 0x01)
    override val querySupportedBatteries: ByteArray = byteArrayOf(0x00, 0x1D, 0x1A, 0x00)
    override val queryBatteryLevels: ByteArray = byteArrayOf(0x00, 0x1D, 0x1A, 0x01, 0x01, 0x02)

    override val ancCycle: List<Int> = (1..8).filter { ui -> ancSetMap.getOrNull(ui)?.let { it >= 0 } == true }

    // ---- Extended feature queries ------------------------------------------------

    override val querySupportedFeatures: ByteArray = byteArrayOf(0x00, 0x1D, 0x00, 0x01)
    override val querySpatialAudio: ByteArray = byteArrayOf(0x00, 0x1D, 0x24, 0x01)
    override val queryHeadTracking: ByteArray = byteArrayOf(0x00, 0x1D, 0x24, 0x03)
    override val queryVoiceConfig: ByteArray = byteArrayOf(0x00, 0x1D, 0x1C, 0x01)
    override val queryLhdc: ByteArray = byteArrayOf(0x00, 0x1D, 0x20, 0x05)
    override val queryDualConnection: ByteArray = byteArrayOf(0x00, 0x1D, 0x28, 0x01)

    // ---- SET commands ------------------------------------------------------------

    override fun setNoiseMode(uiStatus: Int): ByteArray? {
        val wire = ancSetMap.getOrNull(uiStatus)?.takeIf { it >= 0 } ?: return null
        return when (ancPath) {
            AncPathKind.ANC_V2 -> byteArrayOf(0x00, 0x1D, 0x40, 0x04, wire.toByte())
            AncPathKind.AUDIO_CURATION -> byteArrayOf(0x00, 0x1D, 0x10, 0x04, wire.toByte())
            AncPathKind.ANC_V1 -> byteArrayOf(0x00, 0x1D, 0x04, 0x02, wire.toByte())
        }
    }

    override fun setGainLevel(uiValue: Int): ByteArray? {
        val wire = gainMap.getOrNull(uiValue) ?: return null
        return byteArrayOf(0x00, 0x1D, 0x1E, 0x02, wire.toByte())
    }

    override fun setLedEnabled(enabled: Boolean): ByteArray = byteArrayOf(
        0x00, 0x1D, 0x26, 0x02, if (enabled) 0x01 else 0x00
    )

    override fun setSpatialAudio(enabled: Boolean): ByteArray =
        byteArrayOf(0x00, 0x1D, 0x24, 0x02, if (enabled) 0x01 else 0x00)

    override fun setHeadTracking(enabled: Boolean): ByteArray =
        byteArrayOf(0x00, 0x1D, 0x24, 0x04, if (enabled) 0x01 else 0x00)

    override fun setTransparencyVocal(enabled: Boolean): ByteArray =
        byteArrayOf(0x00, 0x1D, 0x1C, 0x02, if (enabled) 0x01 else 0x00, 0x00, 0x00)

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

    // ---- Value translation -------------------------------------------------------

    override fun gainUiToWire(uiValue: Int): Int? = gainMap.getOrNull(uiValue)
    override fun gainWireToUi(wire: Int): Int? = gainMap.indexOf(wire).takeIf { it >= 0 }

    override fun ancUiToWire(uiStatus: Int): Int? =
        ancSetMap.getOrNull(uiStatus)?.takeIf { it >= 0 }

    // ---- Receive path ------------------------------------------------------------

    override fun parse(payload: ByteArray): GaiaReport {
        if (payload.size < 4 || payload[0] != 0x00.toByte() || payload[1] != 0x1D.toByte()) {
            return GaiaReport.Unknown
        }
        val cmd = payload[2].toInt() and 0xFF
        val sub = payload[3].toInt() and 0xFF

        // ANC responses (SET echo / GET) carry one value byte at index 4.
        val ancResponseCmd = when (ancPath) {
            AncPathKind.ANC_V2 -> 0x41
            AncPathKind.AUDIO_CURATION -> 0x11
            AncPathKind.ANC_V1 -> 0x05
        }
        if (cmd == ancResponseCmd && (sub == 0x03 || sub == 0x04) && payload.size >= 5) {
            val reported = payload[4].toInt() and 0xFF
            val ui = ancGetMap.getOrNull(reported)?.takeIf { it >= 0 }
                ?: ancSetMap.indexOf(reported).takeIf { it >= 0 }
            if (ui != null) return GaiaReport.AncMode(ui, reported)
        }

        // Battery: 00 1D 1B 01 [id, level]...
        if (cmd == 0x1B && sub == 0x01 && payload.size >= 6) {
            var left: Int? = null
            var right: Int? = null
            var case: Int? = null
            var i = 4
            while (i + 1 < payload.size) {
                val id = payload[i].toInt() and 0xFF
                val level = payload[i + 1].toInt() and 0xFF
                if (level in 0..100) when (id) {
                    1 -> left = level
                    2 -> right = level
                    3 -> case = level
                }
                i += 2
            }
            if (left != null || right != null || case != null) return GaiaReport.Battery(left, right, case)
        }

        // DAC gain: 00 1D 1F 01/02 <wire>
        if (cmd == 0x1F && (sub == 0x01 || sub == 0x02) && payload.size >= 5) {
            val wire = payload[4].toInt() and 0xFF
            val ui = gainWireToUi(wire)
            if (ui != null) return GaiaReport.GainLevel(ui, wire)
        }

        // LED: 00 1D 27 01/02 <0|1>
        if (cmd == 0x27 && (sub == 0x01 || sub == 0x02) && payload.size >= 5) {
            val v = payload[4].toInt() and 0xFF
            if (v == 0 || v == 1) return GaiaReport.LedEnabled(v == 1)
        }

        // Spatial audio: 00 1D 25 01 <0|1>; head tracking: 00 1D 25 03 <0|1>
        if (cmd == 0x25 && payload.size >= 5) {
            val v = payload[4].toInt() and 0xFF
            if (v == 0 || v == 1) {
                if (sub == 0x01) return GaiaReport.SpatialAudio(v == 1)
                if (sub == 0x03) return GaiaReport.HeadTracking(v == 1)
            }
        }

        // Voice config: 00 1D 1D 01 <enabled, volume, index>
        if (cmd == 0x1D && (sub == 0x01 || sub == 0x02) && payload.size >= 7) {
            return GaiaReport.VoiceConfig(
                enabled = payload[4].toInt() and 0xFF == 1,
                volume = payload[5].toInt() and 0xFF,
                index = payload[6].toInt() and 0xFF,
            )
        }

        // LHDC: 00 1D 21 05 <0|1>; dual connection: 00 1D 29 01 <0|1>
        if (cmd == 0x21 && payload.size >= 5) {
            val v = payload[4].toInt() and 0xFF
            if (v == 0 || v == 1) return GaiaReport.Lhdc(v == 1)
        }
        if (cmd == 0x29 && payload.size >= 5) {
            val v = payload[4].toInt() and 0xFF
            if (v == 0 || v == 1) return GaiaReport.DualConnection(v == 1)
        }

        // Supported features: 00 1D 01 01 <bitmask words>
        if (cmd == 0x01 && (sub == 0x01 || sub == 0x02)) {
            val features = mutableSetOf<Int>()
            var i = 4
            while (i + 3 < payload.size) {
                val word = ((payload[i].toInt() and 0xFF) shl 24) or
                    ((payload[i + 1].toInt() and 0xFF) shl 16) or
                    ((payload[i + 2].toInt() and 0xFF) shl 8) or
                    (payload[i + 3].toInt() and 0xFF)
                for (bit in 0..31) if (word and (1 shl bit) != 0) features += bit
                i += 4
            }
            return GaiaReport.SupportedFeatures(features)
        }

        if (payload.size >= 4 && payload[1] == 0x0A.toByte()) return GaiaReport.GaiaNegotiation
        return GaiaReport.Unknown
    }
}
