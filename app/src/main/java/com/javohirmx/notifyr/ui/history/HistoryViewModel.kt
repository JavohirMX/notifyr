package com.javohirmx.notifyr.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javohirmx.notifyr.data.repository.NotificationRepository
import com.javohirmx.notifyr.domain.model.NotificationData
import com.javohirmx.notifyr.domain.model.NotificationImportance
import com.javohirmx.notifyr.domain.model.NotificationItem
import com.javohirmx.notifyr.domain.model.NotificationGroup
import com.javohirmx.notifyr.domain.usecase.GroupNotificationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    application: Application,
    private val notificationRepository: NotificationRepository,
    private val groupNotificationsUseCase: GroupNotificationsUseCase
) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()
    
    init {
        loadNotifications()
    }
    
    private fun loadNotifications() {
        viewModelScope.launch {
            combine(
                notificationRepository.getNotificationsByImportance(NotificationImportance.URGENT),
                notificationRepository.getNotificationsByImportance(NotificationImportance.NORMAL),
                notificationRepository.getNotificationsByImportance(NotificationImportance.IGNORE),
                _searchQuery,
                _filterState
            ) { urgent, normal, ignored, query, filterState ->
                // Filter out notifications with both empty title and text
                val nonEmptyUrgent = urgent.filter { it.title.isNotBlank() || it.text.isNotBlank() }
                val nonEmptyNormal = normal.filter { it.title.isNotBlank() || it.text.isNotBlank() }
                val nonEmptyIgnored = ignored.filter { it.title.isNotBlank() || it.text.isNotBlank() }
                
                // Extract available apps from all notifications
                val allNotifications = nonEmptyUrgent + nonEmptyNormal + nonEmptyIgnored
                val availableApps = allNotifications
                    .distinctBy { it.packageName }
                    .map { AppInfo(it.packageName, it.appName) }
                    .sortedBy { it.appName }
                
                // Apply search query
                val searchedUrgent = applySearchFilter(nonEmptyUrgent, query)
                val searchedNormal = applySearchFilter(nonEmptyNormal, query)
                val searchedIgnored = applySearchFilter(nonEmptyIgnored, query)
                
                // Apply filters
                val filteredUrgent = applyFilters(searchedUrgent, filterState)
                val filteredNormal = applyFilters(searchedNormal, filterState)
                val filteredIgnored = applyFilters(searchedIgnored, filterState)
                
                // Apply grouping to filtered notifications
                val groupedUrgent = applyGroupFilters(
                    groupNotificationsUseCase.execute(filteredUrgent),
                    filterState
                )
                val groupedNormal = applyGroupFilters(
                    groupNotificationsUseCase.execute(filteredNormal),
                    filterState
                )
                val groupedIgnored = applyGroupFilters(
                    groupNotificationsUseCase.execute(filteredIgnored),
                    filterState
                )
                
                _uiState.value = _uiState.value.copy(
                    urgentNotifications = groupedUrgent,
                    normalNotifications = groupedNormal,
                    ignoredNotifications = groupedIgnored,
                    isLoading = false,
                    filterState = filterState,
                    availableApps = availableApps
                )
            }.collect()
        }
    }
    
    private fun applySearchFilter(
        notifications: List<NotificationData>,
        query: String
    ): List<NotificationData> {
        if (query.isBlank()) return notifications
        
        return notifications.filter { 
            it.title.contains(query, ignoreCase = true) || 
            it.text.contains(query, ignoreCase = true) ||
            it.appName.contains(query, ignoreCase = true)
        }
    }
    
    private fun applyFilters(
        notifications: List<NotificationData>,
        filterState: FilterState
    ): List<NotificationData> {
        var filtered = notifications
        
        // Read status filter
        filtered = when (filterState.readStatus) {
            ReadStatusFilter.ALL -> filtered
            ReadStatusFilter.UNREAD_ONLY -> filtered.filter { !it.isRead }
            ReadStatusFilter.READ_ONLY -> filtered.filter { it.isRead }
        }
        
        // App filter
        if (filterState.selectedApps.isNotEmpty()) {
            filtered = filtered.filter { it.packageName in filterState.selectedApps }
        }
        
        // Time range filter
        filtered = applyTimeRangeFilter(filtered, filterState)
        
        // Context filter
        if (filterState.selectedContexts.isNotEmpty()) {
            filtered = filtered.filter { notification ->
                notification.tags.contexts.any { it in filterState.selectedContexts }
            }
        }
        
        // Sender filter
        if (filterState.senderQuery.isNotBlank()) {
            filtered = filtered.filter { notification ->
                notification.sender?.contains(filterState.senderQuery, ignoreCase = true) == true
            }
        }
        
        return filtered
    }
    
    private fun applyTimeRangeFilter(
        notifications: List<NotificationData>,
        filterState: FilterState
    ): List<NotificationData> {
        val now = System.currentTimeMillis()
        
        return when (filterState.timeRange) {
            TimeRangeFilter.ALL_TIME -> notifications
            TimeRangeFilter.TODAY -> {
                val startOfDay = getStartOfDay(now)
                notifications.filter { it.timestamp >= startOfDay }
            }
            TimeRangeFilter.LAST_7_DAYS -> {
                val sevenDaysAgo = now - (7 * 24 * 60 * 60 * 1000L)
                notifications.filter { it.timestamp >= sevenDaysAgo }
            }
            TimeRangeFilter.LAST_30_DAYS -> {
                val thirtyDaysAgo = now - (30 * 24 * 60 * 60 * 1000L)
                notifications.filter { it.timestamp >= thirtyDaysAgo }
            }
            TimeRangeFilter.CUSTOM -> {
                filterState.customTimeRange?.let { (start, end) ->
                    notifications.filter { it.timestamp in start..end }
                } ?: notifications
            }
        }
    }
    
    private fun getStartOfDay(timestamp: Long): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
    
    private fun applyGroupFilters(
        items: List<NotificationItem>,
        filterState: FilterState
    ): List<NotificationItem> {
        // For groups, apply filter based on whether they contain any matching notifications
        return when (filterState.readStatus) {
            ReadStatusFilter.UNREAD_ONLY -> items.filter { item ->
                when (item) {
                    is NotificationItem.Single -> !item.notification.isRead
                    is NotificationItem.Group -> item.group.unreadCount > 0
                }
            }
            ReadStatusFilter.READ_ONLY -> items.filter { item ->
                when (item) {
                    is NotificationItem.Single -> item.notification.isRead
                    is NotificationItem.Group -> item.group.isAllRead
                }
            }
            ReadStatusFilter.ALL -> items
        }
    }
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun updateReadStatusFilter(status: ReadStatusFilter) {
        _filterState.value = _filterState.value.copy(readStatus = status)
    }
    
    fun toggleAppFilter(packageName: String) {
        val currentApps = _filterState.value.selectedApps.toMutableSet()
        if (packageName in currentApps) {
            currentApps.remove(packageName)
        } else {
            currentApps.add(packageName)
        }
        _filterState.value = _filterState.value.copy(selectedApps = currentApps)
    }
    
    fun updateTimeRangeFilter(range: TimeRangeFilter, customRange: Pair<Long, Long>? = null) {
        _filterState.value = _filterState.value.copy(
            timeRange = range,
            customTimeRange = customRange
        )
    }
    
    fun toggleContextFilter(context: com.javohirmx.notifyr.domain.model.NotificationContext) {
        val currentContexts = _filterState.value.selectedContexts.toMutableSet()
        if (context in currentContexts) {
            currentContexts.remove(context)
        } else {
            currentContexts.add(context)
        }
        _filterState.value = _filterState.value.copy(selectedContexts = currentContexts)
    }
    
    fun updateSenderFilter(sender: String) {
        _filterState.value = _filterState.value.copy(senderQuery = sender)
    }
    
    fun clearFilters() {
        _filterState.value = FilterState()
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
    
    fun markGroupAsRead(group: NotificationGroup) {
        viewModelScope.launch {
            group.notifications.forEach { notification ->
                notificationRepository.markAsRead(notification.id, true)
            }
        }
    }
    
    fun deleteGroup(group: NotificationGroup) {
        viewModelScope.launch {
            group.notifications.forEach { notification ->
                notificationRepository.deleteNotification(notification)
            }
        }
    }
    
    fun refreshData() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        loadNotifications()
    }
}

data class HistoryUiState(
    val urgentNotifications: List<NotificationItem> = emptyList(),
    val normalNotifications: List<NotificationItem> = emptyList(),
    val ignoredNotifications: List<NotificationItem> = emptyList(),
    val isLoading: Boolean = true,
    val selectedTab: Int = 0,
    val filterState: FilterState = FilterState(),
    val availableApps: List<AppInfo> = emptyList() // For app filter dropdown
)

/**
 * Represents an app available for filtering
 */
data class AppInfo(
    val packageName: String,
    val appName: String
)
