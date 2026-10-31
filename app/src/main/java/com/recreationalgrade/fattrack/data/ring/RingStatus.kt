package com.recreationalgrade.fattrack.data.ring

import com.recreationalgrade.fattrack.data.ble.colmi.ColmiResponse

enum class RingConnection { DISCONNECTED, CONNECTING, READY, DISCONNECTING }

data class RingStatus(
    val connection: RingConnection = RingConnection.DISCONNECTED,
    /** Last reported battery; kept after disconnect as "last known". */
    val battery: ColmiResponse.Battery? = null,
    val error: String? = null,
)
