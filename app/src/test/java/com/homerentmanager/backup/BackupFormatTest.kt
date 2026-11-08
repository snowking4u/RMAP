package com.homerentmanager.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant

class BackupFormatTest {
    @Test
    fun `encrypted payload round trips and rejects a wrong password`() {
        val plain = "rental database bytes".toByteArray()
        val password = "correct horse battery".toCharArray()
        val payload = BackupCrypto.encrypt(plain, password)

        assertFalse(plain.contentEquals(payload.cipherText))
        assertTrue(payload.cipherText.size > plain.size)
        assertEquals(BackupCrypto.ITERATIONS, payload.iterations)
        assertEquals(BackupCrypto.SALT_BYTES, payload.salt.size)
        assertEquals(BackupCrypto.IV_BYTES, payload.iv.size)
        assertTrue(BackupCrypto.decrypt(payload, password).contentEquals(plain))

        assertThrows(BackupCrypto.WrongPasswordOrCorruptBackup::class.java) {
            BackupCrypto.decrypt(payload, "wrong password".toCharArray())
        }
    }

    @Test
    fun `tampered ciphertext fails authentication`() {
        val payload = BackupCrypto.encrypt("data".toByteArray(), "password123".toCharArray())
        val tampered = payload.copy(cipherText = payload.cipherText.copyOf().also {
            it[0] = (it[0].toInt() xor 0x01).toByte()
        })
        assertThrows(BackupCrypto.WrongPasswordOrCorruptBackup::class.java) {
            BackupCrypto.decrypt(tampered, "password123".toCharArray())
        }
    }

    @Test
    fun `password shorter than eight characters is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.encrypt("data".toByteArray(), "short".toCharArray())
        }
    }

    @Test
    fun `envelope header and manifest round trip`() {
        val payload = BackupCrypto.encrypt("zip bytes".toByteArray(), "password123".toCharArray())
        val createdAt = Instant.parse("2026-02-03T04:05:06Z")
        val header = BackupFormat.buildHeader(2, "1.0.0", payload, createdAt)
        val output = ByteArrayOutputStream()
        BackupFormat.writeEnvelope(output, header, payload.cipherText)
        val bytes = output.toByteArray()

        assertEquals("HRMBK1", String(bytes.copyOfRange(0, 6), Charsets.US_ASCII))
        val envelope = BackupFormat.readEnvelope(ByteArrayInputStream(bytes))
        assertEquals(1, envelope.header.formatVersion)
        assertEquals(2, envelope.header.dbVersion)
        assertEquals(createdAt.toEpochMilli(), envelope.header.createdAtMillis)
        assertTrue(envelope.cipherText.contentEquals(payload.cipherText))
        assertTrue(
            BackupCrypto.decrypt(
                BackupCrypto.EncryptedPayload(
                    envelope.cipherText,
                    envelope.header.salt,
                    envelope.header.iv,
                    envelope.header.iterations,
                ),
                "password123".toCharArray(),
            ).contentEquals("zip bytes".toByteArray()),
        )

        val manifest = BackupFormat.buildManifest(
            appVersion = "1.0.0",
            dbVersion = 2,
            createdAt = createdAt,
            databaseSha256 = BackupCrypto.sha256("db".toByteArray()),
            databaseBytes = 2,
            rooms = 7,
            tenants = 1,
            tenancies = 1,
            readings = 0,
            bills = 1,
            payments = 0,
        )
        val parsed = BackupFormat.Manifest.fromJson(manifest.toJson())
        assertEquals(manifest, parsed)
    }

    @Test
    fun `foreign or truncated files are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupFormat.readEnvelope(ByteArrayInputStream("NOTBACKUP".toByteArray()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupFormat.readEnvelope(ByteArrayInputStream(ByteArray(3)))
        }
    }
}
