package com.javohirmx.notifyr.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.javohirmx.notifyr.domain.model.DigestMode
import com.javohirmx.notifyr.domain.model.DigestSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.digestDataStore by preferencesDataStore(name = "digest_settings")

@Singleton
class DigestSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val MODE = stringPreferencesKey("digest_mode")
    private val MIN_THRESHOLD = intPreferencesKey("min_threshold")
    private val UNLOCK_DELAY = intPreferencesKey("unlock_delay")
    private val GROUP_CONVERSATIONS = booleanPreferencesKey("group_conversations")
    private val GROUP_BY_APP = booleanPreferencesKey("group_by_app")
    private val SHOW_SUMMARY = booleanPreferencesKey("show_summary")
    
    val digestSettings: Flow<DigestSettings> = context.digestDataStore.data.map { preferences ->
        val mode = preferences[MODE]?.let {
            try { DigestMode.valueOf(it) } catch (e: Exception) { DigestMode.CONTEXT_AWARE }
        } ?: DigestMode.CONTEXT_AWARE
        
        DigestSettings(
            mode = mode,
            minNotificationThreshold = preferences[MIN_THRESHOLD] ?: 3,
            unlockDelayMinutes = preferences[UNLOCK_DELAY] ?: 30,
            groupConversations = preferences[GROUP_CONVERSATIONS] ?: true,
            groupByApp = preferences[GROUP_BY_APP] ?: true,
            showSummary = preferences[SHOW_SUMMARY] ?: true
        )
    }
    
    suspend fun updateDigestSettings(settings: DigestSettings) {
        context.digestDataStore.edit { preferences ->
            preferences[MODE] = settings.mode.name
            preferences[MIN_THRESHOLD] = settings.minNotificationThreshold
            preferences[UNLOCK_DELAY] = settings.unlockDelayMinutes
            preferences[GROUP_CONVERSATIONS] = settings.groupConversations
            preferences[GROUP_BY_APP] = settings.groupByApp
            preferences[SHOW_SUMMARY] = settings.showSummary
        }
    }
}


