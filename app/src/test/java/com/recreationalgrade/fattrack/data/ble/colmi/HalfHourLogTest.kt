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
        assertEquals(HalfHourLogPart.Header(HalfHourMetric.STRESS), stress(0, 5))
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
}
