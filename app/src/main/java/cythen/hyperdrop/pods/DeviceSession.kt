package cythen.hyperdrop.pods

import android.annotation.SuppressLint
import android.app.StatusBarManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaRoute2Info
import android.media.MediaRouter2
import android.media.RouteDiscoveryPreference
import android.os.SystemClock
import cythen.hyperdrop.hook.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import cythen.hyperdrop.BuildConfig
import cythen.hyperdrop.config.ConfigManager
import cythen.hyperdrop.device.DeviceRegistry
import cythen.hyperdrop.device.PodTransport
import cythen.hyperdrop.device.protocol.GaiaCommandTable
import cythen.hyperdrop.device.protocol.GaiaReport
import cythen.hyperdrop.device.protocol.GaiaTransport
import cythen.hyperdrop.pods.link.BleGaiaPodLink
import cythen.hyperdrop.pods.link.LinkLogger
import cythen.hyperdrop.pods.link.LinkProbe
import cythen.hyperdrop.pods.link.PodLink
import cythen.hyperdrop.pods.link.RfcommPodLink
import cythen.hyperdrop.pods.link.toLinkHex
import cythen.hyperdrop.utils.MediaControl
import cythen.hyperdrop.utils.SystemApisUtils
import cythen.hyperdrop.utils.SystemApisUtils.setIconVisibility
import cythen.hyperdrop.utils.miuiStrongToast.MiuiStrongToastUtil
import cythen.hyperdrop.utils.miuiStrongToast.MiuiStrongToastUtil.cancelPodsNotificationByMiuiBt
import cythen.hyperdrop.utils.miuiStrongToast.data.BatteryParams
import cythen.hyperdrop.utils.miuiStrongToast.data.OppoPodsAction
import cythen.hyperdrop.utils.miuiStrongToast.data.PodParams
import java.io.IOException
import android.content.SharedPreferences
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "HyperDrop-DeviceSession"
private const val AUTO_RECONNECT_DELAY_MS = 120_000L
// Safety net for a missed ACTION_PODS_UI_CLOSED. The UI is what really decides active
// state; a long window keeps status echoes flowing while the earbuds page stays open
// (an LHDC codec switch can drop the RFCOMM link for far longer than the old 75s).
private const val APP_UI_ACTIVE_TIMEOUT_MS = 600_000L
// While an optimistic LHDC/dual write awaits confirmation, contradictory reports are
// ignored for this long (mirrors miuixmoondrop's "keep optimistic value on timeout").
private const val WRITE_CONFIRM_GRACE_MS = 5_000L
private const val DEFAULT_GAIN_LEVEL = 1 // medium (UI low/medium/high = 0/1/2)
/** Upper bound on follow-up GET_SUPPORTED_FEATURES_NEXT requests per connection. */
private const val MAX_FEATURE_PAGES = 8
/**
 * Transports this build can actually open, in preference order. RFCOMM first keeps the
 * verified PUDDING/MIRAGE path byte-identical to previous releases; BLE GAIA is used when the
 * probe finds no SPP service or the profile declares BLE only.
 */
private val IMPLEMENTED_TRANSPORTS = listOf(PodTransport.RFCOMM_GAIA, PodTransport.BLE_GAIA)


/** UI/system write actions that must not reach the earbuds while control is yielded. */
private val YIELD_BLOCKED_ACTIONS = setOf(
    OppoPodsAction.ACTION_ANC_SELECT,
    OppoPodsAction.ACTION_REFRESH_STATUS,
    OppoPodsAction.ACTION_GAIN_LEVEL_SET,
    OppoPodsAction.ACTION_LED_STATE_SET,
    OppoPodsAction.ACTION_CYCLE_ANC,
    OppoPodsAction.ACTION_RFCOMM_DEBUG_SEND,
    OppoPodsAction.ACTION_TRANSPARENCY_VOCAL_ENHANCEMENT_SET,
    OppoPodsAction.ACTION_SPATIAL_AUDIO_SET,
    OppoPodsAction.ACTION_EQ_PRESET_SET,
    OppoPodsAction.ACTION_PROMPT_TONE_SET,
    OppoPodsAction.ACTION_PROMPT_VOLUME_SET,
    OppoPodsAction.ACTION_HEAD_TRACKING_SET,
    OppoPodsAction.ACTION_LHDC_SET,
    OppoPodsAction.ACTION_DUAL_CONNECTION_SET,
)

@SuppressLint("MissingPermission", "StaticFieldLeak")
class DeviceSession {

    // Basic Objects
    /**
     * Active transport. Which implementation it is gets decided by `LinkProbe` at connect
     * time, not by the profile: the official app also resolves the transport per device
     * (`resolveBluetoothType()`) instead of consulting a compiled table.
     */
    private var link: PodLink? = null
    private var mContext: Context? = null
    lateinit var mDevice: BluetoothDevice
    private lateinit var mPrefs: SharedPreferences

    private var scanToken: MediaRouter2.ScanToken? = null
    var routes: List<MediaRoute2Info> = listOf()
    private lateinit var mediaRouter: MediaRouter2

    // Status
    private var mShowedConnectedToast = false
    private var isConnected = false
    private var lastTempBatt = 0
    lateinit var currentBatteryParams: BatteryParams
    private var currentAnc: Int = 1
    /** -1 = unknown / not in smart mode; otherwise a [NoiseControlMode].ordinal of the
     *  level smart mode is currently auto-applying (light/medium/deep). */
    private var currentSmartAncLevel: Int = -1
    private var currentTransparencyVocalEnhancement: Boolean = false
    private var currentSpatialAudioMode: Int = SpatialAudioMode.OFF
    /** -1 = unknown; otherwise one of [EqPreset.ALL]. */
    private var currentEqPreset: Int = -1
    private var currentGainLevel = DEFAULT_GAIN_LEVEL
    private var currentLedEnabled = true
    /** Last voice-config report from the device, used as a safe default when the UI
     *  omits an extra in the prompt-volume set broadcast. */
    private var currentPromptToneEnabled: Boolean = false
    private var currentPromptVolume: Int = 50
    private var currentVoiceIndex: Int = 0
    private var currentLhdcEnabled: Boolean = false
    private var currentDualConnectionEnabled: Boolean = false
    private var currentHeadTrackingEnabled: Boolean = false
    /** How many extra "supported features" pages we asked for since the last connect.
     *  Bounded so a chatty/looping device cannot pin the link in a request storm. */
    private var featurePagesRequested: Int = 0
    /** GAIA features the connected earbud has reported in this session. Reset on every
     *  connect so a different device can never inherit the previous one's feature list. */
    private var observedFeatures: cythen.hyperdrop.device.protocol.ObservedFeatures =
        cythen.hyperdrop.device.protocol.ObservedFeatures.UNKNOWN
    /** Timestamp until which a contradictory device report for LHDC is treated as a
     *  transient reconnect artifact and ignored (see [setLhdc]). */
    private var lhdcWriteGraceUntilMs = 0L
    private var dualWriteGraceUntilMs = 0L
    private var lastKnownLeftBattery: Int = 0
    private var lastKnownRightBattery: Int = 0
    private var lastKnownCaseBattery: Int = 0
    private var lastKnownLeftCharging: Boolean = false
    private var lastKnownRightCharging: Boolean = false
    private var lastKnownCaseCharging: Boolean = false
    private var cachedDeviceName: String = ""
    private var receiverRegistered = false
    private var routeScanStarted = false
    private var appUiActive = false
    private var appUiActiveUntilMs = 0L

    /** True while control has been yielded to the official MOONDROP app. */
    private var yielded = false
    private var yieldWatchJob: kotlinx.coroutines.Job? = null
    /** Monotonic timestamp of the last ACTION_OFFICIAL_APP_PRESENCE received in this
     *  (bluetooth) process. In-memory is authoritative: it avoids depending on
     *  RemotePreferences writes from another process propagating here. */
    private var officialAppLastSeenMs = 0L

    data class StatusSnapshot(
        val battery: BatteryParams?,
        val anc: Int,
        val transparencyVocalEnhancement: Boolean,
        val address: String?,
        val deviceName: String?,
        val connected: Boolean,
        val connecting: Boolean,
        val reconnectPending: Boolean,
    )

    // Link jobs
    private var connectionJob: kotlinx.coroutines.Job? = null
    private var reconnectJob: kotlinx.coroutines.Job? = null
    private val reconnectAttempts = AtomicInteger(0)
    private var reconnectPending = false

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            handleUIEvent(p1!!)
        }
    }

    private fun changeUIAncStatus(status: Int) {
        if (status < 1 || status > 8) return
        sendAppStatusBroadcast(OppoPodsAction.ACTION_PODS_ANC_CHANGED) {
            if (::mDevice.isInitialized) this.putExtra("address", mDevice.address)
            this.putExtra("status", status)
        }
        sendExternalPodsStatusBroadcast(OppoPodsAction.ACTION_PODS_ANC_CHANGED) {
            putExtra("status", status)
        }
    }

    private fun changeUIBatteryStatus(status: BatteryParams) {
        sendAppStatusBroadcast(OppoPodsAction.ACTION_PODS_BATTERY_CHANGED) {
            if (::mDevice.isInitialized) this.putExtra("address", mDevice.address)
            this.putExtra("status", status)
            putBatteryExtras(status)
        }
        sendExternalPodsStatusBroadcast(OppoPodsAction.ACTION_PODS_BATTERY_CHANGED) {
            putExtra("status", status)
            putBatteryExtras(status)
        }
    }

    private var currentWearStatus = WearStatus()

    private fun changeUIWearStatus(status: WearStatus) {
        sendAppStatusBroadcast(OppoPodsAction.ACTION_PODS_WEAR_STATUS_CHANGED) {
            if (::mDevice.isInitialized) this.putExtra("address", mDevice.address)
            putWearStatusExtras(status)
        }
        sendExternalPodsStatusBroadcast(OppoPodsAction.ACTION_PODS_WEAR_STATUS_CHANGED) {
            putWearStatusExtras(status)
        }
    }

    private fun changeUITransparencyVocalEnhancementStatus(enabled: Boolean) {
        sendAppStatusBroadcast(OppoPodsAction.ACTION_PODS_TRANSPARENCY_VOCAL_ENHANCEMENT_CHANGED) {
            this.putExtra("enabled", enabled)
        }
        sendExternalPodsStatusBroadcast(OppoPodsAction.ACTION_PODS_TRANSPARENCY_VOCAL_ENHANCEMENT_CHANGED) {
            putExtra("enabled", enabled)
        }
    }

    private fun changeUISpatialAudioStatus(mode: Int) {
        sendAppStatusBroadcast(OppoPodsAction.ACTION_PODS_SPATIAL_AUDIO_CHANGED) {
            this.putExtra("mode", mode)
        }
    }

    private fun changeUIEqPreset(presetId: Int) {
        sendAppStatusBroadcast(OppoPodsAction.ACTION_PODS_EQ_PRESET_CHANGED) {
            this.putExtra("preset", presetId)
        }
    }

    private fun changeUISmartAncLevel(ordinal: Int) {
        sendAppStatusBroadcast(OppoPodsAction.ACTION_PODS_SMART_ANC_LEVEL_CHANGED) {
            this.putExtra("ordinal", ordinal)
        }
    }

    private fun changeUIGainLevel(level: Int) {
        sendAppStatusBroadcast(OppoPodsAction.ACTION_PODS_GAIN_LEVEL_CHANGED) {
            putExtra("level", level)
        }
    }

    private fun changeUILedState(enabled: Boolean) {
        sendAppStatusBroadcast(OppoPodsAction.ACTION_PODS_LED_STATE_CHANGED) {
            putExtra("enabled", enabled)
        }
    }

    private fun changeUIPromptTone(enabled: Boolean, volume: Int, index: Int) {
        sendAppStatusBroadcast(OppoPodsAction.ACTION_PROMPT_TONE_CHANGED) {
            putExtra("enabled", enabled)
            putExtra("volume", volume)
            putExtra("index", index)
        }
    }

    private fun changeUIHeadTracking(enabled: Boolean) {
        sendAppStatusBroadcast(OppoPodsAction.ACTION_HEAD_TRACKING_CHANGED) {
            putExtra("enabled", enabled)
        }
    }

    private fun changeUILhdc(enabled: Boolean) {
        sendAppStatusBroadcast(OppoPodsAction.ACTION_LHDC_CHANGED) {
            putExtra("enabled", enabled)
        }
    }

    private fun changeUIDualConnection(enabled: Boolean) {
        sendAppStatusBroadcast(OppoPodsAction.ACTION_DUAL_CONNECTION_CHANGED) {
            putExtra("enabled", enabled)
        }
    }

    fun handleUIEvent(intent: Intent) {
        if (yielded && YIELD_BLOCKED_ACTIONS.contains(intent.action ?: "")) {
            Log.d(TAG, "dropped UI event while yielded: ${intent.action}")
            return
        }
        when (intent.action) {
            OppoPodsAction.ACTION_GET_PODS_MAC -> {
                if (::mDevice.isInitialized) {
                    Intent(OppoPodsAction.ACTION_PODS_MAC_RECEIVED).apply {
                        setPackage("com.android.systemui")
                        putExtra("mac", mDevice.address)
                        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                        mContext?.sendBroadcast(this)
                    }
                    Log.i(TAG, "ACTION_GET_PODS_MAC mac=${mDevice.address}")
                }
            }
            OppoPodsAction.ACTION_PODS_UI_INIT -> {
                markAppUiActive()
                Log.i(TAG, "UI Init")
                changeUIConnectionState(currentConnectionState())
                if (::currentBatteryParams.isInitialized)
                    changeUIBatteryStatus(currentBatteryParams)
                changeUIWearStatus(currentWearStatus)
                changeUIAncStatus(currentAnc)
                changeUISmartAncLevel(currentSmartAncLevel)
                changeUITransparencyVocalEnhancementStatus(currentTransparencyVocalEnhancement)
                changeUISpatialAudioStatus(currentSpatialAudioMode)
                changeUIEqPreset(currentEqPreset)
                changeUIGainLevel(currentGainLevel)
                changeUILedState(currentLedEnabled)
                changeUIPromptTone(currentPromptToneEnabled, currentPromptVolume, currentVoiceIndex)
                changeUIHeadTracking(currentHeadTrackingEnabled)
                changeUILhdc(currentLhdcEnabled)
                changeUIDualConnection(currentDualConnectionEnabled)
                if (::mDevice.isInitialized && isConnected) {
                    // Our own UI still needs to know which device is connected, otherwise the
                    // popup title falls back to the app name. While yielded we only refresh the
                    // in-app snapshot and never touch the channel the official app now owns.
                    sendAppStatusBroadcast(OppoPodsAction.ACTION_PODS_CONNECTED) {
                        this.putExtra("address", mDevice.address)
                        this.putExtra("device_name", mDevice.name ?: cachedDeviceName)
                    }
                    if (!yielded) {
                        sendExternalPodsStatusBroadcast(OppoPodsAction.ACTION_PODS_CONNECTED) {
                            putExtra("device_name", mDevice.name ?: cachedDeviceName)
                        }
                        // The standalone detail page always asks the earbuds themselves for fresh
                        // left/right/case battery instead of trusting Android's single generic battery.
                        queryStatus(immediateReconnect = false)
                    }
                }
            }
            OppoPodsAction.ACTION_PODS_UI_CLOSED -> {
                appUiActive = false
                appUiActiveUntilMs = 0L
                Log.i(TAG, "UI Closed")
            }
            OppoPodsAction.ACTION_ANC_SELECT -> {
                val status = intent.getIntExtra("status", 0)
                setANCMode(status)
            }
            OppoPodsAction.ACTION_REFRESH_STATUS -> {
                queryStatus(immediateReconnect = true)
            }
            OppoPodsAction.ACTION_TRANSPARENCY_VOCAL_ENHANCEMENT_SET -> {
                val enabled = intent.getBooleanExtra("enabled", false)
                setTransparencyVocalEnhancement(enabled)
            }
            OppoPodsAction.ACTION_SPATIAL_AUDIO_SET -> {
                val mode = intent.getIntExtra("mode", SpatialAudioMode.OFF)
                setSpatialAudioMode(mode)
            }
            OppoPodsAction.ACTION_EQ_PRESET_SET -> {
                val preset = intent.getIntExtra("preset", -1)
                if (preset in EqPreset.ALL) setEqPreset(preset)
            }
            OppoPodsAction.ACTION_GAIN_LEVEL_SET -> {
                setGainLevel(intent.getIntExtra("level", currentGainLevel))
            }
            OppoPodsAction.ACTION_LED_STATE_SET -> {
                setLedEnabled(intent.getBooleanExtra("enabled", currentLedEnabled))
            }
            OppoPodsAction.ACTION_PROMPT_TONE_SET -> {
                val enabled = intent.getBooleanExtra("enabled", false)
                val volume = intent.getIntExtra("volume", 50)
                val index = intent.getIntExtra("index", 0)
                setPromptTone(enabled, volume, index)
            }
            OppoPodsAction.ACTION_PROMPT_VOLUME_SET -> {
                val enabled = if (intent.hasExtra("enabled")) {
                    intent.getBooleanExtra("enabled", false)
                } else {
                    currentPromptToneEnabled
                }
                val index = intent.getIntExtra("index", currentVoiceIndex)
                val volume = intent.getIntExtra("volume", currentPromptVolume)
                setPromptVolume(enabled, volume, index)
            }
            OppoPodsAction.ACTION_HEAD_TRACKING_SET -> {
                setHeadTracking(intent.getBooleanExtra("enabled", false))
            }
            OppoPodsAction.ACTION_LHDC_SET -> {
                setLhdc(intent.getBooleanExtra("enabled", false))
            }
            OppoPodsAction.ACTION_DUAL_CONNECTION_SET -> {
                setDualConnection(intent.getBooleanExtra("enabled", false))
            }
            OppoPodsAction.ACTION_CYCLE_ANC -> {
                cycleAnc()
            }
            OppoPodsAction.ACTION_CONFIG_CHANGED -> {
                ConfigManager.refreshFromPrefs(mPrefs)
                Log.d(TAG, "Config synced")
                evaluateYield()
                if (!currentCapabilities().adaptiveSupported && currentAnc == 4) {
                    setANCMode(2)
                }
            }
            OppoPodsAction.ACTION_OFFICIAL_APP_PRESENCE -> {
                val fromPackage = intent.getStringExtra(OppoPodsAction.EXTRA_OFFICIAL_APP_PACKAGE)
                val allowed = ConfigManager.officialAppPackages()
                if (fromPackage == null || !allowed.contains(fromPackage)) return
                ConfigManager.refreshFromPrefs(mPrefs)
                noteOfficialAppPresence()
            }
            OppoPodsAction.ACTION_RFCOMM_LOG_CONNECT -> {
                if (!RfcommLog.isEnabled()) {
                    RfcommLog.setEnabled(true, mContext)
                }
            }
            OppoPodsAction.ACTION_RFCOMM_LOG_DISCONNECT -> {
                RfcommLog.setEnabled(false)
            }
            OppoPodsAction.ACTION_RFCOMM_LOG_CLEAR -> {
                RfcommLog.clear()
            }
            OppoPodsAction.ACTION_RFCOMM_DEBUG_SEND -> {
                val hex = intent.getStringExtra("hex").orEmpty()
                sendDebugHex(hex)
            }
        }
    }

    fun currentStatusSnapshot(): StatusSnapshot {
        return StatusSnapshot(
            battery = if (::currentBatteryParams.isInitialized) currentBatteryParams else null,
            anc = currentAnc,
            transparencyVocalEnhancement = currentTransparencyVocalEnhancement,
            address = if (::mDevice.isInitialized) mDevice.address else null,
            deviceName = if (::mDevice.isInitialized) mDevice.name ?: cachedDeviceName else cachedDeviceName.takeIf { it.isNotEmpty() },
            connected = isConnected && link?.isOpen == true,
            connecting = connectionJob?.isActive == true,
            reconnectPending = reconnectPending,
        )
    }

    private fun currentConnectionState(): String = when {
        isConnected && link?.isOpen == true && ::currentBatteryParams.isInitialized -> "connected"
        connectionJob?.isActive == true || reconnectPending -> "connecting"
        isConnected && link?.isOpen == true -> "connecting"
        else -> "disconnected"
    }

    private fun changeUIConnectionState(state: String) {
        sendAppStatusBroadcast(OppoPodsAction.ACTION_PODS_CONNECTION_STATE_CHANGED) {
            if (::mDevice.isInitialized) {
                putExtra("address", mDevice.address)
                putExtra("device_name", mDevice.name ?: cachedDeviceName)
            }
            putExtra("state", state)
        }
    }

    private fun sendAppStatusBroadcast(action: String, fill: Intent.() -> Unit = {}) {
        val ctx = mContext ?: return
        if (!isAppUiActive()) return
        Intent(action).apply {
            fill()
            this.`package` = BuildConfig.APPLICATION_ID
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            ctx.sendBroadcast(this)
        }
    }

    private fun isAppUiActive(): Boolean {
        if (!appUiActive) return false
        if (SystemClock.elapsedRealtime() <= appUiActiveUntilMs) return true
        appUiActive = false
        appUiActiveUntilMs = 0L
        Log.d(TAG, "app UI active timeout, stop app status broadcasts")
        return false
    }

    private fun markAppUiActive() {
        appUiActive = true
        appUiActiveUntilMs = SystemClock.elapsedRealtime() + APP_UI_ACTIVE_TIMEOUT_MS
    }


    fun miuiRefreshPayload(battery: BatteryParams?, anc: Int, transparencyVocalEnhancement: Boolean = false): String {
        val values = MutableList(16) { "" }
        values[0] = miuiBatteryValue(battery?.left)
        values[1] = miuiBatteryValue(battery?.right)
        values[2] = miuiBatteryValue(battery?.case)
        values[7] = miuiAncLevel(anc, transparencyVocalEnhancement)
        values[8] = "true"
        values[11] = "00"
        values[13] = "00"
        values[14] = "00"
        return values.joinToString(",")
    }

    private fun miuiBatteryValue(params: PodParams?): String {
        if (params?.isConnected != true) return "255"
        val value = params.battery.coerceIn(0, 100)
        // HyperOS' status-bar contract packs charging into bit 7 of this string field. That is
        // a *UI* convention, not something the earbud reports: GAIA battery records are
        // [batteryId, level] with no charging flag (BatteryLevel.smali:17-21), so isCharging
        // stays false on the GAIA path. Do not confuse this with the legacy OPPO AA protocol,
        // where the earbud itself encoded charging as 0x80 in the battery value.
        return (if (params.isCharging) value or 128 else value).toString()
    }

    private fun miuiAncLevel(anc: Int, transparencyVocalEnhancement: Boolean): String {
        // Reuse HyperOS' existing ANC level UI as PUDDING's three modes.
        return when (anc) {
            2 -> "0100" // Basic ANC
            4 -> "0101" // Adaptive ANC
            5 -> "0102" // Wind-noise reduction
            3 -> "0200" // Transparency
            else -> "0000" // Off
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun handleBatteryChanged(result: BatteryParser.BatteryResult) {
        // GAIA battery notifications/responses may contain a subset of components. Preserve
        // the last value for components not present in this packet instead of turning them
        // into 0/disconnected. This keeps the three-battery UI stable.
        result.left?.let {
            lastKnownLeftBattery = it.level
            lastKnownLeftCharging = it.isCharging
        }
        result.right?.let {
            lastKnownRightBattery = it.level
            lastKnownRightCharging = it.isCharging
        }
        result.case?.let {
            lastKnownCaseBattery = it.level
            lastKnownCaseCharging = it.isCharging
        }

        val previous = if (::currentBatteryParams.isInitialized) currentBatteryParams else BatteryParams()
        val left = result.left?.let { PodParams(it.level, it.isCharging, true, 0) }
            ?: previous.left?.takeIf { it.isConnected }
            ?: PodParams(lastKnownLeftBattery, lastKnownLeftCharging, lastKnownLeftBattery > 0, 0)
        val right = result.right?.let { PodParams(it.level, it.isCharging, true, 0) }
            ?: previous.right?.takeIf { it.isConnected }
            ?: PodParams(lastKnownRightBattery, lastKnownRightCharging, lastKnownRightBattery > 0, 0)
        val case = result.case?.let { PodParams(it.level, it.isCharging, true, 0) }
            ?: previous.case?.takeIf { it.isConnected }
            ?: PodParams(lastKnownCaseBattery, lastKnownCaseCharging, lastKnownCaseBattery > 0, 0)

        Log.i(
            TAG,
            "PUDDING battery snapshot left=${left.battery}/${left.isConnected} " +
                    "right=${right.battery}/${right.isConnected} case=${case.battery}/${case.isConnected}"
        )

        val shouldShowToast = !mShowedConnectedToast
        if (shouldShowToast) {
            // Wait until at least one connected ear has valid battery data
            val hasValidData = (left.isConnected && left.battery > 0) ||
                    (right.isConnected && right.battery > 0)
            if (!hasValidData) return
        }

        val batteryParams = BatteryParams(left, right, case)
        currentBatteryParams = batteryParams

        if (shouldShowToast) {
            changeUIConnectionState("connected")
            sendAppStatusBroadcast(OppoPodsAction.ACTION_PODS_CONNECTED) {
                this.putExtra("address", mDevice.address)
                this.putExtra("device_name", mDevice.name ?: cachedDeviceName)
            }
            sendExternalPodsStatusBroadcast(OppoPodsAction.ACTION_PODS_CONNECTED) {
                putExtra("device_name", mDevice.name ?: cachedDeviceName)
            }
            // HyperOriG emits a Focus Island once for every real connection instead of
            // depending on a hidden/legacy island preference. Keep that behaviour for PUDDING.
            MiuiStrongToastUtil.showPodsBatteryToastByMiuiBt(mContext!!, batteryParams, mDevice)
            mShowedConnectedToast = true
        }
        MiuiStrongToastUtil.showPodsNotificationByMiuiBt(mContext!!, batteryParams, mDevice)
        changeUIBatteryStatus(batteryParams)

        lastTempBatt = if (left.isConnected && right.isConnected)
            minOf(left.battery, right.battery)
        else if (left.isConnected)
            left.battery
        else if (right.isConnected)
            right.battery
        else SystemApisUtils.BATTERY_LEVEL_UNKNOWN

        setRegularBatteryLevel(lastTempBatt)
    }

    private val routeCallback = object : MediaRouter2.RouteCallback() {
        override fun onRoutesUpdated(routes: List<MediaRoute2Info>) {
            Log.v(TAG, "routes updated: $routes")
            this@DeviceSession.routes = routes
        }
    }

    private fun startRoutesScan() {
        if (routeScanStarted) return
        val executor = Executor { p0 ->
            CoroutineScope(Dispatchers.IO).launch { p0?.run() }
        }
        val preferredFeature = listOf(MediaRoute2Info.FEATURE_LIVE_AUDIO, MediaRoute2Info.FEATURE_LIVE_VIDEO)
        mediaRouter.registerRouteCallback(executor, routeCallback, RouteDiscoveryPreference.Builder(preferredFeature, true).build())
        scanToken = mediaRouter.requestScan(MediaRouter2.ScanRequest.Builder().build())
        routeScanStarted = true
    }

    private fun stopRoutesScan() {
        scanToken?.let { mediaRouter.cancelScanRequest(it) }
        if (routeScanStarted) {
            mediaRouter.unregisterRouteCallback(routeCallback)
            routeScanStarted = false
        }
    }

    /**
     * Build the link for this device.
     *
     * The transport is decided at connection time from what the device advertises, mirroring
     * the official app's `resolveBluetoothType()`: BLE when the GAIA GATT service is present,
     * otherwise RFCOMM/SPP. 9ECA is still unimplemented, so a device that only advertises the
     * 9ECA service falls back to RFCOMM exactly as this build did before.
     *
     * A verified model is pinned to the transport it was confirmed on. Its probe result is
     * still logged, but it never steers the choice: PUDDING/MIRAGE work over SPP on real
     * hardware, and a firmware that also advertises the GAIA GATT service must not move them
     * onto a transport nobody has tested.
     */
    private fun openLink(): PodLink {
        val profile = DeviceRegistry.profileFor(deviceName())
        val probe = LinkProbe.probe(mContext ?: throw IOException("no context"), mDevice)
        Log.d(TAG, "link probe: ${LinkProbe.describe(probe)}")
        RfcommLog.i(mContext, TAG, "probe: ${LinkProbe.describe(probe)}")

        val probed = (probe as? LinkProbe.ProbeResult.Candidates)?.transports.orEmpty()
        val order = if (profile.verified) {
            Log.d(TAG, "verified profile ${profile.id}: transport pinned to ${profile.transports.first().name}")
            profile.transports
        } else {
            LinkProbe.resolveOrder(profile.transports, probed)
        }
        Log.d(TAG, "link candidate order: ${order.joinToString { it.name }}")

        val chosen = order.firstOrNull { it in IMPLEMENTED_TRANSPORTS }
            ?: IMPLEMENTED_TRANSPORTS.first()
        if (chosen != order.first()) {
            Log.w(TAG, "preferred transport ${order.first().name} unimplemented; using ${chosen.name}")
            RfcommLog.w(mContext, TAG, "${order.first().name} unimplemented, using ${chosen.name}")
        }

        val linkLogger = LinkLogger { direction, bytes, _ ->
            // The reader thread and the sender both land here; RfcommLog already
            // serialises on its own lock and caps its buffer.
            RfcommLog.d(
                mContext,
                if (direction == LinkLogger.Direction.RX) "LINK/RX" else "LINK/TX",
                bytes.toLinkHex(),
            )
        }
        val newLink: PodLink = when (chosen) {
            PodTransport.BLE_GAIA -> BleGaiaPodLink(
                context = mContext ?: throw IOException("no context"),
                device = mDevice,
                logger = linkLogger,
                isCancelled = { yielded },
            )

            // RFCOMM_GAIA, plus BLE_SRC_9ECA as a stand-in: 9ECA is not in
            // IMPLEMENTED_TRANSPORTS, so a 9ECA-only device lands here and gets the same
            // RFCOMM attempt this build made before link probing existed.
            PodTransport.RFCOMM_GAIA, PodTransport.BLE_SRC_9ECA -> RfcommPodLink(
                device = mDevice,
                sppUuid = profile.commandTable.sppUuid,
                logger = linkLogger,
                isCancelled = { yielded },
            )
        }
        newLink.listener = object : PodLink.Listener {
            override fun onPayload(payload: ByteArray) {
                handleGaiaPayload(payload)
            }

            override fun onClosed(reason: String) {
                if (isConnected) {
                    Log.d(TAG, "link closed: $reason")
                    scheduleReconnect(reason)
                }
            }
        }
        return newLink
    }

    fun connectPod(context: Context, device: BluetoothDevice, prefs: SharedPreferences, appRequested: Boolean = false) {
        connectionJob?.cancel()
        reconnectJob?.cancel()
        closeLinkOnly()
        mContext = context
        mDevice = device
        mPrefs = prefs
        cachedDeviceName = device.name ?: ""
        // Never carry a partial battery snapshot from a previous RFCOMM session into a
        // fresh PUDDING connection. The official app polls GAIA battery repeatedly and
        // receives LEFT(1), RIGHT(2) and CASE(3) independently.
        currentBatteryParams = BatteryParams()
        lastKnownLeftBattery = 0
        lastKnownRightBattery = 0
        lastKnownCaseBattery = 0
        lastKnownLeftCharging = false
        lastKnownRightCharging = false
        lastKnownCaseCharging = false
        if (appRequested) {
            markAppUiActive()
        }
        ConfigManager.refreshFromPrefs(mPrefs)
        Log.d(TAG, "Adaptive support initial: ${currentCapabilities().adaptiveSupported}")
        Log.d(TAG, "RFCOMM UUID: ${commandTable().sppUuid}")

        if (!receiverRegistered) {
            context.registerReceiver(broadcastReceiver, IntentFilter().apply {
                this.addAction(OppoPodsAction.ACTION_ANC_SELECT)
                this.addAction(OppoPodsAction.ACTION_PODS_UI_INIT)
                this.addAction(OppoPodsAction.ACTION_PODS_UI_CLOSED)
                this.addAction(OppoPodsAction.ACTION_REFRESH_STATUS)
                this.addAction(OppoPodsAction.ACTION_GET_PODS_MAC)
                this.addAction(OppoPodsAction.ACTION_TRANSPARENCY_VOCAL_ENHANCEMENT_SET)
                this.addAction(OppoPodsAction.ACTION_SPATIAL_AUDIO_SET)
                this.addAction(OppoPodsAction.ACTION_EQ_PRESET_SET)
                this.addAction(OppoPodsAction.ACTION_GAIN_LEVEL_SET)
                this.addAction(OppoPodsAction.ACTION_LED_STATE_SET)
                this.addAction(OppoPodsAction.ACTION_PROMPT_TONE_SET)
                this.addAction(OppoPodsAction.ACTION_PROMPT_VOLUME_SET)
                this.addAction(OppoPodsAction.ACTION_HEAD_TRACKING_SET)
                this.addAction(OppoPodsAction.ACTION_LHDC_SET)
                this.addAction(OppoPodsAction.ACTION_DUAL_CONNECTION_SET)
                this.addAction(OppoPodsAction.ACTION_CYCLE_ANC)
                this.addAction(OppoPodsAction.ACTION_CONFIG_CHANGED)
                this.addAction(OppoPodsAction.ACTION_OFFICIAL_APP_PRESENCE)
                this.addAction(OppoPodsAction.ACTION_RFCOMM_LOG_CONNECT)
                this.addAction(OppoPodsAction.ACTION_RFCOMM_LOG_DISCONNECT)
                this.addAction(OppoPodsAction.ACTION_RFCOMM_LOG_CLEAR)
                this.addAction(OppoPodsAction.ACTION_RFCOMM_DEBUG_SEND)
            }, Context.RECEIVER_EXPORTED)
            receiverRegistered = true
        }

        MediaControl.mContext = mContext
        mediaRouter = MediaRouter2.getInstance(mContext!!)
        startRoutesScan()

        isConnected = true
        changeUIConnectionState("connecting")

        // Decide whether the official app currently owns the channel before opening RFCOMM.
        evaluateYield()
        connectLink(initialDelayMs = 500L)

    }

    private fun sendExternalPodsStatusBroadcast(action: String, fill: Intent.() -> Unit = {}) {
        val ctx = mContext ?: return
        listOf("com.milink.service", "com.xiaomi.bluetooth", "com.android.settings").forEach { targetPackage ->
            Intent(action).apply {
                if (::mDevice.isInitialized) {
                    putExtra("address", mDevice.address)
                    putExtra("device_name", mDevice.name ?: cachedDeviceName)
                }
                fill()
                setPackage(targetPackage)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                ctx.sendBroadcast(this)
            }
        }
    }

    /**
     * Re-evaluate the official-app lease and enter/leave the yielded state.
     * Idempotent: safe to call from heartbeats, config changes, connect and the watch loop.
     */
    fun evaluateYield() {
        val enabled = ConfigManager.current().yieldToOfficialApp
        val prefsLastSeen = if (::mPrefs.isInitialized)
            mPrefs.getLong(YieldPolicy.PREF_KEY_OFFICIAL_APP_LAST_SEEN_MS, 0L) else 0L
        // PRESENCE broadcasts are delivered in-process and are authoritative (no
        // dependency on RemotePreferences writes from another process propagating).
        // Fall back to the prefs lease only when no broadcast has been received yet.
        val lastSeen = if (officialAppLastSeenMs > 0L) officialAppLastSeenMs else prefsLastSeen
        val officialAlive = YieldPolicy.officialAlive(SystemClock.elapsedRealtime(), lastSeen)
        val headsetConnected = isConnected && ::mDevice.isInitialized
        val shouldYield = YieldPolicy.shouldYield(enabled, officialAlive, headsetConnected)
        if (shouldYield && !yielded) {
            yieldControl()
        } else if (!shouldYield && yielded) {
            resumeControl()
        }
    }

    /** Record that the official app is alive in this process, then re-evaluate. */
    fun noteOfficialAppPresence() {
        officialAppLastSeenMs = SystemClock.elapsedRealtime()
        evaluateYield()
    }

    private fun yieldControl() {
        yielded = true
        connectionJob?.cancel()
        reconnectJob?.cancel()
        reconnectPending = false
        closeLinkOnly()
        Log.i(TAG, "yield control: official app is alive")
        // Drop the MIUI bluetooth card: it would otherwise keep showing the last battery/ANC
        // snapshot we pushed, which is stale once the official app owns the channel.
        runCatching {
            val ctx = mContext
            if (ctx != null && ::mDevice.isInitialized) {
                cancelPodsNotificationByMiuiBt(ctx, mDevice)
            }
        }.onFailure { Log.w(TAG, "cancel pods notification on yield skipped", it) }
        sendControlStateBroadcast(OppoPodsAction.ACTION_CONTROL_YIELDED)
        yieldWatchJob?.cancel()
        yieldWatchJob = CoroutineScope(Dispatchers.IO).launch {
            while (yielded) {
                delay(YieldPolicy.YIELD_CHECK_INTERVAL_MS)
                evaluateYield()
            }
        }
    }

    private fun resumeControl() {
        yielded = false
        yieldWatchJob?.cancel()
        yieldWatchJob = null
        Log.i(TAG, "resume control: official app lease expired")
        sendControlStateBroadcast(OppoPodsAction.ACTION_CONTROL_RESUMED)
        if (isConnected && ::mDevice.isInitialized) {
            connectLink(initialDelayMs = 800L)
        }
    }

    /** Notify every L2 status consumer, including the bluetooth process itself. */
    private fun sendControlStateBroadcast(action: String) {
        val ctx = mContext ?: return
        listOf(
            "com.milink.service",
            "com.xiaomi.bluetooth",
            "com.android.settings",
            "com.android.bluetooth",
        ).forEach { targetPackage ->
            Intent(action).apply {
                if (::mDevice.isInitialized) {
                    putExtra("address", mDevice.address)
                    putExtra("device_name", mDevice.name ?: cachedDeviceName)
                }
                setPackage(targetPackage)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                ctx.sendBroadcast(this)
            }
        }
    }

    private fun Intent.putBatteryExtras(status: BatteryParams) {
        putExtra("left_battery", status.left?.battery ?: 0)
        putExtra("left_charging", status.left?.isCharging == true)
        putExtra("left_connected", status.left?.isConnected == true)
        putExtra("right_battery", status.right?.battery ?: 0)
        putExtra("right_charging", status.right?.isCharging == true)
        putExtra("right_connected", status.right?.isConnected == true)
        putExtra("case_battery", status.case?.battery ?: 0)
        putExtra("case_charging", status.case?.isCharging == true)
        putExtra("case_connected", status.case?.isConnected == true)
    }

    private fun Intent.putWearStatusExtras(status: WearStatus) {
        putExtra("left_wear_status", status.left?.value ?: -1)
        putExtra("right_wear_status", status.right?.value ?: -1)
        putExtra("case_wear_status", status.case?.value ?: -1)
    }

    private fun mergeWearStatus(current: WearStatus, update: WearStatus): WearStatus {
        return WearStatus(
            left = update.left ?: current.left,
            right = update.right ?: current.right,
            case = update.case ?: current.case
        )
    }

    private fun showIslandForWearStatusChange(previous: WearStatus, current: WearStatus) {
        if (!::currentBatteryParams.isInitialized) return
        val changedTimings = setOfNotNull(
            islandShowTimingForChange(previous.left, current.left),
            islandShowTimingForChange(previous.right, current.right),
            islandShowTimingForChange(previous.case, current.case),
        )
        if (changedTimings.any { shouldShowIsland(it) }) {
            MiuiStrongToastUtil.showPodsBatteryToastByMiuiBt(mContext ?: return, currentBatteryParams)
        }
    }

    private fun shouldShowIsland(timing: Int): Boolean {
        return ConfigManager.islandMode() == ConfigManager.ISLAND_MODE_MODULE &&
                timing in ConfigManager.islandShowTimings()
    }

    private fun islandShowTimingForChange(previous: WearState?, current: WearState?): Int? {
        if (previous == current) return null
        return when (current) {
            WearState.WEARING -> ConfigManager.ISLAND_SHOW_TIMING_WEARING
            WearState.REMOVED -> ConfigManager.ISLAND_SHOW_TIMING_REMOVED
            WearState.IN_CASE -> ConfigManager.ISLAND_SHOW_TIMING_IN_CASE
            else -> null
        }
    }

    private fun connectLink(initialDelayMs: Long = 0L) {
        connectionJob?.cancel()
        connectionJob = CoroutineScope(Dispatchers.IO).launch {
            if (initialDelayMs > 0) delay(initialDelayMs)
            if (!isConnected || !::mDevice.isInitialized) return@launch
            if (yielded) {
                Log.d(TAG, "link connect skipped while yielded")
                return@launch
            }
            closeLinkOnly()
            try {
                val newLink = openLink()
                newLink.open()
                // A yield may have landed while the transport was connecting (cancellation
                // does not interrupt the blocking connect). Never keep a link the official
                // app is trying to own, and never start a reader after closeLinkOnly() ran
                // inside yieldControl().
                if (yielded) {
                    newLink.close()
                    Log.d(TAG, "link released: yielded during connect")
                    return@launch
                }
                link = newLink
                reconnectAttempts.set(0)
                reconnectPending = false
                featurePagesRequested = 0
                observedFeatures = cythen.hyperdrop.device.protocol.ObservedFeatures.UNKNOWN
                Log.d(TAG, "link connected! uuid=${commandTable().sppUuid}")
                RfcommLog.i(mContext, TAG, "connected uuid=${commandTable().sppUuid}")
                changeUIConnectionState("connecting")

                delay(HandshakeSequence.LEAD_IN_MS)
                // Match the official MOONDROP app: negotiate GAIA with a v1 API-version
                // request, then use v4 for the regular commands.
                sendNegotiation(GaiaTransport.v1(commandTable().queryGaiaVersion), newLink)
                delay(HandshakeSequence.stepDelayMs(HandshakeSequence.Step.NEGOTIATE_VERSION, newLink.transport))
                sendPayload(commandTable().queryNoiseMode)
                delay(HandshakeSequence.stepDelayMs(HandshakeSequence.Step.NOISE_MODE, newLink.transport))
                sendPayload(commandTable().queryGainLevel)
                delay(HandshakeSequence.stepDelayMs(HandshakeSequence.Step.GAIN, newLink.transport))
                sendPayload(commandTable().queryLedState)
                delay(HandshakeSequence.stepDelayMs(HandshakeSequence.Step.LED, newLink.transport))
                sendPayload(commandTable().querySupportedBatteries)
                delay(HandshakeSequence.stepDelayMs(HandshakeSequence.Step.SUPPORTED_BATTERIES, newLink.transport))
                sendPayload(commandTable().queryBatteryLevels)
                // Ask what the earbud actually implements. The reply gates which controls the
                // UI offers (`DeviceRegistry.applyObservedFeatures`).
                commandTable().querySupportedFeatures?.let {
                    delay(HandshakeSequence.stepDelayMs(HandshakeSequence.Step.BATTERY_LEVELS, newLink.transport))
                    sendPayload(it, "supported features query")
                }

                changeUIConnectionState("connected")
                sendAppStatusBroadcast(OppoPodsAction.ACTION_PODS_CONNECTED) {
                    putExtra("address", mDevice.address)
                    putExtra("device_name", mDevice.name ?: cachedDeviceName)
                }
                sendExternalPodsStatusBroadcast(OppoPodsAction.ACTION_PODS_CONNECTED) {
                    putExtra("device_name", mDevice.name ?: cachedDeviceName)
                }
                mShowedConnectedToast = true
            } catch (e: IOException) {
                Log.e(TAG, "link connect failed", e)
                changeUIConnectionState("error")
                scheduleReconnect("connect failed")
            }
        }
    }

    // No periodic RFCOMM polling. ANC changes are consumed from the earbud's GAIA
    // notifications/echoes. Fresh status is queried only on connect, when a UI/system page
    // explicitly opens/refreshes, or when a real ANC change triggers a battery refresh.

    private fun scheduleReconnect(reason: String, immediate: Boolean = false) {
        if (yielded) return
        if (!isConnected || !::mDevice.isInitialized || mContext == null) return
        RfcommLog.w(mContext, TAG, "schedule reconnect reason=$reason immediate=$immediate")
        closeLinkOnly()
        reconnectPending = true
        if (immediate) {
            if (connectionJob?.isActive == true) {
                Log.d(TAG, "immediate link reconnect skipped: connecting reason=$reason")
                return
            }
            Log.d(TAG, "immediate link reconnect reason=$reason")
            reconnectJob?.cancel()
            reconnectJob = null
            reconnectPending = false
            connectLink()
            return
        }
        if (reconnectJob?.isActive == true) {
            Log.d(TAG, "link reconnect already scheduled reason=$reason")
            return
        }
        val attempt = reconnectAttempts.incrementAndGet()
        Log.d(TAG, "schedule link reconnect reason=$reason attempt=$attempt delay=${AUTO_RECONNECT_DELAY_MS}ms")
        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            delay(AUTO_RECONNECT_DELAY_MS)
            reconnectJob = null
            reconnectPending = false
            connectLink()
        }
    }

    private fun reconnectNowForRequest(reason: String) {
        if (link?.isOpen == true && !reconnectPending) return
        scheduleReconnect(reason, immediate = true)
    }

    private fun closeLinkOnly() {
        val current = link
        link = null
        runCatching { current?.close() }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun handleGaiaPayload(payload: ByteArray) {
        Log.v(TAG, "GAIA payload: ${payload.toHexString(HexFormat.UpperCase)}")
        when (val report = commandTable().parse(payload)) {
            is GaiaReport.AncMode -> {
                val previousAnc = currentAnc
                currentAnc = report.uiStatus
                changeUIAncStatus(currentAnc)
                Log.i(TAG, "noise mode: ui=${report.uiStatus} wire=${report.wire} previous=$previousAnc")
                // A mode change made outside HyperDrop (e.g. an earbud long-press) refreshes
                // battery immediately. App-originated SETs update currentAnc first, so their
                // echo does not trigger an extra battery query.
                if (previousAnc != currentAnc) queryBattery()
            }
            is GaiaReport.Battery -> {
                val left = report.left?.let { BatteryParser.BatteryInfo(it, false) }
                val right = report.right?.let { BatteryParser.BatteryInfo(it, false) }
                val case = report.case?.let { BatteryParser.BatteryInfo(it, false) }
                if (left != null || right != null || case != null) {
                    handleBatteryChanged(BatteryParser.BatteryResult(left, right, case))
                    Log.i(TAG, "battery: left=${report.left} right=${report.right} case=${report.case}")
                }
            }
            is GaiaReport.GainLevel -> {
                currentGainLevel = report.uiValue
                changeUIGainLevel(currentGainLevel)
                Log.i(TAG, "gain level: ui=${report.uiValue} wire=${report.wire}")
            }
            is GaiaReport.LedEnabled -> {
                currentLedEnabled = report.enabled
                changeUILedState(currentLedEnabled)
                Log.i(TAG, "LED enabled: $currentLedEnabled")
            }
            is GaiaReport.SpatialAudio -> {
                currentSpatialAudioMode = if (report.enabled) 1 else 0
                changeUISpatialAudioStatus(currentSpatialAudioMode)
                Log.i(TAG, "spatial audio: ${report.enabled}")
            }
            is GaiaReport.HeadTracking -> {
                currentHeadTrackingEnabled = report.enabled
                changeUIHeadTracking(report.enabled)
                Log.i(TAG, "head tracking: ${report.enabled}")
            }
            is GaiaReport.VoiceConfig -> {
                currentPromptToneEnabled = report.enabled
                currentPromptVolume = report.volume
                currentVoiceIndex = report.index
                changeUIPromptTone(report.enabled, report.volume, report.index)
                Log.i(
                    TAG,
                    "voice config: enabled=${report.enabled} volume=${report.volume} index=${report.index}",
                )
            }
            is GaiaReport.Lhdc -> {
                // An LHDC switch drops the RFCOMM link; during the write-confirm grace a
                // transient OFF report is a reconnect artifact, not a real state change.
                if (report.enabled != currentLhdcEnabled &&
                    !report.enabled &&
                    SystemClock.elapsedRealtime() < lhdcWriteGraceUntilMs
                ) {
                    Log.i(TAG, "LHDC: ignoring transient OFF during reconnect grace")
                } else {
                    currentLhdcEnabled = report.enabled
                    lhdcWriteGraceUntilMs = 0L
                    changeUILhdc(report.enabled)
                    Log.i(TAG, "LHDC: ${report.enabled}")
                }
            }
            is GaiaReport.DualConnection -> {
                if (report.enabled != currentDualConnectionEnabled &&
                    !report.enabled &&
                    SystemClock.elapsedRealtime() < dualWriteGraceUntilMs
                ) {
                    Log.i(TAG, "dual connection: ignoring transient OFF during reconnect grace")
                } else {
                    currentDualConnectionEnabled = report.enabled
                    dualWriteGraceUntilMs = 0L
                    changeUIDualConnection(report.enabled)
                    Log.i(TAG, "dual connection: ${report.enabled}")
                }
            }
            is GaiaReport.SupportedFeatures -> {
                observedFeatures = observedFeatures.merge(report.features, report.moreData)
                Log.i(
                    TAG,
                    "supported features: ${observedFeatures.features} " +
                        "pages=${observedFeatures.pages} complete=${observedFeatures.complete}",
                )
                publishObservedFeatures()
                // GetSupportedFeaturesData carries `hasMoreData`; the official client keeps
                // asking with V1_GET_SUPPORTED_FEATURES_NEXT (command 0x02) until it clears.
                if (report.moreData && featurePagesRequested < MAX_FEATURE_PAGES) {
                    featurePagesRequested++
                    commandTable().querySupportedFeaturesNext?.let { next ->
                        CoroutineScope(Dispatchers.IO).launch {
                            sendPayload(next, "supported features next page")
                        }
                    }
                }
            }
            GaiaReport.GaiaNegotiation -> {
                Log.d(TAG, "GAIA negotiation response: ${payload.toHexString(HexFormat.UpperCase)}")
            }
            GaiaReport.Unknown -> {
                Log.d(TAG, "Unhandled GAIA payload: ${payload.toHexString(HexFormat.UpperCase)}")
            }
        }
    }

    fun disconnectedPod(context: Context, device: BluetoothDevice) {
        isConnected = false
        yielded = false
        yieldWatchJob?.cancel()
        yieldWatchJob = null
        connectionJob?.cancel()
        reconnectJob?.cancel()
        reconnectAttempts.set(0)
        reconnectPending = false

        closeLinkOnly()

        mContext?.let {
            stopRoutesScan()
            cancelPodsNotificationByMiuiBt(context, device)
            sendAppStatusBroadcast(OppoPodsAction.ACTION_PODS_DISCONNECTED) {
                putExtra("address", device.address)
            }
            if (receiverRegistered) {
                it.unregisterReceiver(broadcastReceiver)
                receiverRegistered = false
            }
        }

        mShowedConnectedToast = false
        currentWearStatus = WearStatus()
        currentAnc = 1
        currentSmartAncLevel = -1
        currentTransparencyVocalEnhancement = false
        currentSpatialAudioMode = SpatialAudioMode.OFF
        currentEqPreset = -1
        currentGainLevel = DEFAULT_GAIN_LEVEL
        currentLedEnabled = true
        lastKnownLeftBattery = 0
        lastKnownRightBattery = 0
        lastKnownCaseBattery = 0
        lastKnownLeftCharging = false
        lastKnownRightCharging = false
        lastKnownCaseCharging = false
        currentBatteryParams = BatteryParams()
        changeUIConnectionState("disconnected")
        cachedDeviceName = ""
        mContext = null
        MediaControl.mContext = null
    }

    /**
     * Send one unframed GAIA payload. Framing belongs to the link, because RFCOMM wraps the
     * payload in a 0xFF-framed packet while GAIA-over-BLE writes it bare.
     */
    private fun sendPayload(payload: ByteArray?, requestReason: String? = null) {
        if (payload == null) return
        if (yielded) {
            Log.d(TAG, "dropped GAIA write while yielded")
            return
        }
        if (requestReason != null) reconnectNowForRequest(requestReason)
        val current = link
        if (current == null || !current.isOpen) {
            RfcommLog.w(mContext, "LINK/TX", "link closed: ${payload.toHexString(HexFormat.UpperCase)}")
            scheduleReconnect("link closed before send", immediate = requestReason != null)
            return
        }
        if (!current.send(payload)) {
            // The link reports write failures through Listener.onClosed, which already
            // schedules the reconnect; logging here is all that is left to do.
            Log.e(TAG, "Send payload failed")
        }
    }

    /**
     * Send the GAIA v1/v2 API-version negotiation.
     *
     * The frame differs per transport: RFCOMM carries the v1 framing header, while BLE writes
     * the bare packet. Passing an RFCOMM-framed buffer to a GATT write would put a stray `FF`
     * in front of the vendor id that the earbud never sees from the official client.
     */
    private fun sendNegotiation(v1Frame: ByteArray, target: PodLink) {
        if (target.transport == PodTransport.BLE_GAIA) {
            sendBareFrame(GaiaTransport.stripFrame(v1Frame))
        } else {
            sendRawFrame(v1Frame)
        }
    }

    /** Write bytes to a BLE link that must not carry RFCOMM framing. */
    private fun sendBareFrame(frame: ByteArray) {
        if (yielded) {
            Log.d(TAG, "dropped GAIA write while yielded")
            return
        }
        val current = link
        if (current == null || !current.isOpen) {
            RfcommLog.w(mContext, "LINK/TX", "link closed: ${frame.toHexString(HexFormat.UpperCase)}")
            scheduleReconnect("link closed before send")
            return
        }
        if (!current.sendRaw(frame)) Log.e(TAG, "Send bare frame failed")
    }

    /** Send bytes that are already framed (the v1 negotiation frame, debug console input). */
    private fun sendRawFrame(frame: ByteArray, requestReason: String? = null) {
        if (yielded) {
            Log.d(TAG, "dropped GAIA write while yielded")
            return
        }
        if (requestReason != null) reconnectNowForRequest(requestReason)
        val current = link
        if (current == null || !current.isOpen) {
            RfcommLog.w(mContext, "LINK/TX", "link closed: ${frame.toHexString(HexFormat.UpperCase)}")
            scheduleReconnect("link closed before send", immediate = requestReason != null)
            return
        }
        if (!current.sendRaw(frame)) {
            Log.e(TAG, "Send raw frame failed")
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun sendDebugHex(hex: String) {
        val normalized = hex.filterNot { it.isWhitespace() || it == ':' || it == '-' }
        if (normalized.isEmpty() || normalized.length % 2 != 0 || !normalized.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            RfcommLog.e(mContext, "RFCOMM/DEBUG", "invalid HEX: $hex")
            return
        }
        val packet = normalized.hexToByteArray()
        RfcommLog.i(mContext, "RFCOMM/DEBUG", "send ${packet.size} bytes")
        sendRawFrame(packet, "rfcomm debug send")
    }

    fun setTransparencyVocalEnhancement(enabled: Boolean) {
        val packet = commandTable().setTransparencyVocal(enabled) ?: run {
            Log.d(TAG, "transparency vocal enhancement unsupported; ignored=$enabled")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            sendPayload(packet, "transparency vocal")
            commandTable().queryVoiceConfig?.let { q ->
                delay(140)
                sendPayload(q, "voice config query")
            }
        }
    }

    fun setSpatialAudioMode(mode: Int) {
        val enabled = mode != 0
        val packet = commandTable().setSpatialAudio(enabled) ?: run {
            Log.d(TAG, "spatial audio unsupported; ignored=$mode")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            sendPayload(packet, "spatial audio")
            commandTable().querySpatialAudio?.let { q ->
                delay(140)
                sendPayload(q, "spatial audio query")
            }
        }
    }

    /** Head tracking toggle (feature 0x18, sub 0x04). */
    fun setHeadTracking(enabled: Boolean) {
        val packet = commandTable().setHeadTracking(enabled) ?: return
        CoroutineScope(Dispatchers.IO).launch {
            sendPayload(packet, "head tracking")
            commandTable().queryHeadTracking?.let { q ->
                delay(140)
                sendPayload(q, "head tracking query")
            }
        }
    }

    /** Voice prompt on/off with volume (0..100) and prompt index. */
    fun setPromptTone(enabled: Boolean, volume: Int = 50, index: Int = 0) {
        val packet = commandTable().setPromptTone(enabled, volume, index) ?: return
        // Optimistic echo like gain/LED: reflect the requested state immediately, then
        // let the readback confirm (or correct) it.
        currentPromptToneEnabled = enabled
        currentPromptVolume = volume.coerceIn(0, 100)
        currentVoiceIndex = index
        changeUIPromptTone(enabled, currentPromptVolume, index)
        CoroutineScope(Dispatchers.IO).launch {
            sendPayload(packet, "prompt tone")
            commandTable().queryVoiceConfig?.let { q ->
                delay(140)
                sendPayload(q, "voice config query")
            }
        }
    }

    /** Prompt volume only; keeps the current enabled/index values as reported by the device. */
    fun setPromptVolume(enabled: Boolean, volume: Int, index: Int) {
        currentPromptToneEnabled = enabled
        currentPromptVolume = volume.coerceIn(0, 100)
        currentVoiceIndex = index
        setPromptTone(enabled, currentPromptVolume, index)
    }

    /** LHDC codec toggle (feature 0x10). Uses optimistic echo + a readback grace
     *  window so the codec-switch reconnect cannot bounce the switch back. */
    fun setLhdc(enabled: Boolean) {
        val packet = commandTable().setLhdc(enabled) ?: return
        currentLhdcEnabled = enabled
        lhdcWriteGraceUntilMs = SystemClock.elapsedRealtime() + WRITE_CONFIRM_GRACE_MS
        changeUILhdc(enabled)
        CoroutineScope(Dispatchers.IO).launch {
            sendPayload(packet, "LHDC")
            commandTable().queryLhdc?.let { q ->
                delay(140)
                sendPayload(q, "LHDC query")
            }
        }
    }

    /** Dual-connection toggle (feature 0x14), same optimistic + grace strategy. */
    fun setDualConnection(enabled: Boolean) {
        val packet = commandTable().setDualConnection(enabled) ?: return
        currentDualConnectionEnabled = enabled
        dualWriteGraceUntilMs = SystemClock.elapsedRealtime() + WRITE_CONFIRM_GRACE_MS
        changeUIDualConnection(enabled)
        CoroutineScope(Dispatchers.IO).launch {
            sendPayload(packet, "dual connection")
            commandTable().queryDualConnection?.let { q ->
                delay(140)
                sendPayload(q, "dual connection query")
            }
        }
    }

    /**
     * Ask the earbud which GAIA features it implements (feature 0x00, command 0x01).
     *
     * The reply is a list of `(featureId, version)` byte pairs, not a bitmask; see
     * [cythen.hyperdrop.device.protocol.GenericGaiaCommandTable.parse]. This only logs the
     * result today, and gating the UI on it is a later phase.
     */
    fun querySupportedFeatures() {
        val query = commandTable().querySupportedFeatures ?: return
        CoroutineScope(Dispatchers.IO).launch {
            sendPayload(query, "supported features query")
        }
    }

    fun setEqPreset(presetId: Int) { Log.d(TAG, "OPPO EQ preset protocol removed; ignored=$presetId") }

    fun setGainLevel(level: Int) {
        val packet = commandTable().setGainLevel(level) ?: return
        currentGainLevel = level
        changeUIGainLevel(currentGainLevel)
        CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "set gain: ui=$level wire=${commandTable().gainUiToWire(level)}")
            sendPayload(packet, "gain")
            delay(140)
            sendPayload(commandTable().queryGainLevel)
        }
    }

    fun setLedEnabled(enabled: Boolean) {
        currentLedEnabled = enabled
        changeUILedState(enabled)
        CoroutineScope(Dispatchers.IO).launch {
            sendPayload(commandTable().setLedEnabled(enabled), "LED")
            delay(140)
            sendPayload(commandTable().queryLedState)
        }
    }

    fun cycleAnc() {
        val cycle = commandTable().ancCycle
        if (cycle.isEmpty()) return
        val currentIndex = cycle.indexOf(currentAnc)
        val next = cycle[(currentIndex + 1).floorMod(cycle.size)]
        setANCMode(next)
    }

    private fun deviceName(): String =
        if (::mDevice.isInitialized) mDevice.name ?: cachedDeviceName else cachedDeviceName

    /**
     * The profile's command table, re-aimed at the ANC feature the device actually answers on.
     *
     * The profile's translation tables (uiStatus -> wire) are untouched: [withAncPath] only
     * changes which GAIA feature carries the mode. For a model with no observations yet, or a
     * verified model, this is the table exactly as declared.
     *
     * Note this never runs for a verified model: [DeviceRegistry.resolveAncPath] pins a verified
     * profile to its declared path, so `resolved == declared.ancPath` and the specific
     * [ControlProfile.verifiedTable] (PUDDING's) is returned as-is. The re-aim branch therefore
     * only ever applies to the generic derived table.
     */
    private fun commandTable(): GaiaCommandTable {
        val declared = DeviceRegistry.profileFor(deviceName()).commandTable
        val resolved = currentCapabilities().ancPath
        return if (resolved == declared.ancPath) declared else declared.withAncPath(resolved)
    }

    private fun currentCapabilities(): cythen.hyperdrop.device.DeviceCapabilities {
        return cythen.hyperdrop.device.DeviceRegistry.applyObservedFeatures(
            deviceName = deviceName(),
            observed = observedFeatures,
            adaptiveOverride = ConfigManager.adaptiveCapabilityOverride(),
            spatialAudioOverride = ConfigManager.spatialAudioCapabilityOverride(),
            spatialSoundSwitchOverride = ConfigManager.spatialSoundSwitchCapabilityOverride(),
            ancImplementationOverride = ConfigManager.ancImplementationCapabilityOverride(),
        )
    }

    /**
     * Push the capabilities implied by the device's own feature list to the UI.
     *
     * The UI itself holds no state: it re-derives capabilities from the device name, so it
     * cannot see a list reported over RFCOMM. Carrying the observed features across is what
     * lets a control the profile left off appear once the earbud says it supports it.
     */
    private fun publishObservedFeatures() {
        val features = observedFeatures.features
        if (features.isEmpty()) return
        sendAppStatusBroadcast(OppoPodsAction.ACTION_PODS_CAPABILITIES_CHANGED) {
            if (::mDevice.isInitialized) putExtra("address", mDevice.address)
            putExtra("features", features.toIntArray())
            putExtra("anc_path", currentCapabilities().ancPath.name)
            putExtra("complete", observedFeatures.complete)
        }
    }

    private fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor

    fun setANCMode(mode: Int) {
        val packet = commandTable().setNoiseMode(mode) ?: return
        Log.d(TAG, "set noise mode: ui=$mode wire=${commandTable().ancUiToWire(mode)}")
        currentAnc = mode
        changeUIAncStatus(mode)
        CoroutineScope(Dispatchers.IO).launch {
            sendPayload(packet, "noise control")
            delay(180)
            sendPayload(commandTable().queryNoiseMode)
        }
    }

    fun queryBattery() {
        CoroutineScope(Dispatchers.IO).launch {
            sendPayload(commandTable().querySupportedBatteries, "battery query")
            delay(80)
            sendPayload(commandTable().queryBatteryLevels)
        }
    }

    fun queryStatus(immediateReconnect: Boolean = true) {
        CoroutineScope(Dispatchers.IO).launch {
            sendStatusQueryPackets(immediateReconnect)
        }
    }

    private suspend fun sendStatusQueryPackets(immediateReconnect: Boolean = true) {
        val reason = if (immediateReconnect) "status query" else null
        sendPayload(commandTable().queryNoiseMode, reason)
        delay(100)
        sendPayload(commandTable().queryGainLevel)
        delay(100)
        sendPayload(commandTable().queryLedState)
        delay(100)
        // Space the feature queries too: back-to-back RFCOMM frames can be coalesced
        // by the earbud and one of the responses lost.
        commandTable().querySpatialAudio?.let { sendPayload(it, "spatial audio query") }
        delay(100)
        commandTable().queryHeadTracking?.let { sendPayload(it, "head tracking query") }
        delay(100)
        commandTable().queryVoiceConfig?.let { sendPayload(it, "voice config query") }
        delay(100)
        commandTable().queryLhdc?.let { sendPayload(it, "LHDC query") }
        delay(100)
        commandTable().queryDualConnection?.let { sendPayload(it, "dual connection query") }
        delay(100)
        sendPayload(commandTable().querySupportedBatteries)
        delay(80)
        sendPayload(commandTable().queryBatteryLevels)
    }

    fun disconnectAudio(context: Context, device: BluetoothDevice?) {
        val bluetoothAdapter = context.getSystemService(BluetoothManager::class.java).adapter

        MediaControl.sendPause()

        bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HEADSET) {
                    try {
                        val method = proxy.javaClass.getMethod("disconnect", BluetoothDevice::class.java)
                        method.invoke(proxy, device)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy)
                    }
                }
            }
            override fun onServiceDisconnected(profile: Int) { }
        }, BluetoothProfile.HEADSET)

        CoroutineScope(Dispatchers.Default).launch {
            delay(500)
            for (route in routes) {
                if (route.type == MediaRoute2Info.TYPE_BUILTIN_SPEAKER) {
                    Log.d(TAG, "found speaker route $route")
                    mediaRouter.transferTo(route)
                }
            }
        }

        setRegularBatteryLevel(lastTempBatt)
    }

    fun connectAudio(context: Context, device: BluetoothDevice?) {
        val bluetoothAdapter = context.getSystemService(BluetoothManager::class.java).adapter

        bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HEADSET) {
                    try {
                        val method = proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
                        method.invoke(proxy, device)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy)
                    }
                }
            }
            override fun onServiceDisconnected(profile: Int) { }
        }, BluetoothProfile.HEADSET)

        for (route in routes) {
            if (route.type == MediaRoute2Info.TYPE_BLUETOOTH_A2DP && route.name == device!!.name) {
                Log.d(TAG, "found bt route $route")
                mediaRouter.transferTo(route)
            }
        }

        val statusBarManager = context.getSystemService("statusbar") as StatusBarManager
        statusBarManager.setIconVisibility("wireless_headset", true)
        setRegularBatteryLevel(lastTempBatt)
    }

    fun setRegularBatteryLevel(level: Int) {
        try {
            val service = getObjectField(mContext, "mAdapterService")
            callMethod(service, "setBatteryLevel", mDevice, level, false)
        } catch (e: Exception) {
            Log.e(TAG, "setRegularBatteryLevel failed", e)
        }
    }

    private fun getObjectField(instance: Any?, fieldName: String): Any? {
        if (instance == null) return null
        var cls: Class<*>? = instance.javaClass
        while (cls != null) {
            runCatching {
                return cls.getDeclaredField(fieldName).apply { isAccessible = true }.get(instance)
            }
            cls = cls.superclass
        }
        throw NoSuchFieldException(fieldName)
    }

    private fun callMethod(instance: Any?, methodName: String, vararg args: Any?): Any? {
        if (instance == null) return null
        var cls: Class<*>? = instance.javaClass
        while (cls != null) {
            cls.declaredMethods.firstOrNull { it.name == methodName && it.parameterTypes.size == args.size }?.let {
                it.isAccessible = true
                return it.invoke(instance, *args)
            }
            cls = cls.superclass
        }
        throw NoSuchMethodException(methodName)
    }
}
