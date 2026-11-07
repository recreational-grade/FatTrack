package com.recreationalgrade.fattrack.data.ble.colmi

import java.time.Instant
import java.time.ZoneOffset
import java.time.LocalDate

/** Builders for outgoing packets. */
object ColmiRequests {

    private const val LANGUAGE_ENGLISH: Byte = 1
    private const val ACTION_START: Byte = 1

    /**
     * Sets the ring's clock. The ring has no time-zone concept; we always set
     * it to UTC so history timestamps are unambiguous across DST and travel.
     * Grouping by local day happens on the phone.
     */
    fun setTime(now: Instant = Instant.now()): ByteArray {
        val t = now.atOffset(ZoneOffset.UTC)
        require(t.year in 2000..2099) { "Year out of range for ring: ${t.year}" }
        val payload = byteArrayOf(
            bcd(t.year % 100),
            bcd(t.monthValue),
            bcd(t.dayOfMonth),
            bcd(t.hour),
            bcd(t.minute),
            bcd(t.second),
            LANGUAGE_ENGLISH,
        )
        return ColmiPacket.build(ColmiCommand.SET_TIME, payload)
    }

    fun battery(): ByteArray = ColmiPacket.build(ColmiCommand.BATTERY)

    fun startRealTime(type: RealTimeType): ByteArray =
        ColmiPacket.build(ColmiCommand.REAL_TIME_START, byteArrayOf(type.code.toByte(), ACTION_START))

    fun stopRealTime(type: RealTimeType): ByteArray =
        ColmiPacket.build(ColmiCommand.REAL_TIME_STOP, byteArrayOf(type.code.toByte(), 0, 0))

    /** Two decimal digits packed into one byte: 26 -> 0x26. */
    internal fun bcd(value: Int): Byte {
        require(value in 0..99) { "BCD value out of range: $value" }
        return (((value / 10) shl 4) or (value % 10)).toByte()
    }

    /** Requests the 5-minute HR log for one UTC day. */
    fun heartRateLog(dayUtc: LocalDate): ByteArray {
        val epochSeconds = dayUtc.atStartOfDay(ZoneOffset.UTC).toEpochSecond()
        return ColmiPacket.build(ColmiCommand.HEART_RATE_LOG, epochSeconds.toUInt32Le())
    }

    fun readHeartRateLogSettings(): ByteArray =
        ColmiPacket.build(ColmiCommand.HEART_RATE_LOG_SETTINGS, byteArrayOf(1))

    fun writeHeartRateLogSettings(enabled: Boolean, intervalMinutes: Int): ByteArray {
        require(intervalMinutes in 1..255) { "Interval out of range: $intervalMinutes" }
        return ColmiPacket.build(
            ColmiCommand.HEART_RATE_LOG_SETTINGS,
            byteArrayOf(2, if (enabled) 1 else 2, intervalMinutes.toByte()),
        )
    }
}
