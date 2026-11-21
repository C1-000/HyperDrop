package cythen.hyperdrop.device

import cythen.hyperdrop.device.protocol.AncPathKind
import cythen.hyperdrop.device.protocol.GaiaCommandTable
import cythen.hyperdrop.device.protocol.GenericGaiaCommandTable
import cythen.hyperdrop.pods.NoiseControlMode

/**
 * One wire-level command vocabulary, shared by every model that speaks it.
 *
 * This is deliberately separate from [DeviceProfile]. A model profile answers "which device is
 * this and what shape is it"; a control profile answers "which bytes drive its ANC and gain".
 * The split exists because several models are the *same electronics under a different name* —
 * MOONDROP MIRAGE (海市蜃楼) is a PUDDING variant and shares [ControlProfiles.pudding] verbatim.
 * Before this split, that fact could only be expressed by copying a whole capability block, so
 * every change had to be made twice and could drift.
 *
 * A model gets its own control profile only when there is evidence for one. Guessing a mapping
 * per model is what produced the original 43 hand-tuned "inferred" variants; those are now all
 * folded back into [ControlProfiles.probeFallback], which carries the generic GAIA constants
 * and defers to runtime probing.
 */
data class ControlProfile(
    /** Stable id, referenced from [DeviceProfile.control] and shown in the adaptation UI. */
    val id: String,

    /**
     * Which GAIA feature carries ANC here. This is a property of the firmware's GAIA build,
     * not of the product name: ANC_V2 is the modern numeric-mode feature, AUDIO_CURATION the
     * older semantic one.
     */
    val ancPath: AncPathKind = AncPathKind.AUDIO_CURATION,

    /**
     * ANC modes this vocabulary can drive, paired with the wire value to SET them.
     *
     * Order is the SET order the UI cycles through, not necessarily the GET order: for
     * [AncPathKind.AUDIO_CURATION] the device reports a 0-based index while SET takes a command
     * byte, which is why [ancGetMap] is derived by position rather than by value.
     */
    val ancPairs: List<Pair<NoiseControlMode, Int>> = emptyList(),

    /**
     * UI gain index -> wire value. Empty means this vocabulary has no gain control at all.
     *
     * This is the mapping, not the visibility switch: whether the gain control is *shown* is
     * decided by [DeviceModels.DeclaredFeatures.gain] plus the device reporting `DAC_GAIN`. A
     * vocabulary may therefore carry a mapping it has not yet been able to confirm — see
     * [ControlProfiles.probeFallback], which assumes the identity order and says so.
     */
    val gainMap: IntArray = intArrayOf(),

    /** Where this vocabulary came from — surfaced in the adaptation page. */
    val note: String = "",

    /** True when this profile is safe to write to even with no probing evidence. */
    val verifiedOnHardware: Boolean = false,

    /**
     * Hand-written table used instead of the derived one, only when a model verified on real
     * hardware carries behaviour the generic table cannot express — today that is PUDDING's
     * ANC cycle order and its extra voice/LHDC/double-connection vocabulary.
     *
     * Leave null otherwise: the derived table is the whole point of this type, and an override
     * re-introduces the risk that the table and the UI disagree about a mapping.
     */
    val verifiedTable: GaiaCommandTable? = null,
) {
    /** UI-status -> wire value, indexed by the 1-based UI status constants. */
    val ancSetMap: IntArray by lazy {
        val map = IntArray(9) { -1 }
        ancPairs.forEach { (mode, wire) ->
            val ui = DeviceModels.uiStatus(mode)
            if (ui in 1..8) map[ui] = wire
        }
        map
    }

    /**
     * Wire value -> UI status.
     *
     * For AUDIO_CURATION the device answers with a 0-based index into [ancPairs], so the map is
     * keyed by position; every other path answers with the same byte it was SET with.
     */
    val ancGetMap: IntArray by lazy {
        val map = IntArray(16) { -1 }
        ancPairs.forEachIndexed { index, (mode, wire) ->
            val ui = DeviceModels.uiStatus(mode)
            if (ui == -1) return@forEachIndexed
            val key = if (ancPath == AncPathKind.AUDIO_CURATION) index else wire
            if (key in 0..15) map[key] = ui
        }
        map
    }

    /** True when this vocabulary can drive ANC at all. */
    val hasAnc: Boolean get() = ancPairs.isNotEmpty()

    /** True when this vocabulary can drive gain. */
    val hasGain: Boolean get() = gainMap.isNotEmpty()

    /**
     * The command table for this vocabulary.
     *
     * Derived, never authored separately, so the table can never be handed a mapping set that
     * differs from the one the UI reads. The single exception is [verifiedTable], used by models
     * whose real-hardware behaviour needs the extra commands of a hand-written table.
     */
    val table: GaiaCommandTable by lazy {
        verifiedTable ?: GenericGaiaCommandTable(
            ancPathKind = ancPath,
            ancSetMap = ancSetMap,
            ancGetMap = ancGetMap,
            // IntArray has no ifEmpty() in the standard library, so spell it out.
            gainMap = if (gainMap.isEmpty()) intArrayOf(0, 1, 2) else gainMap,
        )
    }

    // IntArray has identity equality, so compare on the identifying fields instead.
    override fun equals(other: Any?): Boolean =
        this === other || (other is ControlProfile && other.id == id)

    override fun hashCode(): Int = id.hashCode()
}
