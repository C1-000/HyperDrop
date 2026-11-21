package cythen.hyperdrop.device.protocol

/**
 * Shared decoder for a Qualcomm GAIA battery-level response.
 *
 * Official reference (APK `com.qualcomm.qti.gaiaclient`):
 *  - `qtil/data/battery/BatteryLevel.smali:17-21` — one record is two bytes,
 *    `TYPE_OFFSET = 0` (battery id) and `LEVEL_OFFSET = 1` (level), with
 *    `LEVEL_UNKNOWN = 0xff`. There is **no** charging flag in GAIA.
 *  - `V3BatteryPlugin$COMMANDS.smali` — GET_BATTERY_LEVELS = 0x1, so the response
 *    command word is `0x1B` (feature 0x0D, RESPONSE) and sub-command 0x01.
 *
 * Battery ids are positional: 1 = left, 2 = right, 3 = case. HyperDrop previously
 * decoded this separately in two command tables, which let the two drift apart; it now
 * lives here so PUDDING and the generic table cannot disagree again.
 *
 * Charging state is *not* part of this wire format. The old OPPO AA protocol packed
 * `value & 0x7F` plus a `0x80` charging bit; a GAIA level byte can legitimately be
 * >= 0x80 only if it is a stray/unknown marker, so it is masked and range-checked
 * instead of being read as "charging".
 */
object GaiaBatteryParser {

    /** First payload byte of a battery record list (after vendor+cmd+sub). */
    private const val RECORD_START = 4

    /** GAIA `BatteryLevel.LEVEL_UNKNOWN`; reported as "no reading". */
    private const val LEVEL_UNKNOWN = 0xFF

    private const val LEVEL_MASK = 0x7F

    /**
     * Parses `00 1D 1B 01 [id, level]...` into a [GaiaReport.Battery].
     *
     * Returns null when the payload is not a battery response or carries no usable level.
     */
    fun parse(payload: ByteArray): GaiaReport.Battery? {
        if (payload.size < 6) return null
        if (payload[0] != 0x00.toByte() || payload[1] != 0x1D.toByte()) return null
        if ((payload[2].toInt() and 0xFF) != 0x1B) return null
        if ((payload[3].toInt() and 0xFF) != 0x01) return null

        var left: Int? = null
        var right: Int? = null
        var case: Int? = null

        var i = RECORD_START
        while (i + 1 < payload.size) {
            val id = payload[i].toInt() and 0xFF
            val raw = payload[i + 1].toInt() and 0xFF
            val level = levelOf(raw)
            if (level != null) {
                when (id) {
                    1 -> left = level
                    2 -> right = level
                    3 -> case = level
                }
            }
            i += 2
        }

        if (left == null && right == null && case == null) return null
        return GaiaReport.Battery(left, right, case)
    }

    /**
     * Normalises one raw level byte to 0..100, or null when it is not a real reading.
     *
     * `0xFF` is the spec's "unknown". A byte above 0x80 cannot be a percentage, so the
     * low seven bits are used — this also tolerates a firmware that reuses the top bit
     * for something else, without inventing a charging semantic GAIA does not define.
     */
    private fun levelOf(raw: Int): Int? {
        if (raw == LEVEL_UNKNOWN) return null
        val level = raw and LEVEL_MASK
        return if (level in 0..100) level else null
    }
}
