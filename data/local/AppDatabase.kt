package com.recreationalgrade.fattrack.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.recreationalgrade.fattrack.data.local.dao.UserProfileDao
import com.recreationalgrade.fattrack.data.local.dao.WeightEntryDao
import com.recreationalgrade.fattrack.data.local.entity.UserProfile
import com.recreationalgrade.fattrack.data.local.entity.WeightEntry

@Database(
    entities = [UserProfile::class, WeightEntry::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun weightEntryDao(): WeightEntryDao
}
