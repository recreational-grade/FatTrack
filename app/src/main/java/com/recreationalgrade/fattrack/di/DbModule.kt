package com.recreationalgrade.fattrack.di

import android.content.Context
import androidx.room.Room
import com.recreationalgrade.fattrack.data.local.AppDatabase
import com.recreationalgrade.fattrack.data.local.dao.UserProfileDao
import com.recreationalgrade.fattrack.data.local.dao.WeightEntryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import com.recreationalgrade.fattrack.data.local.dao.DeviceDao
import com.recreationalgrade.fattrack.data.local.dao.HeartRateDao
import com.recreationalgrade.fattrack.data.local.dao.StepDao
import com.recreationalgrade.fattrack.data.local.dao.HrvDao
import com.recreationalgrade.fattrack.data.local.dao.StressDao

@Module
@InstallIn(SingletonComponent::class)
object DbModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "fattrack.db")
            // Pre-alpha only: wipes data on schema change. Replace with real
            // migrations before any real user data exists.
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideUserProfileDao(db: AppDatabase): UserProfileDao = db.userProfileDao()
    @Provides fun provideWeightEntryDao(db: AppDatabase): WeightEntryDao = db.weightEntryDao()
    @Provides fun provideDeviceDao(db: AppDatabase): DeviceDao = db.deviceDao()
    @Provides fun provideHeartRateDao(db: AppDatabase): HeartRateDao = db.heartRateDao()
    @Provides fun provideStepDao(db: AppDatabase): StepDao = db.stepDao()
    @Provides fun provideStressDao(db: AppDatabase): StressDao = db.stressDao()
    @Provides fun provideHrvDao(db: AppDatabase): HrvDao = db.hrvDao()
}
