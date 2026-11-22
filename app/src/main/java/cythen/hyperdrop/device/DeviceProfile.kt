package cythen.hyperdrop.device

import cythen.hyperdrop.device.protocol.GaiaCommandTable

/**
 * One supported device = one model archive ("机型档案").
 *
 * The profile is deliberately thin: it references a reusable [CapabilityProfile]
 * (what the device can do) and a reusable [ControlProfile] (how to command it), plus
 * identity/matching/image/verification metadata. Adding a model that reuses an existing
 * mapping (e.g. MIRAGE = PUDDING) is now a single record — no capability or command copy.
 */
interface DeviceProfile {
    /** Stable identifier, e.g. "pudding". Used in IPC payloads. */
    val id: String

    /** Human-readable name shown in device pickers / debug UI. */
    val displayName: String

    /** True when this profile owns the given Bluetooth name/alias. */
    fun matches(name: String?): Boolean

    /** Declared capability archive reference. */
    val capabilityProfile: CapabilityProfile

    /** Declared command-mapping archive reference. */
    val controlProfile: ControlProfile

    /** Capability view (declared, before runtime observation and user overrides). */
    val capabilities: DeviceCapabilities get() = capabilityProfile.toCapabilities()

    /** Byte-level command vocabulary — convenience view of [controlProfile]. */
    val commandTable: GaiaCommandTable get() = controlProfile.table

    // ---- Declarative metadata -----------------------------------------------------

    /** Normalised Bluetooth-name tokens that identify this model. */
    val aliases: List<String> get() = listOf(id)

    /** True only for models verified on real hardware; false = protocol inferred. */
    val verified: Boolean get() = false

    /** Stable key used to locate this model's built-in art (asset slug). */
    val imageKey: String get() = id

    /** Transport capabilities recorded for this model. Only RFCOMM is implemented today. */
    val transports: List<PodTransport> get() = listOf(PodTransport.RFCOMM_GAIA)
}
