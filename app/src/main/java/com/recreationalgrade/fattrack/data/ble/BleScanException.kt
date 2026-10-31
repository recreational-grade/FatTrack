package com.recreationalgrade.fattrack.data.ble

/** Error raised by the scan flow when the platform reports a scan failure. */
class BleScanException(val errorCode: Int) : Exception("BLE scan failed: code=$errorCode")
