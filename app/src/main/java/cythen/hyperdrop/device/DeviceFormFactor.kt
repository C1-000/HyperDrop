package cythen.hyperdrop.device

/**
 * Physical shape of a model. Drives which layout the detail UI shows and, by default, whether
 * battery is reported for one part or for three (left / right / case).
 *
 * The values come from the official CDN catalogue (`products/all` exposes `istws` and
 * `isSpeaker`), cross-checked against which models only have a single artwork asset here —
 * [DeviceImageCatalog] falls back to box art for both earbuds on exactly the one-piece models.
 *
 * Note this is a *shape*, not a protocol fact: it must not be used to pick command bytes.
 */
enum class DeviceFormFactor {
    /** True wireless: two independent earbuds plus a charging case. */
    TWS,

    /**
     * One physical unit worn as a whole: neckband, open-ear hook, clip-on or a single-piece
     * headset. Reports one battery, and its artwork has no separate left/right.
     */
    ONE_PIECE,

    /** Over-ear / on-ear headphones. One battery, wearable as a single object. */
    HEADPHONES,

    /** A speaker rather than something worn (MOONDROP MM3A is the only case in the catalogue). */
    SPEAKER,

    /** Wired dongle / DAC with no battery of its own. */
    WIRED_DONGLE,
    ;

    /**
     * Battery parts to expect when the model says nothing else.
     *
     * Only [TWS] has a case; everything else is one part. A model may still override this —
     * the field exists so the layout can be right without every profile restating it.
     */
    val singleBatteryByDefault: Boolean get() = this != TWS
}
