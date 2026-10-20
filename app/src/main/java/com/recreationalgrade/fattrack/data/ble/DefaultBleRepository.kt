package com.recreationalgrade.fattrack.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

/**
 * Platform-scanner implementation of [BleRepository].
 * Permissions are the caller's responsibility (see BlePermissionGate), hence
 * the MissingPermission suppression.
 */
class DefaultBleRepository @Inject constructor(
    private val bluetoothAdapter: BluetoothAdapter?,
) : BleRepository {

    override val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    override fun scanForDevices(): Flow<List<ScannedDevice>> = callbackFlow {
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            close(IllegalStateException("Bluetooth scanner unavailable"))
            return@callbackFlow
        }

        // Preserve discovery order, de-dupe by MAC.
        val found = LinkedHashMap<String, ScannedDevice>()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = ScannedDevice(
                    address = result.device.address,
                    // Prefer the advertised name (no CONNECT permission needed).
                    name = result.scanRecord?.deviceName,
                    rssi = result.rssi,
                )
                found[device.address] = device
                trySend(found.values.toList())
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("BLE scan failed: code=$errorCode"))
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(/* filters = */ null, settings, callback)
        awaitClose { scanner.stopScan(callback) }
    }
}
