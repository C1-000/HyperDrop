package cythen.hyperdrop.ui.pages

import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import cythen.hyperdrop.R
import cythen.hyperdrop.device.AncModeOption
import cythen.hyperdrop.device.DeviceCapabilities
import cythen.hyperdrop.config.ConfigManager
import cythen.hyperdrop.config.DeviceImageCatalog
import cythen.hyperdrop.pods.NoiseControlMode
import cythen.hyperdrop.pods.WearStatus
import cythen.hyperdrop.ui.components.AncSwitch
import cythen.hyperdrop.ui.components.PodStatus
import cythen.hyperdrop.utils.miuiStrongToast.data.BatteryParams
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Text
import cythen.hyperdrop.pods.EqPreset
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun PodDetailPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    bottomContentPadding: Dp = 16.dp,
    podName: String,
    batteryParams: BatteryParams,
    wearStatus: WearStatus = WearStatus(),
    ancMode: NoiseControlMode,
    onAncModeChange: (NoiseControlMode) -> Unit,
    smartAncLevel: NoiseControlMode? = null,
    transparencyVocalEnhancement: Boolean = false,
    onTransparencyVocalEnhancementChange: (Boolean) -> Unit = {},
    spatialAudioMode: Int = ConfigManager.SPATIAL_AUDIO_OFF,
    onSpatialAudioModeChange: (Int) -> Unit = {},
    spatialAudioSupported: Boolean = false,
    spatialSoundSupported: Boolean = false,
    ancModes: List<AncModeOption> = emptyList(),
    eqPreset: Int = -1,
    onEqPresetChange: (Int) -> Unit = {},
    gainLevel: Int = 1,
    onGainLevelChange: (Int) -> Unit = {},
    ledEnabled: Boolean = true,
    onLedEnabledChange: (Boolean) -> Unit = {},
    promptToneEnabled: Boolean = false,
    onPromptToneChange: (Boolean) -> Unit = {},
    promptVolume: Int = 50,
    onPromptVolumeChange: (Int) -> Unit = {},
    headTrackingEnabled: Boolean = false,
    onHeadTrackingChange: (Boolean) -> Unit = {},
    lhdcEnabled: Boolean = false,
    onLhdcChange: (Boolean) -> Unit = {},
    dualConnectionEnabled: Boolean = false,
    onDualConnectionChange: (Boolean) -> Unit = {},
    capabilities: DeviceCapabilities = DeviceCapabilities.NONE,
    boxImagePath: String? = null,
    deviceImageKey: String? = null,
    colorVariantKey: String? = null,
    showAppSettingsEntry: Boolean = false,
    onOpenAppSettings: () -> Unit = {},
    showSystemSettingsEntry: Boolean = false,
    onOpenSystemSettings: () -> Unit = {},
    controlsEnabled: Boolean = true,
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val builtinBoxRes = DeviceImageCatalog.resolve(deviceImageKey, colorVariantKey).box
    val imagePainter = rememberPodImagePainter(boxImagePath, builtinBoxRes)

    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = imagePainter,
                    contentDescription = "Earphones",
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .widthIn(max = 360.dp),
                    contentScale = ContentScale.FillWidth
                )
                Text(
                    text = podName,
                    modifier = Modifier.padding(top = 12.dp),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                contentPadding = PaddingValues(top = 12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                podControlItems(
                    batteryParams = batteryParams,
                    wearStatus = wearStatus,
                    ancMode = ancMode,
                    onAncModeChange = onAncModeChange,
                    smartAncLevel = smartAncLevel,
                    transparencyVocalEnhancement = transparencyVocalEnhancement,
                    onTransparencyVocalEnhancementChange = onTransparencyVocalEnhancementChange,
                    spatialAudioMode = spatialAudioMode,
                    onSpatialAudioModeChange = onSpatialAudioModeChange,
                    spatialAudioSupported = spatialAudioSupported,
                    spatialSoundSupported = spatialSoundSupported,
                    ancModes = ancModes,
                    eqPreset = eqPreset,
                    onEqPresetChange = onEqPresetChange,
                    gainLevel = gainLevel,
                    onGainLevelChange = onGainLevelChange,
                    ledEnabled = ledEnabled,
                    onLedEnabledChange = onLedEnabledChange,
                    promptToneEnabled = promptToneEnabled,
                    onPromptToneChange = onPromptToneChange,
                    promptVolume = promptVolume,
                    onPromptVolumeChange = onPromptVolumeChange,
                    headTrackingEnabled = headTrackingEnabled,
                    onHeadTrackingChange = onHeadTrackingChange,
                    lhdcEnabled = lhdcEnabled,
                    onLhdcChange = onLhdcChange,
                    dualConnectionEnabled = dualConnectionEnabled,
                    onDualConnectionChange = onDualConnectionChange,
                    capabilities = capabilities,
                    showAppSettingsEntry = showAppSettingsEntry,
                    onOpenAppSettings = onOpenAppSettings,
                    showSystemSettingsEntry = showSystemSettingsEntry,
                    onOpenSystemSettings = onOpenSystemSettings,
                    controlsEnabled = controlsEnabled,
                    bottomContentPadding = bottomContentPadding
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Image(
                painter = imagePainter,
                contentDescription = "Earphones",
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .padding(vertical = 16.dp),
                contentScale = ContentScale.FillWidth
            )
        }

        podControlItems(
            batteryParams = batteryParams,
            wearStatus = wearStatus,
            ancMode = ancMode,
            onAncModeChange = onAncModeChange,
            smartAncLevel = smartAncLevel,
            transparencyVocalEnhancement = transparencyVocalEnhancement,
            onTransparencyVocalEnhancementChange = onTransparencyVocalEnhancementChange,
            spatialAudioMode = spatialAudioMode,
            onSpatialAudioModeChange = onSpatialAudioModeChange,
            spatialAudioSupported = spatialAudioSupported,
            spatialSoundSupported = spatialSoundSupported,
            ancModes = ancModes,
            eqPreset = eqPreset,
            onEqPresetChange = onEqPresetChange,
            gainLevel = gainLevel,
            onGainLevelChange = onGainLevelChange,
            ledEnabled = ledEnabled,
            onLedEnabledChange = onLedEnabledChange,
            promptToneEnabled = promptToneEnabled,
            onPromptToneChange = onPromptToneChange,
            promptVolume = promptVolume,
            onPromptVolumeChange = onPromptVolumeChange,
            headTrackingEnabled = headTrackingEnabled,
            onHeadTrackingChange = onHeadTrackingChange,
            lhdcEnabled = lhdcEnabled,
            onLhdcChange = onLhdcChange,
            dualConnectionEnabled = dualConnectionEnabled,
            onDualConnectionChange = onDualConnectionChange,
            capabilities = capabilities,
            showAppSettingsEntry = showAppSettingsEntry,
            onOpenAppSettings = onOpenAppSettings,
            showSystemSettingsEntry = showSystemSettingsEntry,
            onOpenSystemSettings = onOpenSystemSettings,
            controlsEnabled = controlsEnabled,
            bottomContentPadding = bottomContentPadding
        )
    }
}

/**
 * HyperOriG-style detail page: the product artwork belongs to this APK, not Settings.
 * When the user has not configured a custom picture, always render our bundled
 * transparent MOONDROP PUDDING image (R.drawable.img_box).
 */
@Composable
private fun rememberPodImagePainter(path: String?, builtinResId: Int) = remember(path, builtinResId) {
    path?.let {
        runCatching { BitmapFactory.decodeFile(it) }
            .getOrNull()
            ?.let { bitmap -> BitmapPainter(bitmap.asImageBitmap()) }
    }
} ?: painterResource(builtinResId)

private fun LazyListScope.podControlItems(
    batteryParams: BatteryParams,
    wearStatus: WearStatus,
    ancMode: NoiseControlMode,
    onAncModeChange: (NoiseControlMode) -> Unit,
    smartAncLevel: NoiseControlMode?,
    transparencyVocalEnhancement: Boolean,
    onTransparencyVocalEnhancementChange: (Boolean) -> Unit,
    spatialAudioMode: Int,
    onSpatialAudioModeChange: (Int) -> Unit,
    spatialAudioSupported: Boolean,
    spatialSoundSupported: Boolean,
    ancModes: List<AncModeOption>,
    eqPreset: Int,
    onEqPresetChange: (Int) -> Unit,
    gainLevel: Int,
    onGainLevelChange: (Int) -> Unit,
    ledEnabled: Boolean,
    onLedEnabledChange: (Boolean) -> Unit,
    promptToneEnabled: Boolean,
    onPromptToneChange: (Boolean) -> Unit,
    promptVolume: Int,
    onPromptVolumeChange: (Int) -> Unit,
    headTrackingEnabled: Boolean,
    onHeadTrackingChange: (Boolean) -> Unit,
    lhdcEnabled: Boolean,
    onLhdcChange: (Boolean) -> Unit,
    dualConnectionEnabled: Boolean,
    onDualConnectionChange: (Boolean) -> Unit,
    capabilities: DeviceCapabilities,
    showAppSettingsEntry: Boolean,
    onOpenAppSettings: () -> Unit,
    showSystemSettingsEntry: Boolean,
    onOpenSystemSettings: () -> Unit,
    controlsEnabled: Boolean,
    bottomContentPadding: Dp
) {
    val spatialAudioValues = listOf(
        ConfigManager.SPATIAL_AUDIO_OFF,
        ConfigManager.SPATIAL_AUDIO_FIXED,
        ConfigManager.SPATIAL_AUDIO_HEAD_TRACKING,
    )

    item {
        Card(
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            PodStatus(
                batteryParams = batteryParams,
                wearStatus = wearStatus,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)
            )
        }
    }

    item {
        Card(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            AncSwitch(
                ancStatus = ancMode,
                onAncModeChange = onAncModeChange,
                smartAncLevel = smartAncLevel,
                ancModes = ancModes,
                transparencyVocalEnhancement = transparencyVocalEnhancement,
                onTransparencyVocalEnhancementChange = onTransparencyVocalEnhancementChange,
                enabled = controlsEnabled
            )
        }
    }

    // 1. Gain — shown only when the device profile declares gain control.
    if (capabilities.gainSupported) {
        item {
            val gainOptions = capabilities.gainLabels.ifEmpty {
                listOf(
                    stringResource(R.string.pudding_gain_low),
                    stringResource(R.string.pudding_gain_medium),
                    stringResource(R.string.pudding_gain_high),
                )
            }
            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                OverlayDropdownPreference(
                    title = stringResource(R.string.pudding_gain_title),
                    summary = stringResource(R.string.pudding_gain_summary),
                    items = gainOptions,
                    selectedIndex = gainLevel.coerceIn(0, gainOptions.size - 1),
                    onSelectedIndexChange = { onGainLevelChange(it.coerceIn(0, gainOptions.size - 1)) }
                )
            }
        }
    }

    // 2. LED indicator.
    if (capabilities.ledSupported) {
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                SwitchPreference(
                    title = stringResource(R.string.pudding_led_title),
                    summary = stringResource(R.string.pudding_led_summary),
                    checked = ledEnabled,
                    onCheckedChange = onLedEnabledChange
                )
            }
        }
    }

    // 3. Prompt tone on/off.
    if (capabilities.promptToneSupported) {
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                SwitchPreference(
                    title = stringResource(R.string.control_prompt_tone_title),
                    summary = stringResource(R.string.control_prompt_tone_summary),
                    checked = promptToneEnabled,
                    onCheckedChange = onPromptToneChange
                )
            }
        }
    }

    // 4. Prompt tone volume (0..100).
    if (capabilities.promptVolumeSupported) {
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                SliderPreference(
                    value = promptVolume.coerceIn(0, 100).toFloat(),
                    onValueChange = { onPromptVolumeChange(it.toInt().coerceIn(0, 100)) },
                    title = stringResource(R.string.control_prompt_volume_title),
                    summary = stringResource(R.string.control_prompt_volume_summary),
                    valueRange = 0f..100f,
                )
            }
        }
    }

    // 5. Head tracking.
    if (capabilities.headTrackingSupported) {
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                SwitchPreference(
                    title = stringResource(R.string.control_head_tracking_title),
                    summary = stringResource(R.string.control_head_tracking_summary),
                    checked = headTrackingEnabled,
                    onCheckedChange = onHeadTrackingChange
                )
            }
        }
    }

    // 6. LHDC codec.
    if (capabilities.lhdcSupported) {
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                SwitchPreference(
                    title = stringResource(R.string.control_lhdc_title),
                    summary = stringResource(R.string.control_lhdc_summary),
                    checked = lhdcEnabled,
                    onCheckedChange = onLhdcChange
                )
            }
        }
    }

    // 7. Dual-device connection.
    if (capabilities.dualConnectionSupported) {
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                SwitchPreference(
                    title = stringResource(R.string.control_dual_connection_title),
                    summary = stringResource(R.string.control_dual_connection_summary),
                    checked = dualConnectionEnabled,
                    onCheckedChange = onDualConnectionChange
                )
            }
        }
    }

    if (showAppSettingsEntry) {
        item {
            Card(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                BasicComponent(
                    title = stringResource(R.string.app_settings_entry),
                    summary = stringResource(R.string.app_settings_entry_summary),
                    onClick = onOpenAppSettings
                )
            }
        }
    }

    if (showSystemSettingsEntry) {
        item {
            Card(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 0.dp)
            ) {
                BasicComponent(
                    title = stringResource(R.string.system_headset_settings_entry),
                    summary = stringResource(R.string.system_headset_settings_entry_summary),
                    onClick = onOpenSystemSettings
                )
            }
        }
    }

    item {
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(bottomContentPadding))
    }
}
