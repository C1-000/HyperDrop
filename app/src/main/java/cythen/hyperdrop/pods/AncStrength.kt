package cythen.hyperdrop.pods

/**
 * Helpers for remembering the last ANC strength flavour the user actually used.
 *
 * The HyperOS UI exposes a single "Noise cancellation" group button, but the
 * PUDDING profile has three flavours inside it (basic / adaptive / wind
 * reduction). When the user leaves the ANC group (Transparency or Off) and comes
 * back, the group button must restore the flavour that was last used instead of
 * always falling back to basic ANC.
 *
 * Persistence uses the stable broadcast "status" integer (see [ancModeFromUiStatus]
 * / [NoiseControlMode.toUiStatus]) so the memory survives process restarts.
 */
object AncStrength {
    const val PREF_KEY_LAST_ANC_STRENGTH = "last_anc_strength_status"

    /** Interpret a stored status, keeping only valid ANC flavours. */
    fun fromStatus(status: Int): NoiseControlMode {
        val mode = ancModeFromUiStatus(status)
        return if (mode.isNoiseCancellation()) mode else NoiseControlMode.NOISE_CANCELLATION
    }

    /** Status written for a fresh install. */
    fun defaultStatus(): Int = NoiseControlMode.NOISE_CANCELLATION.toUiStatus()

    /**
     * Resolve what the ANC group button should activate: the remembered flavour
     * when the current device supports it, otherwise basic ANC.
     */
    fun resolveTarget(last: NoiseControlMode, supported: List<NoiseControlMode>): NoiseControlMode {
        if (supported.isEmpty()) return NoiseControlMode.NOISE_CANCELLATION
        return if (last in supported) last else NoiseControlMode.NOISE_CANCELLATION
    }
}
