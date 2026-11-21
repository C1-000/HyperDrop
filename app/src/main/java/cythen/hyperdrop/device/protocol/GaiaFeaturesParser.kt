package cythen.hyperdrop.device.protocol

/**
 * Shared decoder for a Qualcomm GAIA "supported features" response.
 *
 * Official reference (APK `com.qualcomm.qti.gaiaclient`):
 *  - `qtil/data/GetSupportedFeaturesData.smali:7-15` — `MORE_DATA_OFFSET = 0x0`,
 *    `FEATURES_LIST_OFFSET = 0x1`, `FEATURE_ID_PAIR_OFFSET = 0x0`,
 *    `FEATURE_VERSION_PAIR_OFFSET = 0x1`.
 *  - The same class's constructor walks the data with `add-int/lit8 ... 0x2`, i.e. the
 *    feature list is an array of **(featureId, version) byte pairs**, not a bitmask.
 *  - Pagination: `FeaturesFetcher.smali:383-395` re-sends
 *    `V1_GET_SUPPORTED_FEATURES_NEXT` (command `0x02`) while `hasMoreData` is set.
 *
 * Because the caller's payload still contains the 4-byte command header, the official
 * `data[0]` lands at `payload[4]` and the first feature id at `payload[5]`. Starting at
 * index 4 would read `hasMoreData` as a feature id and shift every pair by one byte —
 * exactly the bug this decoder replaces.
 */
object GaiaFeaturesParser {

    /** First payload byte of the feature list (after vendor + command word). */
    private const val LIST_START = 5

    /** `hasMoreData` sits in the byte right before the list. */
    private const val MORE_DATA_INDEX = 4

    /** `MORE_DATA_VALUE` in the official decoder. */
    private const val MORE_DATA_VALUE = 1

    /**
     * Parses `00 1D 01 01/02 [hasMoreData] ([featureId] [version])...`.
     *
     * Returns null when the payload is not a supported-features response, or when it
     * carries no complete pair — an empty list tells the caller nothing and must not be
     * mistaken for "this earbud supports zero features".
     */
    fun parse(payload: ByteArray): GaiaReport.SupportedFeatures? {
        if (payload.size < LIST_START + 2) return null
        if (payload[0] != 0x00.toByte() || payload[1] != 0x1D.toByte()) return null
        if ((payload[2].toInt() and 0xFF) != 0x01) return null
        val sub = payload[3].toInt() and 0xFF
        if (sub != 0x01 && sub != 0x02) return null

        val moreData = (payload[MORE_DATA_INDEX].toInt() and 0xFF) == MORE_DATA_VALUE

        // Only the feature id is kept; the version byte is skipped deliberately (see
        // GaiaReport.SupportedFeatures) but must still be stepped over.
        val features = mutableSetOf<Int>()
        var i = LIST_START
        while (i + 1 < payload.size) {
            features += payload[i].toInt() and 0xFF
            i += 2
        }

        return GaiaReport.SupportedFeatures(features, moreData)
    }
}
