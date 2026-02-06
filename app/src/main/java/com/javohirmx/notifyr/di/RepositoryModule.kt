package com.javohirmx.notifyr.di

import com.javohirmx.notifyr.data.database.NotificationDao
import com.javohirmx.notifyr.data.database.ScreenTimeDao
import com.javohirmx.notifyr.data.database.ScreenTimeSessionDao
import com.javohirmx.notifyr.data.repository.NotificationRepository
import com.javohirmx.notifyr.data.repository.ScreenTimeRepository
import com.javohirmx.notifyr.domain.usecase.GroupNotificationsUseCase
import com.javohirmx.notifyr.widget.WidgetRepository
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
    fun provideScreenTimeRepository(
        screenTimeDao: ScreenTimeDao,
        screenTimeSessionDao: ScreenTimeSessionDao
    ): ScreenTimeRepository {
        return ScreenTimeRepository(screenTimeDao, screenTimeSessionDao)
    }
    
    @Provides
    @Singleton
    fun provideGroupNotificationsUseCase(): GroupNotificationsUseCase {
        return GroupNotificationsUseCase()
    }
    
    @Provides
    @Singleton
    fun provideWidgetRepository(
        notificationRepository: NotificationRepository,
        screenTimeRepository: ScreenTimeRepository
    ): WidgetRepository {
        return WidgetRepository(notificationRepository, screenTimeRepository)
    }
}
