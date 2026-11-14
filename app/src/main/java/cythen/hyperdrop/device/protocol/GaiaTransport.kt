package cythen.hyperdrop.device.protocol

/**
 * Generic GAIA-over-RFCOMM framing and stream decoding.
 *
 * The MOONDROP app's bundled Qualcomm GAIA client uses the RFCOMM v4 frame layout:
 *   FF <protocolVersion> <flags> <payloadLength> <payload...>
 * for short packets without checksum/length-extension flags.
 *
 * This layer knows nothing about any particular model: which bytes to send and how to
 * interpret the command payloads is the job of a [CommandTable].
 */
object GaiaTransport {
    const val SOF: Int = 0xFF
    const val PROTOCOL_V1: Int = 0x01
    const val PROTOCOL_V4: Int = 0x04
    const val FLAG_CHECKSUM: Int = 0x01
    const val FLAG_LENGTH_EXTENSION: Int = 0x02

    /** Size of the GAIA command header (`vendor` + `command word`), which the length omits. */
    const val COMMAND_HEADER_SIZE: Int = 4

    // Standard Bluetooth Serial Port Profile UUID used by the GAIA transport.
    const val SPP_UUID = "00001101-0000-1000-8000-00805f9b34fb"

    /**
     * Wraps a payload (`00 1D <cmd> <sub> [params...]`) in an RFCOMM frame.
     *
     * The length field must count only the parameters that follow the command header, exactly
     * like the earbud does on the way back; the official app sends `FF 04 00 02 00 1D 1A 01 01 02`
     * for a two-parameter battery query, not `06`. Declaring the whole payload still works
     * because the earbud ignores a length that overruns the frame, but it breaks the symmetry
     * [StreamDecoder] relies on and makes the wire trace disagree with the reference captures.
     */
    fun frame(protocolVersion: Int, payload: ByteArray): ByteArray {
        require(payload.size >= COMMAND_HEADER_SIZE) { "GAIA payload lacks a command header" }
        val parameterLength = payload.size - COMMAND_HEADER_SIZE
        require(parameterLength <= 0xFF) { "GAIA short frame payload too large" }
        return byteArrayOf(
            SOF.toByte(),
            protocolVersion.toByte(),
            0x00, // no checksum, no length extension
            parameterLength.toByte(),
        ) + payload
    }

    fun v1(payload: ByteArray): ByteArray = frame(PROTOCOL_V1, payload)
    fun v4(payload: ByteArray): ByteArray = frame(PROTOCOL_V4, payload)

    /**
     * Stateful RFCOMM stream decoder. InputStream reads may split or merge GAIA frames.
     * Traffic observed so far uses short, no-checksum frames; checksum and extended
     * length are supported here as well for robustness.
     *
     * The length field counts only the parameters that follow the 4-byte command header,
     * so the parameters a frame carries are always `4 + payloadLength` bytes long. Treating
     * `payloadLength` as the whole parameter block truncates every response by the 4 command
     * bytes: the battery response `00 1D 1B 01 (01 LL)(02 RR)(03 CC)` then decodes as a
     * single `[01, LL]` pair, which is why only the left earbud's level ever reached the UI.
     */
    class StreamDecoder {
        private val pending = ArrayList<Byte>()

        fun reset() = pending.clear()

        fun feed(chunk: ByteArray): List<ByteArray> {
            chunk.forEach { pending.add(it) }
            val result = mutableListOf<ByteArray>()

            while (true) {
                while (pending.isNotEmpty() && (pending[0].toInt() and 0xFF) != SOF) {
                    pending.removeAt(0)
                }
                if (pending.size < 4) break

                val flags = pending[2].toInt() and 0xFF
                val extended = flags and FLAG_LENGTH_EXTENSION != 0
                val headerSize = if (extended) 5 else 4
                if (pending.size < headerSize) break

                val payloadLength = if (extended) {
                    ((pending[3].toInt() and 0xFF) shl 8) or (pending[4].toInt() and 0xFF)
                } else {
                    pending[3].toInt() and 0xFF
                }
                val checksumSize = if (flags and FLAG_CHECKSUM != 0) 1 else 0
                // The declared length covers the command header + parameters, but not the
                // framing header itself, so the command header has to be added back here.
                val frameSize = headerSize + COMMAND_HEADER_SIZE + payloadLength + checksumSize
                if (payloadLength < 0 || frameSize > 65540) {
                    pending.removeAt(0)
                    continue
                }
                if (pending.size < frameSize) break

                val payload = ByteArray(COMMAND_HEADER_SIZE + payloadLength) {
                    pending[headerSize + it]
                }
                result += payload
                // Anything past frameSize is not covered by the declared length: the earbud
                // occasionally appends a stray byte (0x40 / 0x86) that must not be parsed.
                repeat(frameSize) { pending.removeAt(0) }
            }
            return result
        }
    }
}
