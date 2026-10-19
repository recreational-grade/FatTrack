package com.recreationalgrade.fattrack.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    // Room, BLE repository, and dispatcher providers go here in later steps.
}