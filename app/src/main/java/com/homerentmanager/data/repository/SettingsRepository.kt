package com.homerentmanager.data.repository

import com.homerentmanager.data.dao.SettingsDao
import com.homerentmanager.data.entity.SettingsEntity
import com.homerentmanager.data.local.DatabaseProvider
import com.homerentmanager.domain.ValidationException
import kotlinx.coroutines.flow.Flow
import java.time.Instant

class SettingsRepository(private val provider: DatabaseProvider) {
    private val dao: SettingsDao get() = provider.database.settingsDao()

    fun observe(): Flow<SettingsEntity?> = dao.observe()

    suspend fun get(): SettingsEntity = dao.get() ?: SettingsEntity()

    suspend fun update(settings: SettingsEntity) {
        if (settings.propertyName.isBlank()) {
            throw ValidationException("Property name is required")
        }
        if (settings.billDueDay !in 1..28) {
            throw ValidationException("Due day must be between 1 and 28")
        }
        if (settings.defaultElectricityRate < 0) {
            throw ValidationException("Electricity rate cannot be negative")
        }
        if (settings.invoicePrefix.isBlank()) {
            throw ValidationException("Invoice prefix is required")
        }
        if (settings.receiptPrefix.isBlank()) {
            throw ValidationException("Receipt prefix is required")
        }
        if (settings.backupReminderDays !in 1..365) {
            throw ValidationException("Backup reminder must be between 1 and 365 days")
        }
        dao.upsert(settings.copy(id = 1, updatedAt = Instant.now()))
    }

    suspend fun markBackupResult(
        timestamp: Instant,
        status: String,
        fileName: String,
        sizeBytes: Long,
    ) {
        val current = get()
        dao.upsert(
            current.copy(
                lastBackupAt = timestamp,
                lastBackupStatus = status,
                lastBackupFileName = fileName,
                lastBackupSizeBytes = sizeBytes,
                updatedAt = Instant.now(),
            ),
        )
    }

    suspend fun getOrDefault(): SettingsEntity = dao.get() ?: SettingsEntity().also {
        dao.upsert(it)
    }
}
