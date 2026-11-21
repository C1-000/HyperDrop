package cythen.hyperdrop.device.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Frame removal for the transport hop.
 *
 * GAIA-over-RFCOMM wraps the packet in `FF <ver> <flags> <len>`; GAIA-over-BLE writes the
 * packet bare. Anything that builds a frame for one transport and hands it to the other has to
 * cross this boundary, so [GaiaTransport.stripFrame] must be exact and safe to call on input
 * that is already bare.
 */
class GaiaTransportFrameTest {

    @Test
    fun `strips the four byte short frame header`() {
        val payload = byteArrayOf(0x00, 0x0A, 0x03, 0x00)
        val framed = GaiaTransport.v1(payload)
        assertEquals(0xFF, framed[0].toInt() and 0xFF)
        assertArrayEquals(payload, GaiaTransport.stripFrame(framed))
    }

    @Test
    fun `leaves an already bare packet untouched`() {
        val payload = byteArrayOf(0x00, 0x1D, 0x40, 0x03)
        assertArrayEquals(payload, GaiaTransport.stripFrame(payload))
    }

    @Test
    fun `leaves a buffer too short to be framed untouched`() {
        val tiny = byteArrayOf(0x01, 0x02, 0x03)
        assertArrayEquals(tiny, GaiaTransport.stripFrame(tiny))
    }

    @Test
    fun `does not treat a non SOF first byte as framing`() {
        // A GAIA packet starts with the vendor id, and neither QTIL vendor (0x0A, 0x1D) is 0xFF,
        // so a bare packet can never be mistaken for a frame.
        val bare = byteArrayOf(0x00, 0x1D, 0x1B, 0x01, 0x01, 0x64)
        assertArrayEquals(bare, GaiaTransport.stripFrame(bare))
    }

    @Test
    fun `handles an extended length header`() {
        val payload = byteArrayOf(0x00, 0x1D, 0x00, 0x01)
        val extended = byteArrayOf(
            0xFF.toByte(),
            GaiaTransport.PROTOCOL_V4.toByte(),
            GaiaTransport.FLAG_LENGTH_EXTENSION.toByte(),
            0x00,
            0x00,
        ) + payload
        assertArrayEquals(payload, GaiaTransport.stripFrame(extended))
    }

    @Test
    fun `strip then reframe round trips`() {
        val payload = byteArrayOf(0x00, 0x1D, 0x1A, 0x01, 0x01, 0x02)
        val framed = GaiaTransport.v4(payload)
        assertArrayEquals(framed, GaiaTransport.v4(GaiaTransport.stripFrame(framed)))
    }
}
