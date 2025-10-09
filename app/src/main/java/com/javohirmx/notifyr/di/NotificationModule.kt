package com.javohirmx.notifyr.di

import android.content.Context
import com.javohirmx.notifyr.service.NotificationManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NotificationModule {
    
    @Provides
    @Singleton
    fun provideNotificationManager(
        @ApplicationContext context: Context,
        nlDigestGenerator: com.javohirmx.notifyr.domain.digest.NaturalLanguageDigestGenerator
    ): NotificationManager {
        return NotificationManager(context, nlDigestGenerator)
    }
}
