package cythen.hyperdrop.pods.link

import cythen.hyperdrop.device.PodTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Transport discovery rules.
 *
 * The official app decides the transport per device at connect time
 * (`BluetoothConnectionHandler.resolveBluetoothType()` reads `transportType`), never from a
 * compiled model table, so these tests pin the equivalent behaviour: advertised services
 * decide the order, and the profile hint only fills gaps.
 */
class LinkProbeTest {

    private val spp = LinkProbe.SPP_SERVICE
    private val gaiaGatt = LinkProbe.GAIA_GATT_SERVICE
    private val src9Eca = LinkProbe.SRC_9ECA_SERVICE

    @Test
    fun `gaia gatt service wins over spp`() {
        val result = LinkProbe.classify("AA:BB", listOf(spp, gaiaGatt))
        assertEquals(
            LinkProbe.ProbeResult.Candidates(
                listOf(PodTransport.BLE_GAIA, PodTransport.RFCOMM_GAIA)
            ),
            result,
        )
    }

    @Test
    fun `spp only yields rfcomm`() {
        val result = LinkProbe.classify("AA:BB", listOf(spp))
        assertEquals(
            LinkProbe.ProbeResult.Candidates(listOf(PodTransport.RFCOMM_GAIA)),
            result,
        )
    }

    @Test
    fun `9eca service is reported last`() {
        val result = LinkProbe.classify("AA:BB", listOf(src9Eca, spp))
        assertEquals(
            LinkProbe.ProbeResult.Candidates(
                listOf(PodTransport.RFCOMM_GAIA, PodTransport.BLE_SRC_9ECA)
            ),
            result,
        )
    }

    @Test
    fun `uuids are matched case insensitively`() {
        val result = LinkProbe.classify("AA:BB", listOf(spp.uppercase()))
        assertEquals(
            LinkProbe.ProbeResult.Candidates(listOf(PodTransport.RFCOMM_GAIA)),
            result,
        )
    }

    @Test
    fun `unrelated services report no known service`() {
        val result = LinkProbe.classify(
            "AA:BB",
            listOf("0000110b-0000-1000-8000-00805f9b34fb", "0000111e-0000-1000-8000-00805f9b34fb"),
        )
        assertTrue(result is LinkProbe.ProbeResult.NoKnownService)
    }

    @Test
    fun `probed order wins and declared hint fills the gaps`() {
        val order = LinkProbe.resolveOrder(
            declared = listOf(PodTransport.RFCOMM_GAIA),
            probed = listOf(PodTransport.BLE_GAIA, PodTransport.RFCOMM_GAIA),
        )
        assertEquals(listOf(PodTransport.BLE_GAIA, PodTransport.RFCOMM_GAIA), order)
    }

    @Test
    fun `declared hint is used when nothing was probed`() {
        val declared = listOf(PodTransport.RFCOMM_GAIA, PodTransport.BLE_GAIA)
        assertEquals(declared, LinkProbe.resolveOrder(declared, emptyList()))
    }

    @Test
    fun `resolve order never duplicates a transport`() {
        val order = LinkProbe.resolveOrder(
            declared = listOf(PodTransport.RFCOMM_GAIA, PodTransport.BLE_GAIA),
            probed = listOf(PodTransport.RFCOMM_GAIA),
        )
        assertEquals(order.distinct(), order)
        assertEquals(PodTransport.RFCOMM_GAIA, order.first())
    }

    @Test
    fun `describe renders candidate names`() {
        val text = LinkProbe.describe(
            LinkProbe.ProbeResult.Candidates(listOf(PodTransport.BLE_GAIA))
        )
        assertTrue(text.contains("BLE_GAIA"))
    }
}
