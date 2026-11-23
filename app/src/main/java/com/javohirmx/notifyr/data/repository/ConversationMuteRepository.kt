package com.javohirmx.notifyr.data.repository

import androidx.datastore.core.DataStore
import com.javohirmx.notifyr.data.datastore.AppSettings
import com.javohirmx.notifyr.di.ApplicationScope
import com.javohirmx.notifyr.domain.model.MutedConversation
import com.javohirmx.notifyr.domain.model.conversationMuteKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persisted set of muted conversations.
 *
 * Muting a conversation archives its future notifications (IGNORE) instead of surfacing them.
 * The state is kept in its own DataStore field rather than derived from history so it survives
 * retention pruning, and mirrored in a [StateFlow] so the listener can consult it synchronously
 * on the hot path.
 */
@Singleton
class ConversationMuteRepository @Inject constructor(
    private val dataStore: DataStore<AppSettings>,
    @ApplicationScope private val appScope: CoroutineScope
) {

    /** Secondary constructor for tests and non-Hilt contexts. */
    constructor(dataStore: DataStore<AppSettings>) : this(
        dataStore,
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    )

    private val _mutedConversations = MutableStateFlow<Map<String, MutedConversation>>(emptyMap())
    val mutedConversations: StateFlow<Map<String, MutedConversation>> =
        _mutedConversations.asStateFlow()

    /**
     * Guards the initial asynchronous load from overwriting mutations made before it completes,
     * which is the same race [AppRulesRepository] handles.
     */
    private val stateLock = Any()
    private var hasLocalChanges = false

    init {
        appScope.launch {
            loadMutedConversations()
        }
    }

    private suspend fun loadMutedConversations() {
        try {
            val json = dataStore.data.first().conversationMutesJson
            if (json.isEmpty() || json == "[]") return

            val parsed = Json.decodeFromString<List<MutedConversation>>(json)
            synchronized(stateLock) {
                if (hasLocalChanges) return
                _mutedConversations.value = parsed.associateBy {
                    conversationMuteKey(it.packageName, it.conversationId)
                }
            }
        } catch (e: Exception) {
            // Keep whatever is already in memory; a corrupt value should not crash the listener.
            android.util.Log.e(TAG, "Failed to load muted conversations", e)
        }
    }

    /** True when future notifications from this app + conversation should be archived. */
    fun isMuted(packageName: String, conversationId: String?): Boolean {
        if (conversationId == null) return false
        return _mutedConversations.value.containsKey(
            conversationMuteKey(packageName, conversationId)
        )
    }

    fun isMuted(mute: MutedConversation): Boolean =
        isMuted(mute.packageName, mute.conversationId)

    suspend fun mute(mute: MutedConversation) {
        val normalized = mute.copy(
            label = mute.label.ifBlank { mute.conversationId },
            mutedAt = mute.mutedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
        )
        synchronized(stateLock) {
            hasLocalChanges = true
            _mutedConversations.value = _mutedConversations.value +
                (conversationMuteKey(normalized.packageName, normalized.conversationId) to normalized)
        }
        saveMutedConversations()
    }

    suspend fun unmute(packageName: String, conversationId: String) {
        val key = conversationMuteKey(packageName, conversationId)
        synchronized(stateLock) {
            if (!_mutedConversations.value.containsKey(key)) return
            hasLocalChanges = true
            _mutedConversations.value = _mutedConversations.value - key
        }
        saveMutedConversations()
    }

    private suspend fun saveMutedConversations() {
        try {
            dataStore.updateData { current ->
                current.copy(
                    conversationMutesJson = Json.encodeToString(
                        _mutedConversations.value.values.toList()
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to save muted conversations", e)
        }
    }

    private companion object {
        const val TAG = "ConversationMuteRepository"
    }
}
