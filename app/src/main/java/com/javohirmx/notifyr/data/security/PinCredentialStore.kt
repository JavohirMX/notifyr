package com.javohirmx.notifyr.data.security

interface PinCredentialStore {
    suspend fun hasPin(): Boolean
    suspend fun savePin(pin: String)
    suspend fun verifyPin(pin: String): Boolean
    suspend fun clearPin()
}
