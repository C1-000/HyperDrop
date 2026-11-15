package cythen.hyperdrop.hook

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import cythen.hyperdrop.pods.YieldPolicy
import cythen.hyperdrop.utils.miuiStrongToast.data.OppoPodsAction

/**
 * Loaded into the official MOONDROP app process (added to the module scope).
 *
 * Publishes a lease heartbeat into the module's remote preferences and an
 * explicit presence broadcast, so the bluetooth process can detect that the
 * official app is alive and yield the GAIA/RFCOMM control channel. It does NOT
 * touch the official app's UI, accounts, private files, Bluetooth objects or
 * protocol code. Everything is wrapped so a hook failure degrades silently and
 * can never crash the official app.
 */
class OfficialAppPresenceHook : HookContext() {
    companion object {
        private const val TAG = "HyperDrop-OfficialAppPresence"
    }

    @Volatile
    private var heartbeatStarted = false

    override fun onHook() {
        // Application.onCreate() is public and always called once per process; attach()
        // is earlier but package-private/final on some ROMs. Try both, guarded by
        // heartbeatStarted so only one heartbeat loop is ever installed.
        val candidates = listOf(
            "onCreate" to emptyArray<Class<*>>(),
            "attach" to arrayOf<Class<*>>(Context::class.java),
        )
        var installed = false
        for ((name, params) in candidates) {
            val method = runCatching { findMethod("android.app.Application", name, *params) }.getOrNull()
                ?: continue
            runCatching {
                hookAfter(method) {
                    val appContext = instance as? Context
                    if (appContext != null) startHeartbeat(appContext)
                }
                installed = true
            }.onFailure { Log.w(TAG, "Application.$name hook skipped", it) }
        }
        if (!installed) {
            Log.w(TAG, "no Application lifecycle method hookable; heartbeat disabled")
        }
    }

    private fun startHeartbeat(context: Context) {
        if (heartbeatStarted) return
        heartbeatStarted = true
        val handler = Handler(Looper.getMainLooper())
        fun beat() {
            // The broadcast is the authoritative liveness signal; keep it in its own
            // try/catch so a RemotePreferences write failure can never suppress it.
            runCatching {
                prefs.edit()
                    .putLong(YieldPolicy.PREF_KEY_OFFICIAL_APP_LAST_SEEN_MS, SystemClock.elapsedRealtime())
                    .commit()
            }
            runCatching {
                context.sendBroadcast(Intent(OppoPodsAction.ACTION_OFFICIAL_APP_PRESENCE).apply {
                    setPackage("com.android.bluetooth")
                    putExtra(OppoPodsAction.EXTRA_OFFICIAL_APP_PACKAGE, packageName)
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                })
            }
            handler.postDelayed({ beat() }, YieldPolicy.HEARTBEAT_INTERVAL_MS)
        }
        beat()
        Log.i(TAG, "official app presence heartbeat started package=$packageName")
    }
}
