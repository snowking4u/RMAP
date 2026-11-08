package com.homerentmanager.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class BillStatus {
    UNPAID,
    PARTIAL,
    PAID,
}

/**
 * A monthly bill. (room_id, billing_month) is unique at the database level, so duplicate
 * monthly bills are impossible even if two screens submit at the same time.
 *
 * Tenant and tenancy ids are stored as immutable snapshots of who was billed. Editing a
 * tenant later never changes historical bills.
 */
@Entity(
    tableName = "bills",
    foreignKeys = [
        ForeignKey(
            entity = RoomEntity::class,
            parentColumns = ["id"],
            childColumns = ["room_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TenantEntity::class,
            parentColumns = ["id"],
            childColumns = ["tenant_id"],
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
    ],
    indices = [
        Index("room_id"),
        Index("tenant_id"),
        Index("tenancy_id"),
        Index("billing_month"),
        Index("status"),
        Index(value = ["room_id", "billing_month"], unique = true),
        Index(value = ["bill_number"], unique = true),
    ],
)
data class BillEntity(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    @ColumnInfo(name = "bill_number") val billNumber: String,
    @ColumnInfo(name = "room_id") val roomId: UUID,
    @ColumnInfo(name = "tenant_id") val tenantId: UUID,
    @ColumnInfo(name = "tenancy_id") val tenancyId: UUID,
    @ColumnInfo(name = "billing_month") val billingMonth: String,
    @ColumnInfo(name = "bill_date") val billDate: LocalDate,
    @ColumnInfo(name = "due_date") val dueDate: LocalDate,
    @ColumnInfo(name = "rent_paise") val rentPaise: Long,
    @ColumnInfo(name = "electricity_units") val electricityUnits: Double,
    @ColumnInfo(name = "electricity_rate") val electricityRate: Double,
    @ColumnInfo(name = "electricity_amount_paise") val electricityAmountPaise: Long,
    @ColumnInfo(name = "additional_charges_paise") val additionalChargesPaise: Long = 0,
    @ColumnInfo(name = "discount_paise") val discountPaise: Long = 0,
    @ColumnInfo(name = "total_paise") val totalPaise: Long,
    @ColumnInfo(name = "paid_paise") val paidPaise: Long = 0,
    val status: BillStatus = BillStatus.UNPAID,
    val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(),
    @ColumnInfo(name = "updated_at") val updatedAt: Instant = Instant.now(),
)
