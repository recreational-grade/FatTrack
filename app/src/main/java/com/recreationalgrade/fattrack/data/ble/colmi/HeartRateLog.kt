package com.recreationalgrade.fattrack.data.ble.colmi

import com.recreationalgrade.fattrack.data.ble.colmi.ColmiResponse.HeartRateLogPart
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** A fully assembled HR log for one UTC day. */
sealed interface HeartRateLog {

    data object NoData : HeartRateLog

    data class Day(
        val dayStart: Instant,
        /** Reported by the ring; slots are treated as 5 minutes regardless (see notes). */
        val rangeMinutes: Int,
        /** [SLOTS_PER_DAY] values; 0 = no reading, -1 = packet never received. */
        val values: List<Int>,
    ) : HeartRateLog {

        /** Valid readings at or before [now]. Future slots for today are discarded. */
        fun readings(now: Instant): List<Pair<Instant, Int>> =
            values.mapIndexedNotNull { index, bpm ->
                val time = dayStart.plusSeconds(index * SLOT_SECONDS)
                if (bpm in 1..254 && !time.isAfter(now)) time to bpm else null
            }
    }

    companion object {
        const val SLOTS_PER_DAY = 288
        const val SLOT_SECONDS = 5L * 60
    }
}

/**
 * Folds the multi-packet reply to [ColmiRequests.heartRateLog] into a [HeartRateLog].
 * Sub-packet 1 carries a timestamp plus 9 values; 2..n carry 13 values each; the
 * log is complete at sub-packet `packetCount - 1`. Format per tahnok/colmi_r02_client.
 */
class HeartRateLogAssembler(private val requestedDay: LocalDate) {

    private var packetCount = -1
    private var rangeMinutes = 5
    private var dayStart: Instant? = null
    private val values = IntArray(HeartRateLog.SLOTS_PER_DAY) { -1 }

    /** Returns the finished log once complete, otherwise null. */
    fun add(part: HeartRateLogPart): HeartRateLog? = when (part) {
        HeartRateLogPart.NoData -> HeartRateLog.NoData

        is HeartRateLogPart.Header ->
            if (part.packetCount < 2) {
                HeartRateLog.NoData
            } else {
                packetCount = part.packetCount
                rangeMinutes = part.rangeMinutes
                null
            }

        is HeartRateLogPart.Values -> {
            if (packetCount < 0) {
                null // No header yet: stray packet, ignore.
            } else {
                part.timestamp?.let {
                    dayStart = Instant.ofEpochSecond(it).truncatedTo(ChronoUnit.DAYS)
                }
                val offset = if (part.subType == 1) {
                    0
                } else {
                    FIRST_PACKET_VALUES + (part.subType - 2) * VALUES_PER_PACKET
                }
                part.values.forEachIndexed { i, v ->
                    val index = offset + i
                    if (index in values.indices) values[index] = v
                }
                if (part.subType >= packetCount - 1) finish() else null
            }
        }
    }

    private fun finish() = HeartRateLog.Day(
        dayStart = dayStart ?: requestedDay.atStartOfDay(ZoneOffset.UTC).toInstant(),
        rangeMinutes = rangeMinutes,
        values = values.toList(),
    )

    private companion object {
        const val FIRST_PACKET_VALUES = 9
        const val VALUES_PER_PACKET = 13
    }
}