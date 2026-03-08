package com.javohirmx.notifyr.ui.security

import androidx.lifecycle.ViewModel
import com.javohirmx.notifyr.data.repository.AppLockRepository
import com.javohirmx.notifyr.data.repository.AppLockUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val appLockRepository: AppLockRepository
) : ViewModel() {

    val uiState: StateFlow<AppLockUiState> = appLockRepository.uiState

    fun setLockEnabled(enabled: Boolean) {
        appLockRepository.setLockEnabled(enabled)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        appLockRepository.setBiometricEnabled(enabled)
    }

    fun setTimeoutSeconds(seconds: Int) {
        appLockRepository.setTimeoutSeconds(seconds)
    }

    fun setupPinAndEnable(pin: String, confirmPin: String): String? {
        val normalizedPin = pin.trim()
        if (normalizedPin.length < 4 || normalizedPin.length > 8 || !normalizedPin.all { it.isDigit() }) {
            return "PIN must be 4-8 digits"
        }
        if (normalizedPin != confirmPin.trim()) {
            return "PIN confirmation does not match"
        }
        appLockRepository.setPin(normalizedPin)
        appLockRepository.setLockEnabled(true)
        return null
    }

    fun updatePin(pin: String, confirmPin: String): String? {
        val normalizedPin = pin.trim()
        if (normalizedPin.length < 4 || normalizedPin.length > 8 || !normalizedPin.all { it.isDigit() }) {
            return "PIN must be 4-8 digits"
        }
        if (normalizedPin != confirmPin.trim()) {
            return "PIN confirmation does not match"
        }
        appLockRepository.setPin(normalizedPin)
        return null
    }

    fun clearPinAndDisableLock() {
        appLockRepository.clearPin()
    }

    fun verifyPinAndUnlock(pin: String): Boolean {
        return appLockRepository.verifyPinAndUnlock(pin.trim())
    }

    fun unlockSession() {
        appLockRepository.unlockSession()
    }
}
