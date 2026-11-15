package cythen.hyperdrop.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SystemLocaleResolverTest {

    @Test
    fun zhCnResolvesToSimplifiedChinese() {
        assertEquals(
            Locale.SIMPLIFIED_CHINESE,
            SystemLocaleResolver.resolve(Locale.forLanguageTag("zh-CN")),
        )
    }

    @Test
    fun zhWithoutRegionResolvesToSimplifiedChinese() {
        assertEquals(
            Locale.SIMPLIFIED_CHINESE,
            SystemLocaleResolver.resolve(Locale.forLanguageTag("zh")),
        )
    }

    @Test
    fun zhHansSgResolvesToSimplifiedChinese() {
        assertEquals(
            Locale.SIMPLIFIED_CHINESE,
            SystemLocaleResolver.resolve(Locale.forLanguageTag("zh-Hans-SG")),
        )
    }

    @Test
    fun zhTwResolvesToTraditionalChinese() {
        assertEquals(
            Locale.forLanguageTag("zh-Hant-TW"),
            SystemLocaleResolver.resolve(Locale.forLanguageTag("zh-TW")),
        )
    }

    @Test
    fun zhHantHkResolvesToTraditionalChinese() {
        assertEquals(
            Locale.forLanguageTag("zh-Hant-TW"),
            SystemLocaleResolver.resolve(Locale.forLanguageTag("zh-Hant-HK")),
        )
    }

    @Test
    fun zhHantMoResolvesToTraditionalChinese() {
        assertEquals(
            Locale.forLanguageTag("zh-Hant-TW"),
            SystemLocaleResolver.resolve(Locale.forLanguageTag("zh-MO")),
        )
    }

    @Test
    fun nonChineseLocalesFallBackToEnglish() {
        for (tag in listOf("fr-FR", "ja", "en", "en-US", "de-DE", "ko-KR")) {
            assertEquals(
                tag,
                Locale.ENGLISH,
                SystemLocaleResolver.resolve(Locale.forLanguageTag(tag)),
            )
        }
    }

    @Test
    fun nullFallsBackToEnglish() {
        assertEquals(Locale.ENGLISH, SystemLocaleResolver.resolve(null))
    }
}
