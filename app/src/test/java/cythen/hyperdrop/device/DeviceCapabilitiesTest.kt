package cythen.hyperdrop.device

import cythen.hyperdrop.config.ConfigManager
import cythen.hyperdrop.device.DeviceCapabilities
import cythen.hyperdrop.device.DeviceRegistry
import cythen.hyperdrop.device.PuddingProfile
import cythen.hyperdrop.device.StandardBluetoothProfile
import cythen.hyperdrop.pods.NoiseControlMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        // PuddingProfile is now a delegating alias for DeviceModels.pudding, so compare ids.
        assertEquals("pudding", DeviceRegistry.profileById("pudding").id)
        assertEquals(StandardBluetoothProfile, DeviceRegistry.profileById("does-not-exist"))
    }

    @Test
    fun `model matching is data driven with verified and inferred states`() {
        assertEquals("pudding", DeviceModels.match("MOONDROP PUDDING")?.id)
        assertEquals("mirage", DeviceModels.match("海市蜃楼")?.id)
        assertTrue(DeviceModels.match("MOONDROP PUDDING")!!.verified)
        assertTrue(DeviceModels.match("MOONDROP MIRAGE")!!.verified)
        assertFalse(DeviceModels.match("MOONDROP EDGE")!!.verified)
        // Unmatched MOONDROP name falls back to the conservative inferred profile.
        assertEquals("fallback", DeviceModels.match("MOONDROP something new")?.id)
        // Non-MOONDROP name is not a supported device.
        assertNull(DeviceModels.match("Some random headphones"))
        assertEquals(2, DeviceModels.models.count { it.verified })
    }

    @Test
    fun `pudding exposes all MiuixMoondrop-documented capabilities`() {
        val caps = DeviceRegistry.detectDeviceCapabilities("MOONDROP PUDDING")
        assertTrue(caps.gainSupported)
        assertTrue(caps.ledSupported)
        assertTrue(caps.promptToneSupported)
        assertTrue(caps.promptVolumeSupported)
        assertTrue(caps.lhdcSupported)
        assertTrue(caps.dualConnectionSupported)
        // Head tracking / spatial audio are not real-hardware verified for PUDDING.
        assertFalse(caps.headTrackingSupported)
        assertFalse(caps.spatialAudioSupported)
    }

    @Test
    fun `pudding command table handles extended VOICE LHDC and dual commands`() {
        val table = DeviceModels.pudding.commandTable
        assertEquals("00 1D 1C 01", table.queryVoiceConfig!!.joinToString(" ") { "%02X".format(it) })
        assertEquals("00 1D 20 05", table.queryLhdc!!.joinToString(" ") { "%02X".format(it) })
        assertEquals("00 1D 28 01", table.queryDualConnection!!.joinToString(" ") { "%02X".format(it) })
        assertEquals("00 1D 1C 02 01 32 00", table.setPromptTone(true, 50, 0)!!.joinToString(" ") { "%02X".format(it) })
        assertEquals("00 1D 20 06 01", table.setLhdc(true)!!.joinToString(" ") { "%02X".format(it) })
        assertEquals("00 1D 28 02 01", table.setDualConnection(true)!!.joinToString(" ") { "%02X".format(it) })
        assertTrue(table.parse(byteArrayOf(0, 0x1D, 0x1D, 0x01, 1, 50, 0)) is cythen.hyperdrop.device.protocol.GaiaReport.VoiceConfig)
        assertTrue(table.parse(byteArrayOf(0, 0x1D, 0x21, 0x05, 1)) is cythen.hyperdrop.device.protocol.GaiaReport.Lhdc)
        assertTrue(table.parse(byteArrayOf(0, 0x1D, 0x29, 0x01, 1)) is cythen.hyperdrop.device.protocol.GaiaReport.DualConnection)
    }

    @Test
    fun `inferred model caps carry ANC path and gain mapping`() {
        val edge = DeviceRegistry.profileById("edge")
        assertTrue(edge.capabilities.gainSupported)
        assertEquals(3, edge.capabilities.ancModes.size)
        // Pudding ANC set table is indexed by UI status 1..5.
        val pudding = DeviceModels.pudding.capabilities
        assertEquals(0, pudding.ancSetMap[1])  // UI status 1 = OFF -> wire 0
        assertEquals(4, pudding.ancSetMap[4])  // ADAPTIVE -> wire 4
        assertEquals(3, pudding.ancSetMap[5])  // WIND_REDUCTION -> wire 3
    }
}
