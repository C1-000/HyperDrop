package cythen.hyperdrop.device

import cythen.hyperdrop.config.ConfigManager
import cythen.hyperdrop.config.DeviceImageCatalog
import cythen.hyperdrop.device.DeviceCapabilities
import cythen.hyperdrop.device.DeviceRegistry
import cythen.hyperdrop.device.PuddingProfile
import cythen.hyperdrop.device.StandardBluetoothProfile
import cythen.hyperdrop.pods.NoiseControlMode
import cythen.hyperdrop.pods.isNoiseCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCapabilitiesTest {
    @Test
    fun `pudding profile owns its model names`() {
        assertTrue(PuddingProfile.matches("MOONDROP PUDDING"))
        assertTrue(PuddingProfile.matches("布丁"))
        assertTrue(PuddingProfile.matches("pudding"))
        assertFalse(PuddingProfile.matches("MD-TWS-056"))
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
        assertEquals(7, DeviceModels.models.count { it.verified })
        // MoonEar real-device verified models (+ MIRAGE linked variant). Space Travel 2
        // Ultra is NOT verified: it was reported as human-tested but has no published
        // control commands, so it stays conservative and inferred (verified=false).
        listOf("golden_ages", "golden_ages_2", "space_travel_2",
            "moondrop_u_c_t_s", "me_163_komet_moca", "pudding").forEach { id ->
            assertTrue("$id must be verified", DeviceRegistry.profileById(id).verified)
        }
    }

    @Test
    fun `verified profiles carry MoonEar real-device capabilities`() {
        // GA2 / Golden Ages: AudioCuration 4-gang, reversed gain, no LED. Both expose
        // LC3 + LDAC codecs; GA2 (梦回2) also has spatial audio per real-device feedback.
        val ga2 = DeviceRegistry.detectDeviceCapabilities("MOONDROP Golden Ages 2")
        assertTrue(ga2.spatialAudioSupported)
        assertTrue(ga2.lc3Supported)
        assertTrue(ga2.ldacSupported)
        val ga1979 = DeviceRegistry.detectDeviceCapabilities("MOONDROP Golden Ages")
        assertTrue(ga1979.lc3Supported)
        assertTrue(ga1979.ldacSupported)
        assertTrue(ga2.gainSupported)
        assertFalse(ga2.ledSupported)
        // ancModes lists only ANC flavours (NC + wind); OFF/TRANSPARENCY stay in the
        // wire setMap and are driven by the top three buttons, not the strength tab.
        assertEquals(2, ga2.ancModes.size)
        assertTrue(ga2.ancModes.all { it.mode.isNoiseCancellation() })
        // Space Travel 2: reversed gain, no spatial, no LED.
        val st2 = DeviceRegistry.detectDeviceCapabilities("MOONDROP Space Travel 2")
        assertTrue(st2.gainSupported)
        assertFalse(st2.spatialAudioSupported)
        assertFalse(st2.ledSupported)
        // U.C.T.S: open-fit, hardware has NO ANC.
        val ucts = DeviceRegistry.detectDeviceCapabilities("Moondrop U.C.T.S.")
        assertTrue(ucts.ancModes.isEmpty())
        // MOCA: equal gain + LED, no spatial.
        val moca = DeviceRegistry.detectDeviceCapabilities("Me 163 Komet MOCA")
        assertTrue(moca.gainSupported)
        assertTrue(moca.ledSupported)
        assertFalse(moca.spatialAudioSupported)
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
        // edge uses ac3Pairs (OFF/NC/TRANSPARENCY); only the NC flavour belongs in ancModes.
        assertEquals(1, edge.capabilities.ancModes.size)
        assertEquals(NoiseControlMode.NOISE_CANCELLATION, edge.capabilities.ancModes[0].mode)
        // Wire maps now live in the control profile, not capabilities.
        val puddingControl = DeviceModels.pudding.controlProfile
        assertEquals(0, puddingControl.ancSetMap[1])  // UI status 1 = OFF -> wire 0
        assertEquals(4, puddingControl.ancSetMap[4])  // ADAPTIVE -> wire 4
        assertEquals(3, puddingControl.ancSetMap[5])  // WIND_REDUCTION -> wire 3
    }

    @Test
    fun `model catalogue has 45 unique image keys with seven verified`() {
        assertEquals(45, DeviceModels.models.size)
        assertEquals(7, DeviceModels.models.count { it.verified })
        val keys = DeviceModels.models.map { it.imageKey }
        assertEquals("imageKey must be unique", keys.size, keys.toSet().size)
        // Every imageKey except the deliberate fallback resolves to a bundled set.
        DeviceModels.models.forEach { model ->
            val set = DeviceImageCatalog.resolve(model.imageKey)
            assertTrue("Unresolved art for ${model.imageKey}", set.box != 0)
            assertTrue("Unresolved art for ${model.imageKey}", set.left != 0)
            assertTrue("Unresolved art for ${model.imageKey}", set.right != 0)
        }
    }

    @Test
    fun `one-piece models fall back to box art for left and right`() {
        val onePiece = listOf(
            "bianca_stigmata", "moondrop_edge", "moondrop_edge_2",
            "moondrop_littlewhite", "moondrop_voyager", "nicole_reeyn",
        )
        onePiece.forEach { key ->
            val set = DeviceImageCatalog.resolve(key)
            assertEquals(key, set.box, set.left)
            assertEquals(key, set.box, set.right)
        }
    }

    @Test
    fun `unknown image key falls back to default art`() {
        assertEquals(
            DeviceImageCatalog.DEFAULT,
            DeviceImageCatalog.resolve("definitely_not_a_model"),
        )
    }

    @Test
    fun `control and capability archives are shared across models`() {
        // MIRAGE reuses both PUDDING archives verbatim — no duplicated capability block.
        assertTrue(DeviceModels.mirage.controlProfile === DeviceModels.pudding.controlProfile)
        assertTrue(DeviceModels.mirage.capabilityProfile === DeviceModels.pudding.capabilityProfile)
        // Wire maps live only in the control archive, never in capabilities.
        assertEquals(0, DeviceModels.pudding.controlProfile.ancSetMap[1])
    }

    @Test
    fun `one-piece models declare single device battery form`() {
        assertEquals(BatteryForm.SINGLE_DEVICE, DeviceRegistry.profileById("edge").capabilities.batteryForm)
        assertEquals(BatteryForm.SINGLE_DEVICE, DeviceRegistry.profileById("bianca_stigmata").capabilities.batteryForm)
        assertEquals(BatteryForm.TWS_THREE_WAY, DeviceModels.pudding.capabilities.batteryForm)
    }

    @Test
    fun `observation only opens capabilities and declaration wins on conflict`() {
        val declared = DeviceCapabilities.NONE.copy(adaptiveSupported = true, lhdcSupported = true)
        val observed = DeviceRegistry.resolveCapabilities(
            declared,
            setOf(CapabilityProfile.F_LED, CapabilityProfile.F_VOICE, CapabilityProfile.F_CODEC_TYPE),
        )
        // Declared true stays; declared false + observed bit opens.
        assertTrue(observed.adaptiveSupported)
        assertTrue(observed.lhdcSupported)
        assertTrue(observed.ledSupported)
        assertTrue(observed.promptToneSupported)
        assertTrue(observed.promptVolumeSupported)
        // No observed bit for spatial → stays closed.
        assertFalse(observed.spatialAudioSupported)
    }

    @Test
    fun `layer two fallback probes and is used only for moondrop names`() {
        assertTrue(DeviceRegistry.shouldProbeOnConnect("MOONDROP something unknown"))
        assertFalse(DeviceRegistry.shouldProbeOnConnect("Sony XM5"))
        assertEquals("fallback", DeviceRegistry.profileFor("MOONDROP something unknown").id)
    }

    // ---- Protocol audit fixes (v1.1.2beta2) -------------------------------------

    @Test
    fun `supported features decode as featureId-version pairs not a bitmask`() {
        val table = cythen.hyperdrop.device.protocol.GenericGaiaCommandTable()
        // hasMore=1 at payload[4]; pairs: (F_BASIC=0,v1), (LED=19,v2), (ANC_V2=32,v1).
        // A bitmask decoder would have produced garbage bit indices instead of these ids.
        val report = table.parse(
            byteArrayOf(0, 0x1D, 0x01, 0x01, 0x01, 0x00, 0x01, 19, 0x02, 32, 0x01)
        )
        assertTrue(report is cythen.hyperdrop.device.protocol.GaiaReport.SupportedFeatures)
        val f = report as cythen.hyperdrop.device.protocol.GaiaReport.SupportedFeatures
        assertTrue(f.hasMore)
        assertEquals(setOf(0, 19, 32), f.features)
        assertNotNullPagedNext(table)
    }

    private fun assertNotNullPagedNext(table: cythen.hyperdrop.device.protocol.GaiaCommandTable) {
        assertEquals(
            "00 1D 00 02",
            table.querySupportedFeaturesNext!!.joinToString(" ") { "%02X".format(it) },
        )
    }

    @Test
    fun `spatial audio feature id is 18 not 24`() {
        assertEquals(18, CapabilityProfile.F_SPATIAL_AUDIO)
        // Observation driven by a reported feature id 18 must open spatial capability.
        val observed = DeviceRegistry.resolveCapabilities(
            DeviceCapabilities.NONE,
            setOf(CapabilityProfile.F_SPATIAL_AUDIO),
        )
        assertTrue(observed.spatialAudioSupported)
        assertTrue(observed.spatialSoundSwitchSupported)
    }

    @Test
    fun `ANC_V1 uses GET mode 0x04 SET mode 0x05 and matching response subs`() {
        val table = cythen.hyperdrop.device.protocol.GenericGaiaCommandTable(
            ancPathKind = cythen.hyperdrop.device.protocol.AncPathKind.ANC_V1,
            ancSetMap = intArrayOf(-1, 0, 1, 2, 3),
            ancGetMap = intArrayOf(1, 2, 3, 4),
        )
        assertEquals("00 1D 04 04", table.queryNoiseMode.joinToString(" ") { "%02X".format(it) })
        // ui 2 -> ancSetMap[2] = 1 wire
        assertEquals(
            "00 1D 04 05 01",
            table.setNoiseMode(2)!!.joinToString(" ") { "%02X".format(it) },
        )
        // Response cmd 0x05 with sub 0x04/0x05 is accepted; old 0x03 was never hit.
        val r = table.parse(byteArrayOf(0, 0x1D, 0x05, 0x04, 0x01))
        assertTrue(r is cythen.hyperdrop.device.protocol.GaiaReport.AncMode)
    }

    @Test
    fun `codec capability opens only from a real codec report not the coarse feature bit`() {
        // GAIA feature 16 only signals "codec control present" and cannot distinguish
        // LC3 / LDAC / LHDC. It must NOT open any codec by itself — doing so would show
        // codec switches on devices that do not implement them.
        val coarseOnly = DeviceRegistry.resolveCapabilities(
            DeviceCapabilities.NONE,
            setOf(CapabilityProfile.F_CODEC_TYPE),
        )
        assertFalse(coarseOnly.lhdcSupported)
        assertFalse(coarseOnly.lc3Supported)
        assertFalse(coarseOnly.ldacSupported)

        // A confirmed per-codec GET report opens exactly that codec, on the matching key.
        val name = "MOONDROP codec probe test"
        DeviceRegistry.clearObservedFeatures(name)
        assertFalse(DeviceRegistry.detectDeviceCapabilities(name).lc3Supported)
        DeviceRegistry.recordObservedCodec(name, DeviceRegistry.ObservedCodec.LC3)
        val observed = DeviceRegistry.detectDeviceCapabilities(name)
        assertTrue(observed.lc3Supported)
        assertFalse(observed.ldacSupported)
        assertFalse(observed.lhdcSupported)
        DeviceRegistry.recordObservedCodec(name, DeviceRegistry.ObservedCodec.LDAC)
        DeviceRegistry.recordObservedCodec(name, DeviceRegistry.ObservedCodec.LHDC)
        val all = DeviceRegistry.detectDeviceCapabilities(name)
        assertTrue(all.lc3Supported)
        assertTrue(all.ldacSupported)
        assertTrue(all.lhdcSupported)
        // Clearing observations also clears codec attribution (e.g. device switch).
        DeviceRegistry.clearObservedFeatures(name)
        assertFalse(DeviceRegistry.detectDeviceCapabilities(name).lc3Supported)
    }

    @Test
    fun `LC3 codec command bytes and parse are wired on both tables`() {
        val generic = cythen.hyperdrop.device.protocol.GenericGaiaCommandTable()
        val pudding = DeviceModels.pudding.commandTable
        listOf(generic, pudding).forEach { table ->
            assertEquals("00 1D 20 01", table.queryLc3!!.joinToString(" ") { "%02X".format(it) })
            assertEquals("00 1D 20 03 01", table.setLc3(true)!!.joinToString(" ") { "%02X".format(it) })
            assertEquals("00 1D 20 03 00", table.setLc3(false)!!.joinToString(" ") { "%02X".format(it) })
            assertTrue(table.parse(byteArrayOf(0, 0x1D, 0x21, 0x01, 1)) is cythen.hyperdrop.device.protocol.GaiaReport.Lc3)
            assertTrue(table.parse(byteArrayOf(0, 0x1D, 0x21, 0x03, 0)) is cythen.hyperdrop.device.protocol.GaiaReport.Lc3)
            // LHDC subs still decode as LHDC, not LC3.
            assertTrue(table.parse(byteArrayOf(0, 0x1D, 0x21, 0x05, 1)) is cythen.hyperdrop.device.protocol.GaiaReport.Lhdc)
        }
    }

    @Test
    fun `LDAC codec command bytes and parse are wired on both tables`() {
        val generic = cythen.hyperdrop.device.protocol.GenericGaiaCommandTable()
        val pudding = DeviceModels.pudding.commandTable
        listOf(generic, pudding).forEach { table ->
            assertEquals("00 1D 20 02", table.queryLdac!!.joinToString(" ") { "%02X".format(it) })
            assertEquals("00 1D 20 04 01", table.setLdac(true)!!.joinToString(" ") { "%02X".format(it) })
            assertEquals("00 1D 20 04 00", table.setLdac(false)!!.joinToString(" ") { "%02X".format(it) })
            assertTrue(table.parse(byteArrayOf(0, 0x1D, 0x21, 0x02, 1)) is cythen.hyperdrop.device.protocol.GaiaReport.Ldac)
            assertTrue(table.parse(byteArrayOf(0, 0x1D, 0x21, 0x04, 0)) is cythen.hyperdrop.device.protocol.GaiaReport.Ldac)
        }
    }

    @Test
    fun `robin models match by the Robin's Earphones name`() {
        val robin = DeviceModels.match("ROBIN'S EARPHONES")
        assertEquals("robins", robin?.id)
        assertEquals("Robin's Earphones", robin?.displayName)
        assertEquals("robins", DeviceModels.match("MOONDROP robins")?.id)
        assertEquals("robins", DeviceModels.match("robin")?.id)
    }

    @Test
    fun `battery level masks the high charging bit`() {
        val table = cythen.hyperdrop.device.protocol.GenericGaiaCommandTable()
        // Left=0x80|85 => level 85 with a stray charging bit must still be reported.
        val r = table.parse(byteArrayOf(0, 0x1D, 0x1B, 0x01, 0x01, (0x80 or 85).toByte()))
        assertTrue(r is cythen.hyperdrop.device.protocol.GaiaReport.Battery)
        assertEquals(85, (r as cythen.hyperdrop.device.protocol.GaiaReport.Battery).left)
    }
}
