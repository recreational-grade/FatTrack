package com.recreationalgrade.fattrack.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.recreationalgrade.fattrack.data.local.entity.UserProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfile): Long

    @Update suspend fun update(profile: UserProfile)
    @Delete suspend fun delete(profile: UserProfile)

    @Query("SELECT * FROM user_profile WHERE id = :id")
    fun observeById(id: Long): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<UserProfile>>

    @Query("SELECT * FROM user_profile ORDER BY createdAt ASC LIMIT 1")
    suspend fun getFirstOrNull(): UserProfile?
}
