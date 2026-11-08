package com.homerentmanager.data.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Relation
import com.homerentmanager.data.entity.BillEntity
import com.homerentmanager.data.entity.PaymentEntity
import com.homerentmanager.data.entity.RoomEntity
import com.homerentmanager.data.entity.TenancyEntity
import com.homerentmanager.data.entity.TenantEntity
import java.time.LocalDate
import java.util.UUID

/** Room joined with its current active tenancy, if any. */
data class RoomOccupancy(
    @Embedded val room: RoomEntity,
    @ColumnInfo(name = "active_tenancy_id") val activeTenancyId: UUID? = null,
    @ColumnInfo(name = "active_tenant_id") val activeTenantId: UUID? = null,
    @ColumnInfo(name = "active_tenant_name") val activeTenantName: String? = null,
    @ColumnInfo(name = "active_tenant_mobile") val activeTenantMobile: String? = null,
    @ColumnInfo(name = "active_join_date") val activeJoinDate: LocalDate? = null,
    @ColumnInfo(name = "active_rent_paise") val activeRentPaise: Long? = null,
)

data class TenantSummary(
    @Embedded val tenant: TenantEntity,
    @ColumnInfo(name = "active_tenancy_id") val activeTenancyId: UUID? = null,
    @ColumnInfo(name = "active_room_id") val activeRoomId: UUID? = null,
    @ColumnInfo(name = "active_room_number") val activeRoomNumber: String? = null,
    @ColumnInfo(name = "active_join_date") val activeJoinDate: LocalDate? = null,
    @ColumnInfo(name = "active_rent_paise") val activeRentPaise: Long? = null,
)

data class TenancyWithDetails(
    @Embedded val tenancy: TenancyEntity,
    @Relation(parentColumn = "room_id", entityColumn = "id")
    val room: RoomEntity,
    @Relation(parentColumn = "tenant_id", entityColumn = "id")
    val tenant: TenantEntity,
)

data class BillWithDetails(
    @Embedded val bill: BillEntity,
    @Relation(parentColumn = "room_id", entityColumn = "id")
    val room: RoomEntity,
    @Relation(parentColumn = "tenant_id", entityColumn = "id")
    val tenant: TenantEntity,
    @Relation(parentColumn = "tenancy_id", entityColumn = "id")
    val tenancy: TenancyEntity,
)

data class PaymentWithDetails(
    @Embedded val payment: PaymentEntity,
    @Relation(parentColumn = "bill_id", entityColumn = "id")
    val bill: BillEntity,
    @Relation(parentColumn = "tenant_id", entityColumn = "id")
    val tenant: TenantEntity,
)

data class BillPaymentSummary(
    val bill: BillEntity,
    val payments: List<PaymentEntity>,
)

/** Snapshot used by validation before a restore is allowed. */
data class BackupStats(
    val rooms: Int,
    val tenants: Int,
    val tenancies: Int,
    val bills: Int,
    val payments: Int,
    val readings: Int,
)
