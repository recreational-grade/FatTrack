package com.recreationalgrade.fattrack.data.ble.colmi

import com.recreationalgrade.fattrack.data.ble.colmi.ColmiResponse.HalfHourLogPart
import java.time.Instant

/**
 * Metrics logged on a 30-minute UTC slot grid. Same packet format, different
 * commands. On the R10, stress fills every slot; HRV fills every other (hourly).
 */
enum class HalfHourMetric(val label: String, val logCommand: Int, val settingsCommand: Int) {
    STRESS("Stress", ColmiCommand.STRESS_LOG, ColmiCommand.STRESS_SETTINGS),
    HRV("HRV", ColmiCommand.HRV_LOG, ColmiCommand.HRV_SETTINGS),
}

/** A fully assembled stress or HRV log for one UTC day. */
sealed interface HalfHourLog {
    data object NoData : HalfHourLog

    data class Day(
        val dayStart: Instant,
        /** [SLOTS_PER_DAY] values; 0 = no reading. */
        val values: List<Int>,
    ) : HalfHourLog {
        /** Valid readings at or before [now]. */
        fun readings(now: Instant): List<Pair<Instant, Int>> =
            values.mapIndexedNotNull { index, value ->
                val time = dayStart.plusSeconds(index * SLOT_SECONDS)
                if (value in 1..254 && !time.isAfter(now)) time to value else null
            }
    }

    companion object {
        const val SLOTS_PER_DAY = 48
        const val SLOT_SECONDS = 30L * 60
    }
}

/**
 * Folds the reply to a stress (0x37) or HRV (0x39) request. Packet 0 is a
 * header (byte 2 = packet count, byte 3 = slot minutes); packet 1 carries 12
 * values (bytes 3..14); later packets carry 13 (bytes 2..14). 51 values for
 * 48 slots: the last 3 are dropped. Confirmed against an R10 capture.
 */
class HalfHourLogAssembler(private val dayStart: Instant) {
    private var started = false
    private var lastPacket = DEFAULT_LAST_PACKET
    private val values = IntArray(HalfHourLog.SLOTS_PER_DAY)

    /** Returns the finished log once complete, otherwise null. */
    fun add(part: HalfHourLogPart): HalfHourLog? = when (part) {
        is HalfHourLogPart.NoData -> HalfHourLog.NoData
        is HalfHourLogPart.Header -> {
            started = true
            if (part.packetCount >= 2) lastPacket = part.packetCount - 1
            null
        }
        is HalfHourLogPart.Values -> if (!started) null else addValues(part)
    }

    private fun addValues(part: HalfHourLogPart.Values): HalfHourLog? {
        val offset = if (part.packetIndex == 1) {
            0
        } else {
            FIRST_PACKET_VALUES + (part.packetIndex - 2) * VALUES_PER_PACKET
        }
        part.values.forEachIndexed { i, value ->
            val slot = offset + i
            if (slot in values.indices) values[slot] = value
        }
        return if (part.packetIndex >= lastPacket) {
            HalfHourLog.Day(dayStart, values.toList())
        } else {
            null
        }
    }

    private companion object {
        const val FIRST_PACKET_VALUES = 12
        const val VALUES_PER_PACKET = 13
        /** Used if the header's packet count is implausible. R10 reports 5 packets. */
        const val DEFAULT_LAST_PACKET = 4
    }
}
