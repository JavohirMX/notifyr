package com.javohirmx.notifyr.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javohirmx.notifyr.data.repository.NotificationRepository
import com.javohirmx.notifyr.domain.model.NotificationData
import com.javohirmx.notifyr.domain.model.NotificationImportance
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    application: Application,
    private val notificationRepository: NotificationRepository
) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    init {
        loadNotifications()
    }
    
    private fun loadNotifications() {
        viewModelScope.launch {
            combine(
                notificationRepository.getNotificationsByImportance(NotificationImportance.URGENT),
                notificationRepository.getNotificationsByImportance(NotificationImportance.NORMAL),
                notificationRepository.getNotificationsByImportance(NotificationImportance.IGNORE),
                _searchQuery
            ) { urgent, normal, ignored, query ->
                val filteredUrgent = if (query.isBlank()) urgent else urgent.filter { 
                    it.title.contains(query, ignoreCase = true) || 
                    it.text.contains(query, ignoreCase = true) ||
                    it.appName.contains(query, ignoreCase = true)
                }
                val filteredNormal = if (query.isBlank()) normal else normal.filter { 
                    it.title.contains(query, ignoreCase = true) || 
                    it.text.contains(query, ignoreCase = true) ||
                    it.appName.contains(query, ignoreCase = true)
                }
                val filteredIgnored = if (query.isBlank()) ignored else ignored.filter { 
                    it.title.contains(query, ignoreCase = true) || 
                    it.text.contains(query, ignoreCase = true) ||
                    it.appName.contains(query, ignoreCase = true)
                }
                
                _uiState.value = _uiState.value.copy(
                    urgentNotifications = filteredUrgent,
                    normalNotifications = filteredNormal,
                    ignoredNotifications = filteredIgnored,
                    isLoading = false
                )
            }.collect()
        }
    }
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun markAsRead(notification: NotificationData) {
        viewModelScope.launch {
            notificationRepository.markAsRead(notification.id, true)
        }
    }
    
    fun markAllAsRead(importance: NotificationImportance) {
        viewModelScope.launch {
            notificationRepository.markAllAsReadByImportance(importance)
        }
    }
    
    fun deleteNotification(notification: NotificationData) {
        viewModelScope.launch {
            notificationRepository.deleteNotification(notification)
        }
    }
    
    fun refreshData() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        loadNotifications()
    }
}

data class HistoryUiState(
    val urgentNotifications: List<NotificationData> = emptyList(),
    val normalNotifications: List<NotificationData> = emptyList(),
    val ignoredNotifications: List<NotificationData> = emptyList(),
    val isLoading: Boolean = true,
    val selectedTab: Int = 0
)
