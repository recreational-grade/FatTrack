package com.recreationalgrade.fattrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.recreationalgrade.fattrack.data.local.entity.HeartRateSample
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface HeartRateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(samples: List<HeartRateSample>)

    @Query("SELECT MAX(timestamp) FROM heart_rate WHERE deviceAddress = :address")
    suspend fun latestTimestamp(address: String): Instant?

    @Query("SELECT * FROM heart_rate ORDER BY timestamp DESC LIMIT 1")
    fun observeLatest(): Flow<HeartRateSample?>

    @Query("SELECT COUNT(*) FROM heart_rate WHERE timestamp >= :since")
    fun observeCountSince(since: Instant): Flow<Int>
}
