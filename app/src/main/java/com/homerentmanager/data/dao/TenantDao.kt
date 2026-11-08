package com.homerentmanager.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.homerentmanager.data.entity.TenantEntity
import com.homerentmanager.data.model.TenantSummary
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface TenantDao {
    @Query(
        """
        SELECT tn.*,
               t.id AS active_tenancy_id,
               t.room_id AS active_room_id,
               r.room_number AS active_room_number,
               t.join_date AS active_join_date,
               t.monthly_rent_paise AS active_rent_paise
        FROM tenants tn
        LEFT JOIN tenancies t ON t.tenant_id = tn.id AND t.status = 'ACTIVE'
        LEFT JOIN rooms r ON r.id = t.room_id
        ORDER BY tn.is_active DESC, tn.name COLLATE NOCASE
        """,
    )
    fun observeSummaries(): Flow<List<TenantSummary>>

    @Query(
        """
        SELECT tn.*,
               t.id AS active_tenancy_id,
               t.room_id AS active_room_id,
               r.room_number AS active_room_number,
               t.join_date AS active_join_date,
               t.monthly_rent_paise AS active_rent_paise
        FROM tenants tn
        LEFT JOIN tenancies t ON t.tenant_id = tn.id AND t.status = 'ACTIVE'
        LEFT JOIN rooms r ON r.id = t.room_id
        WHERE tn.id = :tenantId
        """,
    )
    fun observeSummaryById(tenantId: UUID): Flow<TenantSummary?>

    @Query("SELECT * FROM tenants ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<TenantEntity>>

    @Query("SELECT * FROM tenants WHERE id = :id")
    suspend fun getById(id: UUID): TenantEntity?

    @Query("SELECT * FROM tenants WHERE id = :id")
    fun observeById(id: UUID): Flow<TenantEntity?>

    @Insert
    suspend fun insert(tenant: TenantEntity)

    @Update
    suspend fun update(tenant: TenantEntity)

    @Query("SELECT COUNT(*) FROM tenants")
    suspend fun count(): Int
}
