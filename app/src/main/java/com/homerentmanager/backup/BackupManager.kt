package com.homerentmanager.backup

import android.content.Context
import android.content.pm.ApplicationInfo
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.homerentmanager.BuildConfig
import com.homerentmanager.data.local.AppDatabase
import com.homerentmanager.data.local.DatabaseProvider
import com.homerentmanager.data.model.BackupStats
import com.homerentmanager.data.repository.BillingRepository
import com.homerentmanager.data.repository.RoomRepository
import com.homerentmanager.data.repository.SettingsRepository
import com.homerentmanager.data.repository.TenancyRepository
import com.homerentmanager.data.entity.PaymentMethod
import com.homerentmanager.util.Csv
import com.homerentmanager.util.formatDisplay
import com.homerentmanager.util.toPlainAmount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Owns manual encrypted backups, validation, CSV export and transactional restore.
 *
 * Restore never replaces the live database file. A validated snapshot is attached to the
 * live SQLite connection and copied table-by-table inside one transaction. Any failure rolls
 * back and leaves the live data untouched. A plain, app-private safety copy of the live
 * database is kept before the transaction.
 */
class BackupManager(
    private val context: Context,
    private val provider: DatabaseProvider,
    private val settingsRepository: SettingsRepository,
    private val billingRepository: BillingRepository,
    private val roomRepository: RoomRepository,
    private val tenancyRepository: TenancyRepository,
) {
    companion object {
        const val AUTO_BACKUP_QUOTA_BYTES = 25L * 1024L * 1024L
        const val WARN_BACKUP_BYTES = 20L * 1024L * 1024L
        private const val MAX_DB_BYTES = 64L * 1024L * 1024L
        private const val MAX_ZIP_TOTAL_BYTES = 128L * 1024L * 1024L
        private val SAFETY_KEEP = 3
        private val fileNameFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        private val requiredTables = setOf(
            "rooms", "tenants", "tenancies", "meter_readings", "bills", "payments", "settings",
        )
    }

    data class BackupOutcome(
        val fileName: String,
        val sizeBytes: Long,
        val createdAt: Instant,
        val uri: Uri,
    )

    data class ValidatedBackup(
        val directory: File,
        val databaseFile: File,
        val header: BackupFormat.Header,
        val manifest: BackupFormat.Manifest,
        val stats: BackupStats,
        val warnings: List<String>,
    ) {
        fun discard() {
            runCatching { directory.deleteRecursively() }
        }
    }

    data class RestoreOutcome(
        val stats: BackupStats,
        val safetyCopyName: String,
        val restoredAt: Instant,
    )

    data class AutoBackupInfo(
        val allowed: Boolean,
        val statusText: String,
        val estimatedBytes: Long,
        val databaseBytes: Long,
        val quotaBytes: Long,
        val apiLevel: Int,
        val overQuota: Boolean,
        val warning: String?,
    )

    enum class CsvKind(val label: String, val fileStem: String) {
        BILLS("Bills and payments summary", "rent-bills"),
        PAYMENTS("Payments", "rent-payments"),
        TENANCIES("Tenancy history", "rent-tenancies"),
    }

    fun suggestedBackupFileName(now: Instant = Instant.now()): String =
        "HomeRentManager-backup-v${BackupFormat.FORMAT_VERSION}-" +
            fileNameFormatter.format(now.atZone(ZoneId.systemDefault())) + ".hrmbackup"

    fun suggestedCsvFileName(kind: CsvKind, now: Instant = Instant.now()): String =
        "${kind.fileStem}-${fileNameFormatter.format(now.atZone(ZoneId.systemDefault()))}.csv"

    suspend fun autoBackupInfo(): AutoBackupInfo = withContext(Dispatchers.IO) {
        val flags = context.applicationInfo.flags
        val allowed = flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0
        val dbBytes = provider.databaseFile().length()
        val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        val prefsBytes = prefsDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val estimate = dbBytes + prefsBytes
        val overQuota = estimate > AUTO_BACKUP_QUOTA_BYTES
        val warning = when {
            !allowed -> "Android backup is disabled for this app (manifest or device policy)."
            overQuota -> "Estimated backup is larger than the 25 MB Auto Backup quota."
            estimate > WARN_BACKUP_BYTES ->
                "Approaching the 25 MB Auto Backup quota. Consider archiving old PDFs externally."
            else -> null
        }
        AutoBackupInfo(
            allowed = allowed,
            statusText = if (allowed) {
                "Enabled. Android decides when to upload, usually once per day while charging " +
                    "and on Wi-Fi. The app cannot query the last system backup time."
            } else {
                "Disabled by the system or device policy."
            },
            estimatedBytes = estimate,
            databaseBytes = dbBytes,
            quotaBytes = AUTO_BACKUP_QUOTA_BYTES,
            apiLevel = android.os.Build.VERSION.SDK_INT,
            overQuota = overQuota,
            warning = warning,
        )
    }

    suspend fun createBackup(
        destination: Uri,
        password: CharArray,
        displayName: String = suggestedBackupFileName(),
    ): BackupOutcome = withContext(Dispatchers.IO) {
        require(password.size >= 8) { "Recovery password must be at least 8 characters" }
        val staging = File(context.cacheDir, "backup_tmp/${UUID.randomUUID()}")
        staging.mkdirs()
        try {
            provider.checkpoint()
            val snapshot = File(staging, "database.db")
            snapshotDatabase(snapshot)
            val inspection = inspectDatabase(snapshot)
            val createdAt = Instant.now()
            val manifest = BackupFormat.buildManifest(
                appVersion = BuildConfig.VERSION_NAME,
                dbVersion = inspection.version,
                createdAt = createdAt,
                databaseSha256 = BackupCrypto.sha256(snapshot),
                databaseBytes = snapshot.length(),
                rooms = inspection.stats.rooms,
                tenants = inspection.stats.tenants,
                tenancies = inspection.stats.tenancies,
                readings = inspection.stats.readings,
                bills = inspection.stats.bills,
                payments = inspection.stats.payments,
            )

            val zip = File(staging, "backup.zip")
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zip))).use { output ->
                writeZipEntry(output, "manifest.json", manifest.toJson().toByteArray(Charsets.UTF_8))
                ZipEntry("database.db").let { entry ->
                    entry.size = snapshot.length()
                    output.putNextEntry(entry)
                    snapshot.inputStream().use { it.copyTo(output, 64 * 1024) }
                    output.closeEntry()
                }
            }

            val encrypted = BackupCrypto.encrypt(zip.readBytes(), password)
            val header = BackupFormat.buildHeader(
                dbVersion = inspection.version,
                appVersion = BuildConfig.VERSION_NAME,
                payload = encrypted,
                createdAt = createdAt,
            )
            val archive = File(staging, "backup.hrmbackup")
            FileOutputStream(archive).use { output ->
                BackupFormat.writeEnvelope(output, header, encrypted.cipherText)
            }

            val resolver = context.contentResolver
            resolver.openOutputStream(destination, "w")?.use { output ->
                archive.inputStream().use { it.copyTo(output, 64 * 1024) }
                output.flush()
            } ?: error("Could not open the selected destination for writing")

            val realName = DocumentFile.fromSingleUri(context, destination)?.name
                ?.takeIf { it.isNotBlank() } ?: displayName
            settingsRepository.markBackupResult(
                timestamp = createdAt,
                status = "Manual backup succeeded",
                fileName = realName,
                sizeBytes = archive.length(),
            )
            BackupOutcome(realName, archive.length(), createdAt, destination)
        } catch (e: Exception) {
            runCatching {
                settingsRepository.markBackupResult(
                    timestamp = Instant.now(),
                    status = "Manual backup failed: ${e.message ?: "unknown error"}",
                    fileName = "",
                    sizeBytes = 0,
                )
            }
            throw e
        } finally {
            runCatching { staging.deleteRecursively() }
        }
    }

    suspend fun validateBackup(source: Uri, password: CharArray): ValidatedBackup =
        withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(source)?.use { it.readBytes() }
                ?: error("Could not open the selected backup file")
            if (bytes.size > BackupFormat.MAX_CIPHER_BYTES + BackupFormat.MAX_HEADER_BYTES) {
                error("Backup file exceeds the supported size")
            }
            val envelope = BackupFormat.envelopeFromBytes(bytes)
            val header = envelope.header
            if (header.formatVersion != BackupFormat.FORMAT_VERSION) {
                error("Unsupported backup format version ${header.formatVersion}")
            }
            if (header.dbVersion > AppDatabase.VERSION) {
                error(
                    "This backup was created by a newer app database version " +
                        "(${header.dbVersion}). Install the matching app version first.",
                )
            }
            if (header.dbVersion != AppDatabase.VERSION) {
                error(
                    "Backup schema v${header.dbVersion} cannot be restored into schema " +
                        "v${AppDatabase.VERSION} by this build. Keep the matching app version.",
                )
            }
            if (header.iterations !in 10_000..2_000_000) {
                error("Backup uses unsupported encryption parameters")
            }
            if (header.salt.size < 8 || header.iv.size != 12) {
                error("Backup encryption header is invalid")
            }

            val plain = try {
                BackupCrypto.decrypt(
                    BackupCrypto.EncryptedPayload(
                        cipherText = envelope.cipherText,
                        salt = header.salt,
                        iv = header.iv,
                        iterations = header.iterations,
                    ),
                    password,
                )
            } catch (e: BackupCrypto.WrongPasswordOrCorruptBackup) {
                throw IllegalArgumentException(
                    "The recovery password is incorrect, or the backup is damaged.",
                    e,
                )
            }

            val directory = File(context.cacheDir, "backup_tmp/validated-${UUID.randomUUID()}")
            directory.mkdirs()
            try {
                val manifest: BackupFormat.Manifest
                val dbFile = File(directory, "database.db")
                var manifestText: String? = null
                var computedSha = ""
                var extracted = 0L
                ZipInputStream(BufferedInputStream(plain.inputStream())).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            when (entry.name) {
                                "manifest.json" -> {
                                    manifestText = zip.readLimited(1L * 1024L * 1024L)
                                        .toString(Charsets.UTF_8)
                                }
                                "database.db" -> {
                                    FileOutputStream(dbFile).use { output ->
                                        computedSha = zip.copyAndHash(output, MAX_DB_BYTES)
                                    }
                                }
                                else -> Unit
                            }
                            extracted += entry.size.coerceAtLeast(0)
                            if (extracted > MAX_ZIP_TOTAL_BYTES) {
                                error("Backup archive expands beyond the supported size")
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
                val manifestJson = manifestText ?: error("Backup manifest is missing")
                manifest = BackupFormat.Manifest.fromJson(manifestJson)
                if (manifest.formatVersion != BackupFormat.FORMAT_VERSION) {
                    error("Backup manifest version is not supported")
                }
                if (!dbFile.exists() || dbFile.length() == 0L) error("Backup database is missing")
                if (manifest.dbVersion != header.dbVersion) {
                    error("Backup schema metadata is inconsistent")
                }
                if (manifest.databaseBytes != dbFile.length()) {
                    error("Backup database size does not match its manifest")
                }
                if (!computedSha.equals(manifest.databaseSha256, ignoreCase = true)) {
                    error("Backup database checksum does not match its manifest")
                }
                val inspection = inspectDatabase(dbFile)
                if (inspection.version != manifest.dbVersion) {
                    error("Backup database schema version does not match its manifest")
                }
                val actualStats = inspection.stats
                if (actualStats.rooms != manifest.rooms ||
                    actualStats.tenants != manifest.tenants ||
                    actualStats.tenancies != manifest.tenancies ||
                    actualStats.readings != manifest.readings ||
                    actualStats.bills != manifest.bills ||
                    actualStats.payments != manifest.payments
                ) {
                    error("Backup row counts do not match its manifest")
                }
                if (actualStats.rooms != 7) {
                    error("Backup must contain exactly seven rooms (found ${actualStats.rooms})")
                }

                val warnings = buildList {
                    if (header.createdAtMillis != manifest.createdAtMillis) {
                        add("Backup timestamps in the header and manifest differ.")
                    }
                    add("Restore replaces all current app data. A safety copy is kept internally.")
                }
                ValidatedBackup(
                    directory = directory,
                    databaseFile = dbFile,
                    header = header,
                    manifest = manifest,
                    stats = actualStats,
                    warnings = warnings,
                )
            } catch (e: Exception) {
                directory.deleteRecursively()
                throw e
            }
        }

    /**
     * Re-verifies the validated snapshot, snapshots the live database into an internal
     * safety file, then imports every table in one SQLite transaction. Validation is never
     * skipped and a failure rolls back to the untouched live data.
     */
    suspend fun restoreBackup(validated: ValidatedBackup): RestoreOutcome =
        withContext(Dispatchers.IO) {
            if (!validated.databaseFile.exists()) {
                throw IllegalStateException("Validated backup is no longer available. Validate it again.")
            }
            if (!BackupCrypto.sha256(validated.databaseFile)
                    .equals(validated.manifest.databaseSha256, ignoreCase = true)
            ) {
                throw IllegalStateException("Validated backup changed on disk. Validate it again.")
            }
            inspectDatabase(validated.databaseFile)

            provider.checkpoint()
            val safetyDir = File(context.filesDir, "restore_safety").apply { mkdirs() }
            val safetyName = "pre-restore-" +
                fileNameFormatter.format(Instant.now().atZone(ZoneId.systemDefault())) + ".db"
            val safetyFile = File(safetyDir, safetyName)
            provider.databaseFile().copyTo(safetyFile, overwrite = true)
            pruneSafetyCopies(safetyDir)

            val raw = provider.database.openHelper.writableDatabase
            try {
                raw.execSQL("ATTACH DATABASE ? AS backup_db", arrayOf(validated.databaseFile.absolutePath))
            } catch (e: Exception) {
                throw IllegalStateException("Could not attach the validated backup: ${e.message}", e)
            }
            try {
                raw.beginTransaction()
                try {
                    raw.execSQL("PRAGMA defer_foreign_keys = ON")
                    listOf(
                        "payments", "meter_readings", "bills", "tenancies", "tenants", "rooms", "settings",
                    ).forEach { raw.execSQL("DELETE FROM main.$it") }
                    listOf(
                        "rooms", "tenants", "tenancies", "bills", "meter_readings", "payments", "settings",
                    ).forEach { table ->
                        raw.execSQL("INSERT INTO main.$table SELECT * FROM backup_db.$table")
                    }
                    raw.setTransactionSuccessful()
                } catch (e: Exception) {
                    throw IllegalStateException(
                        "Restore failed and was rolled back. Your live data is unchanged. " +
                            "Details: ${e.message}",
                        e,
                    )
                } finally {
                    raw.endTransaction()
                }
            } finally {
                runCatching { raw.execSQL("DETACH DATABASE backup_db") }
            }

            val post = inspectDatabase(provider.databaseFile())
            if (post.integrity != "ok") {
                throw IllegalStateException("Database integrity check failed after restore")
            }
            val outcome = RestoreOutcome(
                stats = post.stats,
                safetyCopyName = safetyName,
                restoredAt = Instant.now(),
            )
            validated.discard()
            outcome
        }

    suspend fun exportCsv(destination: Uri, kind: CsvKind): Int = withContext(Dispatchers.IO) {
        val csv = when (kind) {
            CsvKind.BILLS -> buildBillsCsv()
            CsvKind.PAYMENTS -> buildPaymentsCsv()
            CsvKind.TENANCIES -> buildTenanciesCsv()
        }
        context.contentResolver.openOutputStream(destination, "w")?.use { output ->
            output.write(csv.toByteArray(Charsets.UTF_8))
            output.flush()
        } ?: error("Could not open the selected CSV destination")
        csv.lineSequence().count { it.isNotBlank() } - 1
    }

    private suspend fun buildBillsCsv(): String {
        val bills = billingRepository.observeAll().first()
        val rows = bills.map { detail ->
            val b = detail.bill
            listOf(
                b.billNumber,
                b.billingMonth,
                detail.room.roomNumber,
                detail.tenant.name,
                b.billDate.formatDisplay(),
                b.dueDate.formatDisplay(),
                b.rentPaise.toPlainAmount(),
                b.electricityUnits.toString(),
                b.electricityRate.toString(),
                b.electricityAmountPaise.toPlainAmount(),
                b.additionalChargesPaise.toPlainAmount(),
                b.discountPaise.toPlainAmount(),
                b.totalPaise.toPlainAmount(),
                b.paidPaise.toPlainAmount(),
                (b.totalPaise - b.paidPaise).coerceAtLeast(0).toPlainAmount(),
                b.status.name,
                b.notes ?: "",
            )
        }
        return Csv.build(
            header = listOf(
                "Bill No", "Billing Month", "Room", "Tenant", "Bill Date", "Due Date",
                "Rent", "Electricity Units", "Rate/Unit", "Electricity Amount",
                "Additional Charges", "Discount", "Total", "Paid", "Outstanding", "Status", "Notes",
            ),
            rows = rows,
        )
    }

    private suspend fun buildPaymentsCsv(): String {
        val payments = billingRepository.observeAllPayments().first()
        val rooms = roomRepository.observeAll().first().associateBy { it.id }
        val rows = payments.map { detail ->
            val p = detail.payment
            listOf(
                p.receiptNumber,
                p.paidAt.formatDisplay(),
                detail.bill.billNumber,
                detail.bill.billingMonth,
                rooms[detail.bill.roomId]?.roomNumber ?: "",
                detail.tenant.name,
                p.amountPaise.toPlainAmount(),
                p.method.name,
                p.reference,
                p.notes,
            )
        }
        return Csv.build(
            header = listOf(
                "Receipt No", "Payment Date", "Bill No", "Billing Month", "Room", "Tenant",
                "Amount", "Method", "Reference", "Notes",
            ),
            rows = rows,
        )
    }

    private suspend fun buildTenanciesCsv(): String {
        val tenancies = tenancyRepository.observeAll().first()
        val rows = tenancies.map { detail ->
            val t = detail.tenancy
            listOf(
                detail.room.roomNumber,
                detail.tenant.name,
                detail.tenant.mobile,
                t.joinDate.formatDisplay(),
                t.leaveDate?.formatDisplay() ?: "",
                t.monthlyRentPaise.toPlainAmount(),
                t.securityDepositPaise.toPlainAmount(),
                t.status.name,
                t.notes,
            )
        }
        return Csv.build(
            header = listOf(
                "Room", "Tenant", "Mobile", "Join Date", "Leave Date",
                "Monthly Rent", "Security Deposit", "Status", "Notes",
            ),
            rows = rows,
        )
    }

    private fun snapshotDatabase(destination: File) {
        if (destination.exists()) destination.delete()
        val raw = provider.database.openHelper.writableDatabase
        val escaped = destination.absolutePath.replace("'", "''")
        // VACUUM INTO gives a transactionally consistent snapshot on modern SQLite.
        val vacuumWorked = runCatching {
            raw.execSQL("VACUUM INTO '$escaped'")
            destination.exists() && destination.length() > 0
        }.getOrDefault(false)
        if (!vacuumWorked) {
            provider.databaseFile().copyTo(destination, overwrite = true)
        }
        val inspection = inspectDatabase(destination)
        if (inspection.integrity != "ok") {
            destination.delete()
            error("The database snapshot failed its integrity check")
        }
    }

    private data class Inspection(val version: Int, val integrity: String, val stats: BackupStats)

    private fun inspectDatabase(file: File): Inspection {
        if (!file.exists()) error("Database file not found")
        val db = SQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
        return db.use { database ->
            val integrity = database.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else "failed"
            }
            if (!integrity.equals("ok", ignoreCase = true)) {
                error("Database integrity check failed: $integrity")
            }
            val tables = database.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table'",
                null,
            ).use { cursor ->
                buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
            }
            val missing = requiredTables - tables
            if (missing.isNotEmpty()) {
                error("Backup is missing required tables: ${missing.joinToString()}")
            }
            fun count(table: String): Int = database.rawQuery(
                "SELECT COUNT(*) FROM $table",
                null,
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
            Inspection(
                version = database.version,
                integrity = integrity,
                stats = BackupStats(
                    rooms = count("rooms"),
                    tenants = count("tenants"),
                    tenancies = count("tenancies"),
                    bills = count("bills"),
                    payments = count("payments"),
                    readings = count("meter_readings"),
                ),
            )
        }
    }

    private fun pruneSafetyCopies(directory: File) {
        directory.listFiles()
            ?.filter { it.isFile && it.name.startsWith("pre-restore-") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(SAFETY_KEEP)
            ?.forEach { it.delete() }
    }

    private fun writeZipEntry(output: ZipOutputStream, name: String, bytes: ByteArray) {
        val entry = ZipEntry(name)
        entry.size = bytes.size.toLong()
        output.putNextEntry(entry)
        output.write(bytes)
        output.closeEntry()
    }

    private fun ZipInputStream.readLimited(limit: Long): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) error("Backup metadata is too large")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun ZipInputStream.copyAndHash(output: OutputStream, limit: Long): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) error("Backup database exceeds the supported size")
            digest.update(buffer, 0, read)
            output.write(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    /** Used by the backup reminder worker and the backup screen. */
    suspend fun lastManualBackupStatus(): Pair<Instant?, String> {
        val settings = settingsRepository.get()
        return settings.lastBackupAt to settings.lastBackupStatus
    }

    suspend fun needsBackupReminder(now: Instant = Instant.now()): Boolean {
        val settings = settingsRepository.get()
        val last = settings.lastBackupAt ?: return true
        val days = java.time.Duration.between(last, now).toDays()
        return days >= settings.backupReminderDays
    }
}
