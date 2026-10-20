package com.recreationalgrade.fattrack.di

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.recreationalgrade.fattrack.data.ble.BleRepository
import com.recreationalgrade.fattrack.data.ble.DefaultBleRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BleModule {

    @Binds
    @Singleton
    abstract fun bindBleRepository(impl: DefaultBleRepository): BleRepository

    companion object {
        @Provides
        @Singleton
        fun provideBluetoothAdapter(@ApplicationContext context: Context): BluetoothAdapter? =
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }
}
