package com.homerentmanager.backup

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant

/**
 * Versioned, self-describing backup envelope:
 *
 *   "HRMBK1" | 4-byte big-endian header length | UTF-8 JSON header | AES-GCM ciphertext
 *
 * The plaintext header is intentionally limited to non-sensitive metadata needed to pick
 * the right password and validate schema compatibility.
 */
object BackupFormat {
    const val MAGIC = "HRMBK1"
    const val FORMAT_VERSION = 1
    const val MAX_HEADER_BYTES = 64 * 1024
    const val MAX_CIPHER_BYTES = 64 * 1024 * 1024

    data class Header(
        val formatVersion: Int,
        val dbVersion: Int,
        val createdAtMillis: Long,
        val createdAtIso: String,
        val appVersion: String,
        val salt: ByteArray,
        val iv: ByteArray,
        val iterations: Int,
    ) {
        fun toJson(): String = JSONObject().apply {
            put("format", formatVersion)
            put("dbVersion", dbVersion)
            put("createdAtMillis", createdAtMillis)
            put("createdAtIso", createdAtIso)
            put("appVersion", appVersion)
            put("salt", BackupCrypto.encode(salt))
            put("iv", BackupCrypto.encode(iv))
            put("iterations", iterations)
            put("cipher", "AES-256-GCM")
            put("kdf", "PBKDF2WithHmacSHA256")
        }.toString()

        companion object {
            fun fromJson(json: String): Header {
                val obj = JSONObject(json)
                return Header(
                    formatVersion = obj.getInt("format"),
                    dbVersion = obj.getInt("dbVersion"),
                    createdAtMillis = obj.getLong("createdAtMillis"),
                    createdAtIso = obj.optString("createdAtIso", ""),
                    appVersion = obj.optString("appVersion", "unknown"),
                    salt = BackupCrypto.decode(obj.getString("salt")),
                    iv = BackupCrypto.decode(obj.getString("iv")),
                    iterations = obj.getInt("iterations"),
                )
            }
        }
    }

    data class Manifest(
        val formatVersion: Int,
        val appVersion: String,
        val dbVersion: Int,
        val createdAtMillis: Long,
        val createdAtIso: String,
        val databaseSha256: String,
        val databaseBytes: Long,
        val rooms: Int,
        val tenants: Int,
        val tenancies: Int,
        val readings: Int,
        val bills: Int,
        val payments: Int,
    ) {
        fun toJson(): String = JSONObject().apply {
            put("format", formatVersion)
            put("app", "Home Rent Manager")
            put("appVersion", appVersion)
            put("dbVersion", dbVersion)
            put("createdAtMillis", createdAtMillis)
            put("createdAtIso", createdAtIso)
            put("databaseFile", "database.db")
            put("databaseSha256", databaseSha256)
            put("databaseBytes", databaseBytes)
            put(
                "counts",
                JSONObject().apply {
                    put("rooms", rooms)
                    put("tenants", tenants)
                    put("tenancies", tenancies)
                    put("readings", readings)
                    put("bills", bills)
                    put("payments", payments)
                },
            )
        }.toString()

        companion object {
            fun fromJson(json: String): Manifest {
                val obj = JSONObject(json)
                val counts = obj.getJSONObject("counts")
                return Manifest(
                    formatVersion = obj.getInt("format"),
                    appVersion = obj.optString("appVersion", "unknown"),
                    dbVersion = obj.getInt("dbVersion"),
                    createdAtMillis = obj.getLong("createdAtMillis"),
                    createdAtIso = obj.optString("createdAtIso", ""),
                    databaseSha256 = obj.getString("databaseSha256"),
                    databaseBytes = obj.getLong("databaseBytes"),
                    rooms = counts.getInt("rooms"),
                    tenants = counts.getInt("tenants"),
                    tenancies = counts.getInt("tenancies"),
                    readings = counts.getInt("readings"),
                    bills = counts.getInt("bills"),
                    payments = counts.getInt("payments"),
                )
            }
        }
    }

    data class Envelope(val header: Header, val cipherText: ByteArray)

    fun writeEnvelope(output: OutputStream, header: Header, cipherText: ByteArray) {
        val headerBytes = header.toJson().toByteArray(Charsets.UTF_8)
        require(headerBytes.size <= MAX_HEADER_BYTES) { "Backup header is too large" }
        output.write(MAGIC.toByteArray(Charsets.US_ASCII))
        DataOutputStream(output).use { data ->
            data.writeInt(headerBytes.size)
            data.write(headerBytes)
            data.write(cipherText)
            data.flush()
        }
    }

    fun readEnvelope(input: InputStream): Envelope {
        val magic = ByteArray(MAGIC.length)
        var offset = 0
        while (offset < magic.size) {
            val read = input.read(magic, offset, magic.size - offset)
            if (read < 0) throw IllegalArgumentException("File is too short to be a backup")
            offset += read
        }
        if (String(magic, Charsets.US_ASCII) != MAGIC) {
            throw IllegalArgumentException("This is not a Home Rent Manager backup file")
        }
        val data = DataInputStream(input)
        val headerLength = data.readInt()
        if (headerLength !in 1..MAX_HEADER_BYTES) {
            throw IllegalArgumentException("Invalid backup header")
        }
        val headerBytes = ByteArray(headerLength)
        data.readFully(headerBytes)
        val cipher = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val read = data.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_CIPHER_BYTES) throw IllegalArgumentException("Backup is too large")
            cipher.write(buffer, 0, read)
        }
        if (total == 0) throw IllegalArgumentException("Backup payload is empty")
        return Envelope(
            header = Header.fromJson(String(headerBytes, Charsets.UTF_8)),
            cipherText = cipher.toByteArray(),
        )
    }

    fun buildHeader(
        dbVersion: Int,
        appVersion: String,
        payload: BackupCrypto.EncryptedPayload,
        createdAt: Instant,
    ): Header = Header(
        formatVersion = FORMAT_VERSION,
        dbVersion = dbVersion,
        createdAtMillis = createdAt.toEpochMilli(),
        createdAtIso = createdAt.toString(),
        appVersion = appVersion,
        salt = payload.salt,
        iv = payload.iv,
        iterations = payload.iterations,
    )

    fun envelopeFromBytes(bytes: ByteArray): Envelope =
        readEnvelope(ByteArrayInputStream(bytes))

    fun buildManifest(
        appVersion: String,
        dbVersion: Int,
        createdAt: Instant,
        databaseSha256: String,
        databaseBytes: Long,
        rooms: Int,
        tenants: Int,
        tenancies: Int,
        readings: Int,
        bills: Int,
        payments: Int,
    ): Manifest = Manifest(
        formatVersion = FORMAT_VERSION,
        appVersion = appVersion,
        dbVersion = dbVersion,
        createdAtMillis = createdAt.toEpochMilli(),
        createdAtIso = createdAt.toString(),
        databaseSha256 = databaseSha256,
        databaseBytes = databaseBytes,
        rooms = rooms,
        tenants = tenants,
        tenancies = tenancies,
        readings = readings,
        bills = bills,
        payments = payments,
    )
}
