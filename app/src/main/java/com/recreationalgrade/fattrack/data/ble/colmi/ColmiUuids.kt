package com.recreationalgrade.fattrack.data.ble.colmi

import java.util.UUID

/** GATT layout confirmed on a Colmi R10 (fw RT03CR_1.00.02_260319) with nRF Connect. */
object ColmiUuids {
    val COMMAND_SERVICE: UUID = UUID.fromString("6e40fff0-b5a3-f393-e0a9-e50e24dcca9e")
    val COMMAND_WRITE: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
    val COMMAND_NOTIFY: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

    // History ("big data") channel: used from step 4.
    val BIG_DATA_SERVICE: UUID = UUID.fromString("de5bf728-d711-4e47-af26-65e3012a5dc7")
    val BIG_DATA_WRITE: UUID = UUID.fromString("de5bf72a-d711-4e47-af26-65e3012a5dc7")
    val BIG_DATA_NOTIFY: UUID = UUID.fromString("de5bf729-d711-4e47-af26-65e3012a5dc7")

    val DEVICE_INFO_SERVICE: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
    val FIRMWARE_REVISION: UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")
    val HARDWARE_REVISION: UUID = UUID.fromString("00002a27-0000-1000-8000-00805f9b34fb")
}
