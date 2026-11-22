package com.javohirmx.notifyr.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javohirmx.notifyr.data.repository.DigestSettingsRepository
import com.javohirmx.notifyr.domain.digest.SmartDigestScheduler
import com.javohirmx.notifyr.domain.model.DigestMode
import com.javohirmx.notifyr.domain.model.DigestSettings
import com.javohirmx.notifyr.domain.model.TimeOfDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DigestSettingsViewModel @Inject constructor(
    private val digestSettingsRepository: DigestSettingsRepository,
    private val digestScheduler: SmartDigestScheduler
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DigestSettingsUiState())
    val uiState: StateFlow<DigestSettingsUiState> = _uiState.asStateFlow()
    
    init {
        loadSettings()
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            digestSettingsRepository.digestSettings.collect { settings ->
                digestScheduler.updateDigestSettings(settings)
                _uiState.value = DigestSettingsUiState(
                    selectedMode = settings.mode,
                    customTimes = settings.customTimes.sortedBy { it.hour * 60 + it.minute },
                    minNotifications = settings.minNotificationThreshold.toFloat(),
                    unlockDelay = settings.unlockDelayMinutes.toFloat(),
                    groupConversations = settings.groupConversations,
                    groupByApp = settings.groupByApp,
                    showSummary = settings.showSummary
                )
            }
        }
    }
    
    fun selectMode(mode: DigestMode) {
        viewModelScope.launch {
            val currentSettings = _uiState.value
            val newSettings = DigestSettings(
                mode = mode,
                customTimes = currentSettings.customTimes,
                minNotificationThreshold = currentSettings.minNotifications.toInt(),
                unlockDelayMinutes = currentSettings.unlockDelay.toInt(),
                groupConversations = currentSettings.groupConversations,
                groupByApp = currentSettings.groupByApp,
                showSummary = currentSettings.showSummary
            )
            digestSettingsRepository.updateDigestSettings(newSettings)
        }
    }

    fun addCustomTime(time: TimeOfDay) {
        val current = _uiState.value.customTimes
        if (current.any { it.hour == time.hour && it.minute == time.minute }) return
        _uiState.value = _uiState.value.copy(
            customTimes = (current + time).sortedBy { it.hour * 60 + it.minute }
        )
        saveSettings()
    }

    fun removeCustomTime(time: TimeOfDay) {
        _uiState.value = _uiState.value.copy(
            customTimes = _uiState.value.customTimes.filterNot {
                it.hour == time.hour && it.minute == time.minute
            }
        )
        saveSettings()
    }
    
    fun updateMinNotifications(value: Float) {
        _uiState.value = _uiState.value.copy(minNotifications = value)
        saveSettings()
    }
    
    fun updateUnlockDelay(value: Float) {
        _uiState.value = _uiState.value.copy(unlockDelay = value)
        saveSettings()
    }
    
    fun toggleGroupConversations(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(groupConversations = enabled)
        saveSettings()
    }
    
    fun toggleGroupByApp(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(groupByApp = enabled)
        saveSettings()
    }
    
    fun toggleShowSummary(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(showSummary = enabled)
        saveSettings()
    }
    
    private fun saveSettings() {
        viewModelScope.launch {
            val state = _uiState.value
            val settings = DigestSettings(
                mode = state.selectedMode,
                customTimes = state.customTimes,
                minNotificationThreshold = state.minNotifications.toInt(),
                unlockDelayMinutes = state.unlockDelay.toInt(),
                groupConversations = state.groupConversations,
                groupByApp = state.groupByApp,
                showSummary = state.showSummary
            )
            digestSettingsRepository.updateDigestSettings(settings)
        }
    }
    
    fun triggerManualDigest() {
        viewModelScope.launch {
            digestScheduler.showDigest(respectFocusMode = false)
        }
    }
}

data class DigestSettingsUiState(
    val selectedMode: DigestMode = DigestMode.CONTEXT_AWARE,
    val customTimes: List<TimeOfDay> = emptyList(),
    val minNotifications: Float = 3f,
    val unlockDelay: Float = 30f,
    val groupConversations: Boolean = true,
    val groupByApp: Boolean = true,
    val showSummary: Boolean = true
)


