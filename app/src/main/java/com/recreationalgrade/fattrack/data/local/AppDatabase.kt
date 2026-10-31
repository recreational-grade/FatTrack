package com.recreationalgrade.fattrack.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.recreationalgrade.fattrack.data.local.dao.UserProfileDao
import com.recreationalgrade.fattrack.data.local.dao.WeightEntryDao
import com.recreationalgrade.fattrack.data.local.entity.UserProfile
import com.recreationalgrade.fattrack.data.local.entity.WeightEntry
import androidx.room.AutoMigration
import com.recreationalgrade.fattrack.data.local.dao.DeviceDao
import com.recreationalgrade.fattrack.data.local.entity.Device

@Database(
    entities = [UserProfile::class, WeightEntry::class, Device::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)

@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun weightEntryDao(): WeightEntryDao
    abstract fun deviceDao(): DeviceDao

}
