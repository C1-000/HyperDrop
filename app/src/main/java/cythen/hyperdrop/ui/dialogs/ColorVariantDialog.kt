package cythen.hyperdrop.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cythen.hyperdrop.R
import cythen.hyperdrop.device.ColorVariant
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog

/**
 * Lets the user pick which built-in colourway of the current device to display.
 * Custom images (configured separately) always take priority over this choice.
 */
@Composable
internal fun ColorVariantDialog(
    show: Boolean,
    deviceName: String,
    variants: List<ColorVariant>,
    selectedKey: String?,
    onDismissRequest: () -> Unit,
    onSelected: (String) -> Unit,
) {
    OverlayDialog(
        title = stringResource(R.string.color_variant_title),
        summary = deviceName,
        show = show,
        onDismissRequest = onDismissRequest,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            val current = selectedKey ?: "default"
            variants.forEach { variant ->
                val selected = variant.key == current
                Text(
                    text = stringResource(variant.labelRes),
                    color = if (selected) {
                        androidx.compose.ui.graphics.Color(0xFF007AFF)
                    } else {
                        androidx.compose.ui.graphics.Color.Unspecified
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelected(variant.key)
                            onDismissRequest()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
        }
    }
}
