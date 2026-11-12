package com.homerentmanager.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.homerentmanager.data.entity.RoomEntity
import com.homerentmanager.data.model.RoomOccupancy
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface RoomDao {
    @Query(
        """
        SELECT r.*,
               t.id AS active_tenancy_id,
               t.tenant_id AS active_tenant_id,
               tn.name AS active_tenant_name,
               tn.mobile AS active_tenant_mobile,
               t.join_date AS active_join_date,
               t.monthly_rent_paise AS active_rent_paise
        FROM rooms r
        LEFT JOIN tenancies t ON t.room_id = r.id AND t.status = 'ACTIVE'
        LEFT JOIN tenants tn ON tn.id = t.tenant_id
        ORDER BY CAST(r.room_number AS INTEGER), r.room_number COLLATE NOCASE
        """,
    )
    fun observeOccupancy(): Flow<List<RoomOccupancy>>

    @Query(
        """
        SELECT r.*,
               t.id AS active_tenancy_id,
               t.tenant_id AS active_tenant_id,
               tn.name AS active_tenant_name,
               tn.mobile AS active_tenant_mobile,
               t.join_date AS active_join_date,
               t.monthly_rent_paise AS active_rent_paise
        FROM rooms r
        LEFT JOIN tenancies t ON t.room_id = r.id AND t.status = 'ACTIVE'
        LEFT JOIN tenants tn ON tn.id = t.tenant_id
        WHERE r.id = :roomId
        """,
    )
    fun observeOccupancyById(roomId: UUID): Flow<RoomOccupancy?>

    @Query("SELECT * FROM rooms ORDER BY CAST(room_number AS INTEGER), room_number COLLATE NOCASE")
    fun observeAll(): Flow<List<RoomEntity>>

    @Query("SELECT * FROM rooms ORDER BY CAST(room_number AS INTEGER), room_number COLLATE NOCASE")
    suspend fun getAll(): List<RoomEntity>

    @Query("SELECT * FROM rooms WHERE id = :id")
    suspend fun getById(id: UUID): RoomEntity?

    @Query("SELECT * FROM rooms WHERE room_number = :roomNumber LIMIT 1")
    suspend fun getByNumber(roomNumber: String): RoomEntity?

    @Insert
    suspend fun insert(room: RoomEntity)

    @Update
    suspend fun update(room: RoomEntity)

    @Query("DELETE FROM rooms WHERE id = :roomId")
    suspend fun deleteById(roomId: UUID): Int

    @Query("SELECT COUNT(*) FROM rooms")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM rooms")
    fun observeCount(): Flow<Int>
}
