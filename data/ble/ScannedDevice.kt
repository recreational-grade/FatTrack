package com.recreationalgrade.fattrack.data.ble

/** A device discovered during a BLE scan. */
data class ScannedDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
)
