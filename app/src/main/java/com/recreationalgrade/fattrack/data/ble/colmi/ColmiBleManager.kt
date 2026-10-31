package com.recreationalgrade.fattrack.data.ble.colmi

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.util.Log
import com.recreationalgrade.fattrack.BuildConfig
import com.recreationalgrade.fattrack.data.ble.cleanDeviceName
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.ktx.suspend

/**
 * GATT layer for Colmi rings. Knows characteristics, not protocol: it sends raw
 * 16-byte packets and exposes raw notifications. Never bonds. The ring also
 * exposes a HID service that Android reserves for the system.
 */
class ColmiBleManager(context: Context) : BleManager(context) {

    private var commandWrite: BluetoothGattCharacteristic? = null
    private var commandNotify: BluetoothGattCharacteristic? = null
    private var firmwareChar: BluetoothGattCharacteristic? = null
    private var hardwareChar: BluetoothGattCharacteristic? = null

    private val _packets = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)

    /** Raw notifications from the command channel. */
    val packets: SharedFlow<ByteArray> = _packets.asSharedFlow()

    /** Read during initialization; null if the ring has no Device Information service. */
    var firmwareRevision: String? = null
        private set
    var hardwareRevision: String? = null
        private set

    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        val command = gatt.getService(ColmiUuids.COMMAND_SERVICE) ?: return false
        commandWrite = command.getCharacteristic(ColmiUuids.COMMAND_WRITE)
            ?.takeIf { (it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 }
        commandNotify = command.getCharacteristic(ColmiUuids.COMMAND_NOTIFY)
            ?.takeIf { (it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 }

        // Optional: Device Information.
        gatt.getService(ColmiUuids.DEVICE_INFO_SERVICE)?.let { dis ->
            firmwareChar = dis.getCharacteristic(ColmiUuids.FIRMWARE_REVISION)
            hardwareChar = dis.getCharacteristic(ColmiUuids.HARDWARE_REVISION)
        }
        return commandWrite != null && commandNotify != null
    }

    override fun initialize() {
        val notify = commandNotify
        setNotificationCallback(notify).with { _, data ->
            data.value?.let { _packets.tryEmit(it) }
        }
        enableNotifications(notify).enqueue()

        firmwareChar?.let { c ->
            readCharacteristic(c).with { _, data ->
                firmwareRevision = data.getStringValue(0)?.cleanDeviceName()
            }.enqueue()
        }
        hardwareChar?.let { c ->
            readCharacteristic(c).with { _, data ->
                hardwareRevision = data.getStringValue(0)?.cleanDeviceName()
            }.enqueue()
        }
    }

    override fun onServicesInvalidated() {
        commandWrite = null
        commandNotify = null
        firmwareChar = null
        hardwareChar = null
        firmwareRevision = null
        hardwareRevision = null
    }

    /** Writes one packet (with response). Throws if not connected or the write fails. */
    suspend fun send(packet: ByteArray) {
        val characteristic = checkNotNull(commandWrite) { "Ring not connected" }
        writeCharacteristic(
            characteristic,
            packet,
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
        ).suspend()
    }

    override fun getMinLogPriority(): Int =
        if (BuildConfig.DEBUG) Log.VERBOSE else Log.WARN

    override fun log(priority: Int, message: String) {
        Log.println(priority, TAG, message)
    }

    private companion object {
        const val TAG = "ColmiBle"
    }
}
