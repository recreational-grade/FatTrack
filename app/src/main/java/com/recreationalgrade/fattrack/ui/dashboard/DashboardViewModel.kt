package com.recreationalgrade.fattrack.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recreationalgrade.fattrack.data.ble.colmi.HalfHourMetric
import com.recreationalgrade.fattrack.data.local.dao.StepTotals
import com.recreationalgrade.fattrack.data.local.entity.Device
import com.recreationalgrade.fattrack.data.local.entity.HeartRateSample
import com.recreationalgrade.fattrack.data.local.entity.HrvSample
import com.recreationalgrade.fattrack.data.local.entity.StressSample
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
        val stepsToday: StepTotals = StepTotals(),
        val latestStress: StressSample? = null,
        val latestHrv: HrvSample? = null,
    )

    // Local midnight at screen creation; refreshed properly in step 6.
    private val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()

    // Must be declared before uiState (initialization order).
    private val halfHourly = combine(
        ringRepository.latestStress,
        ringRepository.latestHrv,
    ) { stress, hrv -> stress to hrv }

    val uiState: StateFlow<UiState> = combine(
        ringRepository.savedRing,
        ringRepository.status,
        ringRepository.latestHeartRate,
        ringRepository.heartRateCountSince(todayStart),
        ringRepository.stepTotalsSince(todayStart),
    ) { ring, status, latest, countToday, stepsToday ->
        UiState(
            loaded = true,
            ring = ring,
            status = status,
            latestHeartRate = latest,
            heartRateReadingsToday = countToday,
            stepsToday = stepsToday,
        )
    }
        .combine(halfHourly) { state, (stress, hrv) ->
            state.copy(latestStress = stress, latestHrv = hrv)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun connect() = ringRepository.connect()
    fun disconnect() = ringRepository.disconnect()
    fun refreshBattery() = ringRepository.refreshBattery()
    fun sync() = ringRepository.sync()
    fun forgetRing() = ringRepository.forgetRing()

    fun setHeartRateLogging(enabled: Boolean, intervalMinutes: Int) =
        ringRepository.setHeartRateLogging(enabled, intervalMinutes)

    fun setAutoMeasure(metric: HalfHourMetric, enabled: Boolean) =
        ringRepository.setAutoMeasure(metric, enabled)
}
