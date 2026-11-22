package cythen.hyperdrop.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cythen.hyperdrop.R
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog

/**
 * Feature toggles that the firmware cannot keep enabled at the same time:
 * codecs LC3 / LDAC / LHDC are mutually exclusive (one codec at a time), and
 * LHDC additionally conflicts with dual-device connection.
 */
enum class MutualExclusionTarget { LHDC, LC3, LDAC, DUAL_CONNECTION }

/** Pairs of toggles that cannot both be on. */
private fun targetsConflict(a: MutualExclusionTarget, b: MutualExclusionTarget): Boolean {
    // All codecs are mutually exclusive.
    val codecs = setOf(MutualExclusionTarget.LHDC, MutualExclusionTarget.LC3, MutualExclusionTarget.LDAC)
    if (a in codecs && b in codecs) return true
    // LHDC also conflicts with dual-device connection.
    return (a == MutualExclusionTarget.LHDC && b == MutualExclusionTarget.DUAL_CONNECTION) ||
        (a == MutualExclusionTarget.DUAL_CONNECTION && b == MutualExclusionTarget.LHDC)
}

/** `first` = supported by the device, `second` = currently on. */
typealias ExclusionStates = Map<MutualExclusionTarget, Pair<Boolean, Boolean>>

/**
 * Decides whether a requested toggle can be sent straight away, or whether the
 * firmware conflict needs to be surfaced to the user first. Mirrors
 * miuixmoondrop's MutualExclusionState.
 */
class MutualExclusionState {
    var pendingTarget: MutualExclusionTarget? by mutableStateOf(null)
        private set

    /**
     * Returns true when [wanted] can be applied immediately. When a conflicting
     * feature is currently on, records the pending target and returns false so the
     * caller must not send the command yet.
     */
    fun request(states: ExclusionStates, target: MutualExclusionTarget, wanted: Boolean): Boolean {
        if (mutualExclusionDeferred(states, target, wanted)) {
            pendingTarget = target
            return false
        }
        return true
    }

    fun dismiss() {
        pendingTarget = null
    }
}

@Composable
fun rememberMutualExclusionState(): MutualExclusionState = remember { MutualExclusionState() }

/**
 * Generic decision helper (no Compose state, unit-testable): true when enabling
 * [target] must be deferred to a confirmation dialog. A conflict only counts when
 * both features are supported and the other one is currently on; a declared
 * unsupported feature is never treated as active.
 */
fun mutualExclusionDeferred(
    states: ExclusionStates,
    target: MutualExclusionTarget,
    wanted: Boolean,
): Boolean {
    if (!wanted || states[target]?.first != true) return false
    return states.any { (other, state) ->
        other != target && state.first && state.second && targetsConflict(target, other)
    }
}

/**
 * Backwards-compatible two-feature form used by existing callers/tests (LHDC ↔ dual).
 */
fun mutualExclusionDeferred(
    lhdcSupported: Boolean,
    dualSupported: Boolean,
    lhdcOn: Boolean,
    dualOn: Boolean,
    target: MutualExclusionTarget,
    wanted: Boolean,
): Boolean = mutualExclusionDeferred(
    mapOf(
        MutualExclusionTarget.LHDC to (lhdcSupported to lhdcOn),
        MutualExclusionTarget.DUAL_CONNECTION to (dualSupported to dualOn),
    ),
    target,
    wanted,
)

/**
 * Confirmation dialog shown when enabling a feature that conflicts with the one
 * currently active. Confirming hands the target back to the caller, which
 * switches the opposite feature off before enabling the requested one.
 */
@Composable
fun MutualExclusionDialog(
    state: MutualExclusionState,
    onApply: (MutualExclusionTarget) -> Unit,
) {
    val target = state.pendingTarget
    OverlayDialog(
        title = stringResource(
            when (target) {
                MutualExclusionTarget.LC3 -> R.string.conflict_lc3_lhdc_title
                MutualExclusionTarget.LDAC -> R.string.conflict_ldac_title
                else -> R.string.conflict_lhdc_dual_title
            }
        ),
        summary = when (target) {
            MutualExclusionTarget.LHDC -> stringResource(R.string.conflict_lhdc_dual_message_lhdc)
            MutualExclusionTarget.LC3 -> stringResource(R.string.conflict_lc3_lhdc_message_lc3)
            MutualExclusionTarget.LDAC -> stringResource(R.string.conflict_ldac_message)
            MutualExclusionTarget.DUAL_CONNECTION -> stringResource(R.string.conflict_lhdc_dual_message_dual)
            null -> ""
        },
        show = target != null,
        onDismissRequest = { state.dismiss() },
    ) {
        if (target != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                    color = Color.Unspecified,
                    modifier = Modifier
                        .clickable { state.dismiss() }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
                Text(
                    text = stringResource(R.string.conflict_switch),
                    color = Color(0xFF007AFF),
                    modifier = Modifier
                        .clickable {
                            state.dismiss()
                            onApply(target)
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}
