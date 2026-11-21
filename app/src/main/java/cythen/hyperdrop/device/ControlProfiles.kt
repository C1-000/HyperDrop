package cythen.hyperdrop.device

import cythen.hyperdrop.pods.NoiseControlMode

/**
 * The shared control-profile catalogue.
 *
 * There are exactly two entries today, on purpose:
 *
 *  * [pudding] — the only wire vocabulary confirmed on real hardware. MOONDROP PUDDING and
 *    MOONDROP MIRAGE (海市蜃楼) are the same electronics, so they share it.
 *  * [probeFallback] — the generic GAIA vocabulary every other model starts from. It carries
 *    the canonical GAIA command bytes and the standard "OFF/NC/TP/SMART" wire values, and then
 *    relies on `GET_SUPPORTED_FEATURES` at runtime to decide which controls are actually shown.
 *
 * Splitting per model is what produced the old 43 hand-tuned `inferredCaps` variants: fourteen
 * different guesses at gain reversal, spatial audio and LHDC, none of them backed by a capture.
 * Those are gone. A model earns its own control profile only when someone has verified one.
 */
object ControlProfiles {

    /**
     * MOONDROP PUDDING / MD-TWS-056, and by extension MIRAGE.
     *
     * ANC_V2 with the five wire values observed on real hardware (`ancSetMap` becomes
     * `[-1, 0, 1, 2, 4, 3, -1, -1, -1]`, indexed by UI status). Gain is reversed: the UI's
     * Low/Medium/High are wire 2/1/0.
     *
     * [ControlProfile.verifiedTable] points at [PuddingCommandTable] because the real-hardware
     * behaviour includes the PUDDING-specific ANC cycle order (2,4,5,3,1) and the voice / LHDC /
     * double-connection vocabulary that the generic XML-ish table does not model. Keeping the
     * mapping data here and the extra commands there is the whole point: one control profile,
     * one place to fix a wire value.
     */
    val pudding = ControlProfile(
        id = "pudding",
        ancPath = cythen.hyperdrop.device.protocol.AncPathKind.ANC_V2,
        ancPairs = listOf(
            NoiseControlMode.OFF to 0,
            NoiseControlMode.NOISE_CANCELLATION to 1,
            NoiseControlMode.TRANSPARENCY to 2,
            NoiseControlMode.ADAPTIVE to 4,
            NoiseControlMode.NOISE_CANCELLATION_SMART to 3,
        ),
        gainMap = intArrayOf(2, 1, 0),
        note = "Verified on real hardware (PUDDING / MIRAGE, GAIA v4 over RFCOMM)",
        verifiedOnHardware = true,
        verifiedTable = cythen.hyperdrop.device.protocol.PuddingCommandTable,
    )

    /**
     * Generic GAIA vocabulary for a model nobody has captured yet.
     *
     * AUDIO_CURATION rather than ANC_V2 because that is the older semantic path, which is the
     * safer first guess: a device that turns out to speak ANC_V2 is moved over at runtime by
     * `DeviceRegistry.applyObservedFeatures` once it reports feature `0x20`.
     *
     * Gain carries the identity assumption (UI Low/Medium/High -> wire 0/1/2) rather than an
     * empty map, because an empty map would be unrecoverable: `applyObservedFeatures` opens the
     * gain control only when a mapping exists, so an empty one makes a genuine `DAC_GAIN`
     * report a no-op and no device could ever unlock it. The mapping being a guess is harmless
     * because it is not what makes the control visible — `DeclaredFeatures.gain` is false here,
     * so nothing is shown until the device reports the feature.
     */
    val probeFallback = ControlProfile(
        id = "probe_fallback",
        ancPath = cythen.hyperdrop.device.protocol.AncPathKind.AUDIO_CURATION,
        ancPairs = listOf(
            NoiseControlMode.OFF to 1,
            NoiseControlMode.NOISE_CANCELLATION to 2,
            NoiseControlMode.TRANSPARENCY to 4,
            NoiseControlMode.NOISE_CANCELLATION_SMART to 3,
        ),
        gainMap = intArrayOf(0, 1, 2),
        note = "Generic GAIA constants — capabilities come from runtime probing",
        verifiedOnHardware = false,
    )

    /** Every control profile, for the adaptation page and for diagnostics. */
    val all: List<ControlProfile> = listOf(pudding, probeFallback)
}
