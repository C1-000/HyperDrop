package cythen.hyperdrop.device

import cythen.hyperdrop.device.protocol.AncPathKind
import cythen.hyperdrop.device.protocol.GaiaFeature
import cythen.hyperdrop.device.protocol.ObservedFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Capability gating from the feature list an earbud reports.
 *
 * The rule under test throughout: the profile stays authoritative, and a reported feature can
 * only ever turn a control *on*. A partial list, a list missing ANC, or no list at all must
 * never hide a control that already works — firmware commonly reports a fraction of what it
 * implements.
 */
class ObservedFeaturesTest {

    private val puddingName = "MOONDROP PUDDING"
    private val inferredName = "MOONDROP Space Travel"

    @Test
    fun `empty observation leaves capabilities untouched`() {
        val declared = DeviceRegistry.detectDeviceCapabilities(puddingName)
        val gated = DeviceRegistry.applyObservedFeatures(puddingName, ObservedFeatures.UNKNOWN)
        assertEquals(declared, gated)
    }

    @Test
    fun `reported anc v2 keeps the declared path for a verified model`() {
        val observed = ObservedFeatures(setOf(GaiaFeature.ANC_V2, GaiaFeature.BATTERY))
        val gated = DeviceRegistry.applyObservedFeatures(puddingName, observed)
        assertEquals(AncPathKind.ANC_V2, gated.ancPath)
    }

    @Test
    fun `verified model does not switch path when only audio curation is reported`() {
        val observed = ObservedFeatures(setOf(GaiaFeature.AUDIO_CURATION))
        val gated = DeviceRegistry.applyObservedFeatures(puddingName, observed)
        assertEquals(
            "a verified model must keep the path confirmed on real hardware",
            AncPathKind.ANC_V2,
            gated.ancPath,
        )
    }

    @Test
    fun `inferred model switches to the anc feature the device reports`() {
        val declared = DeviceRegistry.detectDeviceCapabilities(inferredName)
        assertEquals(AncPathKind.AUDIO_CURATION, declared.ancPath)

        val observed = ObservedFeatures(setOf(GaiaFeature.ANC_V2))
        val gated = DeviceRegistry.applyObservedFeatures(inferredName, observed)
        assertEquals(AncPathKind.ANC_V2, gated.ancPath)
    }

    @Test
    fun `inferred model keeps declared path when the device reports neither anc feature`() {
        val observed = ObservedFeatures(setOf(GaiaFeature.BASIC, GaiaFeature.BATTERY))
        val gated = DeviceRegistry.applyObservedFeatures(inferredName, observed)
        assertEquals(AncPathKind.AUDIO_CURATION, gated.ancPath)
    }

    @Test
    fun `reported features can enable a control the profile left off`() {
        val declared = DeviceRegistry.detectDeviceCapabilities(inferredName)
        assertFalse("precondition: conservative profile has no LED", declared.ledSupported)

        val gated = DeviceRegistry.applyObservedFeatures(
            inferredName,
            ObservedFeatures(setOf(GaiaFeature.LED)),
        )
        assertTrue(gated.ledSupported)
    }

    @Test
    fun `reported features never disable a declared control`() {
        val declared = DeviceRegistry.detectDeviceCapabilities(puddingName)
        assertTrue("precondition: PUDDING declares LED", declared.ledSupported)
        assertTrue("precondition: PUDDING declares gain", declared.gainSupported)

        val observed = ObservedFeatures(setOf(GaiaFeature.BASIC))
        val gated = DeviceRegistry.applyObservedFeatures(puddingName, observed)

        assertTrue(gated.ledSupported)
        assertTrue(gated.gainSupported)
        assertTrue(gated.lhdcSupported)
        assertTrue(gated.dualConnectionSupported)
    }

    @Test
    fun `a reported DAC_GAIN actually unlocks gain on an inferred model`() {
        // Guards the gate itself: applyObservedFeatures requires gainMap.isNotEmpty(), so a
        // vocabulary with an empty map would silently stay closed forever.
        val before = DeviceRegistry.detectDeviceCapabilities(inferredName)
        assertFalse("precondition: nothing declared", before.gainSupported)

        val after = DeviceRegistry.applyObservedFeatures(
            inferredName,
            ObservedFeatures(setOf(GaiaFeature.DAC_GAIN)),
        )
        assertTrue("a genuine DAC_GAIN report must surface the gain control", after.gainSupported)
        assertTrue("and it needs a usable mapping to drive", after.gainMap.isNotEmpty())
    }

    @Test
    fun `unknown device stays at no capabilities even when features are reported`() {
        val gated = DeviceRegistry.applyObservedFeatures(
            "Some Random Headset",
            ObservedFeatures(setOf(GaiaFeature.ANC_V2, GaiaFeature.LED, GaiaFeature.CODEC_TYPE)),
        )
        assertEquals(DeviceCapabilities.NONE, gated)
    }

    @Test
    fun `merge accumulates pages and tracks completion`() {
        val first = ObservedFeatures.UNKNOWN.merge(setOf(GaiaFeature.BASIC), hasMore = true)
        assertEquals(1, first.pages)
        assertFalse(first.complete)

        val second = first.merge(setOf(GaiaFeature.ANC_V2), hasMore = false)
        assertEquals(setOf(GaiaFeature.BASIC, GaiaFeature.ANC_V2), second.features)
        assertEquals(2, second.pages)
        assertTrue(second.complete)
    }

    @Test
    fun `merge keeps features already observed on an empty later page`() {
        val first = ObservedFeatures(setOf(GaiaFeature.LED), pages = 1, complete = false)
        val second = first.merge(emptySet(), hasMore = false)
        assertEquals(setOf(GaiaFeature.LED), second.features)
        assertTrue(second.complete)
        assertFalse(second.isEmpty)
    }

    @Test
    fun `candidate paths lead with the declared path and exclude anc v1`() {
        val pudding = DeviceRegistry.detectDeviceCapabilities(puddingName)
        assertEquals(
            listOf(AncPathKind.ANC_V2, AncPathKind.AUDIO_CURATION),
            pudding.ancPathCandidates,
        )

        val inferred = DeviceRegistry.detectDeviceCapabilities(inferredName)
        assertEquals(
            listOf(AncPathKind.AUDIO_CURATION, AncPathKind.ANC_V2),
            inferred.ancPathCandidates,
        )
    }

    @Test
    fun `candidate paths are empty when the model declares no anc`() {
        assertTrue(DeviceCapabilities.NONE.ancPathCandidates.isEmpty())
    }
}
