package com.javohirmx.notifyr.data.repository

import androidx.datastore.core.DataStore
import com.javohirmx.notifyr.data.datastore.AppSettings
import com.javohirmx.notifyr.domain.model.TemporaryAppStatus
import com.javohirmx.notifyr.domain.model.TemporaryStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemporaryAppStatusRepository @Inject constructor(
    private val dataStore: DataStore<AppSettings>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val _temporaryStatuses = MutableStateFlow<Map<String, TemporaryAppStatus>>(emptyMap())
    val temporaryStatuses: StateFlow<Map<String, TemporaryAppStatus>> = _temporaryStatuses.asStateFlow()
    
    // Flow that automatically filters out expired statuses
    val activeStatuses: StateFlow<Map<String, TemporaryAppStatus>> = _temporaryStatuses
        .map { statuses ->
            val now = System.currentTimeMillis()
            statuses.filter { (_, status) -> status.expiresAt > now }
        }
        .stateIn(
            scope = scope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )
    
    init {
        scope.launch {
            loadTemporaryStatuses()
        }
    }
    
    private suspend fun loadTemporaryStatuses() {
        try {
            val settings = dataStore.data.first()
            if (settings.temporaryStatusesJson.isNotEmpty() && settings.temporaryStatusesJson != "[]") {
                val statuses = Json.decodeFromString<List<TemporaryAppStatus>>(settings.temporaryStatusesJson)
                // Filter out expired statuses on load
                val now = System.currentTimeMillis()
                val activeStatuses = statuses.filter { it.expiresAt > now }
                _temporaryStatuses.value = activeStatuses.associateBy { it.packageName }
                // Save back if any were expired
                if (activeStatuses.size != statuses.size) {
                    saveTemporaryStatuses()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TemporaryAppStatusRepository", "Failed to load temporary statuses", e)
        }
    }
    
    private suspend fun saveTemporaryStatuses() {
        try {
            dataStore.updateData { currentSettings ->
                val statusesJson = Json.encodeToString(_temporaryStatuses.value.values.toList())
                currentSettings.copy(temporaryStatusesJson = statusesJson)
            }
        } catch (e: Exception) {
            android.util.Log.e("TemporaryAppStatusRepository", "Failed to save temporary statuses", e)
        }
    }
    
    fun setTemporaryStatus(
        packageName: String,
        appName: String,
        status: TemporaryStatus,
        durationMinutes: Int
    ) {
        val now = System.currentTimeMillis()
        val expiresAt = now + (durationMinutes * 60_000L)
        
        val temporaryStatus = TemporaryAppStatus(
            packageName = packageName,
            appName = appName,
            status = status,
            expiresAt = expiresAt,
            createdAt = now
        )
        
        val currentStatuses = _temporaryStatuses.value.toMutableMap()
        currentStatuses[packageName] = temporaryStatus
        _temporaryStatuses.value = currentStatuses
        
        scope.launch {
            saveTemporaryStatuses()
        }
    }
    
    fun getTemporaryStatus(packageName: String): TemporaryAppStatus? {
        val status = _temporaryStatuses.value[packageName] ?: return null
        // Check if expired
        if (status.isExpired()) {
            // Remove expired status
            removeTemporaryStatus(packageName)
            return null
        }
        return status
    }
    
    fun getAllActiveStatuses(): Map<String, TemporaryAppStatus> {
        val now = System.currentTimeMillis()
        return _temporaryStatuses.value.filter { (_, status) -> status.expiresAt > now }
    }
    
    fun removeTemporaryStatus(packageName: String) {
        val currentStatuses = _temporaryStatuses.value.toMutableMap()
        currentStatuses.remove(packageName)
        _temporaryStatuses.value = currentStatuses
        
        scope.launch {
            saveTemporaryStatuses()
        }
    }
    
    fun clearExpiredStatuses() {
        val now = System.currentTimeMillis()
        val activeStatuses = _temporaryStatuses.value.filter { (_, status) -> status.expiresAt > now }
        
        if (activeStatuses.size != _temporaryStatuses.value.size) {
            _temporaryStatuses.value = activeStatuses
            scope.launch {
                saveTemporaryStatuses()
            }
        }
    }
}

