package com.homerentmanager.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Historical electricity reading. Readings are append-only. A reading becomes linked to a
 * bill after billing; the link is kept for audit purposes.
 */
@Entity(
    tableName = "meter_readings",
    foreignKeys = [
        ForeignKey(
            entity = RoomEntity::class,
            parentColumns = ["id"],
            childColumns = ["room_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TenancyEntity::class,
            parentColumns = ["id"],
            childColumns = ["tenancy_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = BillEntity::class,
            parentColumns = ["id"],
            childColumns = ["bill_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("room_id"),
        Index("tenancy_id"),
        Index("bill_id"),
        Index(value = ["room_id", "reading_date"]),
    ],
)
data class MeterReadingEntity(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    @ColumnInfo(name = "room_id") val roomId: UUID,
    @ColumnInfo(name = "tenancy_id") val tenancyId: UUID,
    @ColumnInfo(name = "bill_id") val billId: UUID? = null,
    @ColumnInfo(name = "reading_date") val readingDate: LocalDate,
    @ColumnInfo(name = "previous_reading") val previousReading: Double,
    @ColumnInfo(name = "current_reading") val currentReading: Double,
    val units: Double,
    @ColumnInfo(name = "rate_per_unit") val ratePerUnit: Double,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(),
)
