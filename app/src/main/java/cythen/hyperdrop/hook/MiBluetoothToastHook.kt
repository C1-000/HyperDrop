package cythen.hyperdrop.hook

import android.annotation.SuppressLint
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.os.Bundle
import com.xzakota.hyper.notification.focus.FocusNotification
import cythen.hyperdrop.utils.PodImageLoader
import cythen.hyperdrop.utils.SystemApisUtils
import cythen.hyperdrop.utils.SystemApisUtils.cancelAsUser
import cythen.hyperdrop.utils.SystemApisUtils.notifyAsUser
import cythen.hyperdrop.config.ConfigManager
import cythen.hyperdrop.utils.miuiStrongToast.data.BatteryParams
import cythen.hyperdrop.utils.miuiStrongToast.data.OppoPodsAction
import cythen.hyperdrop.R
import cythen.hyperdrop.BuildConfig
import cythen.hyperdrop.device.DeviceRegistry

@SuppressLint("MissingPermission")
object MiBluetoothToastHook : HookContext() {

    // ANC 模式本地缓存，用于循环切换和状态同步（1=关 2=降噪 3=通透 4=自适应）
    // 通过接收 ACTION_PODS_ANC_CHANGED 广播与 DeviceSession 保持同步
    private var localAncMode = 1

    override fun onHook() {

        // HyperOS can still let the user clear an ongoing notification. Cache the most
        // recent payload per address so a swipe can be undone while the headset stays
        // connected (see the delete-intent branch of bridgeReceiver below).
        val notificationCache = HashMap<String, Pair<BluetoothDevice, BatteryParams>>()
        val activeAddresses = HashSet<String>()
        val restoreHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val restoreRunnables = HashMap<String, Runnable>()

        fun deleteIntent(context: Context, bluetoothDevice: BluetoothDevice): PendingIntent? {
            val intent = Intent("com.android.bluetooth.headset.notification.cancle")
            intent.putExtra("android.bluetooth.device.extra.DEVICE", bluetoothDevice)
            return PendingIntent.getBroadcast(context, 0, intent, 201326592)
        }

        @SuppressLint("WrongConstant")
        fun createPodsNotification(bluetoothDevice: BluetoothDevice?, context: Context, batteryParams: BatteryParams) {
            val miheadset_notification_Box = context.resources.getIdentifier("miheadset_notification_Box", "string", "com.xiaomi.bluetooth")
            val miheadset_notification_LeftEar = context.resources.getIdentifier("miheadset_notification_LeftEar", "string", "com.xiaomi.bluetooth")
            val miheadset_notification_RightEar = context.resources.getIdentifier("miheadset_notification_RightEar", "string", "com.xiaomi.bluetooth")
            val system_notification_accent_color = context.resources.getIdentifier("system_notification_accent_color", "color", "android")
            if (bluetoothDevice == null) {
                Log.e("HyperDrop", "createPodsNotification: btDevice null")
                return
            }
            try {
                val address: String = bluetoothDevice.address
                activeAddresses.add(address)
                notificationCache[address] = bluetoothDevice to batteryParams
                restoreRunnables.remove(address)?.let { restoreHandler.removeCallbacks(it) }
                var alias: String? = bluetoothDevice.alias
                if (alias?.isEmpty() == true) {
                    alias = bluetoothDevice.name
                }

                val caseBattStr = if (batteryParams.case != null && batteryParams.case!!.isConnected)
                    "${context.resources.getString(miheadset_notification_Box)}${batteryParams.case!!.battery}%" +
                            "${if (batteryParams.case!!.isCharging) "⚡ " else " "}\n"
                else ""
                val leftEar = if (batteryParams.left != null && batteryParams.left!!.isConnected)
                    "${context.resources.getString(miheadset_notification_LeftEar)}${batteryParams.left!!.battery}%" +
                        (if (batteryParams.left!!.isCharging) "⚡" else "")
                else ""
                val leftToRight = if (batteryParams.left?.isConnected == true && batteryParams.right?.isConnected == true) " " else ""
                val rightEar = if (batteryParams.right != null && batteryParams.right!!.isConnected)
                    "$leftToRight${context.resources.getString(miheadset_notification_RightEar)}${batteryParams.right!!.battery}%" +
                        (if (batteryParams.right!!.isCharging) "⚡ " else " ")
                else ""

                val contentText: String = caseBattStr + leftEar + rightEar
                val notificationManager = context.getSystemService("notification") as NotificationManager
                notificationManager.createNotificationChannel(
                    NotificationChannel(
                        "BTHeadset$address",
                        alias,
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        setSound(null, null)
                        setAllowBubbles(true)
                    }
                )
                val bundle = Bundle()
                bundle.putParcelable("Device", bluetoothDevice)
                val intent = Intent("com.android.bluetooth.headset.notification")
                intent.putExtra("btData", bundle)
                intent.putExtra("disconnect", "1")
                intent.setIdentifier("BTHeadset$address")
                val moduleContext = context.createPackageContext(
                    BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY
                )
                val headsetBitmap = PodImageLoader.loadBoxBitmap(context, prefs, address)
                    ?: BitmapFactory.decodeResource(moduleContext.resources, R.drawable.img_box)
                if (headsetBitmap == null) {
                    Log.e("HyperDrop", "createPodsNotification: headset bitmap null")
                    return
                }
                val headsetIcon = Icon.createWithBitmap(headsetBitmap)
                // Restore OppoPods' proven island route exactly: the Focus notification itself
                // stays owned by com.xiaomi.bluetooth, while a tap opens our lightweight popup.
                // Do not point the notification directly at Settings; some HyperOS builds reject
                // that PendingIntent while parsing focus extras and the whole island disappears.
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    Intent("cythen.hyperdrop.action.show_pods_ui").apply {
                        setClassName(BuildConfig.APPLICATION_ID, "cythen.hyperdrop.PopupActivity")
                        putExtra("android.bluetooth.device.extra.DEVICE", bluetoothDevice)
                        putExtra("bluetoothaddress", bluetoothDevice.address)
                        putExtra("device_name", alias)
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val focusExtras = FocusNotification.buildV3 {
                    val logo = createPicture("key_headset", headsetIcon)
                    // AOD/island must stay attached to this persistent notification.
                    // A separate short-lived island notification used to cancel the
                    // HyperOS focus session and wipe AOD after 4s.
                    enableFloat = false
                    ticker = alias ?: ""
                    updatable = true
//                    tickerPic = logo

                    iconTextInfo {
                        animIconInfo{
                            type = 0
                            src = logo
                        }
                        title = alias ?: ""
                        content = contentText
                    }

                    island {
                        islandProperty = 1
                        // Only bigIslandArea here; no smallIslandArea (avoids a permanent
                        // summary capsule). Island collapses after a few seconds while
                        // the AOD text on this persistent card remains.
                        dismissIsland = true
                        islandTimeout = 5
                        bigIslandArea {
                            imageTextInfoLeft {
                                type = 1
                                picInfo {
                                    type = 1
                                    pic = logo
                                }
                            }
                            imageTextInfoRight {
                                type = 2
                                textInfo {
                                    title = alias ?: ""
                                    content = contentText
                                }
                            }
                        }
                    }


                    // Single circular Close button on the right side of the card.
                    // Miuix Close.Regular is rasterized from the local vector because
                    // this notification is built inside com.xiaomi.bluetooth (no Compose).
                    val closeSize = (24f * moduleContext.resources.displayMetrics.density).toInt()
                        .coerceAtLeast(1)
                    val closeDrawable = moduleContext.getDrawable(R.drawable.ic_miuix_close)?.mutate()
                    val closeKey = if (closeDrawable != null) {
                        val closeBitmap = Bitmap.createBitmap(closeSize, closeSize, Bitmap.Config.ARGB_8888)
                        closeDrawable.setTint(android.graphics.Color.WHITE)
                        closeDrawable.setBounds(0, 0, closeSize, closeSize)
                        closeDrawable.draw(Canvas(closeBitmap))
                        createPicture("key_close", Icon.createWithBitmap(closeBitmap))
                    } else null
                    val disconnectParcel = Notification.Action.Builder(
                        Icon.createWithResource(context, android.R.drawable.ic_delete),
                        moduleContext.getString(R.string.notification_btn_disconnect),
                        PendingIntent.getBroadcast(context, 2, intent, 201326592)
                    ).build()
                    actions {
                        addActionInfo {
                            type = 0
                            if (closeKey != null) {
                                actionIcon = closeKey
                                actionIconDark = closeKey
                            }
                            action = createAction("key_disconnect", disconnectParcel)
                        }
                    }
                }
                // AOD 息屏显示：左右耳电量拼合后注入 aodTitle
                if (focusExtras != null) {
                    val aodParts = mutableListOf<String>()
                    if (batteryParams.left?.isConnected == true)
                        aodParts.add("L ${batteryParams.left!!.battery}%")
                    if (batteryParams.right?.isConnected == true)
                        aodParts.add("R ${batteryParams.right!!.battery}%")
                    val aodTitle = aodParts.joinToString(" | ")
                    try {
                        val json = org.json.JSONObject(focusExtras.getString("miui.focus.param") ?: "{}")
                        val pv2 = json.optJSONObject("param_v2") ?: org.json.JSONObject()
                        pv2.put("aodTitle", aodTitle)
                        pv2.put("aodPic", "key_headset")
                        json.put("param_v2", pv2)
                        focusExtras.putString("miui.focus.param", json.toString())
                    } catch (_: Exception) {}
                }
                notificationManager.notifyAsUser(
                    "BTHeadset$address",
                    10003,
                    Notification.Builder(context, "BTHeadset$address")
                        .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                        .setWhen(0L)
                        .setTicker(alias)
                        .setDefaults(-1)
                        .setContentTitle(alias)
                        .setContentText(contentText)
                        // Keep this persistent headset card alive across manual swipes:
                        // the delete broadcast has no listener, so a cleared card would
                        // never be republished until the next GAIA battery packet.
                        .setOngoing(true)
                        .setContentIntent(pendingIntent)
                        .setDeleteIntent(deleteIntent(context, bluetoothDevice))
                        .setColor(context.getColor(system_notification_accent_color))
                        .apply { focusExtras?.let { addExtras(it) } }
                        .setVisibility(Notification.VISIBILITY_PUBLIC)
                        .build(),
                    SystemApisUtils.getUserAllUserHandle()
                )
            } catch (e: Exception) {
                Log.e("HyperDrop", "Failed to create Pod Notification", e)
            }
        }

        fun cancelNotification(bluetoothDevice: BluetoothDevice, context: Context) {
            try {
                val address = bluetoothDevice.address
                activeAddresses.remove(address)
                notificationCache.remove(address)
                restoreRunnables.remove(address)?.let { restoreHandler.removeCallbacks(it) }
                if (address.isNotEmpty()) {
                    val notificationManager = context.getSystemService("notification") as NotificationManager
                    notificationManager.cancelAsUser("BTHeadset$address", 10003, SystemApisUtils.getUserAllUserHandle())
                }
            } catch (e: Exception) {
                Log.e("HyperDrop", "Failed to cancel Pod Notification!", e)
            }
        }


        // HyperOriG/OppoPods rely on a receiver living in com.xiaomi.bluetooth.  Older
        // HyperDrop builds registered it only after one exact MiuiBluetoothNotification
        // constructor ran; if the constructor signature changed, the Focus-Island broadcast
        // was simply lost.  Register once at Xiaomi Bluetooth application start and keep
        // constructor hooks only as a fallback.
        val focusAction = "cythen.hyperdrop.action.SEND_FOCUS_ISLAND"
        var bridgeReceiverRegistered = false
        var bridgeContext: Context? = null

        val bridgeReceiver = object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                val receiverCtx = p0 ?: bridgeContext ?: return
                android.util.Log.d("HyperDrop-Island", "bridge action=${p1?.action}")
                when (p1?.action) {
                    focusAction,
                    "cythen.hyperdrop.action.sendstrongtoast" -> {
                        val batteryParams = p1.getParcelableExtra("batteryParams", BatteryParams::class.java)
                            ?: return
                        // The island is part of the persistent focus notification now.
                        // Posting a separate short-lived 10086 notification cancelled the
                        // HyperOS focus session and removed AOD after 4s.
                        android.util.Log.d("HyperDrop-Island", "Focus request ignored (island attached to persistent card)")
                    }
                    "com.android.bluetooth.headset.notification.cancle" -> {
                        val device = p1.getParcelableExtra(
                            "android.bluetooth.device.extra.DEVICE", BluetoothDevice::class.java
                        ) ?: return
                        val address = device.address
                        if (!activeAddresses.contains(address)) return
                        restoreRunnables.remove(address)?.let { restoreHandler.removeCallbacks(it) }
                        val restore = Runnable {
                            restoreRunnables.remove(address)
                            if (!activeAddresses.contains(address)) return@Runnable
                            val cached = notificationCache[address] ?: return@Runnable
                            val ctx = bridgeContext ?: return@Runnable
                            Log.d("HyperDrop", "restore notification after swipe address=$address")
                            createPodsNotification(cached.first, ctx, cached.second)
                        }
                        restoreRunnables[address] = restore
                        restoreHandler.postDelayed(restore, 10_000L)
                    }
                    "cythen.hyperdrop.action.updatepodsnotification" -> {
                        val batteryParams = p1.getParcelableExtra("batteryParams", BatteryParams::class.java)
                            ?: return
                        val device = p1.getParcelableExtra("device", BluetoothDevice::class.java)
                        createPodsNotification(device, receiverCtx, batteryParams)
                    }
                    "cythen.hyperdrop.action.cancelpodsnotification" -> {
                        val device = p1.getParcelableExtra("device", BluetoothDevice::class.java) ?: return
                        cancelNotification(device, receiverCtx)
                    }
                    OppoPodsAction.ACTION_PODS_ANC_CHANGED -> {
                        localAncMode = p1.getIntExtra("status", 1)
                    }
                    OppoPodsAction.ACTION_CYCLE_ANC -> {
                        val capabilities = DeviceRegistry.detectDeviceCapabilities(
                            deviceName = p1.getStringExtra("device_name").orEmpty(),
                            adaptiveOverride = prefs.getInt(
                                ConfigManager.PREF_KEY_ADAPTIVE_CAPABILITY_OVERRIDE,
                                ConfigManager.CAPABILITY_OVERRIDE_AUTO
                            ),
                            spatialAudioOverride = ConfigManager.CAPABILITY_OVERRIDE_AUTO,
                            spatialSoundSwitchOverride = ConfigManager.CAPABILITY_OVERRIDE_AUTO,
                        )
                        val cycle = if (capabilities.adaptiveSupported) {
                            listOf(2, 4, 3, 1)
                        } else {
                            listOf(2, 3, 1)
                        }
                        val currentIndex = cycle.indexOf(if (localAncMode in 5..8) 2 else localAncMode)
                        localAncMode = cycle[(currentIndex + 1).floorMod(cycle.size)]
                        receiverCtx.sendBroadcast(Intent(OppoPodsAction.ACTION_ANC_SELECT).apply {
                            putExtra("status", localAncMode)
                            setPackage("com.android.bluetooth")
                            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                        })
                    }
                }
            }
        }

        fun ensureBridgeReceiver(candidate: Context?) {
            if (candidate == null || bridgeReceiverRegistered) return
            val ctx = candidate.applicationContext ?: candidate
            val filter = IntentFilter(focusAction).apply {
                // Keep the old action accepted so an in-flight process from an older module
                // version cannot break the first reconnect after updating.
                addAction("cythen.hyperdrop.action.sendstrongtoast")
                addAction("cythen.hyperdrop.action.updatepodsnotification")
                addAction("cythen.hyperdrop.action.cancelpodsnotification")
                addAction("com.android.bluetooth.headset.notification.cancle")
                addAction(OppoPodsAction.ACTION_CYCLE_ANC)
                addAction(OppoPodsAction.ACTION_PODS_ANC_CHANGED)
            }
            runCatching {
                ctx.registerReceiver(bridgeReceiver, filter, Context.RECEIVER_EXPORTED)
            }.onSuccess {
                bridgeReceiverRegistered = true
                bridgeContext = ctx
                android.util.Log.d("HyperDrop-Island", "bridge receiver registered package=${ctx.packageName}")
                Log.d("HyperDrop", "Xiaomi Bluetooth island receiver registered")
            }.onFailure {
                android.util.Log.e("HyperDrop-Island", "bridge receiver register failed", it)
                Log.e("HyperDrop", "Xiaomi Bluetooth island receiver register failed", it)
            }
        }

        // Guaranteed application lifecycle entry in com.xiaomi.bluetooth on normal launches.
        runCatching {
            hookAfter(findMethod("android.app.Instrumentation", "callApplicationOnCreate", Application::class.java)) {
                ensureBridgeReceiver(args[0] as? Context)
            }
        }.onFailure { Log.w("HyperDrop", "Instrumentation application hook unavailable", it) }

        // Fallback for already-running Xiaomi Bluetooth processes and for vendor variants.
        runCatching {
            findClass("com.android.bluetooth.ble.app.MiuiBluetoothNotification")
                .declaredConstructors
                .forEach { constructor ->
                    constructor.isAccessible = true
                    hookConstructorAfter(constructor) {
                        ensureBridgeReceiver(runCatching { getObjectField(instance, "mContext") as? Context }.getOrNull())
                    }
                }
        }.onFailure { Log.w("HyperDrop", "MiuiBluetoothNotification constructor hooks unavailable", it) }
    }

    private fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor
}
