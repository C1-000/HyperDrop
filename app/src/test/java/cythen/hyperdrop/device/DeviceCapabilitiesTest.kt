package cythen.hyperdrop.device

import cythen.hyperdrop.config.ConfigManager
import cythen.hyperdrop.device.DeviceCapabilities
import cythen.hyperdrop.device.DeviceRegistry
import cythen.hyperdrop.device.PuddingProfile
import cythen.hyperdrop.device.StandardBluetoothProfile
import cythen.hyperdrop.pods.NoiseControlMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCapabilitiesTest {
    @Test
    fun `pudding profile owns its model names`() {
        assertTrue(PuddingProfile.matches("MOONDROP PUDDING"))
        assertTrue(PuddingProfile.matches("MD-TWS-056"))
        assertTrue(PuddingProfile.matches("pudding"))
        assertFalse(PuddingProfile.matches("Some random headphones"))
    }

    @Test
    fun `registry reports support only for known models`() {
        assertTrue(DeviceRegistry.isSupported("MOONDROP PUDDING"))
        assertFalse(DeviceRegistry.isSupported(""))
        assertFalse(DeviceRegistry.isSupported("   "))
        assertFalse(DeviceRegistry.isSupported("---"))
        assertFalse(DeviceRegistry.isSupported("Some random headphones"))
    }

    @Test
    fun `pudding capabilities come from its profile`() {
        val caps = DeviceRegistry.detectDeviceCapabilities("MOONDROP PUDDING")
        assertTrue(caps.adaptiveSupported)
        assertFalse(caps.spatialAudioSupported)
        assertFalse(caps.spatialSoundSwitchSupported)
    }

    @Test
    fun `unknown device falls back to no capabilities`() {
        val caps = DeviceRegistry.detectDeviceCapabilities("Unknown TWS")
        assertEquals(DeviceCapabilities.NONE, caps)
        assertFalse(caps.adaptiveSupported)
    }

    @Test
    fun `anc strength tabs come from the profile`() {
        val caps = DeviceRegistry.detectDeviceCapabilities("MOONDROP PUDDING")
        assertEquals(3, caps.ancModes.size)
        assertEquals(NoiseControlMode.NOISE_CANCELLATION, caps.ancModes[0].mode)
        assertEquals(NoiseControlMode.ADAPTIVE, caps.ancModes[1].mode)
        assertEquals(NoiseControlMode.NOISE_CANCELLATION_SMART, caps.ancModes[2].mode)
        assertTrue(DeviceCapabilities.NONE.ancModes.isEmpty())
    }

    @Test
    fun `user overrides are applied on top of the profile`() {
        val forcedOn = DeviceRegistry.detectDeviceCapabilities(
            "Unknown TWS",
            adaptiveOverride = ConfigManager.CAPABILITY_OVERRIDE_FORCE_ENABLED,
        )
        assertTrue(forcedOn.adaptiveSupported)

        val forcedOff = DeviceRegistry.detectDeviceCapabilities(
            "MOONDROP PUDDING",
            adaptiveOverride = ConfigManager.CAPABILITY_OVERRIDE_FORCE_DISABLED,
        )
        assertFalse(forcedOff.adaptiveSupported)
    }

    @Test
    fun `profile lookup by id`() {
        assertEquals(PuddingProfile, DeviceRegistry.profileById("pudding"))
        assertEquals(StandardBluetoothProfile, DeviceRegistry.profileById("does-not-exist"))
    }
}
