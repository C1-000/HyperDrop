package cythen.hyperdrop.device

import cythen.hyperdrop.device.protocol.PuddingCommandTable
import cythen.hyperdrop.pods.AncImplementation

/**
 * MOONDROP PUDDING / MD-TWS-056 — the first supported model.
 *
 * Every model-specific fact lives in a DeviceProfile. Supporting another MOONDROP
 * model means adding one more profile and one line in [DeviceRegistry.registeredProfiles].
 */
object PuddingProfile : DeviceProfile {
    override val id: String = "pudding"
    override val displayName: String = "MOONDROP PUDDING / MD-TWS-056"

    override fun matches(name: String?): Boolean {
        val normalized = name.orEmpty().lowercase()
            .replace("-", "").replace("_", "").replace(" ", "")
        return normalized.contains("pudding") || normalized.contains("mdtws056")
    }

    override val capabilities: DeviceCapabilities = DeviceCapabilities(
        adaptiveSupported = true,
        spatialAudioSupported = false,
        spatialSoundSwitchSupported = false,
        ancImplementation = AncImplementation.STANDARD,
    )

    override val commandTable = PuddingCommandTable
}
