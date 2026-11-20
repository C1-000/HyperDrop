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

/** The two codec features that the firmware cannot keep enabled at the same time. */
enum class MutualExclusionTarget { LHDC, DUAL_CONNECTION }

/**
 * Decides whether a requested toggle can be sent straight away, or whether the
 * firmware conflict needs to be surfaced to the user first. Mirrors
 * miuixmoondrop's MutualExclusionState.
 */
class MutualExclusionState {
    var pendingTarget: MutualExclusionTarget? by mutableStateOf(null)
        private set

    /**
     * Returns true when [wanted] can be applied immediately.
     * When the opposite feature is currently on, records the pending target and
     * returns false so the caller must not send the command yet.
     */
    fun request(
        lhdcSupported: Boolean,
        dualSupported: Boolean,
        lhdcOn: Boolean,
        dualOn: Boolean,
        target: MutualExclusionTarget,
        wanted: Boolean,
    ): Boolean {
        if (mutualExclusionDeferred(lhdcSupported, dualSupported, lhdcOn, dualOn, target, wanted)) {
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
 * Pure decision helper (no Compose state) so the mutual-exclusion rule can be
 * unit-tested: returns true when enabling [target] must be deferred to a
 * confirmation dialog instead of being sent straight away.
 */
fun mutualExclusionDeferred(
    lhdcSupported: Boolean,
    dualSupported: Boolean,
    lhdcOn: Boolean,
    dualOn: Boolean,
    target: MutualExclusionTarget,
    wanted: Boolean,
): Boolean {
    if (!wanted || !lhdcSupported || !dualSupported) return false
    return if (target == MutualExclusionTarget.LHDC) dualOn else lhdcOn
}

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
        title = stringResource(R.string.conflict_lhdc_dual_title),
        summary = when (target) {
            MutualExclusionTarget.LHDC -> stringResource(R.string.conflict_lhdc_dual_message_lhdc)
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
