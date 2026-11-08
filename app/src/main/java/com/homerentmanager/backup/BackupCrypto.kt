package com.homerentmanager.backup

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-based encryption for manual backups.
 *
 * - PBKDF2-HMAC-SHA256 with 210,000 iterations and a random 16-byte salt
 * - AES-256-GCM with a random 12-byte IV and a 128-bit authentication tag
 *
 * A wrong password or any modification to the archive fails authentication before the
 * live database is touched.
 */
object BackupCrypto {
    const val ITERATIONS = 210_000
    const val SALT_BYTES = 16
    const val IV_BYTES = 12
    const val KEY_BITS = 256
    const val TAG_BITS = 128

    data class EncryptedPayload(
        val cipherText: ByteArray,
        val salt: ByteArray,
        val iv: ByteArray,
        val iterations: Int,
    )

    class WrongPasswordOrCorruptBackup(cause: Throwable? = null) :
        SecurityException("The recovery password is incorrect or the backup file is damaged", cause)

    fun encrypt(plainText: ByteArray, password: CharArray): EncryptedPayload {
        require(password.size >= 8) { "Recovery password must be at least 8 characters" }
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val key = deriveKey(password, salt, ITERATIONS)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            EncryptedPayload(cipher.doFinal(plainText), salt, iv, ITERATIONS)
        } finally {
            key.destroyIfPossible()
        }
    }

    fun decrypt(payload: EncryptedPayload, password: CharArray): ByteArray {
        val key = deriveKey(password, payload.salt, payload.iterations)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, payload.iv))
            cipher.doFinal(payload.cipherText)
        } catch (e: Exception) {
            throw WrongPasswordOrCorruptBackup(e)
        } finally {
            key.destroyIfPossible()
        }
    }

    fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val encoded = factory.generateSecret(spec).encoded
            SecretKeySpec(encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun sha256(file: java.io.File): String =
        file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        }

    fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)

    private fun SecretKey.destroyIfPossible() {
        if (this is SecretKeySpec) {
            runCatching { (this as javax.security.auth.Destroyable).destroy() }
        }
    }
}
