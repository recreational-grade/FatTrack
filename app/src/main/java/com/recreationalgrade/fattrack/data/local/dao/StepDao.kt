package com.recreationalgrade.fattrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.recreationalgrade.fattrack.data.local.entity.StepSample
import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class StepTotals(
    val steps: Int = 0,
    val distanceMeters: Int = 0,
    val calories: Int = 0,
)

@Dao
interface StepDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(samples: List<StepSample>)

    @Query("SELECT MAX(slotStart) FROM steps WHERE deviceAddress = :address")
    suspend fun latestSlot(address: String): Instant?

    @Query(
        "SELECT COALESCE(SUM(steps), 0) AS steps, " +
            "COALESCE(SUM(distanceMeters), 0) AS distanceMeters, " +
            "COALESCE(SUM(calories), 0) AS calories " +
            "FROM steps WHERE slotStart >= :since"
    )
    fun observeTotalsSince(since: Instant): Flow<StepTotals>
}
