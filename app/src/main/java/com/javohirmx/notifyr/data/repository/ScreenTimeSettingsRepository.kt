package com.javohirmx.notifyr.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.screenTimeDataStore by preferencesDataStore(name = "screen_time_settings")

@Singleton
class ScreenTimeSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val RETENTION_DAYS = intPreferencesKey("retention_days")
    
    val retentionDays: Flow<Int> = context.screenTimeDataStore.data.map { preferences ->
        preferences[RETENTION_DAYS] ?: 30 // Default 30 days
    }
    
    suspend fun setRetentionDays(days: Int) {
        context.screenTimeDataStore.edit { preferences ->
            preferences[RETENTION_DAYS] = days
        }
    }
    
}

