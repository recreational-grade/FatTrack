package com.recreationalgrade.fattrack.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import java.time.Instant

/** HRV in milliseconds for a 30-minute UTC slot. */
@Entity(
    tableName = "hrv_sample",
    primaryKeys = ["deviceAddress", "timestamp"],
    indices = [Index("timestamp")],
)
data class HrvSample(
    val deviceAddress: String,
    val timestamp: Instant,
    val valueMs: Int,
)
