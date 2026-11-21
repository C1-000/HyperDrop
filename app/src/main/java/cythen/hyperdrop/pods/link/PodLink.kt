package cythen.hyperdrop.pods.link

import cythen.hyperdrop.device.PodTransport

/**
 * One bidirectional, GAIA-bearing link to a single earbud.
 *
 * A link owns *framing*, not protocol semantics: callers hand it an unframed GAIA payload
 * (`00 1D <cmd> <sub> [params]`) and get unframed payloads back. That split matters because
 * the transports disagree only in framing — RFCOMM wraps every payload in a `FF <ver> <flags>
 * <len>` frame, BLE GATT writes the bare payload to a characteristic — while the GAIA command
 * words are identical on both. Keeping framing here is what lets one command table serve every
 * transport instead of every call site choosing a framing function.
 *
 * Implementations are not thread-safe for `open`/`close`; [send] is expected to be callable from
 * any thread and to fail quietly once the link is closed (the session reconnects).
 */
interface PodLink {

    /** Which transport this link speaks, for diagnostics and for the UI probe page. */
    val transport: PodTransport

    /** True between a successful [open] and the next [close]. */
    val isOpen: Boolean

    /**
     * Receives decoded payloads and link loss. Set before [open]; implementations must not
     * deliver payloads on the calling thread's stack when that would block the reader.
     */
    var listener: Listener?

    /** Blocking connect. Throws on failure so the caller can schedule a reconnect. */
    @Throws(Exception::class)
    fun open()

    /** Idempotent. Must not block on a wedged socket for long. */
    fun close()

    /** Writes one unframed GAIA payload. Returns false when it could not be written. */
    fun send(payload: ByteArray): Boolean

    /**
     * Writes already-framed bytes verbatim.
     *
     * Needed for the two cases that are not ordinary v4 payloads: the GAIA v1 API-version
     * negotiation frame (different protocol version) and the debug console, where the user
     * types a complete frame by hand. Implementations must not add framing here.
     */
    fun sendRaw(frame: ByteArray): Boolean

    interface Listener {
        /** A complete, decoded GAIA payload (`00 <vendor> <cmd> <sub> ...`). */
        fun onPayload(payload: ByteArray)

        /** The link died on its own (stream ended, read error, peer disconnect). */
        fun onClosed(reason: String)
    }
}

/** Hex logging hook so `pods/link` never has to depend on the app's log plumbing. */
fun interface LinkLogger {
    fun log(direction: Direction, bytes: ByteArray, note: String)

    enum class Direction { TX, RX }

    companion object {
        val NONE = LinkLogger { _, _, _ -> }
    }
}

/** Uppercase hex, matching the trace format the official app and `RfcommLog` use. */
fun ByteArray.toLinkHex(): String {
    if (isEmpty()) return ""
    val out = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xFF
        out.append(HEX_DIGITS[v ushr 4]).append(HEX_DIGITS[v and 0x0F])
    }
    return out.toString()
}

private const val HEX_DIGITS = "0123456789ABCDEF"
