package com.recreationalgrade.fattrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.recreationalgrade.fattrack.data.local.entity.HrvSample
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface HrvDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(samples: List<HrvSample>)

    @Query("SELECT MAX(timestamp) FROM hrv_sample WHERE deviceAddress = :address")
    suspend fun latestTimestamp(address: String): Instant?

    @Query("SELECT * FROM hrv_sample ORDER BY timestamp DESC LIMIT 1")
    fun observeLatest(): Flow<HrvSample?>
}
