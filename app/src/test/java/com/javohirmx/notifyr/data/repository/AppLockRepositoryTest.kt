package com.javohirmx.notifyr.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import com.google.common.truth.Truth.assertThat
import com.javohirmx.notifyr.data.datastore.AppSettings
import com.javohirmx.notifyr.data.datastore.SettingsSerializer
import com.javohirmx.notifyr.data.security.PinCredentialStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.File

class AppLockRepositoryTest {

    private lateinit var dataStore: DataStore<AppSettings>
    private lateinit var pinStore: FakePinCredentialStore
    private lateinit var repository: AppLockRepository
    private var currentTimeMs: Long = 0L

    @Before
    fun setup() = runTest {
        val tempFile = File.createTempFile("app_lock_settings", ".json")
        tempFile.deleteOnExit()

        dataStore = DataStoreFactory.create(
            serializer = SettingsSerializer,
            produceFile = { tempFile }
        )

        pinStore = FakePinCredentialStore()
        repository = AppLockRepository(
            dataStore = dataStore,
            pinCredentialStore = pinStore,
            nowProvider = { currentTimeMs }
        )
    }

    @Test
    fun `foreground after timeout relocks`() = runTest {
        repository.setLockEnabled(true)
        repository.setTimeoutSeconds(30)
        repository.setPin("1234")
        repository.unlockSession()

        currentTimeMs = 31_000L
        repository.onAppForegrounded()

        val state = repository.uiState.first()
        assertThat(state.isLocked).isTrue()
    }

    @Test
    fun `foreground within timeout stays unlocked`() = runTest {
        repository.setLockEnabled(true)
        repository.setTimeoutSeconds(60)
        repository.setPin("1234")
        repository.unlockSession()

        currentTimeMs = 30_000L
        repository.onAppForegrounded()

        val state = repository.uiState.first()
        assertThat(state.isLocked).isFalse()
    }

    @Test
    fun `disabled lock never requires unlock`() = runTest {
        repository.setLockEnabled(false)
        repository.lockNow()
        repository.onAppForegrounded()

        val state = repository.uiState.first()
        assertThat(state.lockEnabled).isFalse()
        assertThat(state.isLocked).isFalse()
    }

    @Test
    fun `repeated wrong pins trigger a temporary lockout`() = runTest {
        repository.setLockEnabled(true)
        repository.setPin("1234")
        repository.lockNow()

        repeat(AppLockRepository.MAX_FAILED_PIN_ATTEMPTS) {
            assertThat(repository.verifyPinAndUnlock("0000")).isFalse()
        }

        val lockedOut = repository.uiState.first()
        assertThat(lockedOut.lockoutUntilMs)
            .isEqualTo(currentTimeMs + AppLockRepository.PIN_LOCKOUT_MS)
        assertThat(lockedOut.isLocked).isTrue()

        // Even the correct PIN is rejected while the lockout is active.
        currentTimeMs += 1_000L
        assertThat(repository.verifyPinAndUnlock("1234")).isFalse()

        // Once it expires, the correct PIN works again.
        currentTimeMs += AppLockRepository.PIN_LOCKOUT_MS
        assertThat(repository.verifyPinAndUnlock("1234")).isTrue()
        assertThat(repository.uiState.first().isLocked).isFalse()
    }

    @Test
    fun `pin verify unlocks session`() = runTest {
        repository.setLockEnabled(true)
        repository.setPin("1234")
        repository.lockNow()

        val verified = repository.verifyPinAndUnlock("1234")
        val state = repository.uiState.first()

        assertThat(verified).isTrue()
        assertThat(state.isLocked).isFalse()
    }

    private class FakePinCredentialStore : PinCredentialStore {
        private var pin: String? = null

        override suspend fun hasPin(): Boolean = !pin.isNullOrBlank()

        override suspend fun savePin(pin: String) {
            this.pin = pin
        }

        override suspend fun verifyPin(pin: String): Boolean = this.pin == pin

        override suspend fun clearPin() {
            pin = null
        }
    }
}
