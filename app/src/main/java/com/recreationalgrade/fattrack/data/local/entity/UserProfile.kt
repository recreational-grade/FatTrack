package com.recreationalgrade.fattrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sex: Sex,
    val birthDate: LocalDate,   // used for age in BIA formulas
    val heightCm: Float,        // used for BMI + BIA
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
