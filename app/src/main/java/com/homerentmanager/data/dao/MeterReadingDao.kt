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

    /**
     * Latest reading for every room. Used to show the automatic previous reading in the
     * billing form so the owner only has to enter the current value.
     */
    @Query(
        """
        SELECT * FROM meter_readings r
        WHERE r.id = (
            SELECT r2.id FROM meter_readings r2
            WHERE r2.room_id = r.room_id
            ORDER BY r2.reading_date DESC, r2.created_at DESC
            LIMIT 1
        )
        """,
    )
    fun observeLatestPerRoom(): Flow<List<MeterReadingEntity>>

    @Query("SELECT * FROM meter_readings WHERE id = :id")
    suspend fun getById(id: UUID): MeterReadingEntity?

    @Query("SELECT * FROM meter_readings WHERE bill_id = :billId LIMIT 1")
    suspend fun getForBill(billId: UUID): MeterReadingEntity?

    @Query("SELECT * FROM meter_readings ORDER BY reading_date DESC, created_at DESC")
    suspend fun getAll(): List<MeterReadingEntity>

    @Insert
    suspend fun insert(reading: MeterReadingEntity)

    @Query("UPDATE meter_readings SET bill_id = :billId WHERE id = :readingId")
    suspend fun attachToBill(readingId: UUID, billId: UUID)

    @Query("DELETE FROM meter_readings WHERE id = :readingId")
    suspend fun deleteById(readingId: UUID): Int

    @Query("SELECT COUNT(*) FROM meter_readings WHERE room_id = :roomId")
    suspend fun countForRoom(roomId: UUID): Int

    @Query("SELECT COUNT(*) FROM meter_readings")
    suspend fun count(): Int
}
