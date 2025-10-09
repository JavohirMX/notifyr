package com.javohirmx.notifyr.ui.digest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javohirmx.notifyr.data.repository.NotificationRepository
import com.javohirmx.notifyr.domain.digest.SmartDigestScheduler
import com.javohirmx.notifyr.domain.model.EnhancedDigest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DigestViewModel @Inject constructor(
    private val digestScheduler: SmartDigestScheduler,
    private val notificationRepository: NotificationRepository
) : ViewModel() {
    
    val currentDigest: StateFlow<EnhancedDigest?> = digestScheduler.currentDigest
    
    fun refreshDigest() {
        viewModelScope.launch {
            digestScheduler.showDigest()
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
            }
        }
    }
}


