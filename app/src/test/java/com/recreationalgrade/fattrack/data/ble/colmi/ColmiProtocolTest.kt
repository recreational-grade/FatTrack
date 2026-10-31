package com.recreationalgrade.fattrack.data.ble.colmi

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZonedDateTime

class ColmiProtocolTest {

    private fun hex(s: String): ByteArray =
        s.split(" ").map { it.toInt(16).toByte() }.toByteArray()

    // --- Framing ---

    @Test fun `battery request matches reference`() {
        assertEquals(
            "03 00 00 00 00 00 00 00 00 00 00 00 00 00 00 03",
            ColmiRequests.battery().toHex(),
        )
    }

    @Test fun `checksum keeps low 8 bits, not modulo 255`() {
        val packet = ColmiPacket.build(0xFF, ByteArray(14) { 0xFF.toByte() })
        // 15 * 255 = 3825. 3825 & 0xFF = 0xF1, whereas 3825 % 255 would be 0.
        assertEquals(0xF1.toByte(), packet[15])
    }

    @Test fun `short payload is zero-padded`() {
        val packet = ColmiPacket.build(0x10, byteArrayOf(1, 2))
        assertEquals(16, packet.size)
        assertTrue(packet.copyOfRange(3, 15).all { it == 0.toByte() })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `payload over 14 bytes is rejected`() {
        ColmiPacket.build(0x01, ByteArray(15))
    }

    // --- Set time ---

    @Test fun `set time encodes UTC as BCD with English language`() {
        val packet = ColmiRequests.setTime(Instant.parse("2026-10-31T13:48:49Z"))
        assertEquals(
            "01 26 10 31 13 48 49 01 00 00 00 00 00 00 00 0D",
            packet.toHex(),
        )
    }

    @Test fun `set time converts local time to UTC`() {
        val local = ZonedDateTime.parse("2026-10-31T09:48:49-04:00").toInstant()
        val utc = Instant.parse("2026-10-31T13:48:49Z")
        assertArrayEquals(ColmiRequests.setTime(utc), ColmiRequests.setTime(local))
    }

    @Test fun `bcd packs two decimal digits`() {
        assertEquals(0x00.toByte(), ColmiRequests.bcd(0))
        assertEquals(0x09.toByte(), ColmiRequests.bcd(9))
        assertEquals(0x26.toByte(), ColmiRequests.bcd(26))
        assertEquals(0x99.toByte(), ColmiRequests.bcd(99))
    }

    // --- Real time ---

    @Test fun `real time start and stop packets`() {
        assertEquals(
            "69 01 01 00 00 00 00 00 00 00 00 00 00 00 00 6B",
            ColmiRequests.startRealTime(RealTimeType.HEART_RATE).toHex(),
        )
        assertEquals(
            "6A 01 00 00 00 00 00 00 00 00 00 00 00 00 00 6B",
            ColmiRequests.stopRealTime(RealTimeType.HEART_RATE).toHex(),
        )
    }

    // --- Parsing ---

    @Test fun `parses battery response from reference capture`() {
        // From colmi_r02_client: b'\x03@\x00...\x00C' -> 64%, not charging.
        val packet = hex("03 40 00 00 00 00 00 00 00 00 00 00 00 00 00 43")
        assertEquals(ColmiResponse.Battery(level = 64, charging = false), ColmiParser.parse(packet))
    }

    @Test fun `parses real time reading and error`() {
        val reading = ColmiPacket.build(ColmiCommand.REAL_TIME_START, byteArrayOf(1, 0, 72))
        assertEquals(
            ColmiResponse.RealTimeReading(RealTimeType.HEART_RATE, rawType = 1, value = 72),
            ColmiParser.parse(reading),
        )
        val error = ColmiPacket.build(ColmiCommand.REAL_TIME_START, byteArrayOf(3, 2))
        assertEquals(
            ColmiResponse.RealTimeError(RealTimeType.SPO2, rawType = 3, code = 2),
            ColmiParser.parse(error),
        )
    }

    @Test fun `set time ack is recognized`() {
        assertEquals(ColmiResponse.TimeSet, ColmiParser.parse(ColmiPacket.build(ColmiCommand.SET_TIME)))
    }

    @Test fun `unknown command is preserved`() {
        val response = ColmiParser.parse(ColmiPacket.build(0x43, byteArrayOf(1)))
        assertTrue(response is ColmiResponse.Unknown)
        assertEquals(0x43, (response as ColmiResponse.Unknown).command)
    }

    @Test fun `invalid packets return null`() {
        assertNull(ColmiParser.parse(ByteArray(15)))
        val badChecksum = ColmiRequests.battery().also { it[15] = 0x00 }
        assertNull(ColmiParser.parse(badChecksum))
    }

        // --- Captures from a real Colmi R10 (fw RT03CR_1.00.02_260319), 2026-10-31 ---

    @Test fun `R10 set time request matches capture`() {
        assertEquals(
            "01 26 10 31 20 25 33 01 00 00 00 00 00 00 00 E1",
            ColmiRequests.setTime(Instant.parse("2026-10-31T20:25:33Z")).toHex(),
        )
    }

    @Test fun `R10 set time ack parses`() {
        assertEquals(
            ColmiResponse.TimeSet,
            ColmiParser.parse(hex("01 01 00 00 02 00 00 00 00 01 00 20 00 00 30 55")),
        )
    }

    @Test fun `R10 battery response parses`() {
        assertEquals(
            ColmiResponse.Battery(level = 14, charging = false),
            ColmiParser.parse(hex("03 0E 00 00 00 00 00 00 00 00 00 00 00 00 00 11")),
        )
    }

    @Test fun `R10 unsolicited 0x2F packet is valid and kept as unknown`() {
        // 0x2F + 0xF4 = 0x123 -> 0x23. Modulo 255 would give 0x24, so this
        // also confirms the checksum rule on real hardware.
        val response = ColmiParser.parse(hex("2F F4 00 00 00 00 00 00 00 00 00 00 00 00 00 23"))
        assertEquals(0x2F, (response as ColmiResponse.Unknown).command)
    }

}
