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

/** A selectable built-in colourway; the key maps to dev_{imageKey}_{key}_* drawables. */
data class ColorVariant(val key: String, val labelRes: Int)

object ColorVariants {
    val DEFAULT = ColorVariant("default", R.string.color_default)
    val BLACK = ColorVariant("black", R.string.color_variant_black)
    val WHITE = ColorVariant("white", R.string.color_variant_white)
    val PURPLE = ColorVariant("purple", R.string.color_variant_purple)
}

/** Data-driven [DeviceProfile]: every model-specific fact is a constructor argument. */
data class DeviceProfileSpec(
    override val id: String,
    override val displayName: String,
    override val aliases: List<String>,
    override val verified: Boolean,
    override val note: String,
    override val imageKey: String,
    override val colorVariants: List<ColorVariant>,
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
        caps: DeviceCapabilities,
        imageKey: String = id,
        colorVariants: List<ColorVariant> = listOf(ColorVariants.DEFAULT),
    ) = DeviceProfileSpec(
        id = id,
        displayName = displayName,
        aliases = aliases,
        verified = false,
        note = "Protocol inferred — not verified on real hardware",
        imageKey = imageKey,
        colorVariants = colorVariants,
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
        imageKey = "pudding",
        colorVariants = listOf(
            ColorVariants.DEFAULT,
            ColorVariants.BLACK,
            ColorVariants.WHITE,
            ColorVariants.PURPLE,
        ),
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
            // Per MiuixMoondrop's ADAPTATION.md feature matrix, PUDDING supports
            // prompt tone (on/off + volume), LHDC and dual-device connection.
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
        imageKey = "mirage",
        colorVariants = listOf(ColorVariants.DEFAULT),
        transports = listOf(PodTransport.RFCOMM_GAIA),
        capabilities = pudding.capabilities,
        commandTable = PuddingCommandTable,
    )

    // ---- Protocol-inferred models --------------------------------------------------

    val models: List<DeviceProfile> = listOf(
        pudding,
        mirage,
        inferred("edge", "MOONDROP EDGE", listOf("edge"),
            inferredCaps(ac3Pairs, gain = gainIdentity, singleDeviceBattery = true)),
        inferred("edge2", "MOONDROP EDGE 2", listOf("edge2", "edgeii"),
            inferredCaps(ac3Pairs, gain = gainIdentity)),
        inferred("golden_ages_2", "MOONDROP Golden Ages 2", listOf("goldenages2", "ga2", "goldenagesii"),
            inferredCaps(ac4Pairs, gain = gainReversed, promptTone = true)),
        inferred("space_travel_2", "MOONDROP Space Travel 2", listOf("spacetravel2", "st2"),
            inferredCaps(ac4Pairs, gain = gainReversed, spatial = true)),
        inferred("space_travel_2_ultra", "MOONDROP Space Travel 2 Ultra", listOf("spacetravel2ultra"),
            inferredCaps(ac4Pairs, gain = gainReversed, spatial = true)),
        inferred("space_travel", "MOONDROP Space Travel", listOf("spacetravel"),
            inferredCaps(ac4Pairs)),
        inferred("golden_ages", "MOONDROP Golden Ages", listOf("goldenages"),
            inferredCaps(ac4Pairs, gain = gainReversed)),
        inferred("maca", "MOONDROP MACA", listOf("maca"),
            inferredCaps(ac4Pairs, lhdc = true, spatial = true)),
        inferred("nekocake", "MOONDROP NEKOCAKE", listOf("nekocake", "neko"),
            inferredCaps(ac4Pairs, lhdc = true)),
        inferred("pills", "MOONDROP PILLS", listOf("pills"),
            inferredCaps(ac4Pairs)),
        inferred("sparks", "MOONDROP SPARKS", listOf("sparks"),
            inferredCaps(ac4Pairs)),
        inferred("voyager", "MOONDROP VOYAGER", listOf("voyager"),
            inferredCaps(ac4Pairs)),
        inferred("robins", "MOONDROP robins", listOf("robins", "robin"),
            inferredCaps(ac4Pairs)),
        inferred("alice", "MOONDROP Alice", listOf("alice"),
            inferredCaps(ac4Pairs)),
        inferred("block", "MOONDROP BLOCK", listOf("block"),
            DeviceCapabilities.NONE),
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
        colorVariants = listOf(ColorVariants.DEFAULT),
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
