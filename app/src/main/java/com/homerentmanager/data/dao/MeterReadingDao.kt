package com.homerentmanager.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.homerentmanager.data.entity.MeterReadingEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface MeterReadingDao {
    @Query(
        "SELECT * FROM meter_readings WHERE room_id = :roomId " +
            "ORDER BY reading_date DESC, created_at DESC",
    )
    fun observeForRoom(roomId: UUID): Flow<List<MeterReadingEntity>>

    @Query("SELECT * FROM meter_readings ORDER BY reading_date DESC, created_at DESC")
    fun observeAll(): Flow<List<MeterReadingEntity>>

    @Query(
        "SELECT * FROM meter_readings WHERE room_id = :roomId " +
            "ORDER BY reading_date DESC, created_at DESC LIMIT 1",
    )
    suspend fun getLatestForRoom(roomId: UUID): MeterReadingEntity?

    @Query(
        "SELECT * FROM meter_readings WHERE tenancy_id = :tenancyId " +
            "ORDER BY reading_date DESC, created_at DESC LIMIT 1",
    )
    suspend fun getLatestForTenancy(tenancyId: UUID): MeterReadingEntity?

    @Query("SELECT * FROM meter_readings WHERE id = :id")
    suspend fun getById(id: UUID): MeterReadingEntity?

    @Query("SELECT * FROM meter_readings ORDER BY reading_date DESC, created_at DESC")
    suspend fun getAll(): List<MeterReadingEntity>

    @Insert
    suspend fun insert(reading: MeterReadingEntity)

    @Query("SELECT COUNT(*) FROM meter_readings")
    suspend fun count(): Int
}
