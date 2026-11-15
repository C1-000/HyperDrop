package cythen.hyperdrop.device

import cythen.hyperdrop.pods.NoiseControlMode

/**
 * One selectable ANC strength tab, declared by a [DeviceProfile].
 *
 * [labelRes] is a string-resource id so the UI renders whatever modes the active
 * profile exposes instead of hardcoding one model's mode names.
 */
data class AncModeOption(
    val mode: NoiseControlMode,
    val labelRes: Int,
)
