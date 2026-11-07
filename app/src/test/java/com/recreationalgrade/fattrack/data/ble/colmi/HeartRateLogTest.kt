package com.recreationalgrade.fattrack.data.ble.colmi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class HeartRateLogTest {

    private fun hex(s: String): ByteArray =
        s.split(" ").map { it.toInt(16).toByte() }.toByteArray()

    private val day = LocalDate.of(2026, 10, 31)
    private val dayStart = day.atStartOfDay(ZoneOffset.UTC).toInstant()

    private fun part(packet: ByteArray) =
        ColmiParser.parse(packet) as ColmiResponse.HeartRateLogPart

    // --- Requests ---

    @Test fun `log request is UTC midnight little-endian`() {
        // 2026-10-31T00:00Z = 1793404800 = 0x6AE52F80
        assertEquals(
            "15 80 2F E5 6A 00 00 00 00 00 00 00 00 00 00 13",
            ColmiRequests.heartRateLog(day).toHex(),
        )
    }

    @Test fun `settings requests`() {
        assertEquals(
            "16 01 00 00 00 00 00 00 00 00 00 00 00 00 00 17",
            ColmiRequests.readHeartRateLogSettings().toHex(),
        )
        assertEquals(
            "16 02 01 05 00 00 00 00 00 00 00 00 00 00 00 1E",
            ColmiRequests.writeHeartRateLogSettings(enabled = true, intervalMinutes = 5).toHex(),
        )
        assertEquals(
            "16 02 02 3C 00 00 00 00 00 00 00 00 00 00 00 56",
            ColmiRequests.writeHeartRateLogSettings(enabled = false, intervalMinutes = 60).toHex(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `settings interval of zero is rejected`() {
        ColmiRequests.writeHeartRateLogSettings(enabled = true, intervalMinutes = 0)
    }

    // --- Parsing (reference captures from colmi_r02_client) ---

    @Test fun `parses settings response`() {
        // b'\x16\x01\x01<\x00...T' -> enabled, every 60 minutes.
        assertEquals(
            ColmiResponse.HeartRateLogSettings(enabled = true, intervalMinutes = 60),
            ColmiParser.parse(hex("16 01 01 3C 00 00 00 00 00 00 00 00 00 00 00 54")),
        )
    }

    @Test fun `parses log header`() {
        // b'\x15\x00\x18\x05\x00...2' -> 24 packets, range 5.
        assertEquals(
            ColmiResponse.HeartRateLogPart.Header(packetCount = 24, rangeMinutes = 5),
            ColmiParser.parse(hex("15 00 18 05 00 00 00 00 00 00 00 00 00 00 00 32")),
        )
    }

    @Test fun `parses no data`() {
        assertEquals(
            ColmiResponse.HeartRateLogPart.NoData,
            ColmiParser.parse(ColmiPacket.build(ColmiCommand.HEART_RATE_LOG, byteArrayOf(0xFF.toByte()))),
        )
    }

    // --- Assembly ---

    @Test fun `assembles a full day`() {
        val assembler = HeartRateLogAssembler(day)

        assertNull(assembler.add(part(ColmiPacket.build(0x15, byteArrayOf(0, 24, 5)))))

        val first = byteArrayOf(1) + dayStart.epochSecond.toUInt32Le() +
            ByteArray(9) { (60 + it).toByte() }
        assertNull(assembler.add(part(ColmiPacket.build(0x15, first))))

        var result: HeartRateLog? = null
        for (sub in 2..23) {
            val packet = ColmiPacket.build(0x15, byteArrayOf(sub.toByte()) + ByteArray(13) { 70 })
            result = assembler.add(part(packet))
            if (sub < 23) assertNull("Completed early at sub-packet $sub", result)
        }

        val log = result as HeartRateLog.Day
        assertEquals(dayStart, log.dayStart)
        assertEquals(5, log.rangeMinutes)
        assertEquals(HeartRateLog.SLOTS_PER_DAY, log.values.size)
        assertEquals(60, log.values[0])
        assertEquals(68, log.values[8])
        assertEquals(70, log.values[9])
        assertEquals(70, log.values[287])
    }

    @Test fun `readings skip empty slots and the future`() {
        val values = MutableList(HeartRateLog.SLOTS_PER_DAY) { 0 }
        values[0] = 60   // 00:00
        values[1] = 0    // 00:05, no reading
        values[2] = 255  // 00:10, invalid
        values[12] = 72  // 01:00
        values[13] = 80  // 01:05, after "now"
        val log = HeartRateLog.Day(dayStart, rangeMinutes = 5, values = values)

        val readings = log.readings(now = dayStart.plusSeconds(3600))

        assertEquals(
            listOf(dayStart to 60, dayStart.plusSeconds(3600) to 72),
            readings,
        )
    }

    @Test fun `header with no packets is no data`() {
        val assembler = HeartRateLogAssembler(day)
        assertEquals(
            HeartRateLog.NoData,
            assembler.add(part(ColmiPacket.build(0x15, byteArrayOf(0, 0, 5)))),
        )
    }

    @Test fun `values before header are ignored`() {
        val assembler = HeartRateLogAssembler(day)
        val stray = ColmiPacket.build(0x15, byteArrayOf(5) + ByteArray(13) { 70 })
        assertNull(assembler.add(part(stray)))
    }

    @Test fun `missing timestamp falls back to requested day`() {
        val assembler = HeartRateLogAssembler(day)
        assembler.add(part(ColmiPacket.build(0x15, byteArrayOf(0, 3, 5))))
        // Skip sub-packet 1 (the one with the timestamp); send 2 = last.
        val log = assembler.add(part(ColmiPacket.build(0x15, byteArrayOf(2) + ByteArray(13) { 70 })))
        assertTrue(log is HeartRateLog.Day)
        assertEquals(dayStart, (log as HeartRateLog.Day).dayStart)
    }
    
    // --- Captures from a real Colmi R10 (fw RT03CR_1.00.02_260319), 2026-11-07 ---

    @Test fun `R10 settings reply parses`() {
        // Extra byte 4 (0x05) is undocumented; probably the slot size.
        assertEquals(
            ColmiResponse.HeartRateLogSettings(enabled = true, intervalMinutes = 30),
            ColmiParser.parse(hex("16 01 01 1E 05 00 00 00 00 00 00 00 00 00 00 3B")),
        )
    }

    @Test fun `R10 capture lands on a 30-minute grid in 5-minute slots`() {
        val day = LocalDate.of(2026, 11, 1)
        val start = day.atStartOfDay(ZoneOffset.UTC).toInstant()
        val assembler = HeartRateLogAssembler(day)

        // The real header said 24 packets; use 9 so the 8 captured sub-packets complete the log.
        assembler.add(part(ColmiPacket.build(0x15, byteArrayOf(0, 9, 5))))

        val captured = listOf(
            "15 01 00 81 E6 6A 48 00 00 00 00 00 56 00 00 85",
            "15 02 00 00 00 4B 00 00 00 00 00 47 00 00 00 A9",
            "15 03 00 00 4B 00 00 00 00 00 47 00 00 00 00 AA",
            "15 04 00 41 00 00 00 00 00 3B 00 00 00 00 00 95",
            "15 05 41 00 00 00 00 00 3B 00 00 00 00 00 38 CE",
            "15 06 00 00 00 00 00 49 00 00 00 00 00 38 00 9C",
            "15 07 00 00 00 00 41 00 00 00 00 00 3D 00 00 9A",
            "15 08 00 00 00 42 00 00 00 00 00 3F 00 00 00 9E",
        )
        var log: HeartRateLog? = null
        captured.forEach { log = assembler.add(part(hex(it))) }

        val readings = (log as HeartRateLog.Day).readings(now = start.plusSeconds(86_400))
        val expected = listOf(72, 86, 75, 71, 75, 71, 65, 59, 65, 59, 56, 73, 56, 65, 61, 66, 63)
            .mapIndexed { i, bpm -> start.plusSeconds(i * 30L * 60) to bpm }
        assertEquals(expected, readings)
    }
    
    @Test fun `R10 settings write ack is not mistaken for settings`() {
        assertEquals(
            ColmiResponse.HeartRateLogSettingsAck(status = 1),
            ColmiParser.parse(hex("16 02 01 00 00 00 00 00 00 00 00 00 00 00 00 19")),
        )
    }

    @Test fun `R10 settings reply at 15 minutes`() {
        assertEquals(
            ColmiResponse.HeartRateLogSettings(enabled = true, intervalMinutes = 15),
            ColmiParser.parse(hex("16 01 01 0F 05 00 00 00 00 00 00 00 00 00 00 2C")),
        )
    }

}
