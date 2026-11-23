package cythen.hyperdrop.device

import cythen.hyperdrop.R
import cythen.hyperdrop.device.protocol.AncPathKind
import cythen.hyperdrop.pods.AncImplementation
import cythen.hyperdrop.pods.NoiseControlMode
import cythen.hyperdrop.pods.isNoiseCancellation

/** How a model talks to the earbuds. Only RFCOMM/GAIA v4 is implemented in this build. */
enum class PodTransport { BLE_GAIA, RFCOMM_GAIA, BLE_SRC_9ECA }

/**
 * Data-driven [DeviceProfile]: identity/matching/image metadata plus references to two
 * reusable archives — a [CapabilityProfile] and a [ControlProfile]. Models sharing a wire
 * mapping share one control profile; models sharing a capability declaration share one
 * capability profile. See MiuixMoondrop's "capability-first" design.
 */
data class DeviceProfileSpec(
    override val id: String,
    override val displayName: String,
    override val aliases: List<String>,
    override val verified: Boolean,
    override val imageKey: String,
    override val transports: List<PodTransport>,
    override val capabilityProfile: CapabilityProfile,
    override val controlProfile: ControlProfile,
) : DeviceProfile {
    override fun matches(name: String?): Boolean = DeviceModels.nameMatches(name, aliases)
}

/**
 * Central model registry ("机型档案" table): one declarative record per model,
 * alias-based matching, a verified/inferred flag, and a conservative probe fallback.
 *
 * Verified records are real-hardware models whose ANC/gain/LED/spatial mapping comes from
 * FxxkMoondrop (MoonEar) real-device logs — the seven MoonEar-verified models (PUDDING,
 * Golden Ages [1979] / [2], Space Travel 2 [Ultra], U.C.T.S, MOCA) plus MIRAGE (PUDDING's
 * linked variant). Every other record is protocol-inferred (best-effort GAIA constants,
 * silent degrade).
 */
object DeviceModels {

    // ---- Shared wire descriptors ---------------------------------------------------

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

    /** setMap indexed by uiStatus 1..8; getMap indexed by reported value. */
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

    // ---- Shared archive caches (deduplicated by wire/flag signature) ---------------

    private val controlCache = LinkedHashMap<String, ControlProfile>()
    private val capCache = LinkedHashMap<String, CapabilityProfile>()

    private fun genericControl(
        path: AncPathKind,
        setMap: IntArray,
        getMap: IntArray,
        gainMap: IntArray,
    ): ControlProfile {
        val sig = "${path}|${setMap.joinToString(",")}|${getMap.joinToString(",")}|${gainMap.joinToString(",")}"
        return controlCache.getOrPut(sig) {
            ControlProfile.generic("generic_${controlCache.size}", path, setMap, getMap, gainMap)
        }
    }

    private fun inferredCapability(
        modes: List<AncModeOption>,
        spatial: Boolean,
        headTracking: Boolean,
        gain: Boolean,
        led: Boolean,
        promptTone: Boolean,
        lhdc: Boolean,
        dualConnection: Boolean,
        batteryForm: BatteryForm,
        ldac: Boolean = false,
        lc3: Boolean = false,
    ): CapabilityProfile {
        val sig = listOf(
            modes.joinToString(",") { it.mode.name }, spatial, headTracking, gain, led,
            promptTone, lhdc, lc3, ldac, dualConnection, batteryForm.name,
        ).joinToString("|")
        return capCache.getOrPut(sig) {
            CapabilityProfile(
                key = "inferred_${capCache.size}",
                adaptiveSupported = false,
                spatialAudioSupported = spatial,
                spatialSoundSwitchSupported = spatial,
                ancImplementation = AncImplementation.STANDARD,
                ancModes = modes,
                gainSupported = gain,
                gainLabels = if (gain) gainLabels else emptyList(),
                ledSupported = led,
                headTrackingSupported = headTracking,
                promptToneSupported = promptTone,
                promptVolumeSupported = promptTone,
                lhdcSupported = lhdc,
                lc3Supported = lc3,
                ldacSupported = ldac,
                dualConnectionSupported = dualConnection,
                batteryForm = batteryForm,
            )
        }
    }

    // ---- Verified archives ---------------------------------------------------------

    /** PUDDING's declared capability ceiling (shared verbatim by MIRAGE). */
    val puddingCaps = CapabilityProfile(
        key = "pudding",
        adaptiveSupported = true,
        ancImplementation = AncImplementation.STANDARD,
        ancModes = listOf(
            AncModeOption(NoiseControlMode.NOISE_CANCELLATION, R.string.pudding_anc_basic),
            AncModeOption(NoiseControlMode.ADAPTIVE, R.string.pudding_anc_adaptive),
            AncModeOption(NoiseControlMode.NOISE_CANCELLATION_SMART, R.string.pudding_anc_wind),
        ),
        gainSupported = true,
        gainLabels = gainLabels,
        ledSupported = true,
        promptToneSupported = true,
        promptVolumeSupported = true,
        lhdcSupported = true,
        dualConnectionSupported = true,
        batteryForm = BatteryForm.TWS_THREE_WAY,
    )

    val pudding = DeviceProfileSpec(
        id = "pudding",
        displayName = "MOONDROP PUDDING",
        aliases = listOf("pudding", "布丁"),
        verified = true,
        imageKey = "moondrop_pudding",
        transports = listOf(PodTransport.RFCOMM_GAIA),
        capabilityProfile = puddingCaps,
        controlProfile = ControlProfile.PUDDING,
    )

    /** Mirage (海市蜃楼) — the PUDDING-linked variant; reuses both archives verbatim. */
    val mirage = DeviceProfileSpec(
        id = "mirage",
        displayName = "MOONDROP MIRAGE",
        aliases = listOf("mirage", "海市蜃楼"),
        verified = true,
        imageKey = "moondrop_mirage",
        transports = listOf(PodTransport.RFCOMM_GAIA),
        capabilityProfile = puddingCaps,
        controlProfile = ControlProfile.PUDDING,
    )

    // ---- Inferred model factory ----------------------------------------------------

    private fun inferred(
        id: String,
        displayName: String,
        aliases: List<String>,
        imageKey: String = id,
        pairs: List<Pair<NoiseControlMode, Int>> = ac4Pairs,
        path: AncPathKind = AncPathKind.AUDIO_CURATION,
        gain: IntArray? = null,
        led: Boolean = false,
        spatial: Boolean = false,
        headTracking: Boolean = false,
        promptTone: Boolean = false,
        lhdc: Boolean = false,
        dualConnection: Boolean = false,
        batteryForm: BatteryForm = BatteryForm.TWS_THREE_WAY,
        ldac: Boolean = false,
        lc3: Boolean = false,
        verified: Boolean = false,
    ): DeviceProfileSpec {
        val (setMap, getMap) = ancMaps(pairs, getSequential = path == AncPathKind.AUDIO_CURATION)
        val control = genericControl(path, setMap, getMap, gain ?: gainIdentity)
        // ancModes drives the geometry-only "ANC strength" tab. It must list ONLY the
        // noise-cancellation flavours (like PUDDING's basic/adaptive/wind), never OFF or
        // TRANSPARENCY — those two are controlled by the top three buttons. The full
        // `pairs` (including OFF/TRANSPARENCY) still feed the wire setMap/getMap above.
        val ancFlavours = pairs.map { it.first }.filter { it.isNoiseCancellation() }
        val caps = inferredCapability(
            modes = ancOptions(ancFlavours),
            spatial = spatial,
            headTracking = headTracking,
            gain = gain != null,
            led = led,
            promptTone = promptTone,
            lhdc = lhdc,
            dualConnection = dualConnection,
            batteryForm = batteryForm,
            ldac = ldac,
            lc3 = lc3,
        )
        return DeviceProfileSpec(
            id = id,
            displayName = displayName,
            aliases = aliases,
            verified = verified,
            imageKey = imageKey,
            transports = listOf(PodTransport.RFCOMM_GAIA),
            capabilityProfile = caps,
            controlProfile = control,
        )
    }

    /** Conservative declaration for a model with no real-hardware evidence. */
    private val conservativeCaps: CapabilityProfile
        get() = inferredCapability(
            modes = ancOptions(ac4Pairs.map { it.first }.filter { it.isNoiseCancellation() }),
            spatial = false, headTracking = false, gain = false, led = false,
            promptTone = false, lhdc = false, dualConnection = false,
            batteryForm = BatteryForm.TWS_THREE_WAY,
        )

    /**
     * MOONDROP Pill family — open-fit single-ear (OWS) earpieces, NOT TWS earbuds.
     * Protocol facts from HyperPILL (Moondrop Link v2.24.2c reverse engineering, real-device
     * tested on Pill Kita Ikuyo) and MoonEar DC_PROFILES: no ANC, no spatial/LED, identity
     * gain map, prompt tone + volume, dual-device connection, and a single battery cell
     * reported as id 0. All Pill variants share these two archives verbatim.
     */
    val pillCaps = CapabilityProfile(
        key = "pill",
        ancModes = emptyList(),
        gainSupported = true,
        gainLabels = gainLabels,
        promptToneSupported = true,
        promptVolumeSupported = true,
        dualConnectionSupported = true,
        batteryForm = BatteryForm.SINGLE_DEVICE,
    )

    /** Pill-family profile: shares [pillCaps] capability + [ControlProfile.PILL] control. */
    private fun pillProfile(
        id: String,
        displayName: String,
        aliases: List<String>,
        imageKey: String,
    ) = DeviceProfileSpec(
        id = id,
        displayName = displayName,
        aliases = aliases,
        verified = false,
        imageKey = imageKey,
        transports = listOf(PodTransport.RFCOMM_GAIA),
        capabilityProfile = pillCaps,
        controlProfile = ControlProfile.PILL,
    )

    // ---- Model catalogue (45: 2 verified + 43 inferred) ---------------------------

    val models: List<DeviceProfile> = listOf(
        pudding,
        mirage,
        // One-piece / headband / neckband models: only main art is bundled → single battery.
        inferred("bianca_stigmata", "BIANCA:STIGMATA", listOf("biancastigmata", "bianca", "stigmata"), "bianca_stigmata", batteryForm = BatteryForm.SINGLE_DEVICE),
        inferred("h_i_d_e_404_klukai", "H.I.D.E.404_Klukai", listOf("404klukai", "klukai"), "h_i_d_e_404_klukai"),
        // MOONDROP Pill family (HyperPILL): open-fit single-ear OWS sharing the Pill archives.
        pillProfile("laplace_oba", "LAPLACE-OBA-Ⅱ", listOf("laplaceoba", "laplace"), "laplace_oba"),
        // MoonEar real-device verified (DC_PROFILES): equal gain [0,1,2] + LED, no spatial.
        inferred("me_163_komet_moca", "Me 163 Komet MOCA", listOf("me163kometmoca", "komet"), "me_163_komet_moca", gain = gainIdentity, led = true, verified = true),
        inferred("alice", "MOONDROP Alice", listOf("alice"), "moondrop_alice"),
        inferred("moondrop_dots", "MOONDROP Dots", listOf("MOONDROP Dots"), "moondrop_dots"),
        inferred("edge", "MOONDROP EDGE", listOf("edge"), "moondrop_edge", pairs = ac3Pairs, gain = gainIdentity, batteryForm = BatteryForm.SINGLE_DEVICE),
        inferred("edge2", "MOONDROP EDGE 2", listOf("edge2", "edgeii"), "moondrop_edge_2", pairs = ac3Pairs, gain = gainIdentity, batteryForm = BatteryForm.SINGLE_DEVICE),
        inferred("moondrop_evo_2", "Moondrop EVO 2", listOf("Moondrop EVO 2"), "moondrop_evo_2"),
        // MoonEar real-device verified: AudioCuration 4-gang [1,2,4,3]/[0,1,2,3] (the
        // ac4Pairs + AUDIO_CURATION default), reversed gain, no LED. Both 梦回 (Golden Ages)
        // and 梦回2 (GA2) expose LC3 + LDAC codecs. 梦回2 additionally has spatial audio
        // (per real-device feedback; the earlier removal was based on a misread).
        inferred("golden_ages", "MOONDROP Golden Ages", listOf("goldenages"), "moondrop_golden_ages", gain = gainReversed, lc3 = true, ldac = true, verified = true),
        inferred("golden_ages_2", "MOONDROP Golden Ages 2", listOf("goldenages2", "ga2", "goldenagesii"), "moondrop_golden_ages_2", gain = gainReversed, promptTone = true, lc3 = true, ldac = true, spatial = true, verified = true),
        inferred("moondrop_littlewhite", "MOONDROP littlewhite", listOf("littlewhite"), "moondrop_littlewhite", batteryForm = BatteryForm.SINGLE_DEVICE),
        inferred("moondrop_mm3a", "MOONDROP MM3A", listOf("MOONDROP MM3A"), "moondrop_mm3a"),
        inferred("maca", "MOONDROP MACA", listOf("maca"), "moondrop_moca", lhdc = true, spatial = true),
        inferred("moondrop_moon_travel", "MOONDROP MOON TRAVEL", listOf("moontravel", "travel"), "moondrop_moon_travel"),
        inferred("nekocake", "MOONDROP NEKOCAKE", listOf("nekocake", "neko"), "moondrop_nekocake", lhdc = true),
        inferred("moondrop_nekocake_acht_acht_limited", "Moondrop Nekocake Acht Acht Limited", listOf("nekocakeachtacht", "nekocake"), "moondrop_nekocake_acht_acht_limited"),
        inferred("moondrop_nekocake_qbz_191", "Moondrop Nekocake QBZ-191", listOf("nekocakeqbz191", "nekocake"), "moondrop_nekocake_qbz_191"),
        pillProfile("pills", "MOONDROP PILLS", listOf("pills", "pill", "moondroppill"), "moondrop_pill"),
        inferred("moondrop_punishing_gray_raven", "Moondrop PUNISHING;GRAY RAVEN IOS", listOf("punishinggrayraven", "punishing", "raven"), "moondrop_punishing_gray_raven"),
        inferred("space_travel", "MOONDROP Space Travel", listOf("spacetravel"), "moondrop_space_travel"),
        inferred("sparks", "MOONDROP SPARKS", listOf("sparks"), "moondrop_sparks"),
        inferred("moondrop_sparxie", "MOONDROP Sparxie", listOf("sparxie"), "moondrop_sparxie"),
        inferred("moondrop_susanoo_tws", "Moondrop SUSANOO TWS IOS", listOf("susanoo"), "moondrop_susanoo_tws"),
        inferred("moondrop_the_garden", "MOONDROP The Garden", listOf("garden"), "moondrop_the_garden"),
        // MoonEar real-device verified: open-fit, hardware has NO ANC (use pairs with no modes).
        inferred("moondrop_u_c_t_s", "Moondrop U.C.T.S.", listOf("Moondrop U.C.T.S."), "moondrop_u_c_t_s", pairs = emptyList(), verified = true),
        inferred("moondrop_ultrasonic", "MOONDROP Ultrasonic", listOf("ultrasonic"), "moondrop_ultrasonic"),
        inferred("voyager", "MOONDROP VOYAGER", listOf("voyager"), "moondrop_voyager", batteryForm = BatteryForm.SINGLE_DEVICE),
        inferred("moondrop_x_yasuno_kiyono", "MOONDROP x YASUNO KIYONO", listOf("yasunokiyono", "yasuno", "kiyono"), "moondrop_x_yasuno_kiyono"),
        inferred("nicole_reeyn", "Nicole Reeyn", listOf("nicolereeyn", "nicole", "reeyn"), "nicole_reeyn", batteryForm = BatteryForm.SINGLE_DEVICE),
        pillProfile("pandaer_open_air_pill", "PANDAER Open Air Pill", listOf("pandaeropenairpill", "openairpill"), "pandaer_open_air_pill"),
        inferred("pandaer_space_travel_2", "PANDAER Space Travel 2", listOf("spacetravel", "space", "travel"), "pandaer_space_travel_2"),
        pillProfile("pill_gotoh_hitori", "Pill Gotoh Hitori", listOf("pillgotohhitori", "gotoh", "hitori"), "pill_gotoh_hitori"),
        pillProfile("pill_ijichi_nijika", "Pill Ijichi Nijika", listOf("pillijichinijika", "ijichi", "nijika"), "pill_ijichi_nijika"),
        pillProfile("pill_kita_ikuyo", "Pill Kita Ikuyo", listOf("pillkitaikuyo", "kitaikuyo", "ikuyo"), "pill_kita_ikuyo"),
        pillProfile("pill_yamada_ryo", "Pill Yamada Ryo", listOf("pillyamadaryo", "yamada", "ryo"), "pill_yamada_ryo"),
        inferred("punishing_gray_raven", "PUNISHING:GRAY RAVEN", listOf("punishinggrayraven", "punishing", "raven"), "punishing_gray_raven"),
        inferred("robins", "Robin's Earphones", listOf("robins", "robin", "robin's earphones"), "robin_s_earphones"),
        inferred("space_force", "Space Force", listOf("spaceforce", "space", "force"), "space_force"),
        // MoonEar real-device verified: AudioCuration 4-gang [1,2,4,3]/[0,1,2,3] (default),
        // reversed gain, no spatial/LED (2026-09-01 two-way measured data in MoonEar).
        // User-corrected 2026-11-22: display name "Space Travel 2"; real hardware supports
        // prompt tone + dual-device connection, and has NO codec (LHDC/LC3/LDAC) settings.
        inferred(
            "space_travel_2", "Space Travel 2", listOf("spacetravel2", "st2"), "space_travel_2",
            gain = gainReversed, promptTone = true, dualConnection = true, verified = true,
        ),
        // Space Travel 2 Ultra: MoonEar only records "verified working" with no control
        // command data and unpublished protocol. Keep conservative capabilities and treat
        // as protocol-inferred until real control mappings are known.
        inferred("space_travel_2_ultra", "MOONDROP Space Travel 2 Ultra", listOf("spacetravel2ultra"), "space_travel_2_ultra", gain = gainReversed),
        inferred("susanoo_tws", "SUSANOO TWS", listOf("susanoo"), "susanoo_tws"),
        inferred("zzz_angels_ows", "ZZZ-ANGELS-OWS", listOf("zzzangelsows", "angels"), "zzz_angels_ows"),
    )

    val byAlias: List<Pair<String, DeviceProfile>> = models
        .flatMap { model -> model.aliases.map { normalize(it) to model } }
        .sortedByDescending { it.first.length }

    /**
     * Layer-2 fallback: a MOONDROP-branded name with no matching model archive.
     * Uses [ControlProfile.PROBE_FALLBACK] (probe on connect, then conservative commands).
     */
    val fallback = DeviceProfileSpec(
        id = "fallback",
        displayName = "MOONDROP (inferred)",
        aliases = emptyList(),
        verified = false,
        imageKey = "default",
        transports = listOf(PodTransport.RFCOMM_GAIA),
        capabilityProfile = conservativeCaps,
        controlProfile = ControlProfile.PROBE_FALLBACK,
    )

    fun normalize(name: String?): String = name.orEmpty().lowercase()
        .replace("-", "").replace("_", "").replace(" ", "")

    internal fun nameMatches(name: String?, aliases: List<String>): Boolean {
        val n = normalize(name)
        if (n.isEmpty()) return false
        return aliases.any { alias -> n.contains(normalize(alias)) }
    }

    /** Alias-first, then layer-2 fallback for MOONDROP-branded names. */
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
