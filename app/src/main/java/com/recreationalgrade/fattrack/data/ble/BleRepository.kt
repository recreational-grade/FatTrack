package com.recreationalgrade.fattrack.data.ble

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction boundary over the BLE stack. The current impl uses the platform
 * scanner; a Nordic-backed GATT connection layer slots in behind this interface
 * in Phase 1 without touching ViewModels.
 */
interface BleRepository {
    /** True if a Bluetooth adapter exists and is currently enabled. */
    val isBluetoothEnabled: Boolean

    /**
     * Cold flow: scanning starts on collection and stops on cancellation.
     * Emits the accumulated, de-duplicated device list on each discovery.
     */
    fun scanForDevices(): Flow<List<ScannedDevice>>
}
