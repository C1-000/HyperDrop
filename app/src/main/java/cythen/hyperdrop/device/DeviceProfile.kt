package cythen.hyperdrop.device

/**
 * One supported device is one [DeviceProfile]. Everything that varies between models is a
 * property of the profile; the rest of the app talks to the abstract type.
 *
 * Phase B owns identity + capabilities. Phase C adds the protocol members
 * (command table / transport) once those abstractions exist.
 *
 * Adding a new MOONDROP model = one new [DeviceProfile] implementation + one line in
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
}
