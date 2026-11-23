package com.javohirmx.notifyr.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import com.google.common.truth.Truth.assertThat
import com.javohirmx.notifyr.data.datastore.AppSettings
import com.javohirmx.notifyr.data.datastore.SettingsSerializer
import com.javohirmx.notifyr.domain.model.MutedConversation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.File

class ConversationMuteRepositoryTest {

    private lateinit var dataStore: DataStore<AppSettings>
    private lateinit var repository: ConversationMuteRepository

    private fun mute(sender: String = "Alice") = MutedConversation(
        packageName = "com.whatsapp",
        appName = "WhatsApp",
        conversationId = "com.whatsapp:$sender",
        label = sender
    )

    @Before
    fun setup() = runTest {
        val tempFile = File.createTempFile("conversation_mutes", ".json")
        tempFile.deleteOnExit()

        dataStore = DataStoreFactory.create(
            serializer = SettingsSerializer,
            produceFile = { tempFile }
        )

        repository = ConversationMuteRepository(dataStore)
    }

    @Test
    fun `conversations are not muted by default`() {
        assertThat(repository.isMuted("com.whatsapp", "com.whatsapp:Alice")).isFalse()
        assertThat(repository.isMuted("com.whatsapp", null)).isFalse()
    }

    @Test
    fun `muting hides the conversation and persists it`() = runTest {
        repository.mute(mute())

        assertThat(repository.isMuted("com.whatsapp", "com.whatsapp:Alice")).isTrue()
        assertThat(repository.isMuted(mute())).isTrue()

        val persisted = dataStore.data.first().conversationMutesJson
        assertThat(persisted).contains("com.whatsapp:Alice")
    }

    @Test
    fun `mute is scoped to app and conversation`() = runTest {
        repository.mute(mute("Alice"))

        assertThat(repository.isMuted("com.whatsapp", "com.whatsapp:Bob")).isFalse()
        assertThat(repository.isMuted("com.telegram.messenger", "com.whatsapp:Alice")).isFalse()
    }

    @Test
    fun `unmuting removes the conversation and updates storage`() = runTest {
        repository.mute(mute())
        repository.unmute("com.whatsapp", "com.whatsapp:Alice")

        assertThat(repository.isMuted("com.whatsapp", "com.whatsapp:Alice")).isFalse()
        assertThat(dataStore.data.first().conversationMutesJson).isEqualTo("[]")
    }

    @Test
    fun `unmuting an unknown conversation is a no-op`() = runTest {
        repository.unmute("com.whatsapp", "com.whatsapp:Nobody")

        assertThat(repository.mutedConversations.value).isEmpty()
    }

    @Test
    fun `blank label falls back to the conversation id`() = runTest {
        repository.mute(mute().copy(label = "  "))

        val stored = repository.mutedConversations.value.values.single()
        assertThat(stored.label).isEqualTo(stored.conversationId)
        assertThat(stored.mutedAt).isGreaterThan(0L)
    }
}
