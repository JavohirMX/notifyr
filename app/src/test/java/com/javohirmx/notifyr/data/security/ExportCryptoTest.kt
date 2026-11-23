package com.javohirmx.notifyr.data.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.Assert.assertThrows

class ExportCryptoTest {

    private val plaintext = """{"appName":"Notifyr","notifications":["call mom"]}"""

    @Test
    fun `plain json is not detected as encrypted`() {
        assertThat(ExportCrypto.isEncrypted(plaintext.toByteArray())).isFalse()
        assertThat(ExportCrypto.isEncrypted(ByteArray(0))).isFalse()
    }

    @Test
    fun `encrypted payload round trips with the same password`() {
        val encrypted = ExportCrypto.encrypt(plaintext.toByteArray(), "correct horse")

        assertThat(ExportCrypto.isEncrypted(encrypted)).isTrue()
        assertThat(encrypted.toString(Charsets.UTF_8)).doesNotContain(plaintext)
        assertThat(ExportCrypto.decrypt(encrypted, "correct horse").toString(Charsets.UTF_8))
            .isEqualTo(plaintext)
    }

    @Test
    fun `encryption uses a fresh salt and iv each time`() {
        val first = ExportCrypto.encrypt(plaintext.toByteArray(), "pw")
        val second = ExportCrypto.encrypt(plaintext.toByteArray(), "pw")

        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `wrong password is rejected`() {
        val encrypted = ExportCrypto.encrypt(plaintext.toByteArray(), "right")

        assertThrows(WrongPasswordException::class.java) {
            ExportCrypto.decrypt(encrypted, "wrong")
        }
    }

    @Test
    fun `tampered ciphertext is rejected`() {
        val encrypted = ExportCrypto.encrypt(plaintext.toByteArray(), "right")
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 0x01).toByte()

        assertThrows(WrongPasswordException::class.java) {
            ExportCrypto.decrypt(encrypted, "right")
        }
    }

    @Test
    fun `empty payload round trips`() {
        val encrypted = ExportCrypto.encrypt(ByteArray(0), "pw")

        assertThat(ExportCrypto.decrypt(encrypted, "pw")).isEmpty()
    }
}
