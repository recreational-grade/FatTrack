package com.recreationalgrade.fattrack.data.ring

import com.recreationalgrade.fattrack.data.ble.colmi.ColmiResponse
import java.time.Instant
import com.recreationalgrade.fattrack.data.ble.colmi.HalfHourMetric


enum class RingConnection { DISCONNECTED, CONNECTING, READY, DISCONNECTING }

data class RingStatus(
    val connection: RingConnection = RingConnection.DISCONNECTED,
    /** Last reported battery; kept after disconnect as "last known". */
    val battery: ColmiResponse.Battery? = null,
    val heartRateLogSettings: ColmiResponse.HeartRateLogSettings? = null,
    val syncing: Boolean = false,
    val lastSyncAt: Instant? = null,
    val error: String? = null,
    /** Stress/HRV auto-measure state; a metric is absent until the ring has been read. */
    val autoMeasure: Map<HalfHourMetric, Boolean> = emptyMap(),
)
