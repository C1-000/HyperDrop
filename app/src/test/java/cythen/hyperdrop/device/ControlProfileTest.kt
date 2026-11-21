package cythen.hyperdrop.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The split between a model record and the control profile it points at.
 *
 * What this guards: a model record must not restate a wire vocabulary. Two models that speak the
 * same dialect hold the *same* [ControlProfile] instance, so a mapping fix is a one-line edit and
 * the two can never drift apart.
 */
class ControlProfileTest {

    @Test
    fun `models sharing a control profile also share the table instance`() {
        // Same instance, not just equal contents: there is exactly one PUDDING vocabulary.
        assertSame(DeviceModels.pudding.commandTable, DeviceModels.mirage.commandTable)
        assertSame(DeviceModels.pudding.control, DeviceModels.mirage.control)
    }

    @Test
    fun `unverified models all share the single probing table`() {
        val first = DeviceModels.models.first { !it.verified }
        DeviceModels.models.filter { !it.verified }.forEach { model ->
            assertSame(
                "${model.id} must not own a hand-written table",
                first.commandTable,
                model.commandTable,
            )
        }
    }

    @Test
    fun `only verified models get a dedicated control profile`() {
        // Everything unverified shares the probing fallback: a name is not wire evidence.
        DeviceModels.models.filter { !it.verified }.forEach { model ->
            assertEquals(
                "${model.id} must fall back to the probing vocabulary",
                ControlProfiles.probeFallback,
                model.control,
            )
        }
    }

    @Test
    fun `control profile catalogue has no duplicate ids`() {
        val ids = ControlProfiles.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `probe fallback keeps a gain mapping so a reported DAC_GAIN can unlock it`() {
        // The mapping is an identity assumption, not evidence: it exists only so that
        // applyObservedFeatures has something to open. Visibility is still gated by
        // DeclaredFeatures.gain, which is false here.
        assertTrue("an empty map could never be unlocked", ControlProfiles.probeFallback.hasGain)
        assertEquals(listOf(0, 1, 2), ControlProfiles.probeFallback.gainMap.toList())
        assertTrue(ControlProfiles.probeFallback.hasAnc)
        assertFalse(ControlProfiles.probeFallback.verifiedOnHardware)
    }

    @Test
    fun `probe fallback hides gain until the device reports the feature`() {
        val edge = DeviceRegistry.profileById("edge")
        assertFalse("declared.gain is false, so nothing is shown yet", edge.capabilities.gainSupported)
        assertTrue(
            "but the mapping exists, so the probe gate can open it",
            edge.capabilities.gainMap.isNotEmpty(),
        )
    }

    @Test
    fun `pudding control profile carries the hardware-verified wire values`() {
        val pudding = ControlProfiles.pudding
        assertTrue(pudding.verifiedOnHardware)
        assertTrue(pudding.hasGain)
        // UI Low/Medium/High -> wire 2/1/0, straight off the real device.
        assertEquals(listOf(2, 1, 0), pudding.gainMap.toList())
        // ancSetMap is indexed by the 1-based UI status constants.
        assertEquals(0, pudding.ancSetMap[1])   // OFF
        assertEquals(1, pudding.ancSetMap[2])   // NOISE_CANCELLATION
        assertEquals(2, pudding.ancSetMap[3])   // TRANSPARENCY
        assertEquals(4, pudding.ancSetMap[4])   // ADAPTIVE
        assertEquals(3, pudding.ancSetMap[5])   // NOISE_CANCELLATION_SMART
    }

    @Test
    fun `form factor only decides battery layout, never wire values`() {
        // One battery for anything that is not a two-earbud-plus-case product.
        assertFalse(DeviceFormFactor.TWS.singleBatteryByDefault)
        listOf(
            DeviceFormFactor.ONE_PIECE,
            DeviceFormFactor.HEADPHONES,
            DeviceFormFactor.SPEAKER,
            DeviceFormFactor.WIRED_DONGLE,
        ).forEach { assertTrue("$it must default to a single battery", it.singleBatteryByDefault) }
    }

    @Test
    fun `battery layout follows the declared shape through the registry`() {
        // MOONDROP MM3A is a speaker, BIANCA:STIGMATA is a headphone: both report one battery.
        assertTrue(DeviceRegistry.detectDeviceCapabilities("MOONDROP MM3A").singleDeviceBattery)
        assertTrue(DeviceRegistry.detectDeviceCapabilities("BIANCA:STIGMATA").singleDeviceBattery)
        // PUDDING is TWS, so the UI expects left / right / case.
        assertFalse(DeviceRegistry.detectDeviceCapabilities("MOONDROP PUDDING").singleDeviceBattery)
    }

    @Test
    fun `known one-piece and headphone models are shaped correctly`() {
        assertEquals(DeviceFormFactor.HEADPHONES, DeviceRegistry.formFactorFor("BIANCA:STIGMATA"))
        assertEquals(DeviceFormFactor.SPEAKER, DeviceRegistry.formFactorFor("MOONDROP MM3A"))
        // Unrecognised MOONDROP names default to the TWS shape, which is the common case.
        assertEquals(DeviceFormFactor.TWS, DeviceRegistry.formFactorFor("MOONDROP something new"))
    }

    @Test
    fun `anc tabs can be narrower than the wire vocabulary`() {
        val pudding = DeviceModels.pudding
        // Three product tabs...
        assertEquals(3, pudding.capabilities.ancModes.size)
        // ...but all five wire values remain addressable, so nothing is unreachable.
        assertEquals(5, pudding.control.ancPairs.size)
    }
}
