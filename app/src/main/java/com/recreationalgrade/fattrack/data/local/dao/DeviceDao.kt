package com.recreationalgrade.fattrack.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.recreationalgrade.fattrack.data.ble.match.DeviceFamily
import com.recreationalgrade.fattrack.data.local.entity.Device
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DeviceDao {

    @Query("SELECT * FROM device WHERE family = :family LIMIT 1")
    abstract fun observeByFamily(family: DeviceFamily): Flow<Device?>

    @Query("SELECT * FROM device WHERE family = :family LIMIT 1")
    abstract suspend fun getByFamily(family: DeviceFamily): Device?

    @Upsert
    abstract suspend fun upsert(device: Device)

    @Query("DELETE FROM device WHERE family = :family")
    abstract suspend fun deleteByFamily(family: DeviceFamily)

    @Query("DELETE FROM device WHERE family = :family AND address != :keepAddress")
    protected abstract suspend fun deleteOthersInFamily(family: DeviceFamily, keepAddress: String)

    /**
     * Makes [device] the only saved device of its family. Upsert updates the row
     * in place when it already exists, so history tables (step 4) keep their rows.
     */
    @Transaction
    open suspend fun replaceForFamily(device: Device) {
        deleteOthersInFamily(device.family, device.address)
        upsert(device)
    }

    @Query(
        "UPDATE device SET firmwareRevision = :firmware, hardwareRevision = :hardware " +
            "WHERE address = :address"
    )
    abstract suspend fun updateRevisions(address: String, firmware: String?, hardware: String?)
}
