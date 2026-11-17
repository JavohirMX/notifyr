package com.javohirmx.notifyr.data.repository

import androidx.datastore.core.DataStore
import com.javohirmx.notifyr.data.datastore.AppSettings
import com.javohirmx.notifyr.data.security.PinCredentialStore
import com.javohirmx.notifyr.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLockRepository @Inject constructor(
    private val dataStore: DataStore<AppSettings>,
    private val pinCredentialStore: PinCredentialStore,
    @ApplicationScope private val appScope: CoroutineScope
) {

    private val nowProvider: () -> Long = { System.currentTimeMillis() }

    constructor(
        dataStore: DataStore<AppSettings>,
        pinCredentialStore: PinCredentialStore,
        nowProvider: () -> Long = { System.currentTimeMillis() }
    ) : this(
        dataStore,
        pinCredentialStore,
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    ) {
        this.nowProviderOverride = nowProvider
    }

    private var nowProviderOverride: (() -> Long)? = null

    private fun now(): Long = nowProviderOverride?.invoke() ?: nowProvider()

    private var failedPinAttempts = 0
    private var lockoutUntilMs = 0L

    private val _uiState = MutableStateFlow(AppLockUiState())
    val uiState: StateFlow<AppLockUiState> = _uiState.asStateFlow()

    init {
        runBlocking {
            loadState()
        }
    }

    fun setLockEnabled(enabled: Boolean) {
        val current = _uiState.value
        val hasPin = current.hasPin
        val shouldLock = enabled && hasPin
        if (!enabled) {
            failedPinAttempts = 0
            lockoutUntilMs = 0L
        }
        _uiState.value = current.copy(
            lockEnabled = enabled,
            isLocked = shouldLock,
            lastUnlockTimeMs = if (shouldLock) -1L else current.lastUnlockTimeMs,
            lockoutUntilMs = if (enabled) current.lockoutUntilMs else 0L
        )
        persist { it.copy(appLockEnabled = enabled) }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(biometricEnabled = enabled)
        persist { it.copy(appLockBiometricEnabled = enabled) }
    }

    fun setTimeoutSeconds(seconds: Int) {
        val timeout = seconds.coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS)
        _uiState.value = _uiState.value.copy(timeoutSeconds = timeout)
        persist { it.copy(appLockTimeoutSeconds = timeout) }
    }

    fun setPin(pin: String) {
        runBlocking(Dispatchers.IO) {
            pinCredentialStore.savePin(pin)
        }
        failedPinAttempts = 0
        lockoutUntilMs = 0L
        val current = _uiState.value
        _uiState.value = current.copy(
            hasPin = true,
            isLocked = current.lockEnabled,
            lastUnlockTimeMs = if (current.lockEnabled) -1L else current.lastUnlockTimeMs,
            lockoutUntilMs = 0L
        )
    }

    fun clearPin() {
        runBlocking(Dispatchers.IO) {
            pinCredentialStore.clearPin()
        }
        failedPinAttempts = 0
        lockoutUntilMs = 0L
        _uiState.value = _uiState.value.copy(
            hasPin = false,
            lockEnabled = false,
            isLocked = false,
            lastUnlockTimeMs = -1L,
            lockoutUntilMs = 0L
        )
        persist {
            it.copy(
                appLockEnabled = false,
                appLockBiometricEnabled = false
            )
        }
    }

    fun verifyPinAndUnlock(pin: String): Boolean {
        val currentTime = now()

        // Reject attempts while a lockout is active, without touching the credential store.
        if (currentTime < lockoutUntilMs) {
            return false
        }

        // The previous lockout has expired; clear it before accepting a new attempt.
        if (lockoutUntilMs != 0L) {
            lockoutUntilMs = 0L
            _uiState.value = _uiState.value.copy(lockoutUntilMs = 0L)
        }

        val verified = runBlocking(Dispatchers.IO) {
            pinCredentialStore.verifyPin(pin)
        }
        if (verified) {
            failedPinAttempts = 0
            unlockSession()
        } else {
            registerFailedAttempt(currentTime)
        }
        return verified
    }

    private fun registerFailedAttempt(currentTime: Long) {
        failedPinAttempts++
        if (failedPinAttempts >= MAX_FAILED_PIN_ATTEMPTS) {
            failedPinAttempts = 0
            lockoutUntilMs = currentTime + PIN_LOCKOUT_MS
            _uiState.value = _uiState.value.copy(lockoutUntilMs = lockoutUntilMs)
        }
    }

    fun unlockSession() {
        failedPinAttempts = 0
        lockoutUntilMs = 0L
        _uiState.value = _uiState.value.copy(
            isLocked = false,
            lastUnlockTimeMs = now(),
            lockoutUntilMs = 0L
        )
    }

    fun lockNow() {
        if (!_uiState.value.lockEnabled || !_uiState.value.hasPin) {
            _uiState.value = _uiState.value.copy(isLocked = false)
            return
        }
        _uiState.value = _uiState.value.copy(isLocked = true)
    }

    fun onAppForegrounded() {
        val current = _uiState.value
        if (!current.lockEnabled || !current.hasPin) {
            _uiState.value = current.copy(isLocked = false)
            return
        }

        if (current.lastUnlockTimeMs < 0L) {
            _uiState.value = current.copy(isLocked = true)
            return
        }

        val elapsedMs = now() - current.lastUnlockTimeMs
        val timeoutMs = current.timeoutSeconds * 1000L
        _uiState.value = current.copy(isLocked = elapsedMs >= timeoutMs)
    }

    private suspend fun loadState() {
        val settings = dataStore.data.first()
        val hasPin = pinCredentialStore.hasPin()
        val lockEnabled = settings.appLockEnabled
        _uiState.value = AppLockUiState(
            lockEnabled = lockEnabled,
            biometricEnabled = settings.appLockBiometricEnabled,
            timeoutSeconds = settings.appLockTimeoutSeconds.coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS),
            hasPin = hasPin,
            isLocked = lockEnabled && hasPin,
            lastUnlockTimeMs = -1L
        )
    }

    private fun persist(transform: (AppSettings) -> AppSettings) {
        appScope.launch {
            dataStore.updateData(transform)
        }
    }

    companion object {
        const val MIN_TIMEOUT_SECONDS = 15
        const val MAX_TIMEOUT_SECONDS = 900

        /** Wrong PINs allowed before a short lockout. */
        const val MAX_FAILED_PIN_ATTEMPTS = 5

        /** How long further PIN attempts are rejected after too many failures. */
        const val PIN_LOCKOUT_MS = 30_000L
    }
}

data class AppLockUiState(
    val lockEnabled: Boolean = false,
    val biometricEnabled: Boolean = true,
    val timeoutSeconds: Int = 60,
    val hasPin: Boolean = false,
    val isLocked: Boolean = false,
    val lastUnlockTimeMs: Long = -1L,
    /** Wall-clock time until which PIN attempts are rejected; 0 when not locked out. */
    val lockoutUntilMs: Long = 0L
)
