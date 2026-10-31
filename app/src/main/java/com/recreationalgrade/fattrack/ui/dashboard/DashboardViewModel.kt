package com.recreationalgrade.fattrack.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recreationalgrade.fattrack.data.local.entity.Device
import com.recreationalgrade.fattrack.data.ring.RingRepository
import com.recreationalgrade.fattrack.data.ring.RingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val ringRepository: RingRepository,
) : ViewModel() {

    data class UiState(
        /** False until the saved ring has been read from the database once. */
        val loaded: Boolean = false,
        val ring: Device? = null,
        val status: RingStatus = RingStatus(),
    )

    val uiState: StateFlow<UiState> =
        combine(ringRepository.savedRing, ringRepository.status) { ring, status ->
            UiState(loaded = true, ring = ring, status = status)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun connect() = ringRepository.connect()
    fun disconnect() = ringRepository.disconnect()
    fun refreshBattery() = ringRepository.refreshBattery()
    fun forgetRing() = ringRepository.forgetRing()
}
