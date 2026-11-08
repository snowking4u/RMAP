package com.homerentmanager.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class TenancyStatus {
    ACTIVE,
    ENDED,
}

/**
 * A tenancy is the immutable bridge between a tenant, a room and the rent that applied.
 * Ending a tenancy only sets leaveDate/status; rows are never overwritten or deleted.
 */
@Entity(
    tableName = "tenancies",
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
    ],
    indices = [
        Index("room_id"),
        Index("tenant_id"),
        Index(value = ["room_id", "status"]),
        Index(value = ["tenant_id", "status"]),
    ],
)
data class TenancyEntity(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    @ColumnInfo(name = "room_id") val roomId: UUID,
    @ColumnInfo(name = "tenant_id") val tenantId: UUID,
    @ColumnInfo(name = "join_date") val joinDate: LocalDate,
    @ColumnInfo(name = "leave_date") val leaveDate: LocalDate? = null,
    @ColumnInfo(name = "monthly_rent_paise") val monthlyRentPaise: Long,
    @ColumnInfo(name = "security_deposit_paise") val securityDepositPaise: Long = 0,
    val status: TenancyStatus = TenancyStatus.ACTIVE,
    val notes: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(),
)
