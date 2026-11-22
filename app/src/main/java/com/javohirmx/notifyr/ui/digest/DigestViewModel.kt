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
                // Rebuild in place; opening the digest screen must not post another
                // digest notification.
                digestScheduler.regenerateDigest()
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

                // A notification can appear in more than one digest section, so de-duplicate
                // before the batch write. One transaction avoids re-querying every flow per row.
                val ids = allNotifications
                    .map { it.id }
                    .filter { it > 0L }
                    .distinct()

                notificationRepository.markAsReadBatch(ids, true)
                
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


