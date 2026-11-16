package cythen.hyperdrop.pods

import org.junit.Assert.assertEquals
import org.junit.Test

class AncStrengthTest {
    @Test
    fun fromStatusKeepsOnlyAncFlavours() {
        assertEquals(NoiseControlMode.NOISE_CANCELLATION, AncStrength.fromStatus(2))
        assertEquals(NoiseControlMode.ADAPTIVE, AncStrength.fromStatus(4))
        assertEquals(NoiseControlMode.NOISE_CANCELLATION_SMART, AncStrength.fromStatus(5))
    }

    @Test
    fun fromStatusFallsBackToBasicForNonAncModes() {
        assertEquals(NoiseControlMode.NOISE_CANCELLATION, AncStrength.fromStatus(1)) // off
        assertEquals(NoiseControlMode.NOISE_CANCELLATION, AncStrength.fromStatus(3)) // transparency
        assertEquals(NoiseControlMode.NOISE_CANCELLATION, AncStrength.fromStatus(6)) // legacy light
        assertEquals(NoiseControlMode.NOISE_CANCELLATION, AncStrength.fromStatus(99))
    }

    @Test
    fun defaultStatusIsBasicAnc() {
        assertEquals(NoiseControlMode.NOISE_CANCELLATION.toUiStatus(), AncStrength.defaultStatus())
    }

    @Test
    fun resolveTargetKeepsLastWhenSupported() {
        val supported = listOf(
            NoiseControlMode.NOISE_CANCELLATION,
            NoiseControlMode.ADAPTIVE,
            NoiseControlMode.NOISE_CANCELLATION_SMART,
        )
        assertEquals(
            NoiseControlMode.ADAPTIVE,
            AncStrength.resolveTarget(NoiseControlMode.ADAPTIVE, supported),
        )
        assertEquals(
            NoiseControlMode.NOISE_CANCELLATION_SMART,
            AncStrength.resolveTarget(NoiseControlMode.NOISE_CANCELLATION_SMART, supported),
        )
    }

    @Test
    fun resolveTargetFallsBackWhenUnsupportedOrEmpty() {
        val supported = listOf(NoiseControlMode.NOISE_CANCELLATION, NoiseControlMode.ADAPTIVE)
        assertEquals(
            NoiseControlMode.NOISE_CANCELLATION,
            AncStrength.resolveTarget(NoiseControlMode.NOISE_CANCELLATION_SMART, supported),
        )
        assertEquals(
            NoiseControlMode.NOISE_CANCELLATION,
            AncStrength.resolveTarget(NoiseControlMode.ADAPTIVE, emptyList()),
        )
    }
}
