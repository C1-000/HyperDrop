package cythen.hyperdrop.device

import cythen.hyperdrop.device.protocol.NoOpGaiaCommandTable

/**
 * Conservative fallback for a Bluetooth name that matches no known MOONDROP model.
 *
 * It deliberately claims no capabilities and no name, so an unknown device never gets
 * PUDDING-only controls. It is only returned by [DeviceRegistry.profileFor] as a
 * fallback, never matched directly.
 */
object StandardBluetoothProfile : DeviceProfile {
    override val id: String = "standard_bluetooth"
    override val displayName: String = "Standard Bluetooth"

    override fun matches(name: String?): Boolean = false

    override val capabilities: DeviceCapabilities = DeviceCapabilities.NONE

    override val commandTable = NoOpGaiaCommandTable
}
