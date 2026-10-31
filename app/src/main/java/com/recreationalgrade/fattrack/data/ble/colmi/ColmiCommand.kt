package com.recreationalgrade.fattrack.data.ble.colmi

/** Command bytes for the Colmi command channel. History commands land in step 4. */
object ColmiCommand {
    const val SET_TIME = 0x01
    const val BATTERY = 0x03
    const val REAL_TIME_START = 0x69
    const val REAL_TIME_STOP = 0x6A
}

/** Live measurement types. Only HR and SpO2 give plausible values on these rings. */
enum class RealTimeType(val code: Int) {
    HEART_RATE(1),
    SPO2(3);

    companion object {
        fun fromCode(code: Int): RealTimeType? = entries.firstOrNull { it.code == code }
    }
}
