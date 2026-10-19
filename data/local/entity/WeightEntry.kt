package com.recreationalgrade.fattrack.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "weight_entry",
    indices = [Index("profileId"), Index("timestamp")],
    foreignKeys = [
        ForeignKey(
            entity = UserProfile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val timestamp: Instant,
    val weightKg: Float,
    // Optional metrics — null for manual/weight-only entries
    val bodyFatPct: Float? = null,
    val bodyWaterPct: Float? = null,
    val muscleMassKg: Float? = null,
    val boneMassKg: Float? = null,
    val visceralFat: Float? = null,
    val bmi: Float? = null,
    val source: MeasurementSource = MeasurementSource.SCALE,
)
