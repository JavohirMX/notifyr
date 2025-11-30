package com.javohirmx.notifyr.di

import android.content.Context
import androidx.room.Room
import com.javohirmx.notifyr.data.database.CustomTagDao
import com.javohirmx.notifyr.data.database.NotificationDao
import com.javohirmx.notifyr.data.database.NotifyrDatabase
import com.javohirmx.notifyr.data.database.ScreenTimeDao
import com.javohirmx.notifyr.data.database.ScreenTimeSessionDao
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
        )
        .addMigrations(
            NotifyrDatabase.MIGRATION_1_2,
            NotifyrDatabase.MIGRATION_2_3,
            NotifyrDatabase.MIGRATION_3_4,
            NotifyrDatabase.MIGRATION_4_5,
            NotifyrDatabase.MIGRATION_5_6
        )
        .fallbackToDestructiveMigration() // For MVP, allow destructive migration if needed
        .build()
    }
    
    @Provides
    fun provideNotificationDao(database: NotifyrDatabase): NotificationDao {
        return database.notificationDao()
    }
    
    @Provides
    fun provideScreenTimeDao(database: NotifyrDatabase): ScreenTimeDao {
        return database.screenTimeDao()
    }
    
    @Provides
    fun provideCustomTagDao(database: NotifyrDatabase): CustomTagDao {
        return database.customTagDao()
    }
    
    @Provides
    fun provideScreenTimeSessionDao(database: NotifyrDatabase): ScreenTimeSessionDao {
        return database.screenTimeSessionDao()
    }
}
