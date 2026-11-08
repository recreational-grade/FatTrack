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
import com.recreationalgrade.fattrack.data.local.dao.StepDao
import com.recreationalgrade.fattrack.data.local.entity.StepSample
import com.recreationalgrade.fattrack.data.local.entity.StressSample
import com.recreationalgrade.fattrack.data.local.entity.HrvSample
import com.recreationalgrade.fattrack.data.local.dao.HrvDao
import com.recreationalgrade.fattrack.data.local.dao.StressDao


@Database(
    entities = [
        UserProfile::class,
        WeightEntry::class,
        Device::class,
        HeartRateSample::class,
        StepSample::class,
        StressSample::class,
        HrvSample::class,
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
    ],
)


@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun weightEntryDao(): WeightEntryDao
    abstract fun deviceDao(): DeviceDao
    abstract fun heartRateDao(): HeartRateDao
    abstract fun stepDao(): StepDao
    abstract fun stressDao(): StressDao
    abstract fun hrvDao(): HrvDao
}
