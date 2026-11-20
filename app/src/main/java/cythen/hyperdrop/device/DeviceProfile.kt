package cythen.hyperdrop.device

import cythen.hyperdrop.device.protocol.GaiaCommandTable

/**
 * One supported device is one [DeviceProfile]. Everything that varies between models is a
 * property of the profile; the rest of the app talks to the abstract type.
 *
 * Adding a new MOONDROP model = one new [DeviceProfile] implementation + a
 * [GaiaCommandTable] for its wire protocol + one line in
 * [DeviceRegistry.registeredProfiles]. No change needed in pods/, hook/ or ui/.
 */
interface DeviceProfile {
    /** Stable identifier, e.g. "pudding". Used in IPC payloads from phase F. */
    val id: String

    /** Human-readable name shown in device pickers / debug UI. */
    val displayName: String

    /** True when this profile owns the given Bluetooth name/alias. */
    fun matches(name: String?): Boolean

    /** Auto-detected capabilities for this model, before user overrides. */
    val capabilities: DeviceCapabilities

    /** Byte-level command vocabulary for this model's GAIA flavour. */
    val commandTable: GaiaCommandTable

    // ---- Declarative metadata (data-driven model registry) ------------------------

    /** Normalised Bluetooth-name tokens that identify this model. */
    val aliases: List<String> get() = listOf(id)

    /** True only for models verified on real hardware; false = protocol inferred. */
    val verified: Boolean get() = false

    /** Short provenance / caveat shown in the detail UI ("实机验证" vs "协议推断"). */
    val note: String get() = ""

    /** Stable key used to locate this model's built-in art (dev_{key}_{variant}_*). */
    val imageKey: String get() = id

    /** Built-in colour variants; the UI offers a picker when more than one is present. */
    val colorVariants: List<ColorVariant> get() = listOf(ColorVariants.DEFAULT)

    /** Transport capabilities recorded for this model. Only RFCOMM is implemented today. */
    val transports: List<PodTransport> get() = listOf(PodTransport.RFCOMM_GAIA)
}
