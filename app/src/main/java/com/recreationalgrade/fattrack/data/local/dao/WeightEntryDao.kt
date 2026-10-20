package com.recreationalgrade.fattrack.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.recreationalgrade.fattrack.data.local.entity.WeightEntry
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface WeightEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: WeightEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<WeightEntry>)

    @Delete suspend fun delete(entry: WeightEntry)

    @Query("SELECT * FROM weight_entry WHERE profileId = :profileId ORDER BY timestamp DESC")
    fun observeForProfile(profileId: Long): Flow<List<WeightEntry>>

    @Query("SELECT * FROM weight_entry WHERE profileId = :profileId ORDER BY timestamp DESC LIMIT 1")
    fun observeLatest(profileId: Long): Flow<WeightEntry?>

    @Query("""
        SELECT * FROM weight_entry
        WHERE profileId = :profileId AND timestamp BETWEEN :start AND :end
        ORDER BY timestamp ASC
    """)
    fun observeInRange(profileId: Long, start: Instant, end: Instant): Flow<List<WeightEntry>>
}
