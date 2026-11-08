package com.homerentmanager.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Single-row application settings table (id is always 1).
 * The recovery password is deliberately never stored here or anywhere else.
 */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "owner_name") val ownerName: String = "",
    @ColumnInfo(name = "owner_mobile") val ownerMobile: String = "",
    @ColumnInfo(name = "property_name") val propertyName: String = "Home Rentals",
    @ColumnInfo(name = "property_address") val propertyAddress: String = "",
    @ColumnInfo(name = "default_electricity_rate") val defaultElectricityRate: Double = 8.0,
    @ColumnInfo(name = "bill_due_day") val billDueDay: Int = 5,
    @ColumnInfo(name = "invoice_prefix") val invoicePrefix: String = "INV",
    @ColumnInfo(name = "receipt_prefix") val receiptPrefix: String = "RCP",
    @ColumnInfo(name = "backup_reminder_days") val backupReminderDays: Int = 7,
    @ColumnInfo(name = "last_backup_at") val lastBackupAt: Instant? = null,
    @ColumnInfo(name = "last_backup_status") val lastBackupStatus: String = "No manual backup yet",
    @ColumnInfo(name = "last_backup_file_name") val lastBackupFileName: String = "",
    @ColumnInfo(name = "last_backup_size_bytes") val lastBackupSizeBytes: Long = 0,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant = Instant.now(),
)
