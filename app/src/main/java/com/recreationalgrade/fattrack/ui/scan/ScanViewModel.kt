package com.recreationalgrade.fattrack.ui.scan

import android.bluetooth.le.ScanCallback
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recreationalgrade.fattrack.data.ble.BleRepository
import com.recreationalgrade.fattrack.data.ble.BleScanException
import com.recreationalgrade.fattrack.data.ble.ScannedDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val bleRepository: BleRepository,
) : ViewModel() {

    data class UiState(
        val isScanning: Boolean = false,
        val bluetoothEnabled: Boolean = true,
        val devices: List<ScannedDevice> = emptyList(),
        val error: String? = null,
        /** True when the last scan ended because [SCAN_WINDOW] elapsed. */
        val scanTimedOut: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    // Bumped on every start so a cancelled scan's cleanup can't clear the
    // isScanning flag of a newer one. All access happens on the main thread.
    private var scanGeneration = 0

    // Android allows ~5 scan starts per 30 s per app, and often enforces this
    // by silently returning no results rather than calling onScanFailed.
    private val recentScanStarts = ArrayDeque<TimeSource.Monotonic.ValueTimeMark>()

    fun refreshBluetoothState() = _uiState.update {
        it.copy(bluetoothEnabled = bleRepository.isBluetoothEnabled)
    }

    fun startScan() {
        if (scanJob?.isActive == true) return
        refreshBluetoothState()
        if (!bleRepository.isBluetoothEnabled) return

        throttleRemaining()?.let { wait ->
            _uiState.update {
                it.copy(error = "Scanning too often. Try again in ${wait.inWholeSeconds + 1} s.")
            }
            return
        }
        recentScanStarts.addLast(TimeSource.Monotonic.markNow())

        val generation = ++scanGeneration
        _uiState.update {
            it.copy(isScanning = true, devices = emptyList(), error = null, scanTimedOut = false)
        }

        scanJob = viewModelScope.launch {
            try {
                val completed = withTimeoutOrNull(SCAN_WINDOW) {
                    bleRepository.scanForDevices().collect { devices ->
                        _uiState.update { it.copy(devices = devices) }
                    }
                }
                if (completed == null) _uiState.update { it.copy(scanTimedOut = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.toUserMessage()) }
            } finally {
                if (generation == scanGeneration) {
                    _uiState.update { it.copy(isScanning = false) }
                }
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update { it.copy(isScanning = false) }
    }

    /** Pairing/connection stub — real GATT connect (Nordic) lands in Phase 1. */
    fun onDeviceClick(device: ScannedDevice) {
        stopScan()
        // TODO(Phase 1): hand [device.address] to a connect() on the repository.
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }

    /** Time until another scan start is allowed, or null if one is allowed now. */
    private fun throttleRemaining(): Duration? {
        while (recentScanStarts.isNotEmpty() &&
            recentScanStarts.first().elapsedNow() >= THROTTLE_WINDOW
        ) {
            recentScanStarts.removeFirst()
        }
        if (recentScanStarts.size < MAX_SCAN_STARTS) return null
        return THROTTLE_WINDOW - recentScanStarts.first().elapsedNow()
    }

    private fun Throwable.toUserMessage(): String = when (this) {
        is BleScanException -> when (errorCode) {
            ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED ->
                "This phone doesn't support Bluetooth LE scanning."
            SCAN_FAILED_SCANNING_TOO_FREQUENTLY ->
                "Scanning too often. Wait 30 seconds and try again."
            else -> "Bluetooth scan failed (code $errorCode). Try turning Bluetooth off and on."
        }
        else -> message ?: "Scan failed."
    }

    companion object {
        val SCAN_WINDOW = 20.seconds
        private val THROTTLE_WINDOW = 30.seconds
        private const val MAX_SCAN_STARTS = 5
        // ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY is only public on API 33+.
        private const val SCAN_FAILED_SCANNING_TOO_FREQUENTLY = 6
    }
}
