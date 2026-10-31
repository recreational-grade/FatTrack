package com.recreationalgrade.fattrack.data.ble.colmi

/**
 * Framing for the Colmi command channel (service 6E40FFF0…): every packet,
 * in both directions, is 16 bytes:
 *
 *   [0]      command
 *   [1..14]  payload, zero-padded
 *   [15]     checksum = sum of bytes 0..14, low 8 bits
 *
 * Protocol details from tahnok/colmi_r02_client (MIT).
 */
object ColmiPacket {
    const val SIZE = 16
    const val MAX_PAYLOAD = 14

    fun build(command: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        require(command in 0..0xFF) { "Command out of range: $command" }
        require(payload.size <= MAX_PAYLOAD) { "Payload too long: ${payload.size}" }
        val packet = ByteArray(SIZE)
        packet[0] = command.toByte()
        payload.copyInto(packet, destinationOffset = 1)
        packet[SIZE - 1] = checksum(packet)
        return packet
    }

    /**
     * Low 8 bits of the sum of bytes 0..14. The upstream docs say "modulo 255",
     * but the reference implementation uses `& 0xFF` (i.e. modulo 256).
     */
    fun checksum(packet: ByteArray): Byte {
        var sum = 0
        for (i in 0 until SIZE - 1) sum += packet.u8(i)
        return (sum and 0xFF).toByte()
    }

    fun isValid(packet: ByteArray): Boolean =
        packet.size == SIZE && packet[SIZE - 1] == checksum(packet)
}

/** Unsigned byte at [index]. */
internal fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF

/** Space-separated hex, for logs and test failure messages. */
fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
