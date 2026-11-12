package com.homerentmanager.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.homerentmanager.data.entity.PaymentEntity
import com.homerentmanager.data.model.PaymentWithDetails
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

@Dao
interface PaymentDao {
    @Transaction
    @Query("SELECT * FROM payments ORDER BY paid_at DESC, created_at DESC")
    fun observeAllWithDetails(): Flow<List<PaymentWithDetails>>

    @Transaction
    @Query(
        "SELECT * FROM payments WHERE paid_at BETWEEN :start AND :end " +
            "ORDER BY paid_at DESC, created_at DESC",
    )
    fun observeBetweenWithDetails(start: LocalDate, end: LocalDate): Flow<List<PaymentWithDetails>>

    @Transaction
    @Query("SELECT * FROM payments ORDER BY created_at DESC LIMIT :limit")
    fun observeRecentWithDetails(limit: Int): Flow<List<PaymentWithDetails>>

    @Transaction
    @Query("SELECT * FROM payments WHERE bill_id = :billId ORDER BY paid_at DESC, created_at DESC")
    fun observeForBill(billId: UUID): Flow<List<PaymentWithDetails>>

    @Query("SELECT * FROM payments WHERE bill_id = :billId ORDER BY paid_at ASC, created_at ASC")
    suspend fun getForBill(billId: UUID): List<PaymentEntity>

    @Transaction
    @Query("SELECT * FROM payments WHERE id = :paymentId")
    suspend fun getWithDetails(paymentId: UUID): PaymentWithDetails?

    @Query(
        "SELECT COALESCE(SUM(amount_paise), 0) FROM payments " +
            "WHERE paid_at BETWEEN :start AND :end",
    )
    fun observeCollectedBetween(start: LocalDate, end: LocalDate): Flow<Long>

    @Query("SELECT * FROM payments ORDER BY paid_at DESC, created_at DESC")
    suspend fun getAll(): List<PaymentEntity>

    @Insert
    suspend fun insert(payment: PaymentEntity)

    @Query(
        "SELECT COUNT(*) FROM payments p INNER JOIN bills b ON b.id = p.bill_id " +
            "WHERE b.room_id = :roomId",
    )
    suspend fun countForRoom(roomId: UUID): Int

    @Query("SELECT COUNT(*) FROM payments")
    suspend fun count(): Int
}
