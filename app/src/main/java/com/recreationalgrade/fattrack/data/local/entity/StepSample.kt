package com.recreationalgrade.fattrack.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import java.time.Instant

/** Activity in one one-hour UTC slot. Same keying and no-FK rule as [HeartRateSample]. */
@Entity(
    tableName = "steps",
    primaryKeys = ["deviceAddress", "slotStart"],
    indices = [Index("slotStart")],
)
data class StepSample(
    val deviceAddress: String,
    val slotStart: Instant,
    val steps: Int,
    val distanceMeters: Int,
    /** Small calories (1/1000 kcal); unit inferred, see StepSlot. */
    val calories: Int,
)
