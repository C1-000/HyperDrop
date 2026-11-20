package cythen.hyperdrop.config

import cythen.hyperdrop.R

/**
 * Built-in earbud art, indexed by a device's [imageKey] and the selected colourway key.
 *
 * Every entry resolves to three drawable resource ids (box / left / right). Unknown
 * devices and unmatched colourways fall back to the generic [DEFAULT] set, so a newly
 * added model still renders correctly before any dedicated art has been drawn.
 *
 * The priority chain for the actual displayed image is:
 *   user-provided custom image path  >  built-in device+colourway set  >  [DEFAULT].
 */
data class DeviceImageSet(val box: Int, val left: Int, val right: Int)

object DeviceImageCatalog {
    /** Generic art used for fallback and for models without dedicated assets. */
    val DEFAULT = DeviceImageSet(R.drawable.img_box, R.drawable.img_left, R.drawable.img_right)

    /**
     * Resolve the built-in set for a device/colourway pair.
     * [variantKey] of null or "default" selects the model's base art.
     */
    fun resolve(imageKey: String?, variantKey: String?): DeviceImageSet {
        val key = imageKey.orEmpty()
        val variant = variantKey ?: "default"
        return when {
            key == "pudding" -> when (variant) {
                "black" -> DeviceImageSet(
                    R.drawable.dev_pudding_black_box,
                    R.drawable.dev_pudding_black_left,
                    R.drawable.dev_pudding_black_right,
                )
                "white" -> DeviceImageSet(
                    R.drawable.dev_pudding_white_box,
                    R.drawable.dev_pudding_white_left,
                    R.drawable.dev_pudding_white_right,
                )
                "purple" -> DeviceImageSet(
                    R.drawable.dev_pudding_purple_box,
                    R.drawable.dev_pudding_purple_left,
                    R.drawable.dev_pudding_purple_right,
                )
                else -> DEFAULT
            }
            key == "mirage" -> DeviceImageSet(
                R.drawable.dev_mirage_default_box,
                R.drawable.dev_mirage_default_left,
                R.drawable.dev_mirage_default_right,
            )
            else -> DEFAULT
        }
    }
}
