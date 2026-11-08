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
import com.recreationalgrade.fattrack.data.ble.colmi.HeartRateLog
import com.recreationalgrade.fattrack.data.ble.colmi.HeartRateLogAssembler
import com.recreationalgrade.fattrack.data.local.dao.HeartRateDao
import com.recreationalgrade.fattrack.data.local.entity.HeartRateSample
import java.time.LocalDate
import java.time.ZoneOffset
import com.recreationalgrade.fattrack.data.ble.colmi.StepsLog
import com.recreationalgrade.fattrack.data.ble.colmi.StepsLogAssembler
import com.recreationalgrade.fattrack.data.local.dao.StepDao
import com.recreationalgrade.fattrack.data.local.dao.StepTotals
import com.recreationalgrade.fattrack.data.local.entity.StepSample
import java.time.temporal.ChronoUnit
import com.recreationalgrade.fattrack.data.ble.colmi.HalfHourLog
import com.recreationalgrade.fattrack.data.ble.colmi.HalfHourLogAssembler
import com.recreationalgrade.fattrack.data.ble.colmi.HalfHourMetric
import com.recreationalgrade.fattrack.data.local.dao.HrvDao
import com.recreationalgrade.fattrack.data.local.dao.StressDao
import com.recreationalgrade.fattrack.data.local.entity.HrvSample
import com.recreationalgrade.fattrack.data.local.entity.StressSample
import kotlinx.coroutines.flow.filter


/**
 * Owns the connection to the saved Colmi ring. App-scoped, so connections
 * survive screen changes. Phase 1 supports one ring at a time.
 */
@Singleton
class RingRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val deviceDao: DeviceDao,
    private val heartRateDao: HeartRateDao,
    private val stepDao: StepDao,
    private val stressDao: StressDao,
    private val hrvDao: HrvDao,
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
    val latestHeartRate: Flow<HeartRateSample?> = heartRateDao.observeLatest()
    val latestStress: Flow<StressSample?> = stressDao.observeLatest()
    val latestHrv: Flow<HrvSample?> = hrvDao.observeLatest()

    fun heartRateCountSince(since: Instant): Flow<Int> = heartRateDao.observeCountSince(since)
    fun stepTotalsSince(since: Instant): Flow<StepTotals> = stepDao.observeTotalsSince(since)

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
        scope.launch {
            responses.filterIsInstance<ColmiResponse.PacketSize>().collect {
                Log.i(TAG, "Ring packet size: ${it.bytes} bytes")
            }
        }
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
    
    /** Pulls history from the connected ring. */
    fun sync() {
        _status.update { it.copy(error = null) }
        launchBle { syncNow() }
    }

    fun setHeartRateLogging(enabled: Boolean, intervalMinutes: Int) {
        _status.update { it.copy(error = null) }
        launchBle {
            val reply = request<ColmiResponse.HeartRateLogSettingsAck>(
                ColmiRequests.writeHeartRateLogSettings(enabled, intervalMinutes)
            )
            if (reply == null) Log.w(TAG, "No ack for HR settings write")
            // Read back regardless: the ring's state is the source of truth.
            readHeartRateLogSettings()
        }
    }

    fun setAutoMeasure(metric: HalfHourMetric, enabled: Boolean) {
        _status.update { it.copy(error = null) }
        launchBle {
            val ack = request<ColmiResponse.AutoMeasureSettingAck>(
                ColmiRequests.writeAutoMeasure(metric, enabled),
            ) { it.metric == metric }
            if (ack == null) Log.w(TAG, "No ack for ${metric.label} settings write")
            readAutoMeasure(metric)
        }
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

    /** Runs once per connection: clock, battery, revisions, settings, history. */
    private suspend fun onConnected(address: String) {
        if (request<ColmiResponse.TimeSet>(ColmiRequests.setTime()) == null) {
            Log.w(TAG, "No ack for set time; continuing")
        }
        readBattery()
        deviceDao.updateRevisions(address, manager.firmwareRevision, manager.hardwareRevision)
        readHeartRateLogSettings()
        HalfHourMetric.entries.forEach { readAutoMeasure(it) }
        syncNow()
    }


    private suspend fun readBattery() {
        val battery = request<ColmiResponse.Battery>(ColmiRequests.battery())
        if (battery == null) {
            setError("The ring didn't report its battery level.")
        } else {
            _status.update { it.copy(battery = battery) }
        }
    }
    
    private suspend fun readHeartRateLogSettings() {
        val settings = request<ColmiResponse.HeartRateLogSettings>(
            ColmiRequests.readHeartRateLogSettings()
        )
        if (settings == null) {
            Log.w(TAG, "No reply to HR settings read")
        } else {
            _status.update { it.copy(heartRateLogSettings = settings) }
        }
    }

    private suspend fun syncNow() {
        if (_status.value.syncing) return
        check(manager.isReady) { "Ring not connected" }
        val address = checkNotNull(manager.bluetoothDevice?.address) { "Ring not connected" }
        _status.update { it.copy(syncing = true) }
        try {
            // Short-circuits: if HR times out the ring isn't responding; skip steps.
            val ok = syncHeartRate(address) && syncSteps(address) &&
                syncStress(address) && syncHrv(address)
            if (ok) _status.update { it.copy(lastSyncAt = Instant.now()) }
        } finally {
            _status.update { it.copy(syncing = false) }
        }
    }

    /**
     * Fetches UTC days from the newest stored reading (at most [HISTORY_DAYS]
     * back) through today. Today is always re-fetched; inserts are idempotent.
     * Returns false if the ring stopped responding.
     */
    private suspend fun syncHeartRate(address: String): Boolean {
        val now = Instant.now()
        val today = now.atOffset(ZoneOffset.UTC).toLocalDate()
        val oldest = today.minusDays(HISTORY_DAYS - 1)
        val newestStored = heartRateDao.latestTimestamp(address)
            ?.atOffset(ZoneOffset.UTC)?.toLocalDate()
        var day = if (newestStored != null && newestStored > oldest) newestStored else oldest

        while (day <= today) {
            when (val log = requestHeartRateLog(day)) {
                null -> {
                    Log.w(TAG, "HR log $day: timed out")
                    setError("Heart-rate sync timed out.")
                    return false
                }
                HeartRateLog.NoData -> Log.i(TAG, "HR log $day: no data")
                is HeartRateLog.Day -> {
                    val samples = log.readings(now).map { (time, bpm) ->
                        HeartRateSample(deviceAddress = address, timestamp = time, bpm = bpm)
                    }
                    heartRateDao.insertAll(samples)
                    Log.i(TAG, "HR log $day: ${samples.size} readings (range ${log.rangeMinutes} min)")
                }
            }
            day = day.plusDays(1)
        }
        return true
    }

    /** Sends one log request and assembles the multi-packet reply. Null on timeout. */
    private suspend fun requestHeartRateLog(day: LocalDate): HeartRateLog? =
        withTimeoutOrNull(HISTORY_TIMEOUT) {
            coroutineScope {
                val assembler = HeartRateLogAssembler(day)
                val result = async(start = CoroutineStart.UNDISPATCHED) {
                    responses.filterIsInstance<ColmiResponse.HeartRateLogPart>()
                        .mapNotNull { assembler.add(it) }
                        .first()
                }
                manager.send(ColmiRequests.heartRateLog(day))
                result.await()
            }
        }

    /**
     * Fetches day offsets from the newest stored slot's day (at most
     * [HISTORY_DAYS] back) down to 0 = today (UTC). Returns false on timeout.
     */
    private suspend fun syncSteps(address: String): Boolean {
        val today = Instant.now().atOffset(ZoneOffset.UTC).toLocalDate()
        val newest = stepDao.latestSlot(address)?.atOffset(ZoneOffset.UTC)?.toLocalDate()
        val maxOffset = if (newest == null) {
            HISTORY_DAYS - 1
        } else {
            ChronoUnit.DAYS.between(newest, today).coerceIn(0, HISTORY_DAYS - 1)
        }

        for (offset in maxOffset downTo 0L) {
            val day = today.minusDays(offset)
            when (val log = requestStepsLog(offset.toInt())) {
                null -> {
                    Log.w(TAG, "Steps $day: timed out")
                    setError("Step sync timed out.")
                    return false
                }
                StepsLog.NoData -> Log.i(TAG, "Steps $day: no data")
                is StepsLog.Slots -> {
                    stepDao.insertAll(
                        log.slots.map {
                            StepSample(
                                deviceAddress = address,
                                slotStart = it.start,
                                steps = it.steps,
                                distanceMeters = it.distanceMeters,
                                calories = it.calories,
                            )
                        }
                    )
                    Log.i(TAG, "Steps $day: ${log.slots.sumOf { it.steps }} steps in ${log.slots.size} slots")
                }
            }
        }
        return true
    }

    private suspend fun requestStepsLog(dayOffset: Int): StepsLog? =
        withTimeoutOrNull(HISTORY_TIMEOUT) {
            coroutineScope {
                val assembler = StepsLogAssembler()
                val result = async(start = CoroutineStart.UNDISPATCHED) {
                    responses.filterIsInstance<ColmiResponse.StepsLogPart>()
                        .mapNotNull { assembler.add(it) }
                        .first()
                }
                manager.send(ColmiRequests.stepsLog(dayOffset))
                result.await()
            }
        }

    /**
     * Sends [packet] and waits for the first response of type [T]. Subscribes
     * before writing so a fast reply can't be missed. Null on timeout. Takes 
     * an optional filter, so a reply for the other metric can't be mistaken 
     * for the one requested.
     */
    private suspend inline fun <reified T : ColmiResponse> request(
        packet: ByteArray,
        crossinline accept: (T) -> Boolean = { true },
    ): T? = withTimeoutOrNull(RESPONSE_TIMEOUT) {
        coroutineScope {
            val reply = async(start = CoroutineStart.UNDISPATCHED) {
                responses.filterIsInstance<T>().first { accept(it) }
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
        const val HISTORY_DAYS = 7L
        val HISTORY_TIMEOUT = 10.seconds
    }
    private suspend fun readAutoMeasure(metric: HalfHourMetric) {
        val setting = request<ColmiResponse.AutoMeasureSetting>(
            ColmiRequests.readAutoMeasure(metric),
        ) { it.metric == metric }
        if (setting == null) {
            Log.w(TAG, "No reply to ${metric.label} settings read")
        } else {
            _status.update { it.copy(autoMeasure = it.autoMeasure + (metric to setting.enabled)) }
        }
    }

    /** The stress command takes no day argument, so only today (UTC) is fetched. */
    private suspend fun syncStress(address: String): Boolean =
        syncHalfHourLog(HalfHourMetric.STRESS, maxOffset = 0) { readings ->
            stressDao.insertAll(
                readings.map { (time, value) -> StressSample(deviceAddress = address, timestamp = time, level = value) },
            )
        }

    private suspend fun syncHrv(address: String): Boolean {
        val today = Instant.now().atOffset(ZoneOffset.UTC).toLocalDate()
        val newest = hrvDao.latestTimestamp(address)?.atOffset(ZoneOffset.UTC)?.toLocalDate()
        val maxDays = (HISTORY_DAYS - 1).toLong()
        val maxOffset = if (newest == null) {
            maxDays
        } else {
            ChronoUnit.DAYS.between(newest, today).coerceIn(0L, maxDays)
        }
        return syncHalfHourLog(HalfHourMetric.HRV, maxOffset) { readings ->
            hrvDao.insertAll(
                readings.map { (time, value) -> HrvSample(deviceAddress = address, timestamp = time, valueMs = value) },
            )
        }
    }

    private suspend fun syncHalfHourLog(
        metric: HalfHourMetric,
        maxOffset: Long,
        store: suspend (List<Pair<Instant, Int>>) -> Unit,
    ): Boolean {
        val now = Instant.now()
        val today = now.atOffset(ZoneOffset.UTC).toLocalDate()
        for (offset in maxOffset downTo 0L) {
            val day = today.minusDays(offset)
            val packet = when (metric) {
                HalfHourMetric.STRESS -> ColmiRequests.stressLog()
                HalfHourMetric.HRV -> ColmiRequests.hrvLog(offset.toInt())
            }
            when (val log = requestHalfHourLog(metric, day, packet)) {
                null -> {
                    Log.w(TAG, "${metric.label} $day: timed out")
                    setError("${metric.label} sync timed out.")
                    return false
                }
                HalfHourLog.NoData -> Log.i(TAG, "${metric.label} $day: no data")
                is HalfHourLog.Day -> {
                    val readings = log.readings(now)
                    store(readings)
                    Log.i(TAG, "${metric.label} $day: ${readings.size} readings")
                }
            }
        }
        return true
    }

    private suspend fun requestHalfHourLog(
        metric: HalfHourMetric,
        day: LocalDate,
        packet: ByteArray,
    ): HalfHourLog? = withTimeoutOrNull(HISTORY_TIMEOUT) {
        coroutineScope {
            val assembler = HalfHourLogAssembler(day.atStartOfDay(ZoneOffset.UTC).toInstant())
            val result = async(start = CoroutineStart.UNDISPATCHED) {
                responses.filterIsInstance<ColmiResponse.HalfHourLogPart>()
                    .filter { it.metric == metric }
                    .mapNotNull { assembler.add(it) }
                    .first()
            }
            manager.send(packet)
            result.await()
        }
    }
}
