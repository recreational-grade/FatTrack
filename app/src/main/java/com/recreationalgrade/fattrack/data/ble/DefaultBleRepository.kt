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
import com.recreationalgrade.fattrack.data.ble.match.DeviceRecognizer

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
                val address = result.device.address
                // Prefer the advertised name (no CONNECT permission needed). Keep
                // the previous name if this result arrived without one.
                val name = result.scanRecord?.deviceName?.cleanDeviceName()
                    ?: found[address]?.name
                val device = ScannedDevice(
                    address = address,
                    name = name,
                    rssi = result.rssi,
                    match = DeviceRecognizer.recognize(name),
                )
                found[address] = device
                trySend(found.values.toList())
            }

            override fun onScanFailed(errorCode: Int) {
                close(BleScanException(errorCode))
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(/* filters = */ null, settings, callback)
        awaitClose { scanner.stopScan(callback) }
    }
}
