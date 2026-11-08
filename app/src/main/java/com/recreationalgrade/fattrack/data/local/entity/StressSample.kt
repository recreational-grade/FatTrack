package com.recreationalgrade.fattrack.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import java.time.Instant

/** Stress level (1..100) for a 30-minute UTC slot. */
@Entity(
    tableName = "stress_sample",
    primaryKeys = ["deviceAddress", "timestamp"],
    indices = [Index("timestamp")],
)
data class StressSample(
    val deviceAddress: String,
    val timestamp: Instant,
    val level: Int,
)
