package com.javohirmx.notifyr.ui.digest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javohirmx.notifyr.data.repository.NotificationRepository
import com.javohirmx.notifyr.domain.digest.SmartDigestScheduler
import com.javohirmx.notifyr.domain.model.EnhancedDigest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DigestViewModel @Inject constructor(
    private val digestScheduler: SmartDigestScheduler,
    private val notificationRepository: NotificationRepository
) : ViewModel() {
    
    val currentDigest: StateFlow<EnhancedDigest?> = digestScheduler.currentDigest
    
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    
    init {
        // Load digest on initialization
        refreshDigest()
    }
    
    fun refreshDigest() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                digestScheduler.showDigest()
            } finally {
                _isRefreshing.value = false
            }
        }
    }
    
    fun markAllAsRead() {
        viewModelScope.launch {
            currentDigest.value?.let { digest ->
                val allNotifications = digest.needsAttention + 
                    digest.conversations.flatMap { it.notifications } +
                    digest.appGroups.flatMap { it.notifications }
                
                allNotifications.forEach { notification ->
                    notificationRepository.markAsRead(notification.id, true)
                }
                
                // Refresh digest after marking all as read
                refreshDigest()
            }
        }
    }
    
    fun markNotificationRead(notificationId: Long) {
        viewModelScope.launch {
            notificationRepository.markAsRead(notificationId, true)
            // Refresh digest to update UI
            refreshDigest()
        }
    }
    
    fun dismissNotification(notificationId: Long) {
        viewModelScope.launch {
            // Mark as read and dismissed
            notificationRepository.markAsRead(notificationId, true)
            // Refresh digest to update UI
            refreshDigest()
        }
    }
}


