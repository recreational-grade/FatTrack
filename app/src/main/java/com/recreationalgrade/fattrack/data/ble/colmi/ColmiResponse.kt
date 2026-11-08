package com.recreationalgrade.fattrack.data.ble.colmi
import java.time.LocalDate

/** Decoded packets from the ring's notify characteristic (6E400003…). */
sealed interface ColmiResponse {

    data class Battery(val level: Int, val charging: Boolean) : ColmiResponse

    /**
     * Ack for SET_TIME. The payload is a capability bitmap that upstream calls
     * unreliable (e.g. it claims WeChat support), so it's ignored for now.
     */
    data object TimeSet : ColmiResponse

    /** A live value. In practice the first readings can be 0 while the sensor settles. */
    data class RealTimeReading(val type: RealTimeType?, val rawType: Int, val value: Int) : ColmiResponse

    data class RealTimeError(val type: RealTimeType?, val rawType: Int, val code: Int) : ColmiResponse

    /** Valid packet whose command we don't decode (yet). */
    class Unknown(val command: Int, val packet: ByteArray) : ColmiResponse {
        override fun toString() = "Unknown(command=0x%02X, packet=${packet.toHex()})".format(command)
    }

    /** Usable bytes per packet on this connection. */
    data class PacketSize(val bytes: Int) : ColmiResponse

    data class HeartRateLogSettings(val enabled: Boolean, val intervalMinutes: Int) : ColmiResponse
    
    /** Ack for a settings write (sub-command 0x02). [status] is 1 on the R10; likely "OK". */
    data class HeartRateLogSettingsAck(val status: Int) : ColmiResponse

    /** One packet of a multi-packet HR log. Assemble with [HeartRateLogAssembler]. */
    sealed interface HeartRateLogPart : ColmiResponse {
        data object NoData : HeartRateLogPart
        data class Header(val packetCount: Int, val rangeMinutes: Int) : HeartRateLogPart
        /** [timestamp] (epoch seconds) is only present in sub-packet 1. */
        data class Values(val subType: Int, val timestamp: Long?, val values: List<Int>) : HeartRateLogPart
    }
    
    /** One packet of a multi-packet steps log. Assemble with [StepsLogAssembler]. */
    sealed interface StepsLogPart : ColmiResponse {
        data object NoData : StepsLogPart
        /** If [newCalorieProtocol], calorie values are in units of 10. */
        data class Header(val newCalorieProtocol: Boolean) : StepsLogPart
        data class Slot(
            val date: LocalDate,
            /** 15-minute slot within the UTC day, 0..95. */
            val slotIndex: Int,
            val packetIndex: Int,
            val packetCount: Int,
            val caloriesRaw: Int,
            val steps: Int,
            val distanceMeters: Int,
        ) : StepsLogPart
    }

}

object ColmiParser {

    /** Decodes one notification, or returns null if it's not a valid 16-byte packet. */
    fun parse(packet: ByteArray): ColmiResponse? {
        if (!ColmiPacket.isValid(packet)) return null
        return when (val command = packet.u8(0)) {
            ColmiCommand.BATTERY -> ColmiResponse.Battery(
                level = packet.u8(1),
                charging = packet.u8(2) != 0,
            )
            ColmiCommand.SET_TIME -> ColmiResponse.TimeSet
            ColmiCommand.REAL_TIME_START -> parseRealTime(packet)
            ColmiCommand.HEART_RATE_LOG -> parseHeartRateLog(packet)
            ColmiCommand.HEART_RATE_LOG_SETTINGS -> when (packet.u8(1)) {
                // Byte 1 echoes the sub-command: 1 = read reply, 2 = write ack.
                2 -> ColmiResponse.HeartRateLogSettingsAck(status = packet.u8(2))
                else -> ColmiResponse.HeartRateLogSettings(
                    // 1 = on, 2 = off; anything else treated as off.
                    enabled = packet.u8(2) == 1,
                    intervalMinutes = packet.u8(3),
                )
            }

            ColmiCommand.PACKET_SIZE -> ColmiResponse.PacketSize(packet.u8(1))
            ColmiCommand.STEPS_LOG -> parseStepsLog(packet)
            else -> ColmiResponse.Unknown(command, packet.copyOf())
        }
    }

    private fun parseRealTime(packet: ByteArray): ColmiResponse {
        val rawType = packet.u8(1)
        val type = RealTimeType.fromCode(rawType)
        val error = packet.u8(2)
        return if (error != 0) {
            ColmiResponse.RealTimeError(type, rawType, error)
        } else {
            ColmiResponse.RealTimeReading(type, rawType, packet.u8(3))
        }
    }

    private fun parseHeartRateLog(packet: ByteArray): ColmiResponse.HeartRateLogPart =
        when (val subType = packet.u8(1)) {
            0xFF -> ColmiResponse.HeartRateLogPart.NoData
            0 -> ColmiResponse.HeartRateLogPart.Header(
                packetCount = packet.u8(2),
                rangeMinutes = packet.u8(3),
            )
            1 -> ColmiResponse.HeartRateLogPart.Values(
                subType = 1,
                timestamp = packet.intLe(2).toLong(),
                values = (6..14).map { packet.u8(it) },
            )
            else -> ColmiResponse.HeartRateLogPart.Values(
                subType = subType,
                timestamp = null,
                values = (2..14).map { packet.u8(it) },
            )
        }
        
    private fun parseStepsLog(packet: ByteArray): ColmiResponse = when (packet.u8(1)) {
        0xFF -> ColmiResponse.StepsLogPart.NoData
        0xF0 -> ColmiResponse.StepsLogPart.Header(newCalorieProtocol = packet.u8(3) == 1)
        else -> {
            val date = runCatching {
                LocalDate.of(
                    2000 + fromBcd(packet.u8(1)),
                    fromBcd(packet.u8(2)),
                    fromBcd(packet.u8(3)),
                )
            }.getOrNull()
            if (date == null) {
                ColmiResponse.Unknown(ColmiCommand.STEPS_LOG, packet.copyOf())
            } else {
                ColmiResponse.StepsLogPart.Slot(
                    date = date,
                    slotIndex = packet.u8(4),
                    packetIndex = packet.u8(5),
                    packetCount = packet.u8(6),
                    caloriesRaw = packet.u16Le(7),
                    steps = packet.u16Le(9),
                    distanceMeters = packet.u16Le(11),
                )
            }
        }
    }
}
