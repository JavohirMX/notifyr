package com.javohirmx.notifyr.di

import android.content.Context
import androidx.room.Room
import com.javohirmx.notifyr.data.database.NotificationDao
import com.javohirmx.notifyr.data.database.NotifyrDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideNotifyrDatabase(@ApplicationContext context: Context): NotifyrDatabase {
        return Room.databaseBuilder(
            context,
            NotifyrDatabase::class.java,
            NotifyrDatabase.DATABASE_NAME
        ).build()
    }
    
    @Provides
    fun provideNotificationDao(database: NotifyrDatabase): NotificationDao {
        return database.notificationDao()
    }
}
