package com.javohirmx.notifyr.data.security

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-based envelope for exported backups.
 *
 * Notification exports contain message bodies and sender names, so a plain JSON file in shared
 * storage is a privacy leak. Backups can be encrypted with a user password using AES-256-GCM,
 * with the key derived through PBKDF2-HMAC-SHA256.
 *
 * Layout: [HEADER][salt 16][iv 12][ciphertext + GCM tag]. The authenticating header check keeps
 * plaintext backups readable, and GCM's tag rejects a wrong password or tampered file.
 */
object ExportCrypto {

    private const val HEADER = "NOTIFYR-ENC-V1"
    private val HEADER_BYTES = HEADER.toByteArray(Charsets.US_ASCII)

    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val PBKDF2_ITERATIONS = 120_000

    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"

    /** True when [data] is an encrypted export rather than a plain JSON document. */
    fun isEncrypted(data: ByteArray): Boolean {
        if (data.size < HEADER_BYTES.size) return false
        return HEADER_BYTES.indices.all { data[it] == HEADER_BYTES[it] }
    }

    fun encrypt(plaintext: ByteArray, password: String): ByteArray {
        require(password.isNotEmpty()) { "Password must not be empty" }

        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            deriveKey(password, salt),
            GCMParameterSpec(TAG_BITS, iv)
        )
        val ciphertext = cipher.doFinal(plaintext)

        return HEADER_BYTES + salt + iv + ciphertext
    }

    /**
     * @throws WrongPasswordException when the password is wrong or the payload was tampered with.
     */
    fun decrypt(encrypted: ByteArray, password: String): ByteArray {
        if (!isEncrypted(encrypted)) {
            throw GeneralSecurityException("Not an encrypted Notifyr backup")
        }
        if (password.isEmpty()) {
            throw WrongPasswordException("Enter the backup password")
        }

        val saltStart = HEADER_BYTES.size
        val ivStart = saltStart + SALT_BYTES
        val cipherStart = ivStart + IV_BYTES
        if (encrypted.size <= cipherStart) {
            throw GeneralSecurityException("Encrypted backup is truncated")
        }

        val salt = encrypted.copyOfRange(saltStart, ivStart)
        val iv = encrypted.copyOfRange(ivStart, cipherStart)
        val ciphertext = encrypted.copyOfRange(cipherStart, encrypted.size)

        return try {
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                deriveKey(password, salt),
                GCMParameterSpec(TAG_BITS, iv)
            )
            cipher.doFinal(ciphertext)
        } catch (e: AEADBadTagException) {
            throw WrongPasswordException("Incorrect password for this backup")
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        val keyBytes = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
            .generateSecret(spec)
            .encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}

class WrongPasswordException(message: String) : Exception(message)
