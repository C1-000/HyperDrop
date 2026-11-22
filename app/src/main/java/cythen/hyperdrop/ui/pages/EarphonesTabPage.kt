package cythen.hyperdrop.ui.pages

import android.bluetooth.BluetoothDevice
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import cythen.hyperdrop.R
import cythen.hyperdrop.device.AncModeOption
import cythen.hyperdrop.device.DeviceCapabilities
import cythen.hyperdrop.device.SimulatedDevice
import cythen.hyperdrop.ui.ControlPendingState
import cythen.hyperdrop.pods.NoiseControlMode
import cythen.hyperdrop.pods.WearStatus
import cythen.hyperdrop.utils.miuiStrongToast.data.BatteryParams
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
internal fun EarphonesTabPage(
    showEarphoneDetail: Boolean,
    displayTitle: String,
    displayBattery: BatteryParams,
    displayWearStatus: WearStatus,
    displayAnc: NoiseControlMode,
    onAncModeChange: (NoiseControlMode) -> Unit,
    smartAncLevel: NoiseControlMode?,
    displayTransparencyVocalEnhancement: Boolean,
    onTransparencyVocalEnhancementChange: (Boolean) -> Unit,
    spatialAudioMode: Int,
    onSpatialAudioModeChange: (Int) -> Unit,
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
    dualConnectionEnabled: Boolean,
    onDualConnectionChange: (Boolean) -> Unit,
    capabilities: DeviceCapabilities,
    pending: ControlPendingState,
    spatialAudioSupported: Boolean,
    spatialSoundSupported: Boolean,
    ancModes: List<AncModeOption>,
    boxImagePath: String?,
    deviceImageKey: String?,
    connectedDeviceAddress: String,
    connectingDeviceAddress: String?,
    simulatedDevices: List<SimulatedDevice>,
    showConnectErrorDialog: Boolean,
    contentPadding: PaddingValues,
    pageBottomContentPadding: Dp,
    nestedScrollConnection: NestedScrollConnection,
    onDeviceSelected: (BluetoothDevice) -> Unit,
    onConnectedDeviceClick: () -> Unit,
    onDeviceDisconnect: (BluetoothDevice) -> Unit,
    onSimulatedDeviceSelect: (SimulatedDevice) -> Unit,
    onSimulatedDeviceDisconnect: (SimulatedDevice) -> Unit,
    onDismissConnectError: () -> Unit,
) {
    AnimatedContent(
        targetState = showEarphoneDetail,
        modifier = Modifier.fillMaxSize(),
        label = "EarphonesPageAnim",
    ) { detailVisible ->
        if (detailVisible) {
            PodDetailPage(
                modifier = Modifier
                    .overScrollVertical()
                    .nestedScroll(nestedScrollConnection),
                contentPadding = contentPadding,
                bottomContentPadding = pageBottomContentPadding,
                podName = displayTitle.ifEmpty { stringResource(R.string.pod_info) },
                batteryParams = displayBattery,
                wearStatus = displayWearStatus,
                ancMode = displayAnc,
                onAncModeChange = onAncModeChange,
                smartAncLevel = smartAncLevel,
                transparencyVocalEnhancement = displayTransparencyVocalEnhancement,
                onTransparencyVocalEnhancementChange = onTransparencyVocalEnhancementChange,
                spatialAudioMode = spatialAudioMode,
                onSpatialAudioModeChange = onSpatialAudioModeChange,
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
                lc3Enabled = lc3Enabled,
                onLc3Change = onLc3Change,
                dualConnectionEnabled = dualConnectionEnabled,
                onDualConnectionChange = onDualConnectionChange,
                capabilities = capabilities,
                pending = pending,
                spatialAudioSupported = spatialAudioSupported,
                spatialSoundSupported = spatialSoundSupported,
                ancModes = ancModes,
                boxImagePath = boxImagePath,
                deviceImageKey = deviceImageKey,
            )
        } else {
            DevicePickerPage(
                connectedDeviceName = displayTitle,
                connectedDeviceAddress = connectedDeviceAddress,
                connectingDeviceAddress = connectingDeviceAddress,
                showConnectError = showConnectErrorDialog,
                simulatedDevices = simulatedDevices,
                contentPadding = contentPadding,
                bottomContentPadding = pageBottomContentPadding,
                onDeviceSelected = onDeviceSelected,
                onConnectedDeviceClick = onConnectedDeviceClick,
                onDeviceDisconnect = onDeviceDisconnect,
                onSimulatedDeviceSelect = onSimulatedDeviceSelect,
                onSimulatedDeviceDisconnect = onSimulatedDeviceDisconnect,
                onDismissConnectError = onDismissConnectError,
            )
        }
    }
}
