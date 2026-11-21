@file:Suppress("DEPRECATION")

package cythen.hyperdrop.device.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wire-level regression tests for the GAIA command tables.
 *
 * These lock in the receive-path layout verified against the official APK (see
 * docs/PROTOCOL_ANALYSIS_AUDIT.md, findings A1/A2/D1/D2/D3): the previous implementation
 * decoded supported-features as a bitmask and silently produced garbage.
 */
class GaiaWireFormatTest {

    private fun hex(vararg bytes: Int): ByteArray = ByteArray(bytes.size) { bytes[it].toByte() }

    private fun assertBytes(expected: ByteArray, actual: ByteArray?) =
        assertEquals(expected.toList(), actual?.toList())

    // ---- Supported features: (featureId, version) pairs, not a bitmask ----------------

    @Test
    fun `supported features decode as id-version pairs starting after hasMoreData`() {
        // data = [hasMoreData=0][id=0x01,ver=0x03][id=0x20,ver=0x02]
        val payload = hex(0x00, 0x1D, 0x01, 0x01, 0x00, 0x01, 0x03, 0x20, 0x02)
        val report = GenericGaiaCommandTable().parse(payload)

        assertTrue(report is GaiaReport.SupportedFeatures)
        val features = report as GaiaReport.SupportedFeatures
        assertEquals(setOf(0x01, 0x20), features.features)
        assertFalse(features.moreData)
    }

    @Test
    fun `supported features report a pending next page`() {
        val payload = hex(0x00, 0x1D, 0x01, 0x01, 0x01, 0x02, 0x01)
        val report = GenericGaiaCommandTable().parse(payload) as GaiaReport.SupportedFeatures

        assertEquals(setOf(0x02), report.features)
        assertTrue(report.moreData)
    }

    @Test
    fun `versions are skipped and never read as feature ids`() {
        // Pairs are (0x12,0x05) and (0x08,0x00); 0x05/0x00 must not appear as features.
        val payload = hex(0x00, 0x1D, 0x01, 0x02, 0x00, 0x12, 0x05, 0x08, 0x00)
        val report = GenericGaiaCommandTable().parse(payload) as GaiaReport.SupportedFeatures

        assertEquals(setOf(0x12, 0x08), report.features)
    }

    @Test
    fun `next-page request uses command 0x02`() {
        assertBytes(hex(0x00, 0x1D, 0x00, 0x02), GenericGaiaCommandTable().querySupportedFeaturesNext)
        assertBytes(hex(0x00, 0x1D, 0x00, 0x02), PuddingCommandTable.querySupportedFeaturesNext)
    }

    @Test
    fun `features parser rejects unrelated payloads`() {
        // Not a features response: wrong feature, wrong command, or shorter than the list.
        assertNull(GaiaFeaturesParser.parse(hex(0x00, 0x1D, 0x1A, 0x01, 0x00, 0x01, 0x03)))
        assertNull(GaiaFeaturesParser.parse(hex(0x00, 0x1D, 0x01, 0x07, 0x00, 0x01, 0x03)))
        assertNull(GaiaFeaturesParser.parse(hex(0x00, 0x1D, 0x01, 0x01, 0x00)))
        assertNull(GaiaFeaturesParser.parse(hex(0x00, 0x0A, 0x01, 0x01, 0x00)))
    }

    @Test
    fun `features parser tolerates a trailing odd byte`() {
        // Real harm comes from misaligned pairs, not from a truncated final version byte.
        val report = GaiaFeaturesParser.parse(hex(0x00, 0x1D, 0x01, 0x02, 0x00, 0x12, 0x05, 0x08))

        assertEquals(setOf(0x12), report?.features)
        assertFalse(report!!.moreData)
    }

    // ---- Battery: [id, level] records, 0xFF means unknown, no charging bit ------------

    @Test
    fun `battery decodes the real pudding snapshot`() {
        // Official trace: 00 1D 1B 01 01 64 02 64 03 55
        val report = GaiaBatteryParser.parse(hex(0x00, 0x1D, 0x1B, 0x01, 0x01, 0x64, 0x02, 0x64, 0x03, 0x55))

        assertEquals(GaiaReport.Battery(100, 100, 85), report)
    }

    @Test
    fun `battery level with the obsolete aa high bit set is masked`() {
        // 0xE4 = 228. GAIA has no charging bit, so the AA-style 0x80 flag must not leak in.
        val report = GaiaBatteryParser.parse(hex(0x00, 0x1D, 0x1B, 0x01, 0x01, 0xE4, 0x02, 0x50))

        assertEquals(100, report?.left)
        assertEquals(80, report?.right)
    }

    @Test
    fun `battery level 0xFF is unknown and not reported`() {
        val report = GaiaBatteryParser.parse(hex(0x00, 0x1D, 0x1B, 0x01, 0x01, 0xFF, 0x02, 0x40))

        assertNull(report?.left)
        assertEquals(64, report?.right)
    }

    @Test
    fun `battery ignores non-battery payloads and empty records`() {
        assertNull(GaiaBatteryParser.parse(hex(0x00, 0x1D, 0x1B, 0x00)))
        assertNull(GaiaBatteryParser.parse(hex(0x00, 0x1D, 0x1F, 0x01, 0x00)))
        assertNull(GaiaBatteryParser.parse(hex(0x00, 0x1D, 0x1B, 0x01, 0x01, 0xFF)))
    }

    // ---- ANC -------------------------------------------------------------------------

    /** getMap as DeviceModels.ancMaps produces it for ANC_V2 (keys are wire values). */
    private fun ancV2Table() = GenericGaiaCommandTable(
        ancPathKind = AncPathKind.ANC_V2,
        ancSetMap = intArrayOf(-1, 0, 1, 2, 4, 3, -1, -1, -1),
        ancGetMap = intArrayOf(1, 2, 3, 5, 4, -1),
    )

    /** getMap as DeviceModels.ancMaps produces it for AUDIO_CURATION (keys are 0-based). */
    private fun audioCurationTable() = GenericGaiaCommandTable(
        ancPathKind = AncPathKind.AUDIO_CURATION,
        ancSetMap = intArrayOf(-1, 1, 2, 4, -1, 3, -1, -1, -1),
        ancGetMap = intArrayOf(1, 2, 3, 5),
    )

    @Test
    fun `anc v2 response maps through the model getter map`() {
        val table = ancV2Table()

        // Wire 0 is PUDDING's OFF; wire 4 is its adaptive mode.
        assertEquals(GaiaReport.AncMode(1, 0), table.parse(hex(0x00, 0x1D, 0x41, 0x03, 0x00)))
        assertEquals(GaiaReport.AncMode(4, 4), table.parse(hex(0x00, 0x1D, 0x41, 0x04, 0x04)))
    }

    @Test
    fun `anc v2 set echoes are decoded too`() {
        val table = ancV2Table()

        // Official trace: 00 1D 41 04 01 after "set noise mode: ui=2 wire=1".
        assertEquals(GaiaReport.AncMode(2, 1), table.parse(hex(0x00, 0x1D, 0x41, 0x04, 0x01)))
    }

    @Test
    fun `audio curation response uses its own command word`() {
        val table = audioCurationTable()

        assertEquals(GaiaReport.AncMode(1, 0), table.parse(hex(0x00, 0x1D, 0x11, 0x03, 0x00)))
        assertEquals(GaiaReport.AncMode(3, 2), table.parse(hex(0x00, 0x1D, 0x11, 0x03, 0x02)))
        // The ANC_V2 command word must not be claimed on the AudioCuration path.
        assertEquals(GaiaReport.Unknown, table.parse(hex(0x00, 0x1D, 0x41, 0x03, 0x00)))
    }

    @Test
    fun `anc v1 path never claims a response`() {
        val table = GenericGaiaCommandTable(ancPathKind = AncPathKind.ANC_V1)

        // Whatever command word the earbud answers with, the deprecated path stays inert.
        assertEquals(GaiaReport.Unknown, table.parse(hex(0x00, 0x1D, 0x05, 0x03, 0x00)))
        assertEquals(GaiaReport.Unknown, table.parse(hex(0x00, 0x1D, 0x01, 0x04, 0x00)))
    }

    // ---- Other reports ----------------------------------------------------------------

    @Test
    fun `gaia negotiation response is recognised`() {
        val report = GenericGaiaCommandTable()
            .parse(hex(0x00, 0x0A, 0x83, 0x00, 0x00, 0x04, 0x03, 0x01))

        assertEquals(GaiaReport.GaiaNegotiation, report)
    }

    @Test
    fun `negotiation is matched on the command word, not just the vendor`() {
        // vendor 0x000A with a different command must not be mistaken for the handshake.
        assertEquals(
            GaiaReport.Unknown,
            GenericGaiaCommandTable().parse(hex(0x00, 0x0A, 0x01, 0x01, 0x00)),
        )
        assertEquals(
            GaiaReport.Unknown,
            PuddingCommandTable.parse(hex(0x00, 0x0A, 0x01, 0x01, 0x00)),
        )
        // The pudding table recognises the same ACK-ed handshake frame.
        assertEquals(
            GaiaReport.GaiaNegotiation,
            PuddingCommandTable.parse(hex(0x00, 0x0A, 0x83, 0x00, 0x00, 0x04, 0x03, 0x01)),
        )
    }

    @Test
    fun `pudding table shares the battery decoder`() {
        val report = PuddingCommandTable
            .parse(hex(0x00, 0x1D, 0x1B, 0x01, 0x01, 0x64, 0x02, 0x64, 0x03, 0x55))

        assertEquals(GaiaReport.Battery(100, 100, 85), report)
    }

    @Test
    fun `generic and pudding tables agree on the anc set command word`() {
        assertBytes(hex(0x00, 0x1D, 0x40, 0x04, 0x01), ancV2Table().setNoiseMode(2))
        assertBytes(hex(0x00, 0x1D, 0x40, 0x04, 0x01), PuddingCommandTable.setNoiseMode(2))
    }
}
