package cythen.hyperdrop.pods

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import cythen.hyperdrop.BuildConfig
import cythen.hyperdrop.device.BatteryForm
import cythen.hyperdrop.device.DeviceModels
import cythen.hyperdrop.device.DeviceProfile
import cythen.hyperdrop.device.DeviceRegistry
import cythen.hyperdrop.device.SimulatedDevice
import cythen.hyperdrop.device.protocol.GaiaCommandTable
import cythen.hyperdrop.device.protocol.GaiaTransport
import cythen.hyperdrop.utils.miuiStrongToast.data.BatteryParams
import cythen.hyperdrop.utils.miuiStrongToast.data.OppoPodsAction
import cythen.hyperdrop.utils.miuiStrongToast.data.PodParams
import kotlin.math.min

/**
 * App-process stand-in for [DeviceSession].
 *
 * [DeviceSession] can only run inside the `com.android.bluetooth` hook process because it owns a
 * real RFCOMM socket, so the debug feature cannot ask the hook layer to fake a device. Instead this
 * session lives in the app process, replies to the very same `cythen.hyperdrop.action.*` intents the
 * UI sends, and emits the same `*_CHANGED` broadcasts to our own package. From [cythen.hyperdrop.ui.MainUI]'s
 * point of view a simulated device is indistinguishable from a connected one.
 *
 * Connection state is in-memory only; the device list itself is persisted in
 * [cythen.hyperdrop.config.ConfigManager]. Simulated and real sessions are mutually exclusive in the
 * UI: whoever owns `connectedDeviceAddress` handles the intent.
 */
object VirtualDeviceSession {
    private const val TAG = "HyperDrop-VirtualSession"
    private const val CONNECT_DELAY_MS = 450L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingConnect = Runnable { finishConnect() }

    private var appContext: Context? = null
    private var activeDevice: SimulatedDevice? = null
    private var profile: DeviceProfile? = null
    private var connected = false
    private var connecting = false

    private var currentAnc = 1
    private var currentGainLevel = 1
    private var currentLedEnabled = false
    private var currentPromptToneEnabled = false
    private var currentPromptVolume = 50
    private var currentPromptIndex = 0
    private var currentHeadTrackingEnabled = false
    private var currentLhdcEnabled = false
    private var currentLc3Enabled = false
    private var currentDualConnectionEnabled = false
    private var currentSpatialAudioMode = 0
    private var currentTransparencyVocalEnhancement = false
    private var currentEqPreset = -1
    private var currentBatteryParams: BatteryParams? = null
    private var currentWearStatus = WearStatus()

    fun isConnected(): Boolean = connected

    fun isActive(): Boolean = activeDevice != null

    fun activeAddress(): String? = activeDevice?.address

    fun activeProfileId(): String? = profile?.id

    fun connect(context: Context, device: SimulatedDevice) {
        cancelPendingConnect()
        if (activeDevice != null) {
            disconnect(context, notify = false)
        }
        val resolvedProfile = DeviceModels.models.firstOrNull { it.id == device.profileId }
            ?: DeviceRegistry.profileFor(device.displayName)
        appContext = context.applicationContext
        activeDevice = device
        profile = resolvedProfile
        resetState(resolvedProfile)
        connected = false
        connecting = true
        RfcommLog.setEnabled(true, appContext)
        RfcommLog.i(appContext, TAG, "simulate connect profile=${resolvedProfile.id} address=${device.address}")
        Log.i(TAG, "simulate connect profile=${resolvedProfile.id} address=${device.address}")
        sendConnectionState("connecting")
        mainHandler.postDelayed(pendingConnect, CONNECT_DELAY_MS)
    }

    fun disconnect(context: Context? = null, notify: Boolean = true) {
        val wasActive = activeDevice != null
        cancelPendingConnect()
        context?.applicationContext?.let { appContext = it }
        connected = false
        connecting = false
        currentBatteryParams = null
        if (notify && wasActive) {
            RfcommLog.i(appContext, TAG, "simulate disconnect address=${activeDevice?.address}")
            sendConnectionState("disconnected")
            send(Intent(OppoPodsAction.ACTION_PODS_DISCONNECTED)) {
                putExtra("address", activeDevice?.address ?: "")
            }
        }
        activeDevice = null
        profile = null
    }

    fun handle(context: Context, intent: Intent) {
        val device = activeDevice ?: return
        appContext = context.applicationContext
        when (intent.action) {
            OppoPodsAction.ACTION_PODS_UI_INIT -> {
                sendConnectionState(if (connected) "connected" else if (connecting) "connecting" else "disconnected")
                emitAllState()
                if (connected) {
                    send(Intent(OppoPodsAction.ACTION_PODS_CONNECTED)) {
                        putExtra("address", device.address)
                        putExtra("device_name", device.displayName)
                    }
                }
            }

            OppoPodsAction.ACTION_PODS_UI_CLOSED -> Unit
            OppoPodsAction.ACTION_REFRESH_STATUS -> emitAllState()
            OppoPodsAction.ACTION_ANC_SELECT -> setAncMode(intent.getIntExtra("status", currentAnc))
            OppoPodsAction.ACTION_TRANSPARENCY_VOCAL_ENHANCEMENT_SET ->
                setTransparencyVocal(intent.getBooleanExtra("enabled", false))

            OppoPodsAction.ACTION_SPATIAL_AUDIO_SET ->
                setSpatialAudioMode(intent.getIntExtra("mode", 0))

            OppoPodsAction.ACTION_EQ_PRESET_SET -> setEqPreset(intent.getIntExtra("preset", -1))
            OppoPodsAction.ACTION_GAIN_LEVEL_SET -> setGainLevel(intent.getIntExtra("level", currentGainLevel))
            OppoPodsAction.ACTION_LED_STATE_SET -> setLedEnabled(intent.getBooleanExtra("enabled", false))
            OppoPodsAction.ACTION_PROMPT_TONE_SET -> setPromptTone(
                enabled = intent.getBooleanExtra("enabled", currentPromptToneEnabled),
                volume = intent.getIntExtra("volume", currentPromptVolume),
                index = intent.getIntExtra("index", currentPromptIndex),
            )

            OppoPodsAction.ACTION_PROMPT_VOLUME_SET -> setPromptTone(
                enabled = if (intent.hasExtra("enabled")) intent.getBooleanExtra("enabled", currentPromptToneEnabled) else currentPromptToneEnabled,
                volume = intent.getIntExtra("volume", currentPromptVolume),
                index = intent.getIntExtra("index", currentPromptIndex),
            )

            OppoPodsAction.ACTION_HEAD_TRACKING_SET -> setHeadTracking(intent.getBooleanExtra("enabled", false))
            OppoPodsAction.ACTION_LHDC_SET -> setLhdc(intent.getBooleanExtra("enabled", false))
            OppoPodsAction.ACTION_LC3_SET -> setLc3(intent.getBooleanExtra("enabled", false))
            OppoPodsAction.ACTION_DUAL_CONNECTION_SET -> setDualConnection(intent.getBooleanExtra("enabled", false))
            OppoPodsAction.ACTION_CYCLE_ANC -> cycleAnc()
            OppoPodsAction.ACTION_RFCOMM_LOG_CONNECT -> RfcommLog.setEnabled(true, appContext)
            OppoPodsAction.ACTION_RFCOMM_LOG_DISCONNECT -> RfcommLog.setEnabled(false)
            OppoPodsAction.ACTION_RFCOMM_LOG_CLEAR -> RfcommLog.clear()
            OppoPodsAction.ACTION_RFCOMM_DEBUG_SEND -> {
                val hex = intent.getStringExtra("hex").orEmpty()
                RfcommLog.i(appContext, "RFCOMM/DEBUG", "simulated device ignores manual send: $hex")
            }

            OppoPodsAction.ACTION_CONFIG_CHANGED,
            OppoPodsAction.ACTION_OFFICIAL_APP_PRESENCE,
            OppoPodsAction.ACTION_GET_PODS_MAC -> Unit

            else -> Log.d(TAG, "ignored action=${intent.action}")
        }
    }

    private fun finishConnect() {
        val device = activeDevice ?: return
        connecting = false
        connected = true
        RfcommLog.i(appContext, TAG, "simulated connection established address=${device.address}")
        sendConnectionState("connected")
        send(Intent(OppoPodsAction.ACTION_PODS_CONNECTED)) {
            putExtra("address", device.address)
            putExtra("device_name", device.displayName)
        }
        emitAllState()
    }

    private fun cancelPendingConnect() {
        mainHandler.removeCallbacks(pendingConnect)
    }

    private fun resetState(resolvedProfile: DeviceProfile) {
        val capabilities = resolvedProfile.capabilities
        currentAnc = capabilities.ancModes.firstOrNull()?.mode?.toUiStatus() ?: 1
        currentGainLevel = if (resolvedProfile.controlProfile.gainMap.isEmpty()) 0 else min(1, resolvedProfile.controlProfile.gainMap.size - 1)
        currentLedEnabled = capabilities.ledSupported
        currentPromptToneEnabled = false
        currentPromptVolume = 50
        currentPromptIndex = 0
        currentHeadTrackingEnabled = false
        currentLhdcEnabled = false
        currentLc3Enabled = false
        currentDualConnectionEnabled = false
        currentSpatialAudioMode = 0
        currentTransparencyVocalEnhancement = false
        currentEqPreset = -1
        currentWearStatus = WearStatus(left = WearState.WEARING, right = WearState.WEARING, case = null)
        currentBatteryParams = if (capabilities.batteryForm == BatteryForm.SINGLE_DEVICE) {
            BatteryParams(
                left = PodParams(battery = 88, isCharging = false, isConnected = true),
                right = null,
                case = PodParams(battery = 91, isCharging = true, isConnected = true),
            )
        } else {
            BatteryParams(
                left = PodParams(battery = 88, isCharging = false, isConnected = true),
                right = PodParams(battery = 76, isCharging = false, isConnected = true),
                case = PodParams(battery = 91, isCharging = true, isConnected = true),
            )
        }
    }

    private fun commandTable(): GaiaCommandTable? = profile?.commandTable

    private fun emitAllState() {
        if (activeDevice == null) return
        emitWearStatus()
        emitAncMode()
        emitTransparencyVocalEnhancement()
        emitSpatialAudioMode()
        emitEqPreset()
        emitGainLevel()
        emitLedEnabled()
        emitPromptTone()
        emitHeadTracking()
        emitLhdc()
        emitLc3()
        emitDualConnection()
        emitBattery()
    }

    private fun sendConnectionState(state: String) {
        val device = activeDevice ?: return
        send(Intent(OppoPodsAction.ACTION_PODS_CONNECTION_STATE_CHANGED)) {
            putExtra("address", device.address)
            putExtra("device_name", device.displayName)
            putExtra("state", state)
        }
    }

    private fun send(intent: Intent, fill: Intent.() -> Unit = {}) {
        val context = appContext ?: return
        intent.apply(fill)
        intent.setPackage(BuildConfig.APPLICATION_ID)
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        context.sendBroadcast(intent)
    }

    private fun logUnsupported(operation: String) {
        val context = appContext ?: return
        val profileId = profile?.id ?: "unknown"
        RfcommLog.w(context, TAG, "$operation unsupported by profile=$profileId")
    }

    private fun logTx(packet: ByteArray) {
        val context = appContext ?: return
        RfcommLog.i(context, "RFCOMM/TX", "v4 ${GaiaTransport.v4(packet).toHex()}")
    }

    private fun setAncMode(uiStatus: Int) {
        val table = commandTable() ?: return
        val packet = table.setNoiseMode(uiStatus)
        if (packet == null) {
            logUnsupported("setNoiseMode($uiStatus)")
            return
        }
        logTx(packet)
        currentAnc = uiStatus
        emitAncMode()
    }

    private fun setGainLevel(level: Int) {
        val table = commandTable() ?: return
        val packet = table.setGainLevel(level)
        if (packet == null) {
            logUnsupported("setGainLevel($level)")
            return
        }
        logTx(packet)
        currentGainLevel = level
        emitGainLevel()
    }

    private fun setLedEnabled(enabled: Boolean) {
        val table = commandTable() ?: return
        logTx(table.setLedEnabled(enabled))
        currentLedEnabled = enabled
        emitLedEnabled()
    }

    private fun setPromptTone(enabled: Boolean, volume: Int, index: Int) {
        val table = commandTable() ?: return
        val packet = table.setPromptTone(enabled, volume, index)
        if (packet == null) {
            logUnsupported("setPromptTone($enabled,$volume,$index)")
            return
        }
        logTx(packet)
        currentPromptToneEnabled = enabled
        currentPromptVolume = volume
        currentPromptIndex = index
        emitPromptTone()
    }

    private fun setTransparencyVocal(enabled: Boolean) {
        val table = commandTable() ?: return
        val packet = table.setTransparencyVocal(enabled)
        if (packet == null) {
            logUnsupported("setTransparencyVocal($enabled)")
            return
        }
        logTx(packet)
        currentTransparencyVocalEnhancement = enabled
        emitTransparencyVocalEnhancement()
    }

    private fun setSpatialAudioMode(mode: Int) {
        val table = commandTable() ?: return
        val packet = table.setSpatialAudio(mode != 0)
        if (packet == null) {
            logUnsupported("setSpatialAudio(mode=$mode)")
            return
        }
        logTx(packet)
        currentSpatialAudioMode = mode
        emitSpatialAudioMode()
    }

    private fun setHeadTracking(enabled: Boolean) {
        val table = commandTable() ?: return
        val packet = table.setHeadTracking(enabled)
        if (packet == null) {
            logUnsupported("setHeadTracking($enabled)")
            return
        }
        logTx(packet)
        currentHeadTrackingEnabled = enabled
        emitHeadTracking()
    }

    private fun setLhdc(enabled: Boolean) {
        val table = commandTable() ?: return
        val packet = table.setLhdc(enabled)
        if (packet == null) {
            logUnsupported("setLhdc($enabled)")
            return
        }
        logTx(packet)
        currentLhdcEnabled = enabled
        emitLhdc()
    }

    private fun setLc3(enabled: Boolean) {
        val table = commandTable() ?: return
        val packet = table.setLc3(enabled)
        if (packet == null) {
            logUnsupported("setLc3($enabled)")
            return
        }
        logTx(packet)
        currentLc3Enabled = enabled
        emitLc3()
    }

    private fun setDualConnection(enabled: Boolean) {
        val table = commandTable() ?: return
        val packet = table.setDualConnection(enabled)
        if (packet == null) {
            logUnsupported("setDualConnection($enabled)")
            return
        }
        logTx(packet)
        currentDualConnectionEnabled = enabled
        emitDualConnection()
    }

    private fun setEqPreset(preset: Int) {
        // DeviceSession intentionally treats EQ as a no-op today; keep the same behaviour but still
        // reflect the value locally so the UI can be exercised.
        currentEqPreset = preset
        emitEqPreset()
        val context = appContext ?: return
        RfcommLog.i(context, TAG, "eq preset=$preset recorded locally (no wire command yet)")
    }

    private fun cycleAnc() {
        val cycle = commandTable()?.ancCycle.orEmpty()
        if (cycle.isEmpty()) {
            logUnsupported("cycleAnc")
            return
        }
        val index = cycle.indexOf(currentAnc)
        val next = cycle[(if (index < 0) 0 else (index + 1) % cycle.size)]
        setAncMode(next)
    }

    private fun emitBattery() {
        val device = activeDevice ?: return
        val battery = currentBatteryParams ?: return
        val context = appContext ?: return
        send(Intent(OppoPodsAction.ACTION_PODS_BATTERY_CHANGED)) {
            putExtra("address", device.address)
            putExtra("status", battery)
            putBatteryExtras(battery)
        }
        Log.d(TAG, "emit battery left=${battery.left?.battery} right=${battery.right?.battery} case=${battery.case?.battery}")
        RfcommLog.d(context, TAG, "battery snapshot left=${battery.left?.battery} right=${battery.right?.battery} case=${battery.case?.battery}")
    }

    private fun emitWearStatus() {
        val device = activeDevice ?: return
        send(Intent(OppoPodsAction.ACTION_PODS_WEAR_STATUS_CHANGED)) {
            putExtra("address", device.address)
            putExtra("left_wear_status", currentWearStatus.left?.value ?: -1)
            putExtra("right_wear_status", currentWearStatus.right?.value ?: -1)
            putExtra("case_wear_status", currentWearStatus.case?.value ?: -1)
        }
    }

    private fun emitAncMode() {
        val device = activeDevice ?: return
        send(Intent(OppoPodsAction.ACTION_PODS_ANC_CHANGED)) {
            putExtra("address", device.address)
            putExtra("status", currentAnc)
        }
    }

    private fun emitTransparencyVocalEnhancement() {
        send(Intent(OppoPodsAction.ACTION_PODS_TRANSPARENCY_VOCAL_ENHANCEMENT_CHANGED)) {
            putExtra("address", activeDevice?.address ?: "")
            putExtra("enabled", currentTransparencyVocalEnhancement)
        }
    }

    private fun emitSpatialAudioMode() {
        send(Intent(OppoPodsAction.ACTION_PODS_SPATIAL_AUDIO_CHANGED)) {
            putExtra("address", activeDevice?.address ?: "")
            putExtra("mode", currentSpatialAudioMode)
        }
    }

    private fun emitEqPreset() {
        send(Intent(OppoPodsAction.ACTION_PODS_EQ_PRESET_CHANGED)) {
            putExtra("address", activeDevice?.address ?: "")
            putExtra("preset", currentEqPreset)
        }
    }

    private fun emitGainLevel() {
        send(Intent(OppoPodsAction.ACTION_PODS_GAIN_LEVEL_CHANGED)) {
            putExtra("address", activeDevice?.address ?: "")
            putExtra("level", currentGainLevel)
        }
    }

    private fun emitLedEnabled() {
        send(Intent(OppoPodsAction.ACTION_PODS_LED_STATE_CHANGED)) {
            putExtra("address", activeDevice?.address ?: "")
            putExtra("enabled", currentLedEnabled)
        }
    }

    private fun emitPromptTone() {
        send(Intent(OppoPodsAction.ACTION_PROMPT_TONE_CHANGED)) {
            putExtra("address", activeDevice?.address ?: "")
            putExtra("enabled", currentPromptToneEnabled)
            putExtra("volume", currentPromptVolume)
            putExtra("index", currentPromptIndex)
        }
    }

    private fun emitHeadTracking() {
        send(Intent(OppoPodsAction.ACTION_HEAD_TRACKING_CHANGED)) {
            putExtra("address", activeDevice?.address ?: "")
            putExtra("enabled", currentHeadTrackingEnabled)
        }
    }

    private fun emitLhdc() {
        send(Intent(OppoPodsAction.ACTION_LHDC_CHANGED)) {
            putExtra("address", activeDevice?.address ?: "")
            putExtra("enabled", currentLhdcEnabled)
        }
    }

    private fun emitLc3() {
        send(Intent(OppoPodsAction.ACTION_LC3_CHANGED)) {
            putExtra("address", activeDevice?.address ?: "")
            putExtra("enabled", currentLc3Enabled)
        }
    }

    private fun emitDualConnection() {
        send(Intent(OppoPodsAction.ACTION_DUAL_CONNECTION_CHANGED)) {
            putExtra("address", activeDevice?.address ?: "")
            putExtra("enabled", currentDualConnectionEnabled)
        }
    }

    private fun Intent.putBatteryExtras(battery: BatteryParams) {
        putExtra("left_battery", battery.left?.battery ?: -1)
        putExtra("left_charging", battery.left?.isCharging ?: false)
        putExtra("left_connected", battery.left?.isConnected ?: false)
        putExtra("right_battery", battery.right?.battery ?: -1)
        putExtra("right_charging", battery.right?.isCharging ?: false)
        putExtra("right_connected", battery.right?.isConnected ?: false)
        putExtra("case_battery", battery.case?.battery ?: -1)
        putExtra("case_charging", battery.case?.isCharging ?: false)
        putExtra("case_connected", battery.case?.isConnected ?: false)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }
}
