package cythen.hyperdrop.ui.pages

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cythen.hyperdrop.R
import cythen.hyperdrop.device.DeviceFormFactor
import cythen.hyperdrop.device.DeviceModels
import cythen.hyperdrop.device.DeviceProfile
import cythen.hyperdrop.device.protocol.AncPathKind
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.BasicComponent

/**
 * Settings page that lists every adapted model, grouped by verification status.
 * Verified = real-hardware tested; inferred = protocol best-effort, not confirmed on device.
 *
 * Each row names the two things that actually decide behaviour: the product shape (which picks
 * the layout) and the control profile (which picks the command bytes). Two rows showing the same
 * control profile are the same wire vocabulary — MIRAGE lists `pudding` because it *is* a PUDDING.
 */
@Composable
fun AdaptationStatusPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val verified = DeviceModels.models.filter { it.verified }
    val inferred = DeviceModels.models.filter { !it.verified }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
            start = 12.dp,
            end = 12.dp,
        ),
    ) {
        item {
            Text(
                text = stringResource(R.string.adaptation_verified),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
        items(verified.size) { index ->
            DeviceAdaptationCard(verified[index])
        }

        item {
            Text(
                text = stringResource(R.string.adaptation_inferred),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
        items(inferred.size) { index ->
            DeviceAdaptationCard(inferred[index])
        }
    }
}

@Composable
private fun DeviceAdaptationCard(profile: DeviceProfile) {
    Card(modifier = Modifier.padding(vertical = 4.dp)) {
        BasicComponent(
            title = profile.displayName,
            summary = adaptationSummary(profile),
            onClick = {},
        )
    }
}

/**
 * "TWS earbuds · control: pudding" — shape first because it is what the user sees, then the
 * wire vocabulary, which is the part that explains why two models behave identically.
 */
@Composable
private fun adaptationSummary(profile: DeviceProfile): String {
    val shape = stringResource(formFactorLabelRes(profile.formFactor))
    val control = stringResource(R.string.adaptation_control_profile, controlLabel(profile))
    return "$shape · $control"
}

@Suppress("DEPRECATION")
private fun controlLabel(profile: DeviceProfile): String {
    val control = profile.control
    val id = control.id
    val anc = if (!control.hasAnc) {
        "no ANC"
    } else {
        when (control.ancPath) {
            AncPathKind.ANC_V2 -> "ANC_V2"
            AncPathKind.AUDIO_CURATION -> "AudioCuration"
            AncPathKind.ANC_V1 -> "ANC_V1"
        }
    }
    return "$id ($anc)"
}

@StringRes
private fun formFactorLabelRes(formFactor: DeviceFormFactor): Int = when (formFactor) {
    DeviceFormFactor.TWS -> R.string.form_factor_tws
    DeviceFormFactor.ONE_PIECE -> R.string.form_factor_one_piece
    DeviceFormFactor.HEADPHONES -> R.string.form_factor_headphones
    DeviceFormFactor.SPEAKER -> R.string.form_factor_speaker
    DeviceFormFactor.WIRED_DONGLE -> R.string.form_factor_wired_dongle
}
