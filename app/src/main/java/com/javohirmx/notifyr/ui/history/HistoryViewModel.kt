package com.javohirmx.notifyr.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javohirmx.notifyr.data.repository.NotificationRepository
import com.javohirmx.notifyr.domain.model.NotificationData
import com.javohirmx.notifyr.domain.model.NotificationImportance
import com.javohirmx.notifyr.domain.model.NotificationGroup
import com.javohirmx.notifyr.domain.model.NotificationItem
import com.javohirmx.notifyr.domain.model.muteableConversationId
import com.javohirmx.notifyr.domain.usecase.GroupNotificationsUseCase
import com.javohirmx.notifyr.domain.ml.HybridNotificationClassifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    application: Application,
    private val notificationRepository: NotificationRepository,
    private val groupNotificationsUseCase: GroupNotificationsUseCase,
    private val appRulesRepository: com.javohirmx.notifyr.data.repository.AppRulesRepository,
    private val hybridClassifier: HybridNotificationClassifier,
    val customTagRepository: com.javohirmx.notifyr.data.repository.CustomTagRepository,
    private val conversationMuteRepository: com.javohirmx.notifyr.data.repository.ConversationMuteRepository
) : AndroidViewModel(application) {
    
    companion object {
        /**
         * How long typing must pause before the history list re-queries.
         *
         * Comfortably below the ~500ms at which users start to perceive input lag, while still
         * collapsing a burst of keystrokes into a single database round trip.
         */
        const val SEARCH_DEBOUNCE_MS = 300L
    }
    
    private val sharedPreferences = application.getSharedPreferences("notifyr_prefs", android.content.Context.MODE_PRIVATE)
    
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    /**
     * Debounced so that typing does not re-query and re-group on every keystroke.
     *
     * The blank case is not delayed: it is both the initial value and what the field returns to
     * when cleared, and delaying it would make the list appear to stall on open and on clear.
     */
    @OptIn(FlowPreview::class)
    private val debouncedSearchQuery: Flow<String> = _searchQuery
        .debounce { query -> if (query.isBlank()) 0L else SEARCH_DEBOUNCE_MS }

    /**
     * Declared above `init` deliberately: property initialisers run in declaration order, and
     * `init` calls [subscribe], which touches this field.
     */
    private var subscriptions: List<Job> = emptyList()
    
    init {
        // Restore last selected tab
        val lastTab = sharedPreferences.getInt("history_last_tab", 0)
        _uiState.value = _uiState.value.copy(selectedTab = lastTab.coerceIn(0, 2))
        subscribe()
    }
    
    fun saveSelectedTab(tabIndex: Int) {
        sharedPreferences.edit().putInt("history_last_tab", tabIndex.coerceIn(0, 2)).apply()
        _uiState.value = _uiState.value.copy(selectedTab = tabIndex.coerceIn(0, 2))
    }

    /**
     * Starts (or restarts) the collectors backing the screen.
     *
     * Each importance is observed independently rather than through one big `combine`, so
     * changing a filter no longer re-processes all three tabs, and changing one tab's data does
     * not re-process the other two.
     */
    private fun subscribe() {
        subscriptions.forEach { it.cancel() }
        subscriptions = listOf(
            observeNotifications(NotificationImportance.URGENT) { items ->
                _uiState.update { it.copy(urgentNotifications = items, isLoading = false) }
            },
            observeNotifications(NotificationImportance.NORMAL) { items ->
                _uiState.update { it.copy(normalNotifications = items, isLoading = false) }
            },
            observeNotifications(NotificationImportance.IGNORE) { items ->
                _uiState.update { it.copy(ignoredNotifications = items, isLoading = false) }
            },
            observeAvailableApps(),
            observeMutedConversations()
        )
    }

    private fun observeMutedConversations(): Job = viewModelScope.launch {
        conversationMuteRepository.mutedConversations.collect { mutes ->
            _uiState.update { it.copy(mutedConversationKeys = mutes.keys) }
        }
    }

    private fun observeAvailableApps(): Job = viewModelScope.launch {
        notificationRepository.getDistinctApps().collect { rows ->
            val apps = rows
                .map { AppInfo(it.packageName, it.appName) }
                .sortedBy { it.appName }
            _uiState.update { it.copy(availableApps = apps) }
        }
    }

    /**
     * Rows for one importance, with read/app/time/sender filters pushed down into SQL.
     *
     * Re-subscribes whenever the filter state changes so the database does the narrowing.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun notificationsFlow(importance: NotificationImportance): Flow<List<NotificationData>> =
        _filterState.flatMapLatest { filterState ->
            val bounds = filterState.resolveTimeBounds()
            notificationRepository.getFilteredNotifications(
                importance = importance,
                readStatus = filterState.readStatus.toIsReadOrNull(),
                startTime = bounds.first,
                endTime = bounds.last,
                selectedApps = filterState.selectedApps,
                senderQuery = filterState.senderQuery.trim()
            )
        }

    private fun observeNotifications(
        importance: NotificationImportance,
        onItems: (List<NotificationItem>) -> Unit
    ): Job = viewModelScope.launch {
        combine(
            notificationsFlow(importance),
            debouncedSearchQuery,
            _filterState
        ) { notifications, query, filterState ->
            // Read/app/time/sender are already applied by the query. Only the two filters that
            // are not worth indexing remain here: free-text search, and context matching against
            // the JSON-encoded tags.
            val searched = applySearchFilter(notifications, query)
            val contextFiltered = applyContextFilter(searched, filterState)
            applyGroupFilters(
                groupNotificationsUseCase.execute(contextFiltered),
                filterState
            )
        }.collect(onItems)
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

    private fun applyContextFilter(
        notifications: List<NotificationData>,
        filterState: FilterState
    ): List<NotificationData> {
        if (filterState.selectedContexts.isEmpty()) return notifications

        return notifications.filter { notification ->
            notification.tags.contexts.any { it in filterState.selectedContexts }
        }
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
    
    fun setAppRule(packageName: String, appName: String, ruleType: com.javohirmx.notifyr.domain.model.AppRuleType) {
        appRulesRepository.setAppRule(packageName, appName, ruleType)
    }
    
    fun removeAppRule(packageName: String) {
        appRulesRepository.removeAppRule(packageName)
    }
    
    fun markAsRead(notification: NotificationData) {
        viewModelScope.launch {
            notificationRepository.markAsRead(notification.id, true)
        }
    }

    fun togglePin(notification: NotificationData) {
        viewModelScope.launch {
            notificationRepository.setPinned(notification.id, !notification.isPinned)
        }
    }

    /**
     * Mutes or unmutes the conversation a notification belongs to.
     *
     * Muting archives future notifications from the same app + sender instead of showing them.
     * Notifications without a conversation ID (one-off app alerts) cannot be muted.
     */
    fun toggleConversationMute(notification: NotificationData) {
        val conversationId = notification.muteableConversationId ?: return
        viewModelScope.launch {
            if (conversationMuteRepository.isMuted(notification.packageName, conversationId)) {
                conversationMuteRepository.unmute(notification.packageName, conversationId)
            } else {
                conversationMuteRepository.mute(
                    com.javohirmx.notifyr.domain.model.MutedConversation(
                        packageName = notification.packageName,
                        appName = notification.appName,
                        conversationId = conversationId,
                        label = notification.sender
                            ?: notification.title.ifBlank { notification.appName }
                    )
                )
            }
        }
    }
    
    /**
     * Marks read either the whole importance tab, or only what the user currently sees when a
     * search/filter is active.
     *
     * The visible list is already the exact result of search + SQL filters + context matching +
     * grouping, so marking its IDs keeps this in sync without re-implementing those rules in SQL.
     */
    fun markAllAsRead(importance: NotificationImportance) {
        val criteriaActive = _searchQuery.value.isNotBlank() || _filterState.value.isActive

        if (!criteriaActive) {
            viewModelScope.launch {
                notificationRepository.markAllAsReadByImportance(importance)
            }
            return
        }

        // Capture at tap time so the coroutine marks what the user was looking at.
        val ids = visibleNotifications(importance)
            .flatMap { it.notificationIds() }
            .distinct()

        if (ids.isEmpty()) return

        viewModelScope.launch {
            notificationRepository.markAsReadBatch(ids)
        }
    }

    private fun visibleNotifications(importance: NotificationImportance): List<NotificationItem> =
        when (importance) {
            NotificationImportance.URGENT -> _uiState.value.urgentNotifications
            NotificationImportance.NORMAL -> _uiState.value.normalNotifications
            NotificationImportance.IGNORE -> _uiState.value.ignoredNotifications
        }

    private fun NotificationItem.notificationIds(): List<Long> = when (this) {
        is NotificationItem.Single -> listOf(notification.id)
        is NotificationItem.Group -> group.notifications.map { it.id }
    }
    
    fun deleteNotification(notification: NotificationData) {
        viewModelScope.launch {
            notificationRepository.deleteNotification(notification)
        }
    }
    
    fun markGroupAsRead(group: NotificationGroup) {
        viewModelScope.launch {
            notificationRepository.markAsReadBatch(group.notifications.map { it.id })
        }
    }
    
    fun deleteGroup(group: NotificationGroup) {
        viewModelScope.launch {
            notificationRepository.deleteByIds(group.notifications.map { it.id })
        }
    }
    
    fun refreshData() {
        _uiState.update { it.copy(isLoading = true) }
        // Re-subscribing cancels the previous collectors first. Previously this launched an
        // additional collector on every refresh, so each pull-to-refresh left another set of
        // full-history collectors running alongside the existing ones.
        subscribe()
    }
    
    /**
     * Change notification importance and trigger ML learning
     */
    fun changeNotificationImportance(
        notification: NotificationData,
        newImportance: NotificationImportance
    ) {
        // Don't do anything if importance hasn't changed
        if (notification.importance == newImportance) {
            return
        }
        
        viewModelScope.launch {
            try {
                // Update tags to match new importance
                val newPriority = com.javohirmx.notifyr.domain.model.Priority.fromImportance(newImportance)
                val newTimeSensitivity = when (newImportance) {
                    NotificationImportance.URGENT -> com.javohirmx.notifyr.domain.model.TimeSensitivity.IMMEDIATE
                    NotificationImportance.NORMAL -> com.javohirmx.notifyr.domain.model.TimeSensitivity.LATER
                    NotificationImportance.IGNORE -> com.javohirmx.notifyr.domain.model.TimeSensitivity.WHENEVER
                }
                
                val updatedTags = notification.tags.copy(
                    priority = newPriority,
                    timeSensitivity = newTimeSensitivity
                )
                
                // Update notification in database with new importance and tags
                val updatedNotification = notification.copy(
                    importance = newImportance,
                    tags = updatedTags
                )
                notificationRepository.updateNotification(updatedNotification)
                
                // Trigger ML learning from user correction
                hybridClassifier.learnFromUserCorrection(notification, newImportance)
                
                // UI will automatically refresh via Flow collection
            } catch (e: Exception) {
                android.util.Log.e("HistoryViewModel", "Failed to change notification importance", e)
            }
        }
    }
    
    /**
     * Update notification tags and trigger ML learning
     */
    fun updateNotificationTags(
        notification: NotificationData,
        newTags: com.javohirmx.notifyr.domain.model.NotificationTags
    ) {
        // Don't do anything if tags haven't changed
        if (notification.tags == newTags) {
            return
        }
        
        viewModelScope.launch {
            try {
                // Update notification in database with new tags
                val updatedNotification = notification.copy(tags = newTags)
                notificationRepository.updateNotification(updatedNotification)
                
                // Trigger ML learning from tag changes
                hybridClassifier.learnFromTagChanges(notification, newTags)
                
                // UI will automatically refresh via Flow collection
            } catch (e: Exception) {
                android.util.Log.e("HistoryViewModel", "Failed to update notification tags", e)
            }
        }
    }
}

data class HistoryUiState(
    val urgentNotifications: List<NotificationItem> = emptyList(),
    val normalNotifications: List<NotificationItem> = emptyList(),
    val ignoredNotifications: List<NotificationItem> = emptyList(),
    val isLoading: Boolean = true,
    val selectedTab: Int = 0,
    // Filters are intentionally not mirrored here. They are owned by [filterState] and are
    // consumed directly by the screen; duplicating them would leave a stale copy that no
    // longer tracks the active filters.
    val availableApps: List<AppInfo> = emptyList(), // For app filter dropdown
    /** Keys from [com.javohirmx.notifyr.domain.model.conversationMuteKey] for muted chats. */
    val mutedConversationKeys: Set<String> = emptySet()
)

/**
 * Represents an app available for filtering
 */
data class AppInfo(
    val packageName: String,
    val appName: String
)
