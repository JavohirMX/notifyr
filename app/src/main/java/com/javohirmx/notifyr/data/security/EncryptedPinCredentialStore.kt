package com.javohirmx.notifyr.data.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedPinCredentialStore @Inject constructor(
    @ApplicationContext context: Context
) : PinCredentialStore {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override suspend fun hasPin(): Boolean {
        return !prefs.getString(PIN_HASH_KEY, null).isNullOrBlank() &&
            !prefs.getString(PIN_SALT_KEY, null).isNullOrBlank()
    }

    override suspend fun savePin(pin: String) = withContext(Dispatchers.Default) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = deriveHash(pin, salt)
        prefs.edit()
            .putString(PIN_SALT_KEY, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(PIN_HASH_KEY, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
    }

    override suspend fun verifyPin(pin: String): Boolean = withContext(Dispatchers.Default) {
        val saltBase64 = prefs.getString(PIN_SALT_KEY, null) ?: return@withContext false
        val hashBase64 = prefs.getString(PIN_HASH_KEY, null) ?: return@withContext false
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val storedHash = Base64.decode(hashBase64, Base64.NO_WRAP)
        val candidateHash = deriveHash(pin, salt)
        MessageDigest.isEqual(storedHash, candidateHash)
    }

    override suspend fun clearPin() = withContext(Dispatchers.Default) {
        prefs.edit().remove(PIN_HASH_KEY).remove(PIN_SALT_KEY).apply()
    }

    private fun deriveHash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        return SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
            .generateSecret(spec)
            .encoded
    }

    companion object {
        private const val PREFS_NAME = "notifyr_secure_pin"
        private const val PIN_HASH_KEY = "pin_hash"
        private const val PIN_SALT_KEY = "pin_salt"
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 120000
        private const val KEY_LENGTH_BITS = 256
    }
}
