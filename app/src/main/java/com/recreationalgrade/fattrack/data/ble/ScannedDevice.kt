package com.recreationalgrade.fattrack.data.ble

import com.recreationalgrade.fattrack.data.ble.match.DeviceMatch

/** A device discovered during a BLE scan. */
data class ScannedDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    /** Recognized device family, or null if FatTrack doesn't know this device. */
    val match: DeviceMatch? = null,
)
