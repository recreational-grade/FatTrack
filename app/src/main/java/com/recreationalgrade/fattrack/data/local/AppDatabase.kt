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
import com.recreationalgrade.fattrack.data.local.dao.HeartRateDao
import com.recreationalgrade.fattrack.data.local.entity.HeartRateSample

@Database(
    entities = [UserProfile::class, WeightEntry::class, Device::class, HeartRateSample::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
    ],
)

@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun weightEntryDao(): WeightEntryDao
    abstract fun deviceDao(): DeviceDao
    abstract fun heartRateDao(): HeartRateDao
}
