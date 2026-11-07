package com.recreationalgrade.fattrack.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import java.time.Instant

/**
 * One heart-rate reading from a device's log. Keyed by (device, time) so
 * re-syncing the same day is idempotent. Deliberately no foreign key to
 * [Device]: forgetting a ring keeps its history.
 */
@Entity(
    tableName = "heart_rate",
    primaryKeys = ["deviceAddress", "timestamp"],
    indices = [Index("timestamp")],
)
data class HeartRateSample(
    val deviceAddress: String,
    val timestamp: Instant,
    val bpm: Int,
)
