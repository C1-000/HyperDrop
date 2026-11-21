package cythen.hyperdrop.device.protocol

/**
 * GAIA features a connected earbud has actually reported, accumulated across
 * `GET_SUPPORTED_FEATURES` pages.
 *
 * This is *observation*, not configuration: the profile in [cythen.hyperdrop.device.DeviceModels]
 * still owns the ANC/gain translation tables and is the only thing that says what a wire value
 * means. The reported list is used for two narrower jobs:
 *
 *  1. picking which GAIA feature carries ANC when the profile allows more than one
 *     (see [cythen.hyperdrop.device.DeviceCapabilities.ancPathCandidates]);
 *  2. switching on controls the profile left off conservatively, for the models that were
 *     registered by protocol inference alone.
 *
 * The list is very often partial: firmware may report only a handful of features, or none at
 * all before the first handshake completes. [complete] therefore only means "the device said
 * the last page was the last page" — it does not mean the list is exhaustive, and nothing here
 * is allowed to turn a declared capability *off*.
 */
data class ObservedFeatures(
    val features: Set<Int> = emptySet(),
    /** Number of `GET_SUPPORTED_FEATURES` pages that contributed, including the first. */
    val pages: Int = 0,
    /** True when the last page reported no further pages. */
    val complete: Boolean = false,
) {
    val isEmpty: Boolean get() = features.isEmpty()

    fun supports(feature: Int): Boolean = feature in features

    /** Fold one page into the accumulated set. */
    fun merge(page: Set<Int>, hasMore: Boolean): ObservedFeatures = ObservedFeatures(
        features = features + page,
        pages = pages + 1,
        complete = !hasMore,
    )

    companion object {
        val UNKNOWN = ObservedFeatures()
    }
}
