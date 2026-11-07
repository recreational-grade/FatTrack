package com.recreationalgrade.fattrack.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recreationalgrade.fattrack.data.local.entity.Device
import com.recreationalgrade.fattrack.data.local.entity.HeartRateSample
import com.recreationalgrade.fattrack.data.ring.RingRepository
import com.recreationalgrade.fattrack.data.ring.RingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
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
        val latestHeartRate: HeartRateSample? = null,
        val heartRateReadingsToday: Int = 0,
    )

    // Local midnight at screen creation; refreshed properly in step 6.
    private val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()

    val uiState: StateFlow<UiState> = combine(
        ringRepository.savedRing,
        ringRepository.status,
        ringRepository.latestHeartRate,
        ringRepository.heartRateCountSince(todayStart),
    ) { ring, status, latest, countToday ->
        UiState(
            loaded = true,
            ring = ring,
            status = status,
            latestHeartRate = latest,
            heartRateReadingsToday = countToday,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun connect() = ringRepository.connect()
    fun disconnect() = ringRepository.disconnect()
    fun refreshBattery() = ringRepository.refreshBattery()
    fun sync() = ringRepository.sync()
    fun forgetRing() = ringRepository.forgetRing()

    fun setHeartRateLogging(enabled: Boolean, intervalMinutes: Int) =
        ringRepository.setHeartRateLogging(enabled, intervalMinutes)
}
