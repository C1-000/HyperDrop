package cythen.hyperdrop.device

import cythen.hyperdrop.device.protocol.AncPathKind
import cythen.hyperdrop.device.protocol.GaiaCommandTable
import cythen.hyperdrop.device.protocol.GenericGaiaCommandTable
import cythen.hyperdrop.device.protocol.NoOpGaiaCommandTable
import cythen.hyperdrop.device.protocol.PuddingCommandTable

/**
 * Reusable command-mapping archive ("控制档案").
 *
 * A control profile owns the byte-level GAIA vocabulary AND the wire value maps: which
 * commands exist and how ANC / gain values encode on the wire. Wire maps live HERE, not
 * in [CapabilityProfile] / [DeviceCapabilities] — that decoupling is what lets a model
 * change ANC path without duplicating its whole capability declaration. Models with an
 * identical mapping share one instance; [key] is the stable identity for that sharing.
 *
 * [probeOnConnect] marks the layer-2 fallback: issue GET_SUPPORTED_FEATURES (the
 * getDeviceFunc-style discovery) so observation can open capabilities before the session
 * degrades to the conservative command set carried by [table].
 */
data class ControlProfile(
    val key: String,
    val table: GaiaCommandTable,
    val probeOnConnect: Boolean = false,
    /** Declared ANC GAIA feature path (also recorded for observability/tests). */
    val ancPath: AncPathKind = AncPathKind.ANC_V2,
    /** ANC set map indexed by UI status 1..8 → wire byte (-1 = unsupported). */
    val ancSetMap: IntArray = intArrayOf(),
    /** ANC get map indexed by reported wire value → UI status (-1 = unknown). */
    val ancGetMap: IntArray = intArrayOf(),
    /** Gain map indexed by UI gain index → wire byte. */
    val gainMap: IntArray = intArrayOf(0, 1, 2),
) {
    companion object {
        /** Dedicated, real-hardware-verified mapping (PUDDING and its MIRAGE variant). */
        val PUDDING = ControlProfile(
            key = "pudding",
            table = PuddingCommandTable,
            ancPath = AncPathKind.ANC_V2,
            ancSetMap = intArrayOf(-1, 0, 1, 2, 4, 3, -1, -1, -1),
            ancGetMap = intArrayOf(1, 2, 3, 5, 4, -1),
            gainMap = intArrayOf(2, 1, 0),
        )

        /** Build a shared generic mapping from wire descriptors. */
        fun generic(
            key: String,
            path: AncPathKind,
            ancSetMap: IntArray,
            ancGetMap: IntArray = ancSetMap,
            gainMap: IntArray = intArrayOf(0, 1, 2),
            probeOnConnect: Boolean = false,
            batteryIds: IntArray = intArrayOf(1, 2),
        ) = ControlProfile(
            key,
            GenericGaiaCommandTable(path, ancSetMap, ancGetMap, gainMap, batteryIds),
            probeOnConnect,
            path,
            ancSetMap,
            ancGetMap,
            gainMap,
        )

        /**
         * MOONDROP Pill family (open-fit single-ear OWS). Protocol facts from HyperPILL:
         * no ANC, identity gain map [0,1,2] (Pill's DAC_GAIN SET sends the UI index verbatim),
         * voice prompts + volume, dual-device connection, and battery id 0 (single cell) —
         * query all four cells and map id 0 into `left` so PodStatus's SINGLE_DEVICE branch
         * can render it.
         */
        val PILL = ControlProfile(
            key = "pill",
            table = GenericGaiaCommandTable(
                ancPathKind = AncPathKind.AUDIO_CURATION,
                ancSetMap = intArrayOf(),
                ancGetMap = intArrayOf(),
                gainMap = intArrayOf(0, 1, 2),
                batteryIds = intArrayOf(0, 1, 2, 3),
            ),
            ancPath = AncPathKind.AUDIO_CURATION,
            gainMap = intArrayOf(0, 1, 2),
        )

        /**
         * Layer-2 fallback for a completely unrecognised device: probe capabilities first,
         * then operate with the most conservative generic command set (AudioCuration, no
         * ANC cycle → session degrades silently until a model archive is added).
         */
        val PROBE_FALLBACK = ControlProfile(
            key = "probe_fallback",
            table = GenericGaiaCommandTable(
                ancPathKind = AncPathKind.AUDIO_CURATION,
                ancSetMap = intArrayOf(),
                ancGetMap = intArrayOf(),
                gainMap = intArrayOf(0, 1, 2),
            ),
            probeOnConnect = true,
            ancPath = AncPathKind.AUDIO_CURATION,
        )

        /** A non-MOONDROP / standard device: no GAIA commands at all. */
        val NO_OP = ControlProfile("no_op", NoOpGaiaCommandTable)
    }
}
