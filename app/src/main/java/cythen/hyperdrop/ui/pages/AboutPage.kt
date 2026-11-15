package cythen.hyperdrop.ui.pages

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cythen.hyperdrop.R
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card

private data class Credit(val name: String, val url: String)

private val CREDITS = listOf(
    Credit("HyperPods", "https://github.com/Art-Chen/HyperPods"),
    Credit("OppoPods", "https://github.com/1812z/OppoPods"),
    Credit("HyperOriG", "https://github.com/KiriChen-Wind/HyperOriG"),
    Credit("PuddingPods", "https://github.com/lingbai-rong/PuddingPods"),
    Credit("Miuix", "https://github.com/YuKongA/miuix"),
)

private const val PROJECT_URL = "https://github.com/C1-000/HyperDrop"
private const val LICENSE_URL = "https://github.com/C1-000/HyperDrop/blob/main/LICENSE"

@Composable
fun AboutPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val context = LocalContext.current
    val versionName = context.packageManager
        .getPackageInfo(context.packageName, 0).versionName ?: "unknown"

    // AppLocale.Provider exposes a createConfigurationContext wrapper (not an Activity),
    // so starting an external Intent here requires FLAG_ACTIVITY_NEW_TASK.
    fun openUrl(url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
            start = 12.dp,
            end = 12.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card {
                BasicComponent(
                    title = stringResource(R.string.about_version),
                    summary = versionName,
                )
                BasicComponent(
                    title = "HyperDrop",
                    summary = PROJECT_URL,
                    onClick = { openUrl(PROJECT_URL) }
                )
                BasicComponent(
                    title = stringResource(R.string.about_license),
                    summary = "GPL-3.0",
                    onClick = { openUrl(LICENSE_URL) }
                )
            }
        }
        item {
            Card {
                BasicComponent(
                    title = stringResource(R.string.about_credits),
                    summary = stringResource(R.string.about_credits_summary),
                )
                CREDITS.forEach { credit ->
                    BasicComponent(
                        title = credit.name,
                        summary = credit.url,
                        onClick = { openUrl(credit.url) }
                    )
                }
            }
        }
    }
}
