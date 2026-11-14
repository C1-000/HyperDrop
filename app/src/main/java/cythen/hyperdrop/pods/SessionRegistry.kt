package cythen.hyperdrop.pods

/**
 * Owns the active [DeviceSession].
 *
 * The RFCOMM state used to live in a process-wide `object`; it is now an
 * instance so a second device can later be given its own session. Call sites
 * ask the registry for the current session instead of touching global state.
 */
object SessionRegistry {
    private val session by lazy { DeviceSession() }

    fun current(): DeviceSession = session
}
