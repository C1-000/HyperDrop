package cythen.hyperdrop.pods

/**
 * Decision core for "yield the GAIA/RFCOMM control channel while the official
 * MOONDROP app is running".
 *
 * The official app and this module both connect to the same earbud SPP UUID. The
 * earbud normally accepts a single RFCOMM client, so two clients fight over the
 * socket (read ret: -1) and both push conflicting GAIA control frames / focus
 * island state. When the official app is alive we let it own the channel.
 *
 * Liveness is a lease: module code injected into the official app process
 * refreshes a heartbeat in the module's remote preferences. The bluetooth
 * process considers the official app alive while the lease is fresh. The clock
 * is [android.os.SystemClock.elapsedRealtime], a monotonic clock shared across
 * processes (unlike wall-clock time it cannot jump backwards on user edits).
 */
object YieldPolicy {
    const val HEARTBEAT_INTERVAL_MS = 30_000L
    const val LEASE_TIMEOUT_MS = 90_000L
    const val YIELD_CHECK_INTERVAL_MS = 30_000L
    const val PREF_KEY_OFFICIAL_APP_LAST_SEEN_MS = "official_app_last_seen_ms"

    /** A lease is fresh when a heartbeat exists and is not older than the timeout.
     *  A future timestamp (e.g. stale value left over a device reboot) is NOT alive. */
    fun officialAlive(nowMs: Long, lastHeartbeatMs: Long): Boolean {
        if (lastHeartbeatMs <= 0L) return false
        val age = nowMs - lastHeartbeatMs
        return age in 0 until LEASE_TIMEOUT_MS
    }

    /** All three conditions must hold: the user opted in, the official app is
     *  actually alive, and a headset is currently connected. */
    fun shouldYield(enabled: Boolean, officialAlive: Boolean, headsetConnected: Boolean): Boolean =
        enabled && officialAlive && headsetConnected
}
