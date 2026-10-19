package com.recreationalgrade.fattrack.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recreationalgrade.fattrack.data.ble.BleRepository
import com.recreationalgrade.fattrack.data.ble.ScannedDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val bleRepository: BleRepository,
) : ViewModel() {

    data class UiState(
        val isScanning: Boolean = false,
        val bluetoothEnabled: Boolean = true,
        val devices: List<ScannedDevice> = emptyList(),
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    fun refreshBluetoothState() = _uiState.update {
        it.copy(bluetoothEnabled = bleRepository.isBluetoothEnabled)
    }

    fun startScan() {
        if (scanJob?.isActive == true) return
        refreshBluetoothState()
        if (!bleRepository.isBluetoothEnabled) return

        _uiState.update { it.copy(isScanning = true, devices = emptyList(), error = null) }
        scanJob = bleRepository.scanForDevices()
            .onEach { devices -> _uiState.update { it.copy(devices = devices) } }
            .catch { e -> _uiState.update { it.copy(error = e.message, isScanning = false) } }
            .onCompletion { _uiState.update { it.copy(isScanning = false) } }
            .launchIn(viewModelScope)
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
}
