package cythen.hyperdrop.pods

import cythen.hyperdrop.device.PodTransport

/**
 * The GAIA bring-up sequence sent after a transport opens.
 *
 * Kept as data rather than inline `delay()` calls so both transports share one definition and
 * the ordering can be asserted in a plain JVM test. The sequence mirrors the official app:
 * negotiate with a v1 API-version request first, then read the state the UI needs, then ask the
 * earbud what it implements.
 *
 * Delays are per-step gaps: the earbud answers serially and drops requests that arrive while it
 * is still building the previous response. BLE needs a slightly larger gap than RFCOMM because
 * every request is a separate GATT write, so [stepDelayMs] scales the RFCOMM baseline.
 */
object HandshakeSequence {

    /** One query in the bring-up, in send order. */
    enum class Step {
        /** GAIA v1/v2 API-version negotiation; must be first. */
        NEGOTIATE_VERSION,

        /** Current ANC mode on whichever path the profile resolved. */
        NOISE_MODE,

        /** DAC gain level. */
        GAIN,

        /** LED state. */
        LED,

        /** Which battery slots the earbud exposes (left / right / case). */
        SUPPORTED_BATTERIES,

        /** One level query per exposed slot. */
        BATTERY_LEVELS,

        /** `GET_SUPPORTED_FEATURES`; the reply drives capability gating. */
        SUPPORTED_FEATURES,
    }

    val STEPS: List<Step> = listOf(
        Step.NEGOTIATE_VERSION,
        Step.NOISE_MODE,
        Step.GAIN,
        Step.LED,
        Step.SUPPORTED_BATTERIES,
        Step.BATTERY_LEVELS,
        Step.SUPPORTED_FEATURES,
    )

    /** Gap before the first frame, so the earbud is listening once its link reports open. */
    const val LEAD_IN_MS: Long = 180L

    /**
     * Gap after each step.
     *
     * The official app waits longer between the "cheap" reads than between the battery pair,
     * which is reproduced here: a battery response is two frames for a TWS set (levels then
     * case) and lands later than the single-frame reads.
     */
    private const val GAP_DEFAULT_MS: Long = 120L
    private const val GAP_BEFORE_BATTERIES_MS: Long = 100L
    private const val GAP_BEFORE_FEATURES_MS: Long = 80L

    /** BLE writes are one GATT round trip each, so allow more room per step. */
    private const val BLE_SCALE = 1.3

    fun stepDelayMs(step: Step, transport: PodTransport): Long {
        val base = when (step) {
            Step.NEGOTIATE_VERSION -> 180L
            Step.NOISE_MODE -> 100L
            Step.GAIN -> 100L
            Step.LED -> 100L
            Step.SUPPORTED_BATTERIES -> GAP_BEFORE_BATTERIES_MS
            Step.BATTERY_LEVELS -> GAP_BEFORE_FEATURES_MS
            Step.SUPPORTED_FEATURES -> GAP_DEFAULT_MS
        }
        return if (transport == PodTransport.BLE_GAIA) (base * BLE_SCALE).toLong() else base
    }
}
