package com.javohirmx.notifyr.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javohirmx.notifyr.data.repository.NotificationRepository
import com.javohirmx.notifyr.utils.PermissionUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val notificationRepository: NotificationRepository
) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        loadSettings()
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            val isNotificationListenerEnabled = PermissionUtils.isNotificationListenerEnabled(getApplication())
            val totalNotifications = notificationRepository.getNotificationCount()
            
            _uiState.value = _uiState.value.copy(
                isNotificationListenerEnabled = isNotificationListenerEnabled,
                totalNotifications = totalNotifications,
                isLoading = false
            )
        }
    }
    
    fun requestNotificationListenerPermission() {
        PermissionUtils.openNotificationListenerSettings(getApplication())
    }
    
    fun clearAllNotifications() {
        viewModelScope.launch {
            notificationRepository.deleteAllNotifications()
            loadSettings() // Refresh the count
        }
    }
    
    fun clearOldNotifications(daysOld: Int = 7) {
        viewModelScope.launch {
            val cutoffTime = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
            notificationRepository.deleteOldNotifications(cutoffTime)
            loadSettings() // Refresh the count
        }
    }
    
    fun refreshSettings() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        loadSettings()
    }
}

data class SettingsUiState(
    val isNotificationListenerEnabled: Boolean = false,
    val totalNotifications: Int = 0,
    val isLoading: Boolean = true
)
