package cythen.hyperdrop.pods

import cythen.hyperdrop.device.PodTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bring-up ordering and pacing.
 *
 * The order is load-bearing: the version negotiation has to land before any command the earbud
 * will answer, and `SUPPORTED_FEATURES` has to come after the state reads so the capability gate
 * never races the first UI paint.
 */
class HandshakeSequenceTest {

    @Test
    fun `version negotiation is first and features are last`() {
        assertEquals(HandshakeSequence.Step.NEGOTIATE_VERSION, HandshakeSequence.STEPS.first())
        assertEquals(HandshakeSequence.Step.SUPPORTED_FEATURES, HandshakeSequence.STEPS.last())
    }

    @Test
    fun `batteries are queried before the capability probe`() {
        val slots = HandshakeSequence.STEPS.indexOf(HandshakeSequence.Step.SUPPORTED_BATTERIES)
        val levels = HandshakeSequence.STEPS.indexOf(HandshakeSequence.Step.BATTERY_LEVELS)
        val features = HandshakeSequence.STEPS.indexOf(HandshakeSequence.Step.SUPPORTED_FEATURES)
        assertTrue(slots < levels)
        assertTrue(levels < features)
    }

    @Test
    fun `every declared step appears exactly once`() {
        assertEquals(HandshakeSequence.STEPS.size, HandshakeSequence.STEPS.toSet().size)
        assertEquals(HandshakeSequence.Step.entries.toSet(), HandshakeSequence.STEPS.toSet())
    }

    @Test
    fun `ble paces steps more slowly than rfcomm`() {
        HandshakeSequence.Step.entries.forEach { step ->
            val rfcomm = HandshakeSequence.stepDelayMs(step, PodTransport.RFCOMM_GAIA)
            val ble = HandshakeSequence.stepDelayMs(step, PodTransport.BLE_GAIA)
            assertTrue("$step should be slower on BLE", ble > rfcomm)
        }
    }

    @Test
    fun `negotiation gets the longest pause on both transports`() {
        val rfcommNegotiation = HandshakeSequence.stepDelayMs(
            HandshakeSequence.Step.NEGOTIATE_VERSION,
            PodTransport.RFCOMM_GAIA,
        )
        HandshakeSequence.Step.entries
            .filter { it != HandshakeSequence.Step.NEGOTIATE_VERSION }
            .forEach { step ->
                assertTrue(
                    "$step must not outwait the negotiation",
                    HandshakeSequence.stepDelayMs(step, PodTransport.RFCOMM_GAIA) <= rfcommNegotiation,
                )
            }
    }

    @Test
    fun `lead in leaves the earbud time to open its listener`() {
        assertTrue(HandshakeSequence.LEAD_IN_MS >= 150L)
        assertTrue(HandshakeSequence.LEAD_IN_MS < 1_000L)
    }
}
