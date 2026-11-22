package com.javohirmx.notifyr.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import com.google.common.truth.Truth.assertThat
import com.javohirmx.notifyr.data.datastore.AppSettings
import com.javohirmx.notifyr.data.datastore.SettingsSerializer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.File

class NotificationRetentionRepositoryTest {

    private lateinit var dataStore: DataStore<AppSettings>
    private lateinit var repository: NotificationRetentionRepository

    @Before
    fun setup() = runTest {
        val tempFile = File.createTempFile("retention_settings", ".json")
        tempFile.deleteOnExit()

        dataStore = DataStoreFactory.create(
            serializer = SettingsSerializer,
            produceFile = { tempFile }
        )

        repository = NotificationRetentionRepository(dataStore)
    }

    @Test
    fun `defaults to 90 days`() = runTest {
        assertThat(repository.retentionDays.first()).isEqualTo(90)
    }

    @Test
    fun `persists a valid retention period`() = runTest {
        repository.setRetentionDays(30)

        assertThat(repository.retentionDays.first()).isEqualTo(30)
        assertThat(repository.currentRetentionDays()).isEqualTo(30)
    }

    @Test
    fun `non-positive values mean keep everything`() = runTest {
        repository.setRetentionDays(30)
        repository.setRetentionDays(-5)

        assertThat(repository.retentionDays.first()).isEqualTo(0)
    }

    @Test
    fun `values beyond the maximum are clamped`() = runTest {
        repository.setRetentionDays(1_000)

        assertThat(repository.retentionDays.first())
            .isEqualTo(NotificationRetentionRepository.MAX_RETENTION_DAYS)
    }
}
