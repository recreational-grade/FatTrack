package com.recreationalgrade.fattrack.data.ring

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import com.recreationalgrade.fattrack.data.ble.ScannedDevice
import com.recreationalgrade.fattrack.data.ble.colmi.ColmiBleManager
import com.recreationalgrade.fattrack.data.ble.colmi.ColmiParser
import com.recreationalgrade.fattrack.data.ble.colmi.ColmiRequests
import com.recreationalgrade.fattrack.data.ble.colmi.ColmiResponse
import com.recreationalgrade.fattrack.data.ble.colmi.toHex
import com.recreationalgrade.fattrack.data.ble.match.DeviceFamily
import com.recreationalgrade.fattrack.data.local.dao.DeviceDao
import com.recreationalgrade.fattrack.data.local.entity.Device
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import no.nordicsemi.android.ble.ktx.suspend
import no.nordicsemi.android.ble.observer.ConnectionObserver
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Owns the connection to the saved Colmi ring. App-scoped, so connections
 * survive screen changes. Phase 1 supports one ring at a time.
 */
@Singleton
class RingRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val deviceDao: DeviceDao,
) {
    private val bluetoothAdapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val manager = ColmiBleManager(context)

    private val responses: Flow<ColmiResponse> = manager.packets.mapNotNull { packet ->
        ColmiParser.parse(packet).also {
            if (it == null) Log.w(TAG, "Invalid packet: ${packet.toHex()}")
        }
    }

    private val _status = MutableStateFlow(RingStatus())
    val status: StateFlow<RingStatus> = _status.asStateFlow()

    val savedRing: Flow<Device?> = deviceDao.observeByFamily(DeviceFamily.COLMI_RING)

    private var connectJob: Job? = null

    private val observer = object : ConnectionObserver {
        override fun onDeviceConnecting(device: BluetoothDevice) =
            setConnection(RingConnection.CONNECTING)

        // Still initializing (discovery, notifications, DIS reads) until ready.
        override fun onDeviceConnected(device: BluetoothDevice) =
            setConnection(RingConnection.CONNECTING)

        override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
            setConnection(RingConnection.DISCONNECTED)
            if (reason == ConnectionObserver.REASON_NOT_SUPPORTED) {
                setError("This device doesn't have the Colmi ring services.")
            }
        }

        override fun onDeviceReady(device: BluetoothDevice) =
            setConnection(RingConnection.READY)

        override fun onDeviceDisconnecting(device: BluetoothDevice) =
            setConnection(RingConnection.DISCONNECTING)

        override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
            setConnection(RingConnection.DISCONNECTED)
            if (reason == ConnectionObserver.REASON_LINK_LOSS) {
                setError("Lost connection to the ring.")
            }
        }
    }

    init {
        manager.setConnectionObserver(observer)
    }

    // --- Public API ---

    /** Saves [device] as the ring (replacing any other) and connects to it. */
    fun selectRing(device: ScannedDevice) {
        val match = requireNotNull(device.match) { "Not a recognized device" }
        require(match.family == DeviceFamily.COLMI_RING) { "Not a Colmi ring: ${match.family}" }
        startConnect {
            deviceDao.replaceForFamily(
                Device(
                    address = device.address,
                    name = device.name,
                    family = match.family,
                    model = match.model,
                    addedAt = Instant.now(),
                )
            )
            device.address
        }
    }

    /** Connects to the saved ring. */
    fun connect() = startConnect {
        deviceDao.getByFamily(DeviceFamily.COLMI_RING)?.address
            ?: run { setError("No ring saved. Scan for one first."); null }
    }

    fun disconnect() {
        connectJob?.cancel()
        scope.launch { disconnectQuietly() }
    }

    fun refreshBattery() {
        _status.update { it.copy(error = null) }
        launchBle { readBattery() }
    }

    /** Disconnects and removes the saved ring. */
    fun forgetRing() {
        connectJob?.cancel()
        launchBle {
            disconnectQuietly()
            deviceDao.deleteByFamily(DeviceFamily.COLMI_RING)
            _status.value = RingStatus()
        }
    }

    // --- Internals ---

    private fun startConnect(resolveAddress: suspend () -> String?) {
        connectJob?.cancel()
        _status.update { it.copy(error = null) }
        connectJob = launchBle {
            val address = resolveAddress() ?: return@launchBle
            connectTo(address)
        }
    }

    private suspend fun connectTo(address: String) {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            setError("Bluetooth is off.")
            return
        }
        if (manager.isConnected && manager.bluetoothDevice?.address != address) {
            manager.disconnect().suspend()
        }
        if (!manager.isReady) {
            manager.connect(adapter.getRemoteDevice(address))
                .retry(CONNECT_RETRIES, CONNECT_RETRY_DELAY_MS)
                .useAutoConnect(false)
                .timeout(CONNECT_TIMEOUT_MS)
                .suspend()
        }
        onConnected(address)
    }

    /** Runs once per connection: clock, battery, revisions. */
    private suspend fun onConnected(address: String) {
        if (request<ColmiResponse.TimeSet>(ColmiRequests.setTime()) == null) {
            Log.w(TAG, "No ack for set time; continuing")
        }
        readBattery()
        deviceDao.updateRevisions(address, manager.firmwareRevision, manager.hardwareRevision)
    }

    private suspend fun readBattery() {
        val battery = request<ColmiResponse.Battery>(ColmiRequests.battery())
        if (battery == null) {
            setError("The ring didn't report its battery level.")
        } else {
            _status.update { it.copy(battery = battery) }
        }
    }

    /**
     * Sends [packet] and waits for the first response of type [T]. Subscribes
     * before writing so a fast reply can't be missed. Null on timeout.
     */
    private suspend inline fun <reified T : ColmiResponse> request(packet: ByteArray): T? =
        withTimeoutOrNull(RESPONSE_TIMEOUT) {
            coroutineScope {
                val reply = async(start = CoroutineStart.UNDISPATCHED) {
                    responses.filterIsInstance<T>().first()
                }
                manager.send(packet)
                reply.await()
            }
        }

    /** Also cancels an in-progress connection attempt. */
    private suspend fun disconnectQuietly() {
        try {
            manager.disconnect().suspend()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Disconnect: ${e.message}")
        }
    }

    private fun launchBle(block: suspend () -> Unit): Job = scope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing Bluetooth permission", e)
            setError("Bluetooth permission is missing. Grant it from the Scan screen.")
        } catch (e: Exception) {
            Log.w(TAG, "Ring operation failed", e)
            // Keep a more specific error (e.g. NOT_SUPPORTED) if one was already set.
            if (_status.value.error == null) {
                setError("Couldn't reach the ring. Make sure it's nearby and not connected to another app.")
            }
        }
    }

    private fun setConnection(connection: RingConnection) =
        _status.update { it.copy(connection = connection) }

    private fun setError(message: String) =
        _status.update { it.copy(error = message) }

    private companion object {
        const val TAG = "RingRepository"
        const val CONNECT_RETRIES = 3
        const val CONNECT_RETRY_DELAY_MS = 200
        const val CONNECT_TIMEOUT_MS = 15_000L
        val RESPONSE_TIMEOUT = 5.seconds
    }
}
