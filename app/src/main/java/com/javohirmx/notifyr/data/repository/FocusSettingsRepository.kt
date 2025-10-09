package com.javohirmx.notifyr.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.javohirmx.notifyr.domain.model.FocusMode
import com.javohirmx.notifyr.domain.model.FocusSettings
import com.javohirmx.notifyr.domain.model.TimeRange
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.focusDataStore by preferencesDataStore(name = "focus_settings")

@Singleton
class FocusSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val CURRENT_MODE = stringPreferencesKey("current_mode")
    private val AUTO_SWITCH = booleanPreferencesKey("auto_switch")
    private val WORK_HOURS = stringPreferencesKey("work_hours")
    private val SLEEP_HOURS = stringPreferencesKey("sleep_hours")
    private val WORK_DAYS = stringPreferencesKey("work_days")
    
    val focusSettings: Flow<FocusSettings> = context.focusDataStore.data.map { preferences ->
        val currentMode = preferences[CURRENT_MODE]?.let { 
            try { FocusMode.valueOf(it) } catch (e: Exception) { FocusMode.NORMAL }
        } ?: FocusMode.NORMAL
        
        val autoSwitch = preferences[AUTO_SWITCH] ?: false
        
        val workHours = preferences[WORK_HOURS]?.let {
            try { Json.decodeFromString<TimeRange>(it) } catch (e: Exception) { null }
        }
        
        val sleepHours = preferences[SLEEP_HOURS]?.let {
            try { Json.decodeFromString<TimeRange>(it) } catch (e: Exception) { 
                TimeRange(22, 0, 7, 0) 
            }
        } ?: TimeRange(22, 0, 7, 0)
        
        val workDays = preferences[WORK_DAYS]?.let {
            try { 
                Json.decodeFromString<Set<Int>>(it) 
            } catch (e: Exception) { 
                setOf(2, 3, 4, 5, 6) 
            }
        } ?: setOf(2, 3, 4, 5, 6)
        
        FocusSettings(
            currentMode = currentMode,
            autoSwitch = autoSwitch,
            workHours = workHours,
            sleepHours = sleepHours,
            workDays = workDays
        )
    }
    
    suspend fun updateCurrentMode(mode: FocusMode) {
        context.focusDataStore.edit { preferences ->
            preferences[CURRENT_MODE] = mode.name
        }
    }
    
    suspend fun updateAutoSwitch(enabled: Boolean) {
        context.focusDataStore.edit { preferences ->
            preferences[AUTO_SWITCH] = enabled
        }
    }
    
    suspend fun updateWorkHours(workHours: TimeRange?) {
        context.focusDataStore.edit { preferences ->
            if (workHours != null) {
                preferences[WORK_HOURS] = Json.encodeToString(workHours)
            } else {
                preferences.remove(WORK_HOURS)
            }
        }
    }
    
    suspend fun updateSleepHours(sleepHours: TimeRange) {
        context.focusDataStore.edit { preferences ->
            preferences[SLEEP_HOURS] = Json.encodeToString(sleepHours)
        }
    }
    
    suspend fun updateWorkDays(days: Set<Int>) {
        context.focusDataStore.edit { preferences ->
            preferences[WORK_DAYS] = Json.encodeToString(days)
        }
    }
    
    suspend fun updateFocusSettings(settings: FocusSettings) {
        context.focusDataStore.edit { preferences ->
            preferences[CURRENT_MODE] = settings.currentMode.name
            preferences[AUTO_SWITCH] = settings.autoSwitch
            if (settings.workHours != null) {
                preferences[WORK_HOURS] = Json.encodeToString(settings.workHours)
            }
            preferences[SLEEP_HOURS] = Json.encodeToString(settings.sleepHours)
            preferences[WORK_DAYS] = Json.encodeToString(settings.workDays)
        }
    }
}


