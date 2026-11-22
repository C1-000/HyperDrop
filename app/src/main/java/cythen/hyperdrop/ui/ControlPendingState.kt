package cythen.hyperdrop.ui

import androidx.compose.runtime.mutableStateOf

/**
 * Per-control "pending" flags. A control is temporarily disabled (greyed out)
 * right after the user operates it, until the device reports its real state
 * back (echo broadcast) or a 5s timeout elapses, whichever comes first.
 */
class ControlPendingState {
    val anc = mutableStateOf(false)
    val smartAncLevel = mutableStateOf(false)
    val transparency = mutableStateOf(false)
    val spatial = mutableStateOf(false)
    val eq = mutableStateOf(false)
    val gain = mutableStateOf(false)
    val led = mutableStateOf(false)
    val promptTone = mutableStateOf(false)
    val headTracking = mutableStateOf(false)
    val lhdc = mutableStateOf(false)
    val lc3 = mutableStateOf(false)
    val ldac = mutableStateOf(false)
    val dualConnection = mutableStateOf(false)

    fun clearAll() {
        anc.value = false
        smartAncLevel.value = false
        transparency.value = false
        spatial.value = false
        eq.value = false
        gain.value = false
        led.value = false
        promptTone.value = false
        headTracking.value = false
        lhdc.value = false
        lc3.value = false
        ldac.value = false
        dualConnection.value = false
    }
}
