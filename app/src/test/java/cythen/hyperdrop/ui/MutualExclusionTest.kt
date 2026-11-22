package cythen.hyperdrop.ui

import cythen.hyperdrop.ui.components.MutualExclusionTarget
import cythen.hyperdrop.ui.components.mutualExclusionDeferred
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MutualExclusionTest {

    @Test
    fun turningOffNeverDefers() {
        assertFalse(
            mutualExclusionDeferred(true, true, lhdcOn = false, dualOn = true,
                MutualExclusionTarget.LHDC, wanted = false)
        )
    }

    @Test
    fun unsupportedCapabilityNeverDefers() {
        assertFalse(
            mutualExclusionDeferred(true, false, lhdcOn = false, dualOn = true,
                MutualExclusionTarget.LHDC, wanted = true)
        )
    }

    @Test
    fun enablingLhdcWhileDualOnDefers() {
        assertTrue(
            mutualExclusionDeferred(true, true, lhdcOn = false, dualOn = true,
                MutualExclusionTarget.LHDC, wanted = true)
        )
    }

    @Test
    fun enablingDualWhileLhdcOnDefers() {
        assertTrue(
            mutualExclusionDeferred(true, true, lhdcOn = true, dualOn = false,
                MutualExclusionTarget.DUAL_CONNECTION, wanted = true)
        )
    }

    @Test
    fun enablingWithOtherOffIsImmediate() {
        assertFalse(
            mutualExclusionDeferred(true, true, lhdcOn = false, dualOn = false,
                MutualExclusionTarget.LHDC, wanted = true)
        )
        assertFalse(
            mutualExclusionDeferred(true, true, lhdcOn = false, dualOn = false,
                MutualExclusionTarget.DUAL_CONNECTION, wanted = true)
        )
    }

    @Test
    fun `LC3 and LHDC defer each other but not dual connection`() {
        val states = mapOf(
            MutualExclusionTarget.LHDC to (true to true),
            MutualExclusionTarget.LC3 to (true to false),
            MutualExclusionTarget.DUAL_CONNECTION to (true to false),
        )
        assertTrue(mutualExclusionDeferred(states, MutualExclusionTarget.LC3, wanted = true))
        // LC3 does not conflict with dual connection (with LHDC off).
        val statesDualOn = states + mapOf(
            MutualExclusionTarget.LHDC to (true to false),
            MutualExclusionTarget.DUAL_CONNECTION to (true to true),
        )
        assertFalse(mutualExclusionDeferred(statesDualOn, MutualExclusionTarget.LC3, wanted = true))
        // Enabling LC3 when LHDC is off is immediate.
        val statesLhdcOff = states + (MutualExclusionTarget.LHDC to (true to false))
        assertFalse(mutualExclusionDeferred(statesLhdcOff, MutualExclusionTarget.LC3, wanted = true))
    }
}
