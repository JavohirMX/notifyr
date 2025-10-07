package com.javohirmx.notifyr.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javohirmx.notifyr.data.repository.NotificationRepository
import com.javohirmx.notifyr.domain.model.NotificationData
import com.javohirmx.notifyr.domain.model.NotificationImportance
import com.javohirmx.notifyr.utils.PermissionUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    application: Application,
    private val notificationRepository: NotificationRepository
) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    
    init {
        loadDashboardData()
        checkPermissions()
    }
    
    private fun loadDashboardData() {
        viewModelScope.launch {
            // Load notification counts
            combine(
                notificationRepository.getNotificationsByImportance(NotificationImportance.URGENT),
                notificationRepository.getNotificationsByImportance(NotificationImportance.NORMAL),
                notificationRepository.getNotificationsByImportance(NotificationImportance.IGNORE)
            ) { urgent, normal, ignored ->
                _uiState.value = _uiState.value.copy(
                    urgentCount = urgent.size,
                    normalCount = normal.size,
                    ignoredCount = ignored.size,
                    recentUrgentNotifications = urgent.take(5) // Show only 5 most recent
                )
            }.collect()
        }
    }
    
    private fun checkPermissions() {
        val isEnabled = PermissionUtils.isNotificationListenerEnabled(getApplication())
        _uiState.value = _uiState.value.copy(isNotificationListenerEnabled = isEnabled)
    }
    
    fun requestNotificationListenerPermission() {
        PermissionUtils.openNotificationListenerSettings(getApplication())
    }
    
    fun refreshData() {
        checkPermissions()
        loadDashboardData()
    }
}

data class DashboardUiState(
    val isNotificationListenerEnabled: Boolean = false,
    val urgentCount: Int = 0,
    val normalCount: Int = 0,
    val ignoredCount: Int = 0,
    val recentUrgentNotifications: List<NotificationData> = emptyList(),
    val isLoading: Boolean = false
)
