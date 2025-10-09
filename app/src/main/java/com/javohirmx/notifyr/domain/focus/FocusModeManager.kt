package com.javohirmx.notifyr.domain.focus

import androidx.datastore.core.DataStore
import com.javohirmx.notifyr.data.datastore.AppSettings
import com.javohirmx.notifyr.domain.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FocusModeManager @Inject constructor(
    private val dataStore: DataStore<AppSettings>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val _focusSettings = MutableStateFlow(FocusSettings())
    val focusSettings: StateFlow<FocusSettings> = _focusSettings.asStateFlow()
    
    private val _currentMode = MutableStateFlow(FocusMode.NORMAL)
    val currentMode: StateFlow<FocusMode> = _currentMode.asStateFlow()
    
    init {
        scope.launch {
            loadFocusSettings()
        }
    }
    
    private suspend fun loadFocusSettings() {
        try {
            val settings = dataStore.data.first()
            if (settings.focusModeSettingsJson.isNotEmpty() && settings.focusModeSettingsJson != "{}") {
                val focusSettings = Json.decodeFromString<FocusSettings>(settings.focusModeSettingsJson)
                _focusSettings.value = focusSettings
                _currentMode.value = getCurrentMode()
            }
        } catch (e: Exception) {
            // Use defaults on error
            android.util.Log.e("FocusModeManager", "Failed to load focus settings", e)
        }
    }
    
    private suspend fun saveFocusSettings() {
        try {
            dataStore.updateData { currentSettings ->
                val settingsJson = Json.encodeToString(_focusSettings.value)
                currentSettings.copy(focusModeSettingsJson = settingsJson)
            }
        } catch (e: Exception) {
            android.util.Log.e("FocusModeManager", "Failed to save focus settings", e)
        }
    }
    
    /**
     * Determine current focus mode based on settings and context
     */
    fun getCurrentMode(): FocusMode {
        val settings = _focusSettings.value
        
        if (!settings.autoSwitch) {
            return settings.currentMode
        }
        
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        
        // Check sleep hours
        if (settings.sleepHours.contains(hour, minute)) {
            _currentMode.value = FocusMode.SLEEP
            return FocusMode.SLEEP
        }
        
        // Check work hours (only on work days)
        if (settings.workHours != null && 
            settings.workDays.contains(dayOfWeek) &&
            settings.workHours.contains(hour, minute)) {
            _currentMode.value = FocusMode.WORK
            return FocusMode.WORK
        }
        
        // Default to personal mode
        _currentMode.value = FocusMode.PERSONAL
        return FocusMode.PERSONAL
    }
    
    /**
     * Check if notification should be shown based on current focus mode
     */
    fun shouldShowNotification(
        notification: NotificationData,
        mode: FocusMode = getCurrentMode()
    ): Boolean {
        val tags = notification.tags
        
        return when (mode) {
            FocusMode.NORMAL -> true // Show all notifications based on regular rules
            
            FocusMode.WORK -> {
                // Show work-related or critical notifications
                tags.contexts.contains(NotificationContext.WORK) ||
                tags.contexts.contains(NotificationContext.FINANCIAL) ||
                tags.priority == Priority.CRITICAL ||
                tags.contexts.contains(NotificationContext.EMERGENCY)
            }
            
            FocusMode.PERSONAL -> {
                // Show personal or critical notifications
                tags.contexts.contains(NotificationContext.PERSONAL) ||
                tags.contexts.contains(NotificationContext.FINANCIAL) ||
                tags.priority == Priority.CRITICAL ||
                tags.contexts.contains(NotificationContext.EMERGENCY)
            }
            
            FocusMode.DEEP_FOCUS -> {
                // Only critical notifications
                tags.priority == Priority.CRITICAL ||
                tags.contexts.contains(NotificationContext.EMERGENCY)
            }
            
            FocusMode.SLEEP -> {
                // Only emergency notifications or from emergency contacts
                tags.contexts.contains(NotificationContext.EMERGENCY) ||
                (tags.priority == Priority.CRITICAL && 
                 tags.contexts.contains(NotificationContext.FINANCIAL))
            }
        }
    }
    
    /**
     * Manually set focus mode
     */
    fun setFocusMode(mode: FocusMode) {
        _focusSettings.value = _focusSettings.value.copy(
            currentMode = mode,
            autoSwitch = false // Disable auto-switch when manually set
        )
        _currentMode.value = mode
        
        scope.launch {
            saveFocusSettings()
        }
    }
    
    /**
     * Update focus settings
     */
    fun updateFocusSettings(settings: FocusSettings) {
        _focusSettings.value = settings
        if (settings.autoSwitch) {
            getCurrentMode() // Recalculate current mode
        } else {
            _currentMode.value = settings.currentMode
        }
        
        scope.launch {
            saveFocusSettings()
        }
    }
    
    /**
     * Check if currently in quiet hours
     */
    fun isInQuietHours(): Boolean {
        val settings = _focusSettings.value
        val quietHours = settings.quietHours ?: return false
        
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        
        return quietHours.contains(hour, minute)
    }
    
    /**
     * Enable auto-switching
     */
    fun enableAutoSwitch() {
        _focusSettings.value = _focusSettings.value.copy(autoSwitch = true)
        getCurrentMode()
        
        scope.launch {
            saveFocusSettings()
        }
    }
    
    /**
     * Disable auto-switching
     */
    fun disableAutoSwitch() {
        _focusSettings.value = _focusSettings.value.copy(autoSwitch = false)
        
        scope.launch {
            saveFocusSettings()
        }
    }
}

