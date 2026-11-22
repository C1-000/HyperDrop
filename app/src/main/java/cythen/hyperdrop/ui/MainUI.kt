package cythen.hyperdrop.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.ui.NavDisplay
import cythen.hyperdrop.OppoPodsApp
import cythen.hyperdrop.R
import cythen.hyperdrop.config.ConfigManager
import cythen.hyperdrop.config.PodImagePrefs
import cythen.hyperdrop.config.PodImageResource
import cythen.hyperdrop.pods.NoiseControlMode
import cythen.hyperdrop.pods.ancModeFromUiStatus
import cythen.hyperdrop.pods.toUiStatus
import cythen.hyperdrop.pods.WearState
import cythen.hyperdrop.pods.WearStatus
import cythen.hyperdrop.device.DeviceProfile
import cythen.hyperdrop.device.DeviceRegistry
import cythen.hyperdrop.device.SimulatedDevice
import cythen.hyperdrop.pods.VirtualDeviceSession
import cythen.hyperdrop.ui.pages.AboutPage
import cythen.hyperdrop.ui.pages.AdaptationStatusPage
import cythen.hyperdrop.ui.pages.DebugPage
import cythen.hyperdrop.ui.pages.RfcommDebugPage
import cythen.hyperdrop.ui.pages.ThemeSettingsPage
import cythen.hyperdrop.utils.RootManager
import cythen.hyperdrop.utils.miuiStrongToast.data.BatteryParams
import cythen.hyperdrop.utils.miuiStrongToast.data.OppoPodsAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

sealed interface Screen : NavKey {
    data object Main : Screen
    data object About : Screen
    data object Theme : Screen
    data object RfcommDebug : Screen
    data object DeviceAdaptation : Screen
    data object Debug : Screen
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainUI(
    backStack: SnapshotStateList<Screen>,
    themeMode: MutableState<Int> = mutableStateOf(0),
    onThemeModeChange: (Int) -> Unit = {},
    accentMode: MutableState<Int> = mutableStateOf(0),
    onAccentModeChange: (Int) -> Unit = {},
    floatingBottomBar: MutableState<Boolean> = mutableStateOf(true),
    onFloatingBottomBarChange: (Boolean) -> Unit = {},
    appLanguage: MutableState<Int> = mutableStateOf(AppLocale.DEFAULT),
    onAppLanguageChange: (Int) -> Unit = {},
    openDetail: Boolean = false,
    openSettings: Boolean = false,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pending = remember { ControlPendingState() }

    /** Mark a control busy for up to 5s, or until an echo broadcast clears it. */
    fun markPending(flag: androidx.compose.runtime.MutableState<Boolean>) {
        flag.value = true
        coroutineScope.launch {
            delay(5_000L)
            flag.value = false
        }
    }

    val mainTitle = remember { mutableStateOf("") }
    val batteryParams = remember { mutableStateOf(BatteryParams()) }
    val wearStatus = remember { mutableStateOf(WearStatus()) }
    val ancMode = remember { mutableStateOf(NoiseControlMode.OFF) }
    /** Smart-mode current auto-applied NC level (LIGHT/MEDIUM/DEEP), or null. */
    val smartAncLevel = remember { mutableStateOf<NoiseControlMode?>(null) }
    val hookConnected = remember { mutableStateOf(openDetail) }
    val transparencyVocalEnhancement = remember { mutableStateOf(false) }
    val gainLevel = remember { mutableStateOf(1) }
    val ledEnabled = remember { mutableStateOf(true) }
    val promptToneEnabled = remember { mutableStateOf(false) }
    val promptVolume = remember { mutableStateOf(50) }
    val headTrackingEnabled = remember { mutableStateOf(false) }
    val lhdcEnabled = remember { mutableStateOf(false) }
    val lc3Enabled = remember { mutableStateOf(false) }
    val dualConnectionEnabled = remember { mutableStateOf(false) }
    val tabs = remember { MainTab.entries.toList() }
    var selectedTab by remember { mutableStateOf(if (openSettings) MainTab.Settings else if (openDetail) MainTab.Earphones else MainTab.Module) }
    var hasAppliedDefaultTab by remember { mutableStateOf(openDetail || openSettings) }
    var bluetoothState by remember { mutableStateOf(readBluetoothState(context)) }
    var xposedService by remember { mutableStateOf(OppoPodsApp.xposedService) }
    var showDevicePicker by remember { mutableStateOf(false) }
    var showRestartScopeDialog by remember { mutableStateOf(false) }
    var restartingScopes by remember { mutableStateOf(false) }
    var connectingDeviceAddress by remember { mutableStateOf<String?>(null) }
    var connectedDeviceAddress by remember { mutableStateOf("") }
    var showConnectErrorDialog by remember { mutableStateOf(false) }
    var hookConnectionState by remember { mutableStateOf("disconnected") }
    var pendingOpenEarphonesAfterPickerLoaded by remember { mutableStateOf(false) }
    var lastBluetoothServiceAliveMs by remember { mutableStateOf(0L) }
    var bluetoothServiceResponsive by remember { mutableStateOf(false) }
    val backgroundColor = appBackground()
    val overlayBottomBar = floatingBottomBar.value
    val pageBottomContentPadding = if (overlayBottomBar) 110.dp else 28.dp
    val backdrop = if (floatingBottomBar.value) {
        rememberLayerBackdrop {
            drawRect(backgroundColor)
            drawContent()
        }
    } else {
        null
    }

    val prefs = remember { context.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE) }
    val openEarphonesOnce = remember { mutableStateOf(prefs.getBoolean("open_earphones_once", false)) }
    val appConfig = remember { ConfigManager.refreshFromPrefs(prefs) }
    val notificationClickAction = remember { mutableStateOf(appConfig.notificationClickAction) }
    val moreClickAction = remember { mutableStateOf(appConfig.moreClickAction) }
    val desktopIconHidden = remember { mutableStateOf(isLauncherIconHidden(context)) }
    val logLevel = remember { mutableStateOf(appConfig.logLevel) }
    val yieldToOfficialApp = remember { mutableStateOf(appConfig.yieldToOfficialApp) }
    val fakeDeviceId = remember { mutableStateOf(appConfig.fakeDeviceId) }
    val islandMode = remember { mutableStateOf(appConfig.islandMode) }
    val islandShowTimings = remember { mutableStateOf(appConfig.islandShowTimings) }
    val spatialAudioMode = remember { mutableStateOf(prefs.getInt("spatial_audio_mode", ConfigManager.SPATIAL_AUDIO_OFF)) }
    val eqPreset = remember { mutableStateOf(-1) }
    val earphonePrefs = remember { mutableStateOf(PodImagePrefs.load(prefs)) }
    val adaptiveCapabilityOverride = remember { mutableStateOf(appConfig.adaptiveCapabilityOverride) }
    val spatialAudioCapabilityOverride = remember { mutableStateOf(appConfig.spatialAudioCapabilityOverride) }
    val spatialSoundSwitchCapabilityOverride = remember { mutableStateOf(appConfig.spatialSoundSwitchCapabilityOverride) }
    val ancImplementationCapabilityOverride = remember { mutableStateOf(appConfig.ancImplementationCapabilityOverride) }
    val simulatedDevices = remember { mutableStateOf(appConfig.simulatedDevices) }

    /** Ask whoever owns the active session to re-emit its cached state. */
    fun requestUiSync() {
        if (VirtualDeviceSession.isConnected()) {
            VirtualDeviceSession.handle(context, Intent(OppoPodsAction.ACTION_PODS_UI_INIT))
        }
        // Always ping the hooked process as well: it answers ACTION_MODULE_BLUETOOTH_SERVICE_ALIVE,
        // and its stale device state is filtered out by the broadcast receiver while a simulated
        // device owns the UI.
        sendBluetoothModuleBroadcast(context, OppoPodsAction.ACTION_PODS_UI_INIT)
    }

    /**
     * Send a control intent to the active audio session.
     *
     * A simulated device lives in this process, so its session consumes the intent directly;
     * otherwise the intent goes to the hooked Bluetooth process as before.
     */
    fun dispatchPodIntent(action: String, fill: Intent.() -> Unit = {}) {
        val virtualActive = VirtualDeviceSession.isConnected() &&
                connectedDeviceAddress.equals(VirtualDeviceSession.activeAddress(), ignoreCase = true)
        if (virtualActive) {
            Intent(action).apply {
                fill()
                VirtualDeviceSession.handle(context, this)
            }
            return
        }
        Intent(action).apply {
            fill()
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            context.sendBroadcast(this)
        }
    }

    // External entries (Settings / Xiaomi Bluetooth / MiLink / popup “更多”) open the
    // PUDDING detail immediately, then refresh live state from com.android.bluetooth.
    LaunchedEffect(openSettings) {
        if (openSettings) {
            selectedTab = MainTab.Settings
            showDevicePicker = false
        }
    }

    LaunchedEffect(openDetail) {
        if (openDetail) {
            hookConnected.value = true
            selectedTab = MainTab.Earphones
            showDevicePicker = false
            context.sendBroadcast(Intent(OppoPodsAction.ACTION_PODS_UI_INIT).apply {
                setPackage("com.android.bluetooth")
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            })
            context.sendBroadcast(Intent(OppoPodsAction.ACTION_REFRESH_STATUS).apply {
                setPackage("com.android.bluetooth")
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            })
        }
    }

    val canShowDetailPage = hookConnected.value
    val showEarphoneDetail = canShowDetailPage && !showDevicePicker
    val displayBattery = batteryParams.value
    val displayWearStatus = wearStatus.value
    val displayAnc = ancMode.value
    val displayTransparencyVocalEnhancement = transparencyVocalEnhancement.value
    val displayTitle = mainTitle.value.takeIf { it.isNotBlank() && hookConnected.value } ?: mainTitle.value
    val displayCapabilities = DeviceRegistry.detectDeviceCapabilities(
        deviceName = displayTitle,
        adaptiveOverride = adaptiveCapabilityOverride.value,
        spatialAudioOverride = spatialAudioCapabilityOverride.value,
        spatialSoundSwitchOverride = spatialSoundSwitchCapabilityOverride.value,
        ancImplementationOverride = ancImplementationCapabilityOverride.value,
    )

    LaunchedEffect(displayTitle, displayCapabilities) {
        Log.i(
            "HyperDrop",
            "capability check: deviceName='$displayTitle', adaptive=${displayCapabilities.adaptiveSupported}, spatial=${displayCapabilities.spatialAudioSupported}, spatialSoundSwitch=${displayCapabilities.spatialSoundSwitchSupported}"
        )
    }

    LaunchedEffect(displayTitle) {
        if (displayTitle.isNotEmpty()) {
            mainTitle.value = displayTitle
        }
    }

    LaunchedEffect(canShowDetailPage) {
        if (!hasAppliedDefaultTab) {
            selectedTab = if (canShowDetailPage) MainTab.Earphones else MainTab.Module
            hasAppliedDefaultTab = true
        }
    }

    LaunchedEffect(canShowDetailPage, openEarphonesOnce.value) {
        if (openEarphonesOnce.value && canShowDetailPage) {
            selectedTab = MainTab.Earphones
            showDevicePicker = false
            openEarphonesOnce.value = false
            prefs.edit().remove("open_earphones_once").apply()
        }
    }

    LaunchedEffect(hookConnectionState) {
        if (hookConnectionState == "error") {
            connectingDeviceAddress = null
            pendingOpenEarphonesAfterPickerLoaded = false
            showConnectErrorDialog = true
            showDevicePicker = true
        }
    }

    LaunchedEffect(pendingOpenEarphonesAfterPickerLoaded, connectingDeviceAddress, hookConnected.value) {
        if (pendingOpenEarphonesAfterPickerLoaded && connectingDeviceAddress == null && hookConnected.value) {
            withFrameNanos { }
            pendingOpenEarphonesAfterPickerLoaded = false
            showDevicePicker = false
        }
    }

    val broadcastReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                val action = p1?.action ?: return
                if (action in VIRTUAL_SESSION_ACTIONS && VirtualDeviceSession.isConnected()) {
                    val fromSimulated = p1.getStringExtra("address")
                        ?.equals(VirtualDeviceSession.activeAddress(), ignoreCase = true) == true
                    if (!fromSimulated) return
                }
                when (action) {
                    OppoPodsAction.ACTION_PODS_ANC_CHANGED -> {
                        connectedDeviceAddress = p1.getStringExtra("address") ?: connectedDeviceAddress
                        ancMode.value = ancModeFromUiStatus(p1.getIntExtra("status", 1))
                        pending.anc.value = false
                    }

                    OppoPodsAction.ACTION_PODS_SMART_ANC_LEVEL_CHANGED -> {
                        val ord = p1.getIntExtra("ordinal", -1)
                        smartAncLevel.value = NoiseControlMode.entries.getOrNull(ord)
                        pending.smartAncLevel.value = false
                    }

                    OppoPodsAction.ACTION_PODS_BATTERY_CHANGED -> {
                        connectedDeviceAddress = p1.getStringExtra("address") ?: connectedDeviceAddress
                        batteryParams.value =
                            p1.getParcelableExtra("status", BatteryParams::class.java)!!
                    }

                    OppoPodsAction.ACTION_PODS_WEAR_STATUS_CHANGED -> {
                        connectedDeviceAddress = p1.getStringExtra("address") ?: connectedDeviceAddress
                        wearStatus.value = WearStatus(
                            left = wearStateFromExtra(p1.getIntExtra("left_wear_status", -1)),
                            right = wearStateFromExtra(p1.getIntExtra("right_wear_status", -1)),
                            case = wearStateFromExtra(p1.getIntExtra("case_wear_status", -1))
                        )
                    }

                    OppoPodsAction.ACTION_PODS_TRANSPARENCY_VOCAL_ENHANCEMENT_CHANGED -> {
                        transparencyVocalEnhancement.value = p1.getBooleanExtra("enabled", false)
                        pending.transparency.value = false
                    }

                    OppoPodsAction.ACTION_PODS_SPATIAL_AUDIO_CHANGED -> {
                        spatialAudioMode.value = p1.getIntExtra("mode", ConfigManager.SPATIAL_AUDIO_OFF)
                        pending.spatial.value = false
                    }

                    OppoPodsAction.ACTION_PODS_EQ_PRESET_CHANGED -> {
                        eqPreset.value = p1.getIntExtra("preset", -1)
                        pending.eq.value = false
                    }

                    OppoPodsAction.ACTION_PODS_GAIN_LEVEL_CHANGED -> {
                        gainLevel.value = p1.getIntExtra("level", gainLevel.value).coerceIn(0, 2)
                        pending.gain.value = false
                    }

                    OppoPodsAction.ACTION_PODS_LED_STATE_CHANGED -> {
                        ledEnabled.value = p1.getBooleanExtra("enabled", ledEnabled.value)
                        pending.led.value = false
                    }

                    OppoPodsAction.ACTION_PROMPT_TONE_CHANGED -> {
                        promptToneEnabled.value = p1.getBooleanExtra("enabled", promptToneEnabled.value)
                        promptVolume.value = p1.getIntExtra("volume", promptVolume.value).coerceIn(0, 100)
                        pending.promptTone.value = false
                    }

                    OppoPodsAction.ACTION_HEAD_TRACKING_CHANGED -> {
                        headTrackingEnabled.value = p1.getBooleanExtra("enabled", headTrackingEnabled.value)
                        pending.headTracking.value = false
                    }

                    OppoPodsAction.ACTION_LHDC_CHANGED -> {
                        lhdcEnabled.value = p1.getBooleanExtra("enabled", lhdcEnabled.value)
                        pending.lhdc.value = false
                    }

                    OppoPodsAction.ACTION_LC3_CHANGED -> {
                        lc3Enabled.value = p1.getBooleanExtra("enabled", lc3Enabled.value)
                        pending.lc3.value = false
                    }

                    OppoPodsAction.ACTION_DUAL_CONNECTION_CHANGED -> {
                        dualConnectionEnabled.value = p1.getBooleanExtra("enabled", dualConnectionEnabled.value)
                        pending.dualConnection.value = false
                    }

                    OppoPodsAction.ACTION_PODS_CONNECTED -> {
                        val deviceName = p1.getStringExtra("device_name")
                        val shouldOpenEarphones = connectingDeviceAddress != null || !hasAppliedDefaultTab
                        connectedDeviceAddress = p1.getStringExtra("address") ?: connectedDeviceAddress
                        connectingDeviceAddress = null
                        mainTitle.value = deviceName ?: ""
                        earphonePrefs.value = PodImagePrefs.upsertConnected(
                            prefs = prefs,
                            service = xposedService,
                            address = connectedDeviceAddress,
                            name = deviceName.orEmpty(),
                        )
                        hookConnected.value = true
                        hookConnectionState = "connected"
                        if (shouldOpenEarphones) {
                            if (!hasAppliedDefaultTab) {
                                selectedTab = MainTab.Earphones
                            }
                            hasAppliedDefaultTab = true
                            pendingOpenEarphonesAfterPickerLoaded = true
                        }
                        Log.i("HyperDrop", "pod connected via hook: $deviceName")
                    }

                    OppoPodsAction.ACTION_PODS_CONNECTION_STATE_CHANGED -> {
                        hookConnectionState = p1.getStringExtra("state") ?: hookConnectionState
                        if (hookConnectionState == "disconnected") {
                            connectedDeviceAddress = ""
                            mainTitle.value = ""
                            hookConnected.value = false
                        } else if (hookConnected.value) {
                            connectedDeviceAddress = p1.getStringExtra("address") ?: connectedDeviceAddress
                            p1.getStringExtra("device_name")?.let {
                                mainTitle.value = it
                                earphonePrefs.value = PodImagePrefs.upsertConnected(prefs, xposedService, connectedDeviceAddress, it)
                            }
                        }
                    }

                    OppoPodsAction.ACTION_PODS_DISCONNECTED -> {
                        mainTitle.value = ""
                        connectedDeviceAddress = ""
                        hookConnectionState = "disconnected"
                        hookConnected.value = false
                    }

                    OppoPodsAction.ACTION_MODULE_BLUETOOTH_SERVICE_ALIVE -> {
                        lastBluetoothServiceAliveMs = SystemClock.elapsedRealtime()
                        bluetoothServiceResponsive = true
                    }

                    BluetoothAdapter.ACTION_STATE_CHANGED,
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        bluetoothState = readBluetoothState(context)
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val serviceListener: (io.github.libxposed.service.XposedService?) -> Unit = { service ->
            xposedService = service
        }
        OppoPodsApp.addServiceListener(serviceListener)

        context.registerReceiver(broadcastReceiver, IntentFilter().apply {
            addAction(OppoPodsAction.ACTION_PODS_ANC_CHANGED)
            addAction(OppoPodsAction.ACTION_PODS_SMART_ANC_LEVEL_CHANGED)
            addAction(OppoPodsAction.ACTION_PODS_BATTERY_CHANGED)
            addAction(OppoPodsAction.ACTION_PODS_WEAR_STATUS_CHANGED)
            addAction(OppoPodsAction.ACTION_PODS_TRANSPARENCY_VOCAL_ENHANCEMENT_CHANGED)
            addAction(OppoPodsAction.ACTION_PODS_SPATIAL_AUDIO_CHANGED)
            addAction(OppoPodsAction.ACTION_PODS_EQ_PRESET_CHANGED)
            addAction(OppoPodsAction.ACTION_PODS_GAIN_LEVEL_CHANGED)
            addAction(OppoPodsAction.ACTION_PODS_LED_STATE_CHANGED)
            addAction(OppoPodsAction.ACTION_PROMPT_TONE_CHANGED)
            addAction(OppoPodsAction.ACTION_HEAD_TRACKING_CHANGED)
            addAction(OppoPodsAction.ACTION_LHDC_CHANGED)
            addAction(OppoPodsAction.ACTION_LC3_CHANGED)
            addAction(OppoPodsAction.ACTION_DUAL_CONNECTION_CHANGED)
            addAction(OppoPodsAction.ACTION_PODS_CONNECTED)
            addAction(OppoPodsAction.ACTION_PODS_CONNECTION_STATE_CHANGED)
            addAction(OppoPodsAction.ACTION_PODS_DISCONNECTED)
            addAction(OppoPodsAction.ACTION_MODULE_BLUETOOTH_SERVICE_ALIVE)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }, Context.RECEIVER_EXPORTED)

        requestUiSync()

        onDispose {
            if (VirtualDeviceSession.isConnected()) {
                VirtualDeviceSession.disconnect(context, notify = false)
            }
            sendBluetoothModuleBroadcast(context, OppoPodsAction.ACTION_PODS_UI_CLOSED)
            try {
                context.unregisterReceiver(broadcastReceiver)
            } catch (_: Exception) {}
            OppoPodsApp.removeServiceListener(serviceListener)
        }
    }

    // Event-driven status: query once when the visible app state changes. Earbud-originated ANC
    // changes are received over the existing RFCOMM reader, so no background timer is needed.
    LaunchedEffect(selectedTab, hookConnected.value) {
        requestUiSync()
    }

    LaunchedEffect(lastBluetoothServiceAliveMs) {
        while (true) {
            bluetoothServiceResponsive = lastBluetoothServiceAliveMs > 0L &&
                    SystemClock.elapsedRealtime() - lastBluetoothServiceAliveMs <= 75_000L
            delay(5_000L)
        }
    }

    fun setAncMode(mode: NoiseControlMode) {
        ancMode.value = mode
        markPending(pending.anc)
        val status = mode.toUiStatus()
        dispatchPodIntent(OppoPodsAction.ACTION_ANC_SELECT) {
            putExtra("status", status)
        }
    }

    fun setTransparencyVocalEnhancement(enabled: Boolean) {
        transparencyVocalEnhancement.value = enabled
        markPending(pending.transparency)
        dispatchPodIntent(OppoPodsAction.ACTION_TRANSPARENCY_VOCAL_ENHANCEMENT_SET) {
            putExtra("enabled", enabled)
        }
    }

    fun clearPodConnectionState() {
        connectingDeviceAddress = null
        pendingOpenEarphonesAfterPickerLoaded = false
        connectedDeviceAddress = ""
        mainTitle.value = ""
        batteryParams.value = BatteryParams()
        wearStatus.value = WearStatus()
        ancMode.value = NoiseControlMode.OFF
        hookConnected.value = false
        hookConnectionState = "disconnected"
        showConnectErrorDialog = false
        showDevicePicker = true
        selectedTab = MainTab.Earphones
    }

    fun onDeviceSelected(device: BluetoothDevice) {
        if (VirtualDeviceSession.isConnected()) {
            VirtualDeviceSession.disconnect(context, notify = false)
        }
        connectingDeviceAddress = device.address
        pendingOpenEarphonesAfterPickerLoaded = false
        showConnectErrorDialog = false
        showDevicePicker = true
        selectedTab = MainTab.Earphones
        hookConnectionState = "connecting"
        Intent(OppoPodsAction.ACTION_CONNECT_POD_REQUEST).apply {
            putExtra("device", device)
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            context.sendBroadcast(this)
        }
    }

    fun setSpatialAudioMode(mode: Int) {
        val normalizedMode = mode.coerceIn(ConfigManager.SPATIAL_AUDIO_OFF, ConfigManager.SPATIAL_AUDIO_HEAD_TRACKING)
        spatialAudioMode.value = normalizedMode
        markPending(pending.spatial)
        prefs.edit().putInt("spatial_audio_mode", normalizedMode).apply()
        dispatchPodIntent(OppoPodsAction.ACTION_SPATIAL_AUDIO_SET) {
            putExtra("mode", normalizedMode)
        }
    }

    fun setEqPreset(preset: Int) {
        eqPreset.value = preset
        markPending(pending.eq)
        dispatchPodIntent(OppoPodsAction.ACTION_EQ_PRESET_SET) {
            putExtra("preset", preset)
        }
    }

    fun setGainLevel(level: Int) {
        val normalized = level.coerceIn(0, 2)
        gainLevel.value = normalized
        markPending(pending.gain)
        dispatchPodIntent(OppoPodsAction.ACTION_GAIN_LEVEL_SET) {
            putExtra("level", normalized)
        }
    }

    fun setLedEnabled(enabled: Boolean) {
        ledEnabled.value = enabled
        markPending(pending.led)
        dispatchPodIntent(OppoPodsAction.ACTION_LED_STATE_SET) {
            putExtra("enabled", enabled)
        }
    }

    fun setPromptTone(enabled: Boolean) {
        promptToneEnabled.value = enabled
        markPending(pending.promptTone)
        dispatchPodIntent(OppoPodsAction.ACTION_PROMPT_TONE_SET) {
            putExtra("enabled", enabled)
            putExtra("volume", promptVolume.value)
            putExtra("index", 0)
        }
    }

    fun setPromptVolume(volume: Int) {
        val normalized = volume.coerceIn(0, 100)
        promptVolume.value = normalized
        markPending(pending.promptTone)
        dispatchPodIntent(OppoPodsAction.ACTION_PROMPT_VOLUME_SET) {
            putExtra("enabled", promptToneEnabled.value)
            putExtra("volume", normalized)
            putExtra("index", 0)
        }
    }

    fun setHeadTracking(enabled: Boolean) {
        headTrackingEnabled.value = enabled
        markPending(pending.headTracking)
        dispatchPodIntent(OppoPodsAction.ACTION_HEAD_TRACKING_SET) {
            putExtra("enabled", enabled)
        }
    }

    fun setLhdc(enabled: Boolean) {
        lhdcEnabled.value = enabled
        markPending(pending.lhdc)
        dispatchPodIntent(OppoPodsAction.ACTION_LHDC_SET) {
            putExtra("enabled", enabled)
        }
    }

    fun setLc3(enabled: Boolean) {
        lc3Enabled.value = enabled
        markPending(pending.lc3)
        dispatchPodIntent(OppoPodsAction.ACTION_LC3_SET) {
            putExtra("enabled", enabled)
        }
    }

    fun setDualConnection(enabled: Boolean) {
        dualConnectionEnabled.value = enabled
        markPending(pending.dualConnection)
        dispatchPodIntent(OppoPodsAction.ACTION_DUAL_CONNECTION_SET) {
            putExtra("enabled", enabled)
        }
    }

    fun onDeviceDisconnect(device: BluetoothDevice) {
        connectingDeviceAddress = null
        pendingOpenEarphonesAfterPickerLoaded = false
        if (device.address == connectedDeviceAddress) {
            hookConnected.value = false
            hookConnectionState = "disconnected"
            connectedDeviceAddress = ""
            mainTitle.value = ""
        }
        Intent(OppoPodsAction.ACTION_DISCONNECT_POD_REQUEST).apply {
            putExtra("device", device)
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            context.sendBroadcast(this)
        }
    }

    fun onConnectedDeviceClick() {
        if (connectedDeviceAddress.isBlank() && mainTitle.value.isBlank()) return
        pendingOpenEarphonesAfterPickerLoaded = false
        hookConnected.value = true
        hookConnectionState = "connected"
        showDevicePicker = false
        selectedTab = MainTab.Earphones
    }

    fun backToDevicePicker() {
        showDevicePicker = true
    }

    fun connectSimulatedDevice(device: SimulatedDevice) {
        // Leave the debug page (if it is open) so the connection progress is actually visible.
        while (backStack.size > 1) backStack.removeLast()
        connectingDeviceAddress = device.address
        pendingOpenEarphonesAfterPickerLoaded = false
        showConnectErrorDialog = false
        showDevicePicker = false
        selectedTab = MainTab.Earphones
        hookConnectionState = "connecting"
        hookConnected.value = false
        VirtualDeviceSession.connect(context, device)
    }

    fun disconnectSimulatedDevice(device: SimulatedDevice) {
        connectingDeviceAddress = null
        VirtualDeviceSession.disconnect(context)
        if (device.address.equals(connectedDeviceAddress, ignoreCase = true)) {
            clearPodConnectionState()
        }
        requestUiSync()
    }

    fun removeSimulatedDevice(device: SimulatedDevice) {
        if (device.address.equals(connectedDeviceAddress, ignoreCase = true)) {
            disconnectSimulatedDevice(device)
        }
        val updated = simulatedDevices.value.filterNot { it.address.equals(device.address, ignoreCase = true) }
        simulatedDevices.value = updated
        ConfigManager.updateSimulatedDevices(prefs, xposedService, updated)
    }

    /** Add a simulated device backed by [profile], then connect to it right away. */
    fun addSimulatedDevice(profile: DeviceProfile) {
        val used = simulatedDevices.value.map { it.address.uppercase() }.toSet()
        val address = (1..254)
            .map { "02:00:00:00:00:" + it.toString(16).uppercase().padStart(2, '0') }
            .firstOrNull { it !in used }
            ?: return
        val device = SimulatedDevice(
            profileId = profile.id,
            address = address,
            displayName = profile.displayName,
            createdAt = System.currentTimeMillis(),
        )
        val updated = simulatedDevices.value + device
        simulatedDevices.value = updated
        ConfigManager.updateSimulatedDevices(prefs, xposedService, updated)
        connectSimulatedDevice(device)
    }

    fun openBluetoothSettings() {
        val action = if (bluetoothState.enabled) Settings.ACTION_BLUETOOTH_SETTINGS else BluetoothAdapter.ACTION_REQUEST_ENABLE
        Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(this) }
                .onFailure { Toast.makeText(context, R.string.connect_failed, Toast.LENGTH_SHORT).show() }
        }
    }

    fun openDevicePicker() {
        showDevicePicker = true
        selectedTab = MainTab.Earphones
    }

    fun openSystemHeadsetSettings() {
        // HyperOriG route: never enter the spoofed OPPO MiuiHeadsetActivity.
        // The settings icon stays inside HyperDrop and opens the module Settings tab.
        selectedTab = MainTab.Settings
    }

    fun refreshStatus() {
        if (hookConnected.value) {
            dispatchPodIntent(OppoPodsAction.ACTION_REFRESH_STATUS)
            Toast.makeText(context, R.string.refresh_status_toast, Toast.LENGTH_SHORT).show()
        }
    }

    fun savePodImages(
        address: String,
        name: String,
        images: Map<PodImageResource, Uri?>,
        clearedImages: Set<PodImageResource>,
    ) {
        earphonePrefs.value = PodImagePrefs.saveImages(context, prefs, xposedService, address, name, images, clearedImages)
    }

    fun restartScopes(packages: List<String>) {
        if (packages.isEmpty() || restartingScopes) return
        restartingScopes = true
        coroutineScope.launch {
            val success = withContext(Dispatchers.IO) {
                RootManager.restartPackages(packages)
            }
            restartingScopes = false
            showRestartScopeDialog = false
            if (success && "com.android.bluetooth" in packages) {
                clearPodConnectionState()
            }
            Toast.makeText(
                context,
                if (success) R.string.restart_scope_success else R.string.restart_scope_failed,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val entryProvider = entryProvider<Screen> {
        entry<Screen.Main> {
            MainTabsScaffold(
                tabs = tabs,
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                floatingBottomBar = floatingBottomBar.value,
                backdrop = backdrop,
                backgroundColor = backgroundColor,
                overlayBottomBar = overlayBottomBar,
                pageBottomContentPadding = pageBottomContentPadding,
                xposedService = xposedService,
                bluetoothServiceResponsive = bluetoothServiceResponsive,
                bluetoothEnabled = bluetoothState.enabled,
                bondedDeviceCount = bluetoothState.bondedCount,
                onBluetoothStatusClick = { openBluetoothSettings() },
                onPairedBluetoothClick = { openDevicePicker() },
                showEarphoneDetail = showEarphoneDetail,
                mainTitle = mainTitle.value,
                displayTitle = displayTitle,
                displayBattery = displayBattery,
                displayWearStatus = displayWearStatus,
                displayAnc = displayAnc,
                onAncModeChange = { setAncMode(it) },
                smartAncLevel = smartAncLevel.value,
                displayTransparencyVocalEnhancement = displayTransparencyVocalEnhancement,
                onTransparencyVocalEnhancementChange = { setTransparencyVocalEnhancement(it) },
                spatialAudioMode = spatialAudioMode.value,
                onSpatialAudioModeChange = { setSpatialAudioMode(it) },
                eqPreset = eqPreset.value,
                onEqPresetChange = { setEqPreset(it) },
                gainLevel = gainLevel,
                onGainLevelChange = { setGainLevel(it) },
                ledEnabled = ledEnabled,
                onLedEnabledChange = { setLedEnabled(it) },
                promptToneEnabled = promptToneEnabled,
                onPromptToneChange = { setPromptTone(it) },
                promptVolume = promptVolume,
                onPromptVolumeChange = { setPromptVolume(it) },
                headTrackingEnabled = headTrackingEnabled,
                onHeadTrackingChange = { setHeadTracking(it) },
                lhdcEnabled = lhdcEnabled,
                onLhdcChange = { setLhdc(it) },
                lc3Enabled = lc3Enabled,
                onLc3Change = { setLc3(it) },
                dualConnectionEnabled = dualConnectionEnabled,
                onDualConnectionChange = { setDualConnection(it) },
                capabilities = displayCapabilities,
                pending = pending,
                onRefreshStatus = { refreshStatus() },
                spatialAudioSupported = displayCapabilities.spatialAudioSupported,
                spatialSoundSupported = displayCapabilities.spatialSoundSwitchSupported,
                ancModes = displayCapabilities.ancModes,
                earphonePrefs = earphonePrefs.value,
                connectedDeviceAddress = connectedDeviceAddress,
                connectingDeviceAddress = connectingDeviceAddress,
                simulatedDevices = simulatedDevices.value,
                showConnectErrorDialog = showConnectErrorDialog,
                onDeviceSelected = { onDeviceSelected(it) },
                onConnectedDeviceClick = { onConnectedDeviceClick() },
                onDeviceDisconnect = { onDeviceDisconnect(it) },
                onSimulatedDeviceSelect = { connectSimulatedDevice(it) },
                onSimulatedDeviceDisconnect = { disconnectSimulatedDevice(it) },
                onDismissConnectError = { showConnectErrorDialog = false },
                desktopIconHidden = desktopIconHidden,
                onDesktopIconHiddenChange = {
                    desktopIconHidden.value = it
                    setLauncherIconHidden(context, it)
                },
                yieldToOfficialApp = yieldToOfficialApp,
                onYieldToOfficialAppChange = {
                    yieldToOfficialApp.value = it
                    ConfigManager.updateYieldToOfficialApp(prefs, xposedService, it)
                    broadcastConfigChanged(context, "com.android.bluetooth")
                    broadcastConfigChanged(context, "com.milink.service")
                    broadcastConfigChanged(context, "com.xiaomi.bluetooth")
                    broadcastConfigChanged(context, "com.android.settings")
                },
                logLevel = logLevel,
                onLogLevelChange = {
                    logLevel.value = it
                    ConfigManager.updateLogLevel(prefs, xposedService, it)
                    broadcastConfigChanged(context, "com.android.bluetooth")
                    broadcastConfigChanged(context, "com.milink.service")
                    broadcastConfigChanged(context, "com.xiaomi.bluetooth")
                },
                islandMode = islandMode,
                onIslandModeChange = {
                    islandMode.value = it
                    ConfigManager.updateIslandMode(prefs, xposedService, it)
                    broadcastConfigChanged(context, "com.android.bluetooth")
                    broadcastConfigChanged(context, "com.xiaomi.bluetooth")
                },
                islandShowTimings = islandShowTimings,
                onIslandShowTimingsChange = {
                    islandShowTimings.value = it
                    ConfigManager.updateIslandShowTimings(prefs, xposedService, it)
                    broadcastConfigChanged(context, "com.android.bluetooth")
                },
                appLanguage = appLanguage,
                onAppLanguageChange = {
                    appLanguage.value = it
                    onAppLanguageChange(it)
                },
                notificationClickAction = notificationClickAction,
                onNotificationClickActionChange = {
                    notificationClickAction.value = it
                    ConfigManager.updateNotificationClickAction(prefs, xposedService, it)
                    broadcastConfigChanged(context, "com.xiaomi.bluetooth")
                },
                moreClickAction = moreClickAction,
                onMoreClickActionChange = {
                    moreClickAction.value = it
                    ConfigManager.updateMoreClickAction(prefs, xposedService, it)
                },
                onOpenRfcommDebug = { backStack.add(Screen.RfcommDebug) },
                fakeDeviceId = fakeDeviceId,
                onFakeDeviceIdChange = {
                    fakeDeviceId.value = it
                    ConfigManager.updateFakeDeviceId(prefs, xposedService, it)
                    broadcastConfigChanged(context, "com.android.bluetooth")
                    broadcastConfigChanged(context, "com.android.settings")
                    broadcastConfigChanged(context, "com.milink.service")
                    broadcastConfigChanged(context, "com.xiaomi.bluetooth")
                },
                onOpenTheme = { backStack.add(Screen.Theme) },
                onOpenAbout = { backStack.add(Screen.About) },
                onOpenDeviceAdaptation = { backStack.add(Screen.DeviceAdaptation) },
                onOpenDebug = { backStack.add(Screen.Debug) },
                showRestartScopeDialog = showRestartScopeDialog,
                restartingScopes = restartingScopes,
                onShowRestartScopeDialog = { showRestartScopeDialog = true },
                onDismissRestartScopeDialog = { showRestartScopeDialog = false },
                onRestartScopes = { restartScopes(it) },
                onBackToDevicePicker = { backToDevicePicker() },
                onOpenSystemHeadsetSettings = { openSystemHeadsetSettings() },
                onSavePodImages = { address, name, images, clearedImages ->
                    savePodImages(address, name, images, clearedImages)
                },
            )
        }
        entry<Screen.About> {
            val aboutScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = stringResource(R.string.about),
                        largeTitle = stringResource(R.string.about),
                        scrollBehavior = aboutScrollBehavior,
                        navigationIcon = {
                            IconButton(onClick = { backStack.removeLast() }) {
                                Icon(imageVector = MiuixIcons.Back, contentDescription = "Back")
                            }
                        }
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor)
                        .padding(padding),
                ) {
                    AboutPage(
                        modifier = Modifier
                            .overScrollVertical()
                            .nestedScroll(aboutScrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(bottom = pageBottomContentPadding),
                    )
                }
            }
        }
        entry<Screen.Theme> {
            val themeScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = stringResource(R.string.theme_title),
                        largeTitle = stringResource(R.string.theme_title),
                        scrollBehavior = themeScrollBehavior,
                        navigationIcon = {
                            IconButton(onClick = { backStack.removeLast() }) {
                                Icon(imageVector = MiuixIcons.Back, contentDescription = "Back")
                            }
                        }
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor)
                        .padding(padding),
                ) {
                    ThemeSettingsPage(
                        modifier = Modifier
                            .overScrollVertical()
                            .nestedScroll(themeScrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(bottom = pageBottomContentPadding),
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        accentMode = accentMode,
                        onAccentModeChange = onAccentModeChange,
                        floatingBottomBar = floatingBottomBar,
                        onFloatingBottomBarChange = onFloatingBottomBarChange,
                    )
                }
            }
        }
        entry<Screen.RfcommDebug> {
            val rfcommScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
            var clearRfcommLogsRequest by remember { mutableIntStateOf(0) }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = "RFCOMM 调试",
                        largeTitle = "RFCOMM 调试",
                        scrollBehavior = rfcommScrollBehavior,
                        navigationIcon = {
                            IconButton(onClick = { backStack.removeLast() }) {
                                Icon(imageVector = MiuixIcons.Back, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = { clearRfcommLogsRequest++ }) {
                                Icon(imageVector = MiuixIcons.Delete, contentDescription = "Clear logs")
                            }
                        }
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor)
                        .padding(padding),
                ) {
                    RfcommDebugPage(
                        modifier = Modifier.nestedScroll(rfcommScrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(0.dp),
                        clearRequest = clearRfcommLogsRequest,
                    )
                }
            }
        }
        entry<Screen.Debug> {
            val debugScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = stringResource(R.string.debug_page_title),
                        largeTitle = stringResource(R.string.debug_page_title),
                        scrollBehavior = debugScrollBehavior,
                        navigationIcon = {
                            IconButton(onClick = { backStack.removeLast() }) {
                                Icon(imageVector = MiuixIcons.Back, contentDescription = "Back")
                            }
                        }
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor)
                        .padding(padding),
                ) {
                    DebugPage(
                        modifier = Modifier
                            .overScrollVertical()
                            .nestedScroll(debugScrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(bottom = pageBottomContentPadding),
                        simulatedDevices = simulatedDevices.value,
                        connectedAddress = connectedDeviceAddress,
                        connectingAddress = connectingDeviceAddress,
                        onAddProfile = { addSimulatedDevice(it) },
                        onConnectDevice = { connectSimulatedDevice(it) },
                        onDisconnectDevice = { disconnectSimulatedDevice(it) },
                        onRemoveDevice = { removeSimulatedDevice(it) },
                    )
                }
            }
        }
        entry<Screen.DeviceAdaptation> {
            val adaptationScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = stringResource(R.string.device_adaptation_title),
                        largeTitle = stringResource(R.string.device_adaptation_title),
                        scrollBehavior = adaptationScrollBehavior,
                        navigationIcon = {
                            IconButton(onClick = { backStack.removeLast() }) {
                                Icon(imageVector = MiuixIcons.Back, contentDescription = "Back")
                            }
                        }
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor)
                        .padding(padding),
                ) {
                    AdaptationStatusPage(
                        modifier = Modifier
                            .overScrollVertical()
                            .nestedScroll(adaptationScrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(bottom = pageBottomContentPadding),
                    )
                }
            }
        }
    }

    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryProvider = entryProvider
    )

    NavDisplay(
        entries = entries,
        onBack = {
            if (backStack.size > 1) {
                backStack.removeLast()
            } else {
                (context as? Activity)?.finish()
            }
        }
    )
}

@Composable
fun appBackground(): Color = MiuixTheme.colorScheme.surface

private data class BluetoothSummary(
    val enabled: Boolean,
    val bondedCount: Int,
)

private fun readBluetoothState(context: Context): BluetoothSummary {
    val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    return runCatching {
        BluetoothSummary(
            enabled = adapter?.isEnabled == true,
            bondedCount = adapter?.bondedDevices?.size ?: 0,
        )
    }.getOrDefault(BluetoothSummary(enabled = false, bondedCount = 0))
}

private fun wearStateFromExtra(value: Int): WearState? {
    return WearState.fromValue(value)
}

/**
 * Device-state broadcasts that a simulated session owns while it is connected. Any such broadcast
 * coming from the hooked Bluetooth process (its address does not match the simulated device) is
 * dropped so the cached real-device snapshot cannot wipe the simulated connection UI.
 */
private val VIRTUAL_SESSION_ACTIONS: Set<String> = setOf(
    OppoPodsAction.ACTION_PODS_CONNECTED,
    OppoPodsAction.ACTION_PODS_DISCONNECTED,
    OppoPodsAction.ACTION_PODS_CONNECTION_STATE_CHANGED,
    OppoPodsAction.ACTION_PODS_BATTERY_CHANGED,
    OppoPodsAction.ACTION_PODS_WEAR_STATUS_CHANGED,
    OppoPodsAction.ACTION_PODS_ANC_CHANGED,
    OppoPodsAction.ACTION_PODS_SMART_ANC_LEVEL_CHANGED,
    OppoPodsAction.ACTION_PODS_TRANSPARENCY_VOCAL_ENHANCEMENT_CHANGED,
    OppoPodsAction.ACTION_PODS_SPATIAL_AUDIO_CHANGED,
    OppoPodsAction.ACTION_PODS_EQ_PRESET_CHANGED,
    OppoPodsAction.ACTION_PODS_GAIN_LEVEL_CHANGED,
    OppoPodsAction.ACTION_PODS_LED_STATE_CHANGED,
    OppoPodsAction.ACTION_PROMPT_TONE_CHANGED,
    OppoPodsAction.ACTION_HEAD_TRACKING_CHANGED,
    OppoPodsAction.ACTION_LHDC_CHANGED,
    OppoPodsAction.ACTION_DUAL_CONNECTION_CHANGED,
)

private fun sendBluetoothModuleBroadcast(context: Context, action: String) {
    listOf("com.android.bluetooth", "com.xiaomi.bluetooth").forEach { packageName ->
        Intent(action).apply {
            setPackage(packageName)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            context.sendBroadcast(this)
        }
    }
}

private fun isLauncherIconHidden(context: Context): Boolean {
    val component = ComponentName(context, "cythen.hyperdrop.LauncherActivity")
    val state = context.packageManager.getComponentEnabledSetting(component)
    return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
}

private fun setLauncherIconHidden(context: Context, hidden: Boolean) {
    val component = ComponentName(context, "cythen.hyperdrop.LauncherActivity")
    val state = if (hidden) {
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    } else {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }
    context.packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
}

private fun broadcastConfigChanged(context: Context, packageName: String) {
    Intent(OppoPodsAction.ACTION_CONFIG_CHANGED).apply {
        setPackage(packageName)
        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        context.sendBroadcast(this)
    }
}
