package cythen.hyperdrop.device

import cythen.hyperdrop.R
import cythen.hyperdrop.device.protocol.AncPathKind
import cythen.hyperdrop.device.protocol.GaiaCommandTable
import cythen.hyperdrop.device.protocol.GenericGaiaCommandTable
import cythen.hyperdrop.device.protocol.NoOpGaiaCommandTable
import cythen.hyperdrop.device.protocol.PuddingCommandTable
import cythen.hyperdrop.pods.AncImplementation
import cythen.hyperdrop.pods.NoiseControlMode

/** How a model talks to the earbuds. Only RFCOMM/GAIA v4 is implemented in this build. */
enum class PodTransport { BLE_GAIA, RFCOMM_GAIA, BLE_SRC_9ECA }

/** Data-driven [DeviceProfile]: every model-specific fact is a constructor argument. */
data class DeviceProfileSpec(
    override val id: String,
    override val displayName: String,
    override val aliases: List<String>,
    override val verified: Boolean,
    override val note: String,
    override val imageKey: String,
    override val transports: List<PodTransport>,
    override val capabilities: DeviceCapabilities,
    override val commandTable: GaiaCommandTable,
) : DeviceProfile {
    override fun matches(name: String?): Boolean = DeviceModels.nameMatches(name, aliases)
}

/**
 * Central model registry inspired by MiuixMoondrop: one declarative record per model,
 * alias-based matching, a verified/inferred flag, and a conservative fallback.
 *
 * Only PUDDING and MIRAGE are flagged [DeviceProfile.verified] real-hardware; every other
 * record is protocol-inferred (best-effort GAIA constants, silent degrade on failure).
 */
object DeviceModels {

    // ---- Reusable ANC / gain descriptors ------------------------------------------

    private fun labelFor(mode: NoiseControlMode): Int = when (mode) {
        NoiseControlMode.OFF -> R.string.off
        NoiseControlMode.NOISE_CANCELLATION -> R.string.noise_cancellation_title
        NoiseControlMode.TRANSPARENCY -> R.string.transparency_title
        NoiseControlMode.ADAPTIVE -> R.string.pudding_anc_adaptive
        NoiseControlMode.NOISE_CANCELLATION_SMART -> R.string.pudding_anc_wind
        else -> R.string.noise_cancellation_title
    }

    private fun uiStatus(mode: NoiseControlMode): Int = when (mode) {
        NoiseControlMode.OFF -> 1
        NoiseControlMode.NOISE_CANCELLATION -> 2
        NoiseControlMode.TRANSPARENCY -> 3
        NoiseControlMode.ADAPTIVE -> 4
        NoiseControlMode.NOISE_CANCELLATION_SMART -> 5
        NoiseControlMode.NOISE_CANCELLATION_LIGHT -> 6
        NoiseControlMode.NOISE_CANCELLATION_MEDIUM -> 7
        NoiseControlMode.NOISE_CANCELLATION_DEEP -> 8
    }

    /**
     * Build (setMap indexed by uiStatus 1..8, getMap indexed by reported value).
     * For AUDIO_CURATION, GET reports a 0-based index while SET takes a command byte,
     * so [getSequential] makes getMap key on the 0-based position instead of the wire byte.
     */
    private fun ancMaps(
        pairs: List<Pair<NoiseControlMode, Int>>,
        getSequential: Boolean = false,
    ): Pair<IntArray, IntArray> {
        val setMap = IntArray(9) { -1 }
        val getMap = IntArray(16) { -1 }
        pairs.forEachIndexed { index, (mode, wire) ->
            val ui = uiStatus(mode)
            if (ui in 1..8) setMap[ui] = wire
            val getWire = if (getSequential) index else wire
            if (getWire in 0..15) getMap[getWire] = ui
        }
        return setMap to getMap
    }

    private fun ancOptions(modes: List<NoiseControlMode>) =
        modes.map { AncModeOption(it, labelFor(it)) }

    // ANC descriptors: UI modes paired with their SET wire values.
    private val ac3Pairs = listOf(
        NoiseControlMode.OFF to 1,
        NoiseControlMode.NOISE_CANCELLATION to 2,
        NoiseControlMode.TRANSPARENCY to 4,
    )
    private val ac4Pairs = listOf(
        NoiseControlMode.OFF to 1,
        NoiseControlMode.NOISE_CANCELLATION to 2,
        NoiseControlMode.TRANSPARENCY to 4,
        NoiseControlMode.NOISE_CANCELLATION_SMART to 3,
    )

    private val gainIdentity = intArrayOf(0, 1, 2)
    private val gainReversed = intArrayOf(2, 1, 0)
    private val gainLabels = listOf("Low", "Medium", "High")

    /** Most conservative capability set for a model with no real-hardware evidence. */
    private val conservativeCaps: DeviceCapabilities
        get() = inferredCaps(ac4Pairs, gain = null)

    private fun inferredCaps(
        pairs: List<Pair<NoiseControlMode, Int>>,
        path: AncPathKind = AncPathKind.AUDIO_CURATION,
        gain: IntArray? = null,
        led: Boolean = false,
        spatial: Boolean = false,
        headTracking: Boolean = false,
        promptTone: Boolean = false,
        lhdc: Boolean = false,
        dualConnection: Boolean = false,
        singleDeviceBattery: Boolean = false,
    ): DeviceCapabilities {
        val (setMap, getMap) = ancMaps(pairs, getSequential = path == AncPathKind.AUDIO_CURATION)
        return DeviceCapabilities(
            adaptiveSupported = false,
            spatialAudioSupported = spatial,
            spatialSoundSwitchSupported = spatial,
            ancImplementation = AncImplementation.STANDARD,
            ancModes = ancOptions(pairs.map { it.first }),
            ancPath = path,
            ancSetMap = setMap,
            ancGetMap = getMap,
            gainSupported = gain != null,
            gainMap = gain ?: gainIdentity,
            gainLabels = if (gain != null) gainLabels else emptyList(),
            ledSupported = led,
            headTrackingSupported = headTracking,
            promptToneSupported = promptTone,
            promptVolumeSupported = promptTone,
            lhdcSupported = lhdc,
            dualConnectionSupported = dualConnection,
            singleDeviceBattery = singleDeviceBattery,
        )
    }

    private fun genericTable(caps: DeviceCapabilities) = GenericGaiaCommandTable(
        ancPathKind = caps.ancPath,
        ancSetMap = caps.ancSetMap,
        ancGetMap = caps.ancGetMap ?: caps.ancSetMap,
        gainMap = caps.gainMap,
    )

    private fun inferred(
        id: String,
        displayName: String,
        aliases: List<String>,
        caps: DeviceCapabilities = conservativeCaps,
        imageKey: String = id,
    ) = DeviceProfileSpec(
        id = id,
        displayName = displayName,
        aliases = aliases,
        verified = false,
        note = "Protocol inferred — not verified on real hardware",
        imageKey = imageKey,
        transports = listOf(PodTransport.RFCOMM_GAIA),
        capabilities = caps,
        commandTable = genericTable(caps),
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
        capabilities = DeviceCapabilities(
            adaptiveSupported = true,
            spatialAudioSupported = false,
            spatialSoundSwitchSupported = false,
            ancImplementation = AncImplementation.STANDARD,
            ancModes = listOf(
                AncModeOption(NoiseControlMode.NOISE_CANCELLATION, R.string.pudding_anc_basic),
                AncModeOption(NoiseControlMode.ADAPTIVE, R.string.pudding_anc_adaptive),
                AncModeOption(NoiseControlMode.NOISE_CANCELLATION_SMART, R.string.pudding_anc_wind),
            ),
            ancPath = AncPathKind.ANC_V2,
            ancSetMap = intArrayOf(-1, 0, 1, 2, 4, 3, -1, -1, -1),
            ancGetMap = intArrayOf(1, 2, 3, 5, 4, -1),
            gainSupported = true,
            gainMap = gainReversed,
            gainLabels = gainLabels,
            ledSupported = true,
            promptToneSupported = true,
            promptVolumeSupported = true,
            lhdcSupported = true,
            dualConnectionSupported = true,
        ),
        commandTable = PuddingCommandTable,
    )

    /** Mirage (海市蜃楼) — the PUDDING-linked variant; same verified wire protocol. */
    val mirage = DeviceProfileSpec(
        id = "mirage",
        displayName = "MOONDROP MIRAGE / 海市蜃楼",
        aliases = listOf("mirage", "海市蜃楼"),
        verified = true,
        note = "Verified on real hardware (PUDDING-linked variant)",
        imageKey = "moondrop_mirage",
        transports = listOf(PodTransport.RFCOMM_GAIA),
        capabilities = pudding.capabilities,
        commandTable = PuddingCommandTable,
    )

    // ---- Protocol-inferred models (45 total: 2 verified + 43 inferred) ------------
    // The first 15 keep their historical ids/aliases/capabilities for matching
    // compatibility; imageKey is the asset slug. The remaining 28 are newly added
    // models with conservative capabilities and name-based aliases.

    val models: List<DeviceProfile> = listOf(
        pudding,
        mirage,
        inferred("bianca_stigmata", "BIANCA:STIGMATA", listOf("biancastigmata", "bianca", "stigmata"), conservativeCaps, "bianca_stigmata"),
        inferred("h_i_d_e_404_klukai", "H.I.D.E.404_Klukai", listOf("404klukai", "klukai"), conservativeCaps, "h_i_d_e_404_klukai"),
        inferred("laplace_oba", "LAPLACE-OBA-Ⅱ", listOf("laplaceoba", "laplace"), conservativeCaps, "laplace_oba"),
        inferred("me_163_komet_moca", "Me 163 Komet MOCA", listOf("me163kometmoca", "komet"), conservativeCaps, "me_163_komet_moca"),
        inferred("alice", "MOONDROP Alice", listOf("alice"), inferredCaps(ac4Pairs), "moondrop_alice"),
        inferred("moondrop_dots", "MOONDROP Dots", listOf("MOONDROP Dots"), conservativeCaps, "moondrop_dots"),
        inferred("edge", "MOONDROP EDGE", listOf("edge"), inferredCaps(ac3Pairs, gain = gainIdentity, singleDeviceBattery = true), "moondrop_edge"),
        inferred("edge2", "MOONDROP EDGE 2", listOf("edge2", "edgeii"), inferredCaps(ac3Pairs, gain = gainIdentity), "moondrop_edge_2"),
        inferred("moondrop_evo_2", "Moondrop EVO 2", listOf("Moondrop EVO 2"), conservativeCaps, "moondrop_evo_2"),
        inferred("golden_ages", "MOONDROP Golden Ages", listOf("goldenages"), inferredCaps(ac4Pairs, gain = gainReversed), "moondrop_golden_ages"),
        inferred("golden_ages_2", "MOONDROP Golden Ages 2", listOf("goldenages2", "ga2", "goldenagesii"), inferredCaps(ac4Pairs, gain = gainReversed, promptTone = true), "moondrop_golden_ages_2"),
        inferred("moondrop_littlewhite", "MOONDROP littlewhite", listOf("littlewhite"), conservativeCaps, "moondrop_littlewhite"),
        inferred("moondrop_mm3a", "MOONDROP MM3A", listOf("MOONDROP MM3A"), conservativeCaps, "moondrop_mm3a"),
        inferred("maca", "MOONDROP MACA", listOf("maca"), inferredCaps(ac4Pairs, lhdc = true, spatial = true), "moondrop_moca"),
        inferred("moondrop_moon_travel", "MOONDROP MOON TRAVEL", listOf("moontravel", "travel"), conservativeCaps, "moondrop_moon_travel"),
        inferred("nekocake", "MOONDROP NEKOCAKE", listOf("nekocake", "neko"), inferredCaps(ac4Pairs, lhdc = true), "moondrop_nekocake"),
        inferred("moondrop_nekocake_acht_acht_limited", "Moondrop Nekocake Acht Acht Limited", listOf("nekocakeachtacht", "nekocake"), conservativeCaps, "moondrop_nekocake_acht_acht_limited"),
        inferred("moondrop_nekocake_qbz_191", "Moondrop Nekocake QBZ-191", listOf("nekocakeqbz191", "nekocake"), conservativeCaps, "moondrop_nekocake_qbz_191"),
        inferred("pills", "MOONDROP PILLS", listOf("pills"), inferredCaps(ac4Pairs), "moondrop_pill"),
        inferred("moondrop_punishing_gray_raven", "Moondrop PUNISHING;GRAY RAVEN IOS", listOf("punishinggrayraven", "punishing", "raven"), conservativeCaps, "moondrop_punishing_gray_raven"),
        inferred("space_travel", "MOONDROP Space Travel", listOf("spacetravel"), inferredCaps(ac4Pairs), "moondrop_space_travel"),
        inferred("sparks", "MOONDROP SPARKS", listOf("sparks"), inferredCaps(ac4Pairs), "moondrop_sparks"),
        inferred("moondrop_sparxie", "MOONDROP Sparxie", listOf("sparxie"), conservativeCaps, "moondrop_sparxie"),
        inferred("moondrop_susanoo_tws", "Moondrop SUSANOO TWS IOS", listOf("susanoo"), conservativeCaps, "moondrop_susanoo_tws"),
        inferred("moondrop_the_garden", "MOONDROP The Garden", listOf("garden"), conservativeCaps, "moondrop_the_garden"),
        inferred("moondrop_u_c_t_s", "Moondrop U.C.T.S.", listOf("Moondrop U.C.T.S."), conservativeCaps, "moondrop_u_c_t_s"),
        inferred("moondrop_ultrasonic", "MOONDROP Ultrasonic", listOf("ultrasonic"), conservativeCaps, "moondrop_ultrasonic"),
        inferred("voyager", "MOONDROP VOYAGER", listOf("voyager"), inferredCaps(ac4Pairs), "moondrop_voyager"),
        inferred("moondrop_x_yasuno_kiyono", "MOONDROP x YASUNO KIYONO", listOf("yasunokiyono", "yasuno", "kiyono"), conservativeCaps, "moondrop_x_yasuno_kiyono"),
        inferred("nicole_reeyn", "Nicole Reeyn", listOf("nicolereeyn", "nicole", "reeyn"), conservativeCaps, "nicole_reeyn"),
        inferred("pandaer_open_air_pill", "PANDAER Open Air Pill", listOf("openairpill"), conservativeCaps, "pandaer_open_air_pill"),
        inferred("pandaer_space_travel_2", "PANDAER Space Travel 2", listOf("spacetravel", "space", "travel"), conservativeCaps, "pandaer_space_travel_2"),
        inferred("pill_gotoh_hitori", "Pill Gotoh Hitori", listOf("pillgotohhitori", "gotoh", "hitori"), conservativeCaps, "pill_gotoh_hitori"),
        inferred("pill_ijichi_nijika", "Pill Ijichi Nijika", listOf("pillijichinijika", "ijichi", "nijika"), conservativeCaps, "pill_ijichi_nijika"),
        inferred("pill_kita_ikuyo", "Pill Kita Ikuyo", listOf("pillkitaikuyo", "ikuyo"), conservativeCaps, "pill_kita_ikuyo"),
        inferred("pill_yamada_ryo", "Pill Yamada Ryo", listOf("pillyamadaryo", "yamada"), conservativeCaps, "pill_yamada_ryo"),
        inferred("punishing_gray_raven", "PUNISHING:GRAY RAVEN", listOf("punishinggrayraven", "punishing", "raven"), conservativeCaps, "punishing_gray_raven"),
        inferred("robins", "MOONDROP robins", listOf("robins", "robin"), inferredCaps(ac4Pairs), "robin_s_earphones"),
        inferred("space_force", "Space Force", listOf("spaceforce", "space", "force"), conservativeCaps, "space_force"),
        inferred("space_travel_2", "MOONDROP Space Travel 2", listOf("spacetravel2", "st2"), inferredCaps(ac4Pairs, gain = gainReversed, spatial = true), "space_travel_2"),
        inferred("space_travel_2_ultra", "MOONDROP Space Travel 2 Ultra", listOf("spacetravel2ultra"), inferredCaps(ac4Pairs, gain = gainReversed, spatial = true), "space_travel_2_ultra"),
        inferred("susanoo_tws", "SUSANOO TWS", listOf("susanoo"), conservativeCaps, "susanoo_tws"),
        inferred("zzz_angels_ows", "ZZZ-ANGELS-OWS", listOf("zzzangelsows", "angels"), conservativeCaps, "zzz_angels_ows"),
    )

    val byAlias: List<Pair<String, DeviceProfile>> = models
        .flatMap { model -> model.aliases.map { normalize(it) to model } }
        .sortedByDescending { it.first.length }

    /** Conservative fallback for an unmatched MOONDROP / 水月雨 name. */
    val fallback = DeviceProfileSpec(
        id = "fallback",
        displayName = "MOONDROP (inferred)",
        aliases = emptyList(),
        verified = false,
        note = "Unrecognised MOONDROP model — commands use conservative guessed constants",
        imageKey = "default",
        transports = listOf(PodTransport.RFCOMM_GAIA),
        capabilities = inferredCaps(ac4Pairs, gain = null),
        commandTable = NoOpGaiaCommandTable,
    )

    fun normalize(name: String?): String = name.orEmpty().lowercase()
        .replace("-", "").replace("_", "").replace(" ", "")

    internal fun nameMatches(name: String?, aliases: List<String>): Boolean {
        val n = normalize(name)
        if (n.isEmpty()) return false
        return aliases.any { alias -> n.contains(normalize(alias)) }
    }

    /** Alias-first, then conservative fallback for MOONDROP-branded names. */
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
