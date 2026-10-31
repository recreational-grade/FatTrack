package com.recreationalgrade.fattrack.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.recreationalgrade.fattrack.data.ble.match.DeviceFamily
import java.time.Instant

/** A saved BLE device. Phase 1 keeps at most one per family (one ring). */
@Entity(
    tableName = "device",
    indices = [Index("family")],
)
data class Device(
    @PrimaryKey val address: String,
    val name: String?,
    val family: DeviceFamily,
    /** Human-readable model from the matcher, e.g. "Colmi R10". */
    val model: String,
    val firmwareRevision: String? = null,
    val hardwareRevision: String? = null,
    val addedAt: Instant,
)
