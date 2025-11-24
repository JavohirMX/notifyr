package com.javohirmx.notifyr.di

import com.javohirmx.notifyr.data.database.NotificationDao
import com.javohirmx.notifyr.data.database.ScreenTimeDao
import com.javohirmx.notifyr.data.repository.NotificationRepository
import com.javohirmx.notifyr.data.repository.ScreenTimeRepository
import com.javohirmx.notifyr.domain.usecase.GroupNotificationsUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    
    @Provides
    @Singleton
    fun provideNotificationRepository(
        notificationDao: NotificationDao
    ): NotificationRepository {
        return NotificationRepository(notificationDao)
    }
    
    @Provides
    @Singleton
    fun provideScreenTimeRepository(
        screenTimeDao: ScreenTimeDao
    ): ScreenTimeRepository {
        return ScreenTimeRepository(screenTimeDao)
    }
    
    @Provides
    @Singleton
    fun provideGroupNotificationsUseCase(): GroupNotificationsUseCase {
        return GroupNotificationsUseCase()
    }
}
