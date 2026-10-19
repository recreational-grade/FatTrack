package com.recreationalgrade.fattrack.ui.permissions

import android.Manifest
import android.os.Build

/** BLE runtime permissions, resolved for the device's API level. */
object BlePermissions {
    val required: List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+: dedicated BLE permissions (no location needed since
            // we declared neverForLocation on BLUETOOTH_SCAN).
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            // API 29–30: BLE scanning requires fine location at runtime.
            // BLUETOOTH / BLUETOOTH_ADMIN are install-time, not runtime.
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}
