package com.javohirmx.notifyr.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javohirmx.notifyr.data.repository.FocusSettingsRepository
import com.javohirmx.notifyr.domain.focus.FocusModeManager
import com.javohirmx.notifyr.domain.model.FocusMode
import com.javohirmx.notifyr.domain.model.FocusSettings
import com.javohirmx.notifyr.domain.model.TimeRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FocusModeViewModel @Inject constructor(
    private val focusSettingsRepository: FocusSettingsRepository,
    private val focusModeManager: FocusModeManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(FocusModeUiState())
    val uiState: StateFlow<FocusModeUiState> = _uiState.asStateFlow()
    
    init {
        loadSettings()
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            focusSettingsRepository.focusSettings.collect { settings ->
                focusModeManager.updateFocusSettings(settings)
                _uiState.value = FocusModeUiState(
                    selectedMode = settings.currentMode,
                    autoSwitch = settings.autoSwitch,
                    workHours = settings.workHours,
                    sleepHours = settings.sleepHours,
                    workDays = settings.workDays
                )
            }
        }
    }
    
    fun selectMode(mode: FocusMode) {
        viewModelScope.launch {
            focusSettingsRepository.updateCurrentMode(mode)
            focusModeManager.setFocusMode(mode)
        }
    }
    
    fun toggleAutoSwitch(enabled: Boolean) {
        viewModelScope.launch {
            focusSettingsRepository.updateAutoSwitch(enabled)
            if (enabled) {
                focusModeManager.enableAutoSwitch()
            } else {
                focusModeManager.disableAutoSwitch()
            }
        }
    }
    
    fun updateWorkHours(hours: TimeRange?) {
        viewModelScope.launch {
            focusSettingsRepository.updateWorkHours(hours)
        }
    }
    
    fun updateSleepHours(hours: TimeRange) {
        viewModelScope.launch {
            focusSettingsRepository.updateSleepHours(hours)
        }
    }
}

data class FocusModeUiState(
    val selectedMode: FocusMode = FocusMode.NORMAL,
    val autoSwitch: Boolean = false,
    val workHours: TimeRange? = null,
    val sleepHours: TimeRange = TimeRange(22, 0, 7, 0),
    val workDays: Set<Int> = setOf(2, 3, 4, 5, 6)
)


