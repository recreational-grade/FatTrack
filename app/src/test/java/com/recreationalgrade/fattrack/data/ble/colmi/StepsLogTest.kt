package com.recreationalgrade.fattrack.data.ble.colmi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class StepsLogTest {

    private fun hex(s: String): ByteArray =
        s.split(" ").map { it.toInt(16).toByte() }.toByteArray()

    private fun part(packet: ByteArray) =
        ColmiParser.parse(packet) as ColmiResponse.StepsLogPart

    // Reference captures from colmi_r02_client (2023-08-13).
    private val header = "43 F0 05 01 00 00 00 00 00 00 00 00 00 00 00 39"
    private val slots = listOf(
        "43 23 08 13 10 00 05 C8 00 30 00 1B 00 00 00 A9",
        "43 23 08 13 14 01 05 B6 18 AA 04 69 03 00 00 83",
        "43 23 08 13 18 02 05 38 04 E1 00 95 00 00 00 52",
        "43 23 08 13 1C 03 05 05 02 6C 00 48 00 00 00 60",
        "43 23 08 13 4C 04 05 EF 01 63 00 44 00 00 00 6D",
    )

    // --- Requests ---

    @Test fun `steps request for today and yesterday`() {
        assertEquals(
            "43 00 0F 00 5F 01 00 00 00 00 00 00 00 00 00 B2",
            ColmiRequests.stepsLog(0).toHex(),
        )
        assertEquals(
            "43 01 0F 00 5F 01 00 00 00 00 00 00 00 00 00 B3",
            ColmiRequests.stepsLog(1).toHex(),
        )
    }

    // --- Parsing ---

    @Test fun `parses header with new calorie protocol`() {
        assertEquals(
            ColmiResponse.StepsLogPart.Header(newCalorieProtocol = true),
            ColmiParser.parse(hex(header)),
        )
    }

    @Test fun `parses slot packet`() {
        assertEquals(
            ColmiResponse.StepsLogPart.Slot(
                date = LocalDate.of(2023, 8, 13),
                slotIndex = 20,
                packetIndex = 1,
                packetCount = 5,
                caloriesRaw = 6326,
                steps = 1194,
                distanceMeters = 873,
            ),
            ColmiParser.parse(hex(slots[1])),
        )
    }

    @Test fun `parses no data`() {
        assertEquals(
            ColmiResponse.StepsLogPart.NoData,
            ColmiParser.parse(ColmiPacket.build(ColmiCommand.STEPS_LOG, byteArrayOf(0xFF.toByte()))),
        )
    }

    @Test fun `invalid BCD date is kept as unknown`() {
        val packet = ColmiPacket.build(ColmiCommand.STEPS_LOG, byteArrayOf(0x26, 0x13, 0x45))
        assertTrue(ColmiParser.parse(packet) is ColmiResponse.Unknown)
    }

    // --- Assembly ---

    @Test fun `assembles reference day`() {
        val assembler = StepsLogAssembler()
        assertNull(assembler.add(part(hex(header))))
        var result: StepsLog? = null
        slots.forEachIndexed { i, s ->
            result = assembler.add(part(hex(s)))
            if (i < slots.lastIndex) assertNull("Completed early at $i", result)
        }

        val log = (result as StepsLog.Slots).slots
        assertEquals(5, log.size)
        assertEquals(
            StepSlot(
                start = Instant.parse("2023-08-13T05:00:00Z"),
                steps = 1194,
                distanceMeters = 873,
                calories = 63_260,
            ),
            log[1],
        )
        assertEquals(Instant.parse("2023-08-13T19:00:00Z"), log[4].start)
        assertEquals(1194 + 48 + 225 + 108 + 99, log.sumOf { it.steps })
    }

    @Test fun `calories are about 50 per step in reference data`() {
        // Sanity check behind the "small calories" unit assumption.
        val assembler = StepsLogAssembler()
        assembler.add(part(hex(header)))
        var result: StepsLog? = null
        slots.forEach { result = assembler.add(part(hex(it))) }
        (result as StepsLog.Slots).slots.forEach {
            val perStep = it.calories.toDouble() / it.steps
            assertTrue("Unexpected cal/step $perStep", perStep in 35.0..65.0)
        }
    }

    @Test fun `without header calories are not scaled`() {
        val assembler = StepsLogAssembler()
        val single = ColmiPacket.build(
            ColmiCommand.STEPS_LOG,
            // Date is BCD (26-11-07); slot index is plain binary (24 -> 06:00).
            byteArrayOf(0x26, 0x11, 0x07, 24, 0, 1, 0x64, 0, 0x0A, 0, 0x08, 0),
        )
        val log = assembler.add(part(single)) as StepsLog.Slots
        assertEquals(100, log.slots.single().calories)
        assertEquals(Instant.parse("2026-11-07T06:00:00Z"), log.slots.single().start)
    }
}