package com.javohirmx.notifyr.di

import com.javohirmx.notifyr.domain.rules.NotificationRulesEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RulesModule {
    
    @Provides
    @Singleton
    fun provideNotificationRulesEngine(): NotificationRulesEngine {
        return NotificationRulesEngine()
    }
}
