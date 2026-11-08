package com.recreationalgrade.fattrack.data.ble.colmi

import com.recreationalgrade.fattrack.data.ble.colmi.ColmiResponse.HalfHourLogPart
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class HalfHourLogTest {

    private val day = Instant.parse("2026-11-08T00:00:00Z")

    private fun hex(s: String) = s.split(" ").map { it.toInt(16).toByte() }.toByteArray()

    private fun packet(command: Int, vararg payload: Int) =
        ColmiPacket.build(command, ByteArray(payload.size) { payload[it].toByte() })

    private fun stress(vararg payload: Int) =
        ColmiParser.parse(packet(ColmiCommand.STRESS_LOG, *payload)) as HalfHourLogPart

    private fun at(hour: Int, minute: Int = 0) = day.plusSeconds(hour * 3600L + minute * 60L)

    // --- Requests ---

    @Test fun `stress log request has no payload`() {
        assertArrayEquals(hex("37 00 00 00 00 00 00 00 00 00 00 00 00 00 00 37"), ColmiRequests.stressLog())
    }

    @Test fun `hrv log request carries day offset`() {
        assertArrayEquals(hex("39 02 00 00 00 00 00 00 00 00 00 00 00 00 00 3B"), ColmiRequests.hrvLog(2))
    }

    @Test fun `auto-measure read and write requests`() {
        assertArrayEquals(
            hex("36 01 00 00 00 00 00 00 00 00 00 00 00 00 00 37"),
            ColmiRequests.readAutoMeasure(HalfHourMetric.STRESS),
        )
        assertArrayEquals(
            hex("38 02 01 00 00 00 00 00 00 00 00 00 00 00 00 3B"),
            ColmiRequests.writeAutoMeasure(HalfHourMetric.HRV, true),
        )
        assertArrayEquals(
            hex("36 02 00 00 00 00 00 00 00 00 00 00 00 00 00 38"),
            ColmiRequests.writeAutoMeasure(HalfHourMetric.STRESS, false),
        )
    }

    // --- Parsing ---

    @Test fun `parses no data`() {
        assertEquals(HalfHourLogPart.NoData(HalfHourMetric.STRESS), stress(0xFF))
        assertEquals(
            HalfHourLogPart.NoData(HalfHourMetric.HRV),
            ColmiParser.parse(packet(ColmiCommand.HRV_LOG, 0xFF)),
        )
    }

    @Test fun `parses header`() {
        assertEquals(
            HalfHourLogPart.Header(HalfHourMetric.STRESS, packetCount = 5, intervalMinutes = 30),
            stress(0, 5, 0x1E),
        )
    }

    @Test fun `first packet values start at byte 3`() {
        val part = stress(1, 0x1E, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21)
        assertEquals(HalfHourLogPart.Values(HalfHourMetric.STRESS, 1, (10..21).toList()), part)
    }

    @Test fun `later packet values start at byte 2`() {
        val part = stress(2, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13)
        assertEquals(HalfHourLogPart.Values(HalfHourMetric.STRESS, 2, (1..13).toList()), part)
    }

    @Test fun `parses auto-measure settings`() {
        assertEquals(
            ColmiResponse.AutoMeasureSetting(HalfHourMetric.STRESS, enabled = true),
            ColmiParser.parse(packet(ColmiCommand.STRESS_SETTINGS, 1, 1)),
        )
        assertEquals(
            ColmiResponse.AutoMeasureSetting(HalfHourMetric.HRV, enabled = false),
            ColmiParser.parse(packet(ColmiCommand.HRV_SETTINGS, 1, 0)),
        )
        assertEquals(
            ColmiResponse.AutoMeasureSettingAck(HalfHourMetric.HRV, status = 1),
            ColmiParser.parse(packet(ColmiCommand.HRV_SETTINGS, 2, 1)),
        )
    }

    // --- Assembly ---

    @Test fun `assembles day into 30-minute UTC slots`() {
        val assembler = HalfHourLogAssembler(day)
        assertNull(assembler.add(stress(0, 5)))
        assertNull(assembler.add(stress(1, 0x1E, 20))) // slot 0  -> 00:00
        assertNull(assembler.add(stress(2, 30)))       // slot 12 -> 06:00
        assertNull(assembler.add(stress(3, 40)))       // slot 25 -> 12:30
        // Value 9 -> slot 47 (23:30); values 10..12 fall past midnight.
        val log = assembler.add(stress(4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 50, 77, 77, 77)) as HalfHourLog.Day
        assertEquals(48, log.values.size)
        assertEquals(
            listOf(at(0) to 20, at(6) to 30, at(12, 30) to 40, at(23, 30) to 50),
            log.readings(at(23, 59)),
        )
    }

    @Test fun `packets before header are ignored`() {
        assertNull(HalfHourLogAssembler(day).add(stress(4, 1)))
    }

    @Test fun `no data completes immediately`() {
        assertEquals(HalfHourLog.NoData, HalfHourLogAssembler(day).add(stress(0xFF)))
    }

    @Test fun `readings skip empty and future slots`() {
        val log = HalfHourLog.Day(day, List(48) { if (it == 0) 10 else if (it == 2) 30 else 0 })
        assertEquals(listOf(at(0) to 10), log.readings(at(0, 45)))
    }
    
    // --- Real R10 captures (2026-11-08, synced at 18:29:02 UTC) ---

    private val captureTime = Instant.parse("2026-11-08T18:29:02Z")

    private val stressCapture = listOf(
        "37 00 05 1E 00 00 00 00 00 00 00 00 00 00 00 5A",
        "37 01 00 28 2A 26 30 25 27 2A 28 2B 24 2D 1E 18",
        "37 02 29 20 28 27 1F 28 2B 2E 20 29 20 31 1E 29",
        "37 03 28 3F 27 20 23 2C 25 3F 27 2F 28 2D 00 46",
        "37 04 00 00 00 00 00 00 00 00 00 00 00 00 00 3B",
    )

    private val hrvCapture = listOf(
        "39 00 05 1E 00 00 00 00 00 00 00 00 00 00 00 5C",
        "39 01 00 2E 00 2C 00 26 00 2C 00 2B 00 2C 00 3D",
        "39 02 22 00 2A 00 20 00 2D 00 21 00 2A 00 20 3F",
        "39 03 00 2D 00 30 00 2C 00 31 00 2E 00 31 00 55",
        "39 04 00 00 00 00 00 00 00 00 00 00 00 00 00 3D",
    )

    private fun assembleCapture(packets: List<String>): HalfHourLog.Day {
        val assembler = HalfHourLogAssembler(day)
        var result: HalfHourLog? = null
        packets.forEachIndexed { i, s ->
            result = assembler.add(ColmiParser.parse(hex(s)) as HalfHourLogPart)
            if (i < packets.lastIndex) assertNull("Completed early at $i", result)
        }
        return result as HalfHourLog.Day
    }

    @Test fun `R10 header reports 5 packets of 30-minute slots`() {
        assertEquals(
            HalfHourLogPart.Header(HalfHourMetric.HRV, packetCount = 5, intervalMinutes = 30),
            ColmiParser.parse(hex(hrvCapture[0])),
        )
    }

    @Test fun `R10 stress fills every 30-minute slot`() {
        val readings = assembleCapture(stressCapture).readings(captureTime)
        assertEquals((0..36).map { day.plusSeconds(it * 1800L) }, readings.map { it.first })
        assertEquals(at(0) to 40, readings.first())
        // Dashboard showed "Stress 45 at 1:00 PM" (UTC−5).
        assertEquals(at(18) to 45, readings.last())
    }

    @Test fun `R10 HRV fills hourly slots only`() {
        val readings = assembleCapture(hrvCapture).readings(captureTime)
        assertEquals((0..18).map { at(it) }, readings.map { it.first })
        assertEquals(at(0) to 46, readings.first())
        assertEquals(at(18) to 49, readings.last())
    }
}
