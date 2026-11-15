package cythen.hyperdrop.pods

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YieldPolicyTest {

    @Test
    fun noHeartbeatIsNotAlive() {
        assertFalse(YieldPolicy.officialAlive(100_000L, 0L))
    }

    @Test
    fun freshHeartbeatIsAlive() {
        assertTrue(YieldPolicy.officialAlive(100_000L, 100_000L - 5_000L))
    }

    @Test
    fun heartbeatJustBeforeTimeoutIsAlive() {
        assertTrue(YieldPolicy.officialAlive(100_000L, 100_000L - (YieldPolicy.LEASE_TIMEOUT_MS - 1)))
    }

    @Test
    fun heartbeatAtTimeoutIsNotAlive() {
        assertFalse(YieldPolicy.officialAlive(100_000L, 100_000L - YieldPolicy.LEASE_TIMEOUT_MS))
    }

    @Test
    fun staleHeartbeatIsNotAlive() {
        assertFalse(YieldPolicy.officialAlive(100_000L, 1_000L))
    }

    @Test
    fun futureHeartbeatFromRebootIsNotAlive() {
        assertFalse(YieldPolicy.officialAlive(1_000L, 100_000L))
    }

    @Test
    fun shouldYieldOnlyWhenAllThreeHold() {
        assertTrue(YieldPolicy.shouldYield(true, true, true))
        assertFalse(YieldPolicy.shouldYield(false, true, true))
        assertFalse(YieldPolicy.shouldYield(true, false, true))
        assertFalse(YieldPolicy.shouldYield(true, true, false))
    }

    @Test
    fun disabledIsAlwaysFalse() {
        for (alive in listOf(true, false)) {
            for (connected in listOf(true, false)) {
                assertFalse(YieldPolicy.shouldYield(false, alive, connected))
            }
        }
    }
}
