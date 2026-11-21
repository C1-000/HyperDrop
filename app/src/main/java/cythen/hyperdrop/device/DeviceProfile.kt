package cythen.hyperdrop.device

import cythen.hyperdrop.device.protocol.GaiaCommandTable

/**
 * One supported device is one [DeviceProfile].
 *
 * A profile is deliberately split into three independent axes, because they are maintained by
 * different evidence and change at different speeds:
 *
 *  - **identity** — [id], [displayName], [aliases], [imageKey]: how the device is recognised and
 *    drawn. Changes when a new model ships.
 *  - **shape** — [formFactor]: what kind of product it is (TWS / one-piece / headphone / speaker).
 *    Drives layout choices such as one battery vs three, never a command byte.
 *  - **wire vocabulary** — [control]: the shared command-mapping profile. Several models that
 *    speak the same dialect point at the same [ControlProfile] (MIRAGE is a PUDDING variant and
 *    reuses `ControlProfiles.pudding`), so a mapping fix is made once.
 *
 * [capabilities] is not stored independently: it is the *derived* view of the other three, which is
 * what keeps the UI gate and the command table from drifting apart.
 *
 * Adding a new MOONDROP model = one entry in [DeviceModels.models] naming its [ControlProfile] and
 * [DeviceFormFactor]. No change needed in pods/, hook/ or ui/.
 */
interface DeviceProfile {
    /** Stable identifier, e.g. "pudding". Used in IPC payloads. */
    val id: String

    /** Human-readable name shown in device pickers / debug UI. */
    val displayName: String

    /** True when this profile owns the given Bluetooth name/alias. */
    fun matches(name: String?): Boolean

    /** Derived capability view: declared shape + control profile + declared feature set. */
    val capabilities: DeviceCapabilities

    /**
     * Byte-level command vocabulary for this model's GAIA flavour.
     *
     * Derived from [control] so there is exactly one place that can define a wire mapping.
     */
    val commandTable: GaiaCommandTable get() = control.table

    /**
     * The shared command-mapping profile this model speaks.
     *
     * Defaults to [ControlProfiles.probeFallback]: standard GAIA constants with capabilities left
     * to runtime probing. Only give a model its own profile once there is real evidence for it.
     */
    val control: ControlProfile get() = ControlProfiles.probeFallback

    /**
     * Product shape, used for layout decisions.
     *
     * This is a physical fact, not a protocol fact: it must never select a command byte. It does
     * decide whether the UI expects one battery (speaker / headphone / one-piece) or three
     * (TWS case + two earbuds).
     */
    val formFactor: DeviceFormFactor get() = DeviceFormFactor.TWS

    // ---- Declarative metadata (data-driven model registry) ------------------------

    /** Normalised Bluetooth-name tokens that identify this model. */
    val aliases: List<String> get() = listOf(id)

    /** True only for models verified on real hardware; false = protocol inferred. */
    val verified: Boolean get() = false

    /** Short provenance / caveat shown in the detail UI ("实机验证" vs "协议推断"). */
    val note: String get() = ""

    /** Stable key used to locate this model's built-in art (asset slug). */
    val imageKey: String get() = id

    /**
     * Transport candidates for this model, in the order the session should probe them.
     *
     * This is a hint, not a contract: the session still probes, so a device that answers on a
     * different transport than expected is handled by discovery rather than by a wrong table
     * entry. The default prefers RFCOMM because that is the only transport verified on real
     * hardware so far.
     */
    val transports: List<PodTransport> get() = listOf(PodTransport.RFCOMM_GAIA)
}
