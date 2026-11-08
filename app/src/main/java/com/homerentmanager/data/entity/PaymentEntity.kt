package com.homerentmanager.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class PaymentMethod {
    CASH,
    UPI,
    BANK_TRANSFER,
    CHEQUE,
    CARD,
    OTHER,
}

/**
 * Payments are append-only financial records. A bill's paid total and status are updated in
 * the same transaction as the payment insert.
 */
@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = BillEntity::class,
            parentColumns = ["id"],
            childColumns = ["bill_id"],
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
    ],
    indices = [
        Index("bill_id"),
        Index("tenant_id"),
        Index("paid_at"),
        Index(value = ["receipt_number"], unique = true),
    ],
)
data class PaymentEntity(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    @ColumnInfo(name = "receipt_number") val receiptNumber: String,
    @ColumnInfo(name = "bill_id") val billId: UUID,
    @ColumnInfo(name = "tenant_id") val tenantId: UUID,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    @ColumnInfo(name = "paid_at") val paidAt: LocalDate,
    val method: PaymentMethod = PaymentMethod.CASH,
    val reference: String = "",
    val notes: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(),
)
