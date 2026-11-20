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
}
