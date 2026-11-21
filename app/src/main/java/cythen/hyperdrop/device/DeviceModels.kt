package cythen.hyperdrop.device

import cythen.hyperdrop.R
import cythen.hyperdrop.device.protocol.GaiaCommandTable
import cythen.hyperdrop.pods.AncImplementation
import cythen.hyperdrop.pods.NoiseControlMode

/**
 * How a model talks to the earbuds.
 *
 * A profile's [DeviceProfile.transports] is a *candidate priority list*, not a promise: the
 * session probes each candidate at connect time and keeps the first one that answers. The
 * official app does the same — `BluetoothConnectionHandler.resolveBluetoothType()` reads a
 * per-device `transportType` at connection time rather than consulting a compiled model table.
 */
enum class PodTransport {
    /** GAIA over BLE GATT (`00001100-d102-11e1-9b23-00025b00a5a5` service). */
    BLE_GAIA,

    /** GAIA over Classic RFCOMM/SPP (`00001101-0000-1000-8000-00805f9b34fb`). */
    RFCOMM_GAIA,

    /** Vendor "audio source switch" protocol over BLE (`9ECA0000-...`, 4-byte `A5 01 id len` frames). */
    BLE_SRC_9ECA,
}

/**
 * The capability features a model is *declared* to have.
 *
 * These are hints authored from evidence about the product, and they are what the UI shows before
 * the earbud has said anything. At runtime `ObservedFeatures` can only add to them, never remove
 * (see [DeviceRegistry.applyObservedFeatures]).
 */
data class DeclaredFeatures(
    val adaptive: Boolean = false,
    val spatialAudio: Boolean = false,
    val spatialSoundSwitch: Boolean = false,
    val gain: Boolean = false,
    val led: Boolean = false,
    val headTracking: Boolean = false,
    val promptTone: Boolean = false,
    val promptVolume: Boolean = false,
    val lhdc: Boolean = false,
    val dualConnection: Boolean = false,
    /** Overrides the form-factor default; null means "derive from [DeviceFormFactor]". */
    val singleDeviceBattery: Boolean? = null,
    /**
     * Which of the control profile's ANC modes this product actually exposes as tabs, in UI
     * order. Null means "all of them".
     *
     * The two are not the same thing: PUDDING's firmware accepts five wire values but its product
     * UI offers three tabs (basic / adaptive / anti-wind), so the wire vocabulary stays complete
     * while the picker stays short.
     */
    val ancTabs: List<NoiseControlMode>? = null,
) {
    companion object {
        /** Nothing declared: the UI starts empty and probing fills it in. */
        val NONE = DeclaredFeatures()
    }
}

/**
 * Data-driven [DeviceProfile]: identity, shape and wire vocabulary are separate arguments.
 *
 * [capabilities] is derived from [control] + [formFactor] + [declared], so there is exactly one
 * place each fact can live. Changing an ANC wire value means editing a [ControlProfile], not
 * every model that speaks it.
 */
data class DeviceProfileSpec(
    override val id: String,
    override val displayName: String,
    override val aliases: List<String>,
    override val verified: Boolean,
    override val note: String,
    override val imageKey: String,
    override val transports: List<PodTransport>,
    override val control: ControlProfile = ControlProfiles.probeFallback,
    override val formFactor: DeviceFormFactor = DeviceFormFactor.TWS,
    val declared: DeclaredFeatures = DeclaredFeatures.NONE,
) : DeviceProfile {

    override val commandTable: GaiaCommandTable get() = control.table

    override val capabilities: DeviceCapabilities by lazy {
        DeviceModels.buildCapabilities(
            control = control,
            formFactor = formFactor,
            declared = declared,
        )
    }

    override fun matches(name: String?): Boolean = DeviceModels.nameMatches(name, aliases)
}

/**
 * Central model registry: one declarative record per model.
 *
 * Each record names its [ControlProfile] and [DeviceFormFactor] instead of restating a whole
 * capability block. Models that speak the same dialect share a control profile — MOONDROP MIRAGE
 * (海市蜃楼) is a PUDDING variant and points at `ControlProfiles.pudding` — so a wire fix lands in
 * one place.
 *
 * Only PUDDING and MIRAGE are [DeviceProfile.verified] real hardware. Everything else keeps its
 * own identity record (so the name is recognised and the right art is drawn) but starts from
 * `ControlProfiles.probeFallback` with no declared features, letting `GET_SUPPORTED_FEATURES`
 * decide what the UI offers. Guessing a mapping per model is exactly what this replaces.
 */
object DeviceModels {

    // ---- Capability derivation -----------------------------------------------------

    private fun labelFor(mode: NoiseControlMode): Int = when (mode) {
        NoiseControlMode.OFF -> R.string.off
        NoiseControlMode.NOISE_CANCELLATION -> R.string.noise_cancellation_title
        NoiseControlMode.TRANSPARENCY -> R.string.transparency_title
        NoiseControlMode.ADAPTIVE -> R.string.pudding_anc_adaptive
        NoiseControlMode.NOISE_CANCELLATION_SMART -> R.string.pudding_anc_wind
        else -> R.string.noise_cancellation_title
    }

    internal fun uiStatus(mode: NoiseControlMode): Int = when (mode) {
        NoiseControlMode.OFF -> 1
        NoiseControlMode.NOISE_CANCELLATION -> 2
        NoiseControlMode.TRANSPARENCY -> 3
        NoiseControlMode.ADAPTIVE -> 4
        NoiseControlMode.NOISE_CANCELLATION_SMART -> 5
        NoiseControlMode.NOISE_CANCELLATION_LIGHT -> 6
        NoiseControlMode.NOISE_CANCELLATION_MEDIUM -> 7
        NoiseControlMode.NOISE_CANCELLATION_DEEP -> 8
    }

    private fun ancOptions(control: ControlProfile, declared: DeclaredFeatures): List<AncModeOption> {
        val modes = declared.ancTabs ?: control.ancPairs.map { it.first }
        return modes.map { mode -> AncModeOption(mode, labelFor(mode)) }
    }

    /**
     * The single place capabilities are assembled.
     *
     * [DeviceCapabilities]' `ancPath` / `ancSetMap` / `ancGetMap` / `gainMap` are filled from
     * [control], so the fields the UI reads and the fields the command table uses cannot diverge.
     */
    internal fun buildCapabilities(
        control: ControlProfile,
        formFactor: DeviceFormFactor,
        declared: DeclaredFeatures,
    ): DeviceCapabilities = DeviceCapabilities(
        adaptiveSupported = declared.adaptive,
        spatialAudioSupported = declared.spatialAudio,
        spatialSoundSwitchSupported = declared.spatialSoundSwitch,
        ancImplementation = AncImplementation.STANDARD,
        ancModes = ancOptions(control, declared),
        ancPath = control.ancPath,
        ancSetMap = control.ancSetMap,
        ancGetMap = control.ancGetMap,
        gainSupported = declared.gain && control.hasGain,
        // The mapping comes from the vocabulary and may be an unconfirmed assumption; what keeps
        // an unconfirmed mapping from showing a control is `declared.gain` above, not an empty
        // array. Keeping the mapping present is what lets applyObservedFeatures open the control
        // when the device reports DAC_GAIN.
        gainMap = control.gainMap,
        // Labels follow the mapping, not the declaration: the picker is gated on gainSupported,
        // so keeping the two lists the same length is what stops the UI offering a step the
        // vocabulary cannot address after probing opens the control.
        gainLabels = if (control.hasGain) GAIN_LABELS else emptyList(),
        ledSupported = declared.led,
        headTrackingSupported = declared.headTracking,
        promptToneSupported = declared.promptTone,
        promptVolumeSupported = declared.promptVolume,
        lhdcSupported = declared.lhdc,
        dualConnectionSupported = declared.dualConnection,
        singleDeviceBattery = declared.singleDeviceBattery ?: formFactor.singleBatteryByDefault,
    )

    private val GAIN_LABELS = listOf("Low", "Medium", "High")

    // ---- Record constructors -------------------------------------------------------

    /**
     * A model recognised by name but **without** verified wire behaviour.
     *
     * No declared features: the point is that a name match alone is not evidence that a given
     * control exists, so the UI waits for `GET_SUPPORTED_FEATURES` instead of guessing.
     */
    private fun inferred(
        id: String,
        displayName: String,
        aliases: List<String>,
        imageKey: String = id,
        formFactor: DeviceFormFactor = DeviceFormFactor.TWS,
    ) = DeviceProfileSpec(
        id = id,
        displayName = displayName,
        aliases = aliases,
        verified = false,
        note = "Protocol inferred — capabilities come from runtime probing",
        imageKey = imageKey,
        transports = listOf(PodTransport.RFCOMM_GAIA),
        control = ControlProfiles.probeFallback,
        formFactor = formFactor,
    )

    // ---- Verified models -----------------------------------------------------------

    val pudding = DeviceProfileSpec(
        id = "pudding",
        displayName = "MOONDROP PUDDING / MD-TWS-056",
        aliases = listOf("pudding", "mdtws056"),
        verified = true,
        note = "Verified on real hardware (PuddingPods)",
        imageKey = "moondrop_pudding",
        transports = listOf(PodTransport.RFCOMM_GAIA),
        control = ControlProfiles.pudding,
        formFactor = DeviceFormFactor.TWS,
        declared = DeclaredFeatures(
            adaptive = true,
            gain = true,
            led = true,
            promptTone = true,
            promptVolume = true,
            lhdc = true,
            dualConnection = true,
            // The firmware takes five wire values, but the product only exposes these three tabs.
            ancTabs = listOf(
                NoiseControlMode.NOISE_CANCELLATION,
                NoiseControlMode.ADAPTIVE,
                NoiseControlMode.NOISE_CANCELLATION_SMART,
            ),
        ),
    )

    /** Mirage (海市蜃楼) — the PUDDING-linked variant; same electronics, same control profile. */
    val mirage = DeviceProfileSpec(
        id = "mirage",
        displayName = "MOONDROP MIRAGE / 海市蜃楼",
        aliases = listOf("mirage", "海市蜃楼"),
        verified = true,
        note = "Verified on real hardware (PUDDING-linked variant)",
        imageKey = "moondrop_mirage",
        transports = listOf(PodTransport.RFCOMM_GAIA),
        control = ControlProfiles.pudding,
        formFactor = DeviceFormFactor.TWS,
        declared = pudding.declared,
    )

    // ---- Name-recognised models ----------------------------------------------------
    // Identity records only: they exist so the model is recognised and drawn correctly. Wire
    // behaviour and capabilities come from the control profile plus runtime probing.

    val models: List<DeviceProfile> = listOf(
        pudding,
        mirage,
        inferred("bianca_stigmata", "BIANCA:STIGMATA", listOf("biancastigmata", "bianca", "stigmata"), "bianca_stigmata", DeviceFormFactor.HEADPHONES),
        inferred("h_i_d_e_404_klukai", "H.I.D.E.404_Klukai", listOf("404klukai", "klukai"), "h_i_d_e_404_klukai"),
        inferred("laplace_oba", "LAPLACE-OBA-Ⅱ", listOf("laplaceoba", "laplace"), "laplace_oba"),
        inferred("me_163_komet_moca", "Me 163 Komet MOCA", listOf("me163kometmoca", "komet"), "me_163_komet_moca"),
        inferred("alice", "MOONDROP Alice", listOf("alice"), "moondrop_alice"),
        inferred("moondrop_dots", "MOONDROP Dots", listOf("MOONDROP Dots"), "moondrop_dots"),
        inferred("edge", "MOONDROP EDGE", listOf("edge"), "moondrop_edge"),
        inferred("edge2", "MOONDROP EDGE 2", listOf("edge2", "edgeii"), "moondrop_edge_2"),
        inferred("moondrop_evo_2", "Moondrop EVO 2", listOf("Moondrop EVO 2"), "moondrop_evo_2"),
        inferred("golden_ages", "MOONDROP Golden Ages", listOf("goldenages"), "moondrop_golden_ages"),
        inferred("golden_ages_2", "MOONDROP Golden Ages 2", listOf("goldenages2", "ga2", "goldenagesii"), "moondrop_golden_ages_2"),
        inferred("moondrop_littlewhite", "MOONDROP littlewhite", listOf("littlewhite"), "moondrop_littlewhite", DeviceFormFactor.ONE_PIECE),
        inferred("moondrop_mm3a", "MOONDROP MM3A", listOf("MOONDROP MM3A"), "moondrop_mm3a", DeviceFormFactor.SPEAKER),
        inferred("maca", "MOONDROP MACA", listOf("maca"), "moondrop_moca"),
        inferred("moondrop_moon_travel", "MOONDROP MOON TRAVEL", listOf("moontravel", "travel"), "moondrop_moon_travel"),
        inferred("nekocake", "MOONDROP NEKOCAKE", listOf("nekocake", "neko"), "moondrop_nekocake"),
        inferred("moondrop_nekocake_acht_acht_limited", "Moondrop Nekocake Acht Acht Limited", listOf("nekocakeachtacht", "nekocake"), "moondrop_nekocake_acht_acht_limited"),
        inferred("moondrop_nekocake_qbz_191", "Moondrop Nekocake QBZ-191", listOf("nekocakeqbz191", "nekocake"), "moondrop_nekocake_qbz_191"),
        inferred("pills", "MOONDROP PILLS", listOf("pills"), "moondrop_pill"),
        inferred("moondrop_punishing_gray_raven", "Moondrop PUNISHING;GRAY RAVEN IOS", listOf("punishinggrayraven", "punishing", "raven"), "moondrop_punishing_gray_raven"),
        inferred("sparks", "MOONDROP SPARKS", listOf("sparks"), "moondrop_sparks"),
        inferred("moondrop_sparxie", "MOONDROP Sparxie", listOf("sparxie"), "moondrop_sparxie"),
        // "susanoo" alone is ambiguous between these two; see byAlias ordering note below.
        inferred("moondrop_susanoo_tws", "Moondrop SUSANOO TWS IOS", listOf("susanootwsios"), "moondrop_susanoo_tws"),
        inferred("moondrop_the_garden", "MOONDROP The Garden", listOf("garden"), "moondrop_the_garden"),
        inferred("moondrop_u_c_t_s", "Moondrop U.C.T.S.", listOf("Moondrop U.C.T.S."), "moondrop_u_c_t_s"),
        inferred("moondrop_ultrasonic", "MOONDROP Ultrasonic", listOf("ultrasonic"), "moondrop_ultrasonic"),
        inferred("voyager", "MOONDROP VOYAGER", listOf("voyager"), "moondrop_voyager", DeviceFormFactor.ONE_PIECE),
        inferred("moondrop_x_yasuno_kiyono", "MOONDROP x YASUNO KIYONO", listOf("yasunokiyono", "yasuno", "kiyono"), "moondrop_x_yasuno_kiyono"),
        inferred("nicole_reeyn", "Nicole Reeyn", listOf("nicolereeyn", "nicole", "reeyn"), "nicole_reeyn", DeviceFormFactor.ONE_PIECE),
        inferred("pandaer_open_air_pill", "PANDAER Open Air Pill", listOf("openairpill"), "pandaer_open_air_pill"),
        inferred("pandaer_space_travel_2", "PANDAER Space Travel 2", listOf("spacetravel", "space", "travel"), "pandaer_space_travel_2"),
        inferred("pill_gotoh_hitori", "Pill Gotoh Hitori", listOf("pillgotohhitori", "gotoh", "hitori"), "pill_gotoh_hitori"),
        inferred("pill_ijichi_nijika", "Pill Ijichi Nijika", listOf("pillijichinijika", "ijichi", "nijika"), "pill_ijichi_nijika"),
        inferred("pill_kita_ikuyo", "Pill Kita Ikuyo", listOf("pillkitaikuyo", "ikuyo"), "pill_kita_ikuyo"),
        inferred("pill_yamada_ryo", "Pill Yamada Ryo", listOf("pillyamadaryo", "yamada"), "pill_yamada_ryo"),
        inferred("punishing_gray_raven", "PUNISHING:GRAY RAVEN", listOf("punishinggrayraven", "punishing", "raven"), "punishing_gray_raven"),
        inferred("robins", "MOONDROP robins", listOf("robins", "robin"), "robin_s_earphones"),
        inferred("space_force", "Space Force", listOf("spaceforce", "space", "force"), "space_force"),
        inferred("space_travel", "MOONDROP Space Travel", listOf("spacetravel"), "moondrop_space_travel"),
        inferred("space_travel_2", "MOONDROP Space Travel 2", listOf("spacetravel2", "st2"), "space_travel_2"),
        inferred("space_travel_2_ultra", "MOONDROP Space Travel 2 Ultra", listOf("spacetravel2ultra"), "space_travel_2_ultra"),
        inferred("susanoo_tws", "SUSANOO TWS", listOf("susanoo"), "susanoo_tws"),
        inferred("zzz_angels_ows", "ZZZ-ANGELS-OWS", listOf("zzzangelsows", "angels"), "zzz_angels_ows"),
    )

    val byAlias: List<Pair<String, DeviceProfile>> = models
        .flatMap { model -> model.aliases.map { normalize(it) to model } }
        .sortedByDescending { it.first.length }

    /**
     * Model-aware fallback for a MOONDROP / 水月雨 name no record owns.
     *
     * This is a real fallback, not a refusal: it keeps the generic GAIA vocabulary so a brand-new
     * model can still be probed and driven, it just declares nothing and shows nothing until the
     * earbud answers `GET_SUPPORTED_FEATURES`.
     */
    val fallback = DeviceProfileSpec(
        id = "fallback",
        displayName = "MOONDROP (unrecognised model)",
        aliases = emptyList(),
        verified = false,
        note = "Unrecognised MOONDROP model — features are discovered by probing",
        imageKey = "default",
        transports = listOf(PodTransport.RFCOMM_GAIA),
        control = ControlProfiles.probeFallback,
        formFactor = DeviceFormFactor.TWS,
    )

    fun normalize(name: String?): String = name.orEmpty().lowercase()
        .replace("-", "").replace("_", "").replace(" ", "")

    internal fun nameMatches(name: String?, aliases: List<String>): Boolean {
        val n = normalize(name)
        if (n.isEmpty()) return false
        return aliases.any { alias -> n.contains(normalize(alias)) }
    }

    /** Alias-first, then the probe-only fallback for MOONDROP-branded names. */
    fun match(name: String?): DeviceProfile? {
        val n = normalize(name)
        if (n.isEmpty()) return null
        byAlias.firstOrNull { (alias, _) -> n.contains(alias) }?.let { return it.second }
        if (n.contains("moondrop") || n.contains("水月雨")) return fallback
        return null
    }

    fun isMoondropName(name: String?): Boolean {
        val n = normalize(name)
        return n.contains("moondrop") || n.contains("水月雨")
    }
}
