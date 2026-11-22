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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import cythen.hyperdrop.ui.ControlPendingState
import cythen.hyperdrop.ui.components.AncSwitch
import cythen.hyperdrop.ui.components.ExclusionStates
import cythen.hyperdrop.ui.components.MutualExclusionDialog
import cythen.hyperdrop.ui.components.MutualExclusionTarget
import cythen.hyperdrop.ui.components.rememberMutualExclusionState
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
    lc3Enabled: Boolean = false,
    onLc3Change: (Boolean) -> Unit = {},
    ldacEnabled: Boolean = false,
    onLdacChange: (Boolean) -> Unit = {},
    dualConnectionEnabled: Boolean = false,
    onDualConnectionChange: (Boolean) -> Unit = {},
    capabilities: DeviceCapabilities = DeviceCapabilities.NONE,
    pending: ControlPendingState? = null,
    boxImagePath: String? = null,
    deviceImageKey: String? = null,
    showAppSettingsEntry: Boolean = false,
    onOpenAppSettings: () -> Unit = {},
    showSystemSettingsEntry: Boolean = false,
    onOpenSystemSettings: () -> Unit = {},
    controlsEnabled: Boolean = true,
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val builtinBoxRes = DeviceImageCatalog.resolve(deviceImageKey).box
    val imagePainter = rememberPodImagePainter(boxImagePath, builtinBoxRes)

    // Codecs (LC3 / LDAC / LHDC) are mutually exclusive in firmware, and LHDC
    // additionally conflicts with dual-device connection. Route all switches
    // through a guard that asks before switching the conflicting feature off.
    val exclusion = rememberMutualExclusionState()
    val exclusionScope = rememberCoroutineScope()
    val exclusionStates: ExclusionStates = mapOf(
        MutualExclusionTarget.LHDC to (capabilities.lhdcSupported to lhdcEnabled),
        MutualExclusionTarget.LC3 to (capabilities.lc3Supported to lc3Enabled),
        MutualExclusionTarget.LDAC to (capabilities.ldacSupported to ldacEnabled),
        MutualExclusionTarget.DUAL_CONNECTION to
            (capabilities.dualConnectionSupported to dualConnectionEnabled),
    )
    val handleLhdcChange: (Boolean) -> Unit = { wanted ->
        if (exclusion.request(exclusionStates, MutualExclusionTarget.LHDC, wanted)) {
            onLhdcChange(wanted)
        }
    }
    val handleLc3Change: (Boolean) -> Unit = { wanted ->
        if (exclusion.request(exclusionStates, MutualExclusionTarget.LC3, wanted)) {
            onLc3Change(wanted)
        }
    }
    val handleLdacChange: (Boolean) -> Unit = { wanted ->
        if (exclusion.request(exclusionStates, MutualExclusionTarget.LDAC, wanted)) {
            onLdacChange(wanted)
        }
    }
    val handleDualChange: (Boolean) -> Unit = { wanted ->
        if (exclusion.request(exclusionStates, MutualExclusionTarget.DUAL_CONNECTION, wanted)) {
            onDualConnectionChange(wanted)
        }
    }
    val applyExclusion: (MutualExclusionTarget) -> Unit = { target ->
        exclusionScope.launch {
            // Turn off every conflicting feature that is currently on, then enable target.
            val onCodecs = listOf(
                MutualExclusionTarget.LHDC, MutualExclusionTarget.LC3, MutualExclusionTarget.LDAC,
            ).filter { exclusionStates[it]?.second == true }
            when (target) {
                MutualExclusionTarget.LHDC, MutualExclusionTarget.LC3, MutualExclusionTarget.LDAC -> {
                    onCodecs.filter { it != target }.forEach { other ->
                        when (other) {
                            MutualExclusionTarget.LHDC -> onLhdcChange(false)
                            MutualExclusionTarget.LC3 -> onLc3Change(false)
                            MutualExclusionTarget.LDAC -> onLdacChange(false)
                            else -> Unit
                        }
                    }
                    // LHDC also conflicts with dual connection.
                    if (target == MutualExclusionTarget.LHDC && exclusionStates[MutualExclusionTarget.DUAL_CONNECTION]?.second == true) {
                        onDualConnectionChange(false)
                    }
                    delay(300)
                    when (target) {
                        MutualExclusionTarget.LHDC -> onLhdcChange(true)
                        MutualExclusionTarget.LC3 -> onLc3Change(true)
                        MutualExclusionTarget.LDAC -> onLdacChange(true)
                        MutualExclusionTarget.DUAL_CONNECTION -> Unit
                    }
                }
                MutualExclusionTarget.DUAL_CONNECTION -> {
                    onLhdcChange(false)
                    delay(300)
                    onDualConnectionChange(true)
                }
            }
        }
    }
    MutualExclusionDialog(state = exclusion, onApply = applyExclusion)

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
                    onLhdcChange = handleLhdcChange,
                    lc3Enabled = lc3Enabled,
                    onLc3Change = handleLc3Change,
                    ldacEnabled = ldacEnabled,
                    onLdacChange = handleLdacChange,
                    dualConnectionEnabled = dualConnectionEnabled,
                    onDualConnectionChange = handleDualChange,
                    capabilities = capabilities,
                    pending = pending,
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
            onLhdcChange = handleLhdcChange,
            lc3Enabled = lc3Enabled,
            onLc3Change = handleLc3Change,
            ldacEnabled = ldacEnabled,
            onLdacChange = handleLdacChange,
            dualConnectionEnabled = dualConnectionEnabled,
            onDualConnectionChange = handleDualChange,
            capabilities = capabilities,
            pending = pending,
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
    lc3Enabled: Boolean,
    onLc3Change: (Boolean) -> Unit,
    ldacEnabled: Boolean,
    onLdacChange: (Boolean) -> Unit,
    dualConnectionEnabled: Boolean,
    onDualConnectionChange: (Boolean) -> Unit,
    capabilities: DeviceCapabilities,
    pending: ControlPendingState?,
    showAppSettingsEntry: Boolean,
    onOpenAppSettings: () -> Unit,
    showSystemSettingsEntry: Boolean,
    onOpenSystemSettings: () -> Unit,
    controlsEnabled: Boolean,
    bottomContentPadding: Dp
) {
    // A control is enabled when the session allows control AND it is not mid-operation.
    fun controlEnabled(flag: androidx.compose.runtime.MutableState<Boolean>?): Boolean =
        controlsEnabled && (flag?.value != true)
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
                batteryForm = capabilities.batteryForm,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)
            )
        }
    }

    // Hardware with no ANC (e.g. U.C.T.S — declared with an empty ancModes list) must not
    // render the noise-cancellation block at all; AncSwitch itself always draws the three
    // Transparency/ANC/Off buttons regardless of ancModes, so gate the whole card here.
    if (capabilities.ancModes.isNotEmpty()) {
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
                    enabled = controlsEnabled && pending?.anc?.value != true &&
                        pending?.transparency?.value != true && pending?.smartAncLevel?.value != true
                )
            }
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
                    onSelectedIndexChange = { onGainLevelChange(it.coerceIn(0, gainOptions.size - 1)) },
                    enabled = controlEnabled(pending?.gain)
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
                    onCheckedChange = onLedEnabledChange,
                    enabled = controlEnabled(pending?.led)
                )
            }
        }
    }

    // 3. Prompt tone on/off, with volume slider embedded in the same row (miuix style).
    //    The slider keeps a local value while dragging and only reports on release,
    //    so the control never greys out mid-drag.
    if (capabilities.promptToneSupported || capabilities.promptVolumeSupported) {
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                SwitchPreference(
                    title = stringResource(R.string.control_prompt_tone_title),
                    summary = stringResource(R.string.control_prompt_tone_summary),
                    checked = promptToneEnabled,
                    onCheckedChange = onPromptToneChange,
                    enabled = controlEnabled(pending?.promptTone),
                    bottomAction = if (capabilities.promptVolumeSupported) {
                        @Composable {
                            var localVolume by remember {
                                mutableIntStateOf(promptVolume.coerceIn(0, 100))
                            }
                            LaunchedEffect(promptVolume) {
                                localVolume = promptVolume.coerceIn(0, 100)
                            }
                            SliderPreference(
                                value = localVolume.toFloat(),
                                onValueChange = { localVolume = it.toInt().coerceIn(0, 100) },
                                onValueChangeFinished = { onPromptVolumeChange(localVolume) },
                                title = stringResource(R.string.control_prompt_volume_title),
                                valueText = "$localVolume%",
                                valueRange = 0f..100f,
                                enabled = controlEnabled(pending?.promptTone),
                                insideMargin = PaddingValues(horizontal = 0.dp),
                            )
                        }
                    } else {
                        null
                    },
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
                    onCheckedChange = onHeadTrackingChange,
                    enabled = controlEnabled(pending?.headTracking)
                )
            }
        }
    }

    // 6. Codec controls — LC3 / LDAC / LHDC share GAIA feature 0x10 and are
    //    mutually exclusive. Switching codec briefly disconnects the headset;
    //    the per-switch note keeps the user from being surprised. No group title.
    if (capabilities.lc3Supported) {
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                SwitchPreference(
                    title = stringResource(R.string.control_lc3_title),
                    summary = stringResource(R.string.control_lc3_reconnect_note),
                    checked = lc3Enabled,
                    onCheckedChange = onLc3Change,
                    enabled = controlEnabled(pending?.lc3)
                )
            }
        }
    }
    if (capabilities.ldacSupported) {
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                SwitchPreference(
                    title = stringResource(R.string.control_ldac_title),
                    summary = stringResource(R.string.control_ldac_reconnect_note),
                    checked = ldacEnabled,
                    onCheckedChange = onLdacChange,
                    enabled = controlEnabled(pending?.ldac)
                )
            }
        }
    }
    if (capabilities.lhdcSupported) {
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                SwitchPreference(
                    title = stringResource(R.string.control_lhdc_title),
                    summary = stringResource(R.string.control_lhdc_reconnect_note),
                    checked = lhdcEnabled,
                    onCheckedChange = onLhdcChange,
                    enabled = controlEnabled(pending?.lhdc)
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
                    onCheckedChange = onDualConnectionChange,
                    enabled = controlEnabled(pending?.dualConnection)
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
