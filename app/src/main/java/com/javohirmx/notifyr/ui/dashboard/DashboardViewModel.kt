package com.javohirmx.notifyr.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javohirmx.notifyr.data.repository.NotificationRepository
import com.javohirmx.notifyr.domain.digest.SmartDigestScheduler
import com.javohirmx.notifyr.domain.model.EnhancedDigest
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
    private val notificationRepository: NotificationRepository,
    private val smartDigestScheduler: SmartDigestScheduler
) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    
    init {
        loadDashboardData()
        checkPermissions()
        loadDigestPreview()
    }
    
    private fun loadDashboardData() {
        viewModelScope.launch {
            // Counts and the small preview slice are each served by an aggregate/projection
            // query. Previously this combined three full-table reads just to take .size and
            // .take(5), which meant re-reading the whole history on every database change.
            combine(
                notificationRepository.countNonEmptyByImportance(NotificationImportance.URGENT),
                notificationRepository.countNonEmptyByImportance(NotificationImportance.NORMAL),
                notificationRepository.countNonEmptyByImportance(NotificationImportance.IGNORE),
                notificationRepository.getTopByImportance(NotificationImportance.URGENT, 5)
            ) { urgentCount, normalCount, ignoredCount, recentUrgent ->
                _uiState.value = _uiState.value.copy(
                    urgentCount = urgentCount,
                    normalCount = normalCount,
                    ignoredCount = ignoredCount,
                    recentUrgentNotifications = recentUrgent // Show only 5 most recent
                )
            }.collect()
        }
    }
    
    private fun loadDigestPreview() {
        viewModelScope.launch {
            smartDigestScheduler.currentDigest.collect { digest ->
                _uiState.value = _uiState.value.copy(digestPreview = digest)
            }
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
    
    fun markAsRead(notification: NotificationData) {
        viewModelScope.launch {
            notificationRepository.markAsRead(notification.id, true)
        }
    }
}

data class DashboardUiState(
    val isNotificationListenerEnabled: Boolean = false,
    val urgentCount: Int = 0,
    val normalCount: Int = 0,
    val ignoredCount: Int = 0,
    val recentUrgentNotifications: List<NotificationData> = emptyList(),
    val digestPreview: EnhancedDigest? = null,
    val isLoading: Boolean = false
)
