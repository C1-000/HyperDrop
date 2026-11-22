package cythen.hyperdrop.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cythen.hyperdrop.R
import cythen.hyperdrop.device.DeviceModels
import cythen.hyperdrop.device.DeviceProfile
import cythen.hyperdrop.device.SimulatedDevice
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Debug-only entry point: connect fake devices built from the existing [DeviceProfile] catalog so
 * the multi-device adaptation can be exercised without hardware.
 *
 * The profile list is the single source of truth for what can be simulated, so a new device model
 * shows up here automatically once [DeviceModels] gains it.
 */
@Composable
fun DebugPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    simulatedDevices: List<SimulatedDevice> = emptyList(),
    connectedAddress: String = "",
    connectingAddress: String? = null,
    onAddProfile: (DeviceProfile) -> Unit = {},
    onConnectDevice: (SimulatedDevice) -> Unit = {},
    onDisconnectDevice: (SimulatedDevice) -> Unit = {},
    onRemoveDevice: (SimulatedDevice) -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
            start = 12.dp,
            end = 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card {
                SectionHeader(
                    title = stringResource(R.string.debug_simulate_device),
                    summary = stringResource(R.string.debug_simulate_device_summary),
                )
            }
        }

        // Both the debug simulator and the adaptation page read the same DeviceProfile catalog;
        // grouping follows the archive's verified flag and the note comes straight from its note field.
        val verifiedProfiles = DeviceModels.models.filter { it.verified }
        val inferredProfiles = DeviceModels.models.filter { !it.verified }

        item {
            GroupHeader(title = stringResource(R.string.adaptation_verified))
        }
        items(verifiedProfiles, key = { "verify-${it.id}" }) { profile ->
            ProfileCard(profile = profile, onAdd = onAddProfile)
        }

        item {
            GroupHeader(title = stringResource(R.string.adaptation_inferred))
        }
        items(inferredProfiles, key = { "infer-${it.id}" }) { profile ->
            ProfileCard(profile = profile, onAdd = onAddProfile)
        }

        item {
            Card {
                SectionHeader(
                    title = stringResource(R.string.debug_added_devices),
                    summary = stringResource(R.string.debug_added_devices_summary),
                )
                if (simulatedDevices.isEmpty()) {
                    Text(
                        text = stringResource(R.string.debug_no_added_devices),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }

        items(simulatedDevices, key = { it.address }) { device ->
            SimulatedDeviceCard(
                device = device,
                statusText = when {
                    device.address.equals(connectedAddress, ignoreCase = true) -> stringResource(R.string.debug_device_connected)
                    device.address.equals(connectingAddress.orEmpty(), ignoreCase = true) -> stringResource(R.string.debug_device_connecting)
                    else -> stringResource(R.string.debug_device_disconnected)
                },
                isConnected = device.address.equals(connectedAddress, ignoreCase = true),
                onClick = {
                    if (device.address.equals(connectedAddress, ignoreCase = true)) {
                        onDisconnectDevice(device)
                    } else {
                        onConnectDevice(device)
                    }
                },
                onRemove = { onRemoveDevice(device) },
            )
        }

        item {
            Card {
                Text(
                    text = stringResource(R.string.debug_page_hint),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, summary: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Text(
            text = summary,
            modifier = Modifier.padding(top = 2.dp),
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun GroupHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

@Composable
private fun ProfileCard(
    profile: DeviceProfile,
    onAdd: (DeviceProfile) -> Unit,
) {
    Card(modifier = Modifier.padding(vertical = 4.dp)) {
        BasicComponent(
            title = profile.displayName,
            summary = profile.note.takeIf { it.isNotBlank() },
            onClick = { onAdd(profile) },
        )
    }
}

@Composable
private fun SimulatedDeviceCard(
    device: SimulatedDevice,
    statusText: String,
    isConnected: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.displayName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isConnected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${device.address} · $statusText",
                    modifier = Modifier.padding(top = 2.dp),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = MiuixIcons.Delete,
                    contentDescription = stringResource(R.string.debug_remove_device),
                )
            }
        }
    }
}
