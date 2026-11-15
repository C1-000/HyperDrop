package cythen.hyperdrop.ui

import java.util.Locale

/**
 * Maps a device locale to one of the three locales HyperDrop actually ships
 * resources for: Simplified Chinese (values-zh-rCN), Traditional Chinese
 * (values-zh-rTW) and English (values/, the default).
 *
 * When "Follow System" is selected, AppLocale still sets an explicit locale,
 * so we normalise the device locale here instead of relying on Android's
 * resource matcher. Anything that is not Chinese falls back to English, which
 * is the "unmatched -> en" requirement. This type deliberately depends only on
 * java.util.Locale so the mapping can be covered by plain JVM unit tests.
 */
object SystemLocaleResolver {

    private val TRADITIONAL_REGIONS = setOf("TW", "HK", "MO")

    fun resolve(device: Locale?): Locale {
        if (device == null || device.language != "zh") return Locale.ENGLISH
        val traditional = device.script.equals("Hant", ignoreCase = true) ||
            device.country.uppercase(Locale.ROOT) in TRADITIONAL_REGIONS
        return if (traditional) {
            Locale.forLanguageTag("zh-Hant-TW")
        } else {
            Locale.SIMPLIFIED_CHINESE
        }
    }
}
