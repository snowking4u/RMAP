package com.homerentmanager.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.homerentmanager.data.entity.TenancyEntity
import com.homerentmanager.data.entity.TenancyStatus
import com.homerentmanager.data.model.TenancyWithDetails
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface TenancyDao {
    @Transaction
    @Query("SELECT * FROM tenancies ORDER BY join_date DESC, created_at DESC")
    fun observeAllWithDetails(): Flow<List<TenancyWithDetails>>

    @Transaction
    @Query("SELECT * FROM tenancies WHERE room_id = :roomId ORDER BY join_date DESC, created_at DESC")
    fun observeForRoom(roomId: UUID): Flow<List<TenancyWithDetails>>

    @Transaction
    @Query("SELECT * FROM tenancies WHERE tenant_id = :tenantId ORDER BY join_date DESC, created_at DESC")
    fun observeForTenant(tenantId: UUID): Flow<List<TenancyWithDetails>>

    @Transaction
    @Query("SELECT * FROM tenancies WHERE status = 'ACTIVE' ORDER BY join_date DESC")
    fun observeActiveWithDetails(): Flow<List<TenancyWithDetails>>

    @Query("SELECT * FROM tenancies WHERE room_id = :roomId AND status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveForRoom(roomId: UUID): TenancyEntity?

    @Query("SELECT * FROM tenancies WHERE tenant_id = :tenantId AND status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveForTenant(tenantId: UUID): TenancyEntity?

    @Query("SELECT COUNT(*) FROM tenancies WHERE status = 'ACTIVE'")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tenancies WHERE status = 'ACTIVE'")
    suspend fun activeCount(): Int

    @Query("SELECT * FROM tenancies WHERE id = :id")
    suspend fun getById(id: UUID): TenancyEntity?

    @Query("SELECT * FROM tenancies ORDER BY join_date DESC, created_at DESC")
    suspend fun getAll(): List<TenancyEntity>

    @Insert
    suspend fun insert(tenancy: TenancyEntity)

    @Update
    suspend fun update(tenancy: TenancyEntity)

    @Query("SELECT COUNT(*) FROM tenancies WHERE room_id = :roomId")
    suspend fun countForRoom(roomId: UUID): Int

    @Query("SELECT COUNT(*) FROM tenancies")
    suspend fun count(): Int
}
