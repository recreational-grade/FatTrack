package com.recreationalgrade.fattrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.recreationalgrade.fattrack.data.local.entity.StressSample
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface StressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(samples: List<StressSample>)

    @Query("SELECT MAX(timestamp) FROM stress_sample WHERE deviceAddress = :address")
    suspend fun latestTimestamp(address: String): Instant?

    @Query("SELECT * FROM stress_sample ORDER BY timestamp DESC LIMIT 1")
    fun observeLatest(): Flow<StressSample?>
}
