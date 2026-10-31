package com.recreationalgrade.fattrack.data.ble.colmi

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
}
