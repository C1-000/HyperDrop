package cythen.hyperdrop.device

import cythen.hyperdrop.pods.AncImplementation

/**
 * How a device reports battery level.
 *
 * This is a *display* concern only: the GAIA battery query is unchanged. UI uses it to
 * decide how many battery columns to render (three for TWS, one for a headset/one-piece
 * device, none for a speaker without a battery readout).
 */
enum class BatteryForm {
    /** Left / right / case — the classic TWS tri-column layout. */
    TWS_THREE_WAY,

    /** One whole-device battery, rendered as a single column. */
    SINGLE_DEVICE,

    /** No battery readout at all — hide the battery card. */
    NONE,
}

/**
 * Factory-declared capability ceiling for a family of devices.
 *
 * A capability profile is reusable and independent of both model identity and command
 * mapping: several models may share one. It is the *declared* ceiling; runtime observation
 * (GET_SUPPORTED_FEATURES) may add capabilities that were not declared, but never removes
 * a declared one (see [DeviceRegistry.resolveCapabilities]).
 *
 * ANC / gain wire maps deliberately do NOT live here — they belong to the control profile.
 */
data class CapabilityProfile(
    val key: String,
    val adaptiveSupported: Boolean = false,
    val spatialAudioSupported: Boolean = false,
    val spatialSoundSwitchSupported: Boolean = false,
    val ancImplementation: AncImplementation = AncImplementation.STANDARD,
    /** Selectable ANC strength tabs in UI order; empty means no strength picker. */
    val ancModes: List<AncModeOption> = emptyList(),
    val gainSupported: Boolean = false,
    val gainLabels: List<String> = emptyList(),
    val ledSupported: Boolean = false,
    val headTrackingSupported: Boolean = false,
    val promptToneSupported: Boolean = false,
    val promptVolumeSupported: Boolean = false,
    val lhdcSupported: Boolean = false,
    val dualConnectionSupported: Boolean = false,
    val batteryForm: BatteryForm = BatteryForm.TWS_THREE_WAY,
) {
    /** Materialise the runtime capability view from this declaration. */
    fun toCapabilities(): DeviceCapabilities = DeviceCapabilities(
        adaptiveSupported = adaptiveSupported,
        spatialAudioSupported = spatialAudioSupported,
        spatialSoundSwitchSupported = spatialSoundSwitchSupported,
        ancImplementation = ancImplementation,
        ancModes = ancModes,
        gainSupported = gainSupported,
        gainLabels = gainLabels,
        ledSupported = ledSupported,
        headTrackingSupported = headTrackingSupported,
        promptToneSupported = promptToneSupported,
        promptVolumeSupported = promptVolumeSupported,
        lhdcSupported = lhdcSupported,
        dualConnectionSupported = dualConnectionSupported,
        batteryForm = batteryForm,
    )

    companion object {
        /** Reusable conservative declaration for protocol-inferred models. */
        val MINIMAL = CapabilityProfile(key = "minimal")

        /** Declaration for a one-piece headset that reports a single whole-device battery. */
        val MINIMAL_SINGLE_DEVICE = CapabilityProfile(
            key = "minimal_single_device",
            batteryForm = BatteryForm.SINGLE_DEVICE,
        )

        // ---- GAIA feature ids used by GET_SUPPORTED_FEATURES -----------------------
        const val F_BASIC = 0
        const val F_ANC_V1 = 2
        const val F_AUDIO_CURATION = 8
        const val F_BATTERY = 13
        const val F_VOICE = 14
        const val F_DAC_GAIN = 15
        const val F_CODEC_TYPE = 16
        const val F_LED = 19
        const val F_ONEBRINGTWO = 20
        // Official QTILFeature: SPATIAL_AUDIO wire value is 0x12 (18). 0x18 (24)
        // is POWER_CONTROL and would never open spatial capability.
        const val F_SPATIAL_AUDIO = 18
        const val F_ANC_V2 = 32

        /**
         * Observation rule: a reported feature may only OPEN a capability, never close one.
         * Data-driven so it can be unit-tested without a device.
         */
        data class ObservationRule(
            val featureId: Int,
            val open: (DeviceCapabilities) -> DeviceCapabilities,
        )

        /**
         * Two-state semantics: a declared capability stays authoritative; an observed
         * feature opens a capability the declaration left closed. Head-tracking and the
         * battery form have no GAIA feature bit, so observation cannot change them.
         */
        val OBSERVATION_RULES: List<ObservationRule> = listOf(
            ObservationRule(F_ANC_V1) { it.copy(adaptiveSupported = true) },
            ObservationRule(F_AUDIO_CURATION) { it.copy(adaptiveSupported = true) },
            ObservationRule(F_ANC_V2) { it.copy(adaptiveSupported = true) },
            ObservationRule(F_SPATIAL_AUDIO) {
                it.copy(spatialAudioSupported = true, spatialSoundSwitchSupported = true)
            },
            ObservationRule(F_VOICE) {
                it.copy(promptToneSupported = true, promptVolumeSupported = true)
            },
            ObservationRule(F_DAC_GAIN) { it.copy(gainSupported = true) },
            ObservationRule(F_CODEC_TYPE) { it.copy(lhdcSupported = true) },
            ObservationRule(F_LED) { it.copy(ledSupported = true) },
            ObservationRule(F_ONEBRINGTWO) { it.copy(dualConnectionSupported = true) },
        )
    }
}
