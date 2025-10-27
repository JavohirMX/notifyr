package com.javohirmx.notifyr.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javohirmx.notifyr.data.repository.DigestSettingsRepository
import com.javohirmx.notifyr.domain.digest.SmartDigestScheduler
import com.javohirmx.notifyr.domain.model.DigestMode
import com.javohirmx.notifyr.domain.model.DigestSettings
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
                minNotificationThreshold = currentSettings.minNotifications.toInt(),
                unlockDelayMinutes = currentSettings.unlockDelay.toInt(),
                groupConversations = currentSettings.groupConversations,
                groupByApp = currentSettings.groupByApp,
                showSummary = currentSettings.showSummary
            )
            digestSettingsRepository.updateDigestSettings(newSettings)
        }
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
            digestScheduler.showDigest()
        }
    }
}

data class DigestSettingsUiState(
    val selectedMode: DigestMode = DigestMode.CONTEXT_AWARE,
    val minNotifications: Float = 3f,
    val unlockDelay: Float = 30f,
    val groupConversations: Boolean = true,
    val groupByApp: Boolean = true,
    val showSummary: Boolean = true
)


