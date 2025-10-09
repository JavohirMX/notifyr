package com.javohirmx.notifyr.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import com.javohirmx.notifyr.data.datastore.AppSettings
import com.javohirmx.notifyr.data.datastore.SettingsSerializer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    
    @Provides
    @Singleton
    fun provideAppSettingsDataStore(
        @ApplicationContext context: Context
    ): DataStore<AppSettings> {
        return DataStoreFactory.create(
            serializer = SettingsSerializer,
            produceFile = { context.dataStoreFile("app_settings.json") }
        )
    }
}

