package com.recreationalgrade.fattrack.data.ble.colmi

/** Command bytes for the Colmi command channel. History commands land in step 4. */
object ColmiCommand {
    const val SET_TIME = 0x01
    const val BATTERY = 0x03
    const val HEART_RATE_LOG = 0x15
    const val HEART_RATE_LOG_SETTINGS = 0x16
    /** Unsolicited; reports the usable payload size (20 at default MTU, 244 at 247). */
    const val PACKET_SIZE = 0x2F
    const val REAL_TIME_START = 0x69
    const val REAL_TIME_STOP = 0x6A
    const val STEPS_LOG = 0x43
    const val STRESS_SETTINGS = 0x36
    const val STRESS_LOG = 0x37
    const val HRV_SETTINGS = 0x38
    const val HRV_LOG = 0x39
}

/** Live measurement types. Only HR and SpO2 give plausible values on these rings. */
enum class RealTimeType(val code: Int) {
    HEART_RATE(1),
    SPO2(3);

    companion object {
        fun fromCode(code: Int): RealTimeType? = entries.firstOrNull { it.code == code }
    }
}
