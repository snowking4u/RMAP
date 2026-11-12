package com.homerentmanager.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.homerentmanager.data.entity.BillEntity
import com.homerentmanager.data.entity.BillStatus
import com.homerentmanager.data.model.BillWithDetails
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao
interface BillDao {
    @Transaction
    @Query("SELECT * FROM bills ORDER BY billing_month DESC, created_at DESC")
    fun observeAllWithDetails(): Flow<List<BillWithDetails>>

    @Transaction
    @Query(
        "SELECT * FROM bills WHERE billing_month = :month " +
            "ORDER BY created_at DESC",
    )
    fun observeForMonth(month: String): Flow<List<BillWithDetails>>

    @Transaction
    @Query("SELECT * FROM bills ORDER BY created_at DESC LIMIT :limit")
    fun observeRecentWithDetails(limit: Int): Flow<List<BillWithDetails>>

    @Transaction
    @Query("SELECT * FROM bills WHERE id = :billId")
    fun observeById(billId: UUID): Flow<BillWithDetails?>

    @Transaction
    @Query("SELECT * FROM bills WHERE room_id = :roomId ORDER BY billing_month DESC")
    fun observeForRoom(roomId: UUID): Flow<List<BillWithDetails>>

    @Transaction
    @Query("SELECT * FROM bills WHERE tenant_id = :tenantId ORDER BY billing_month DESC")
    fun observeForTenant(tenantId: UUID): Flow<List<BillWithDetails>>

    @Transaction
    @Query(
        "SELECT * FROM bills WHERE status != 'PAID' " +
            "ORDER BY due_date ASC, billing_month ASC",
    )
    fun observeOutstandingWithDetails(): Flow<List<BillWithDetails>>

    @Query("SELECT * FROM bills WHERE id = :billId")
    suspend fun getById(billId: UUID): BillEntity?

    @Query(
        "SELECT * FROM bills WHERE room_id = :roomId AND billing_month = :month LIMIT 1",
    )
    suspend fun getForRoomMonth(roomId: UUID, month: String): BillEntity?

    @Query("SELECT * FROM bills ORDER BY billing_month DESC, created_at DESC")
    suspend fun getAll(): List<BillEntity>

    @Insert
    suspend fun insert(bill: BillEntity)

    @Query("DELETE FROM bills WHERE id = :billId")
    suspend fun deleteById(billId: UUID): Int

    @Query(
        "UPDATE bills SET paid_paise = :paidPaise, status = :status, updated_at = :updatedAt " +
            "WHERE id = :billId",
    )
    suspend fun updatePaymentState(
        billId: UUID,
        paidPaise: Long,
        status: BillStatus,
        updatedAt: Instant,
    )

    @Query("SELECT COALESCE(SUM(total_paise), 0) FROM bills WHERE billing_month = :month")
    fun observeBilledForMonth(month: String): Flow<Long>

    @Query("SELECT COALESCE(SUM(paid_paise), 0) FROM bills WHERE billing_month = :month")
    fun observeAllocatedForMonth(month: String): Flow<Long>

    @Query(
        "SELECT COALESCE(SUM(total_paise - paid_paise), 0) FROM bills " +
            "WHERE billing_month = :month AND status != 'PAID'",
    )
    fun observeOutstandingForMonth(month: String): Flow<Long>

    @Query(
        "SELECT COALESCE(SUM(total_paise - paid_paise), 0) FROM bills WHERE status != 'PAID'",
    )
    fun observeOutstandingTotal(): Flow<Long>

    @Query(
        "SELECT COALESCE(SUM(total_paise - paid_paise), 0) FROM bills " +
            "WHERE status != 'PAID' AND billing_month < :month",
    )
    fun observeCarriedForward(month: String): Flow<Long>

    @Query("SELECT COUNT(*) FROM bills WHERE status = :status")
    fun observeCountByStatus(status: BillStatus): Flow<Int>

    @Query("SELECT COUNT(*) FROM bills")
    suspend fun count(): Int
}
