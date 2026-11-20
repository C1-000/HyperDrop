package cythen.hyperdrop.ui.pages

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cythen.hyperdrop.R
import cythen.hyperdrop.device.DeviceModels
import cythen.hyperdrop.device.DeviceProfile
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.BasicComponent

/**
 * Settings page that lists every adapted model, grouped by verification status.
 * Verified = real-hardware tested; inferred = protocol best-effort, not confirmed on device.
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
            onClick = {},
        )
    }
}
