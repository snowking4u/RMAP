package com.homerentmanager.data.repository

import androidx.room.withTransaction
import com.homerentmanager.data.dao.RoomDao
import com.homerentmanager.data.entity.RoomEntity
import com.homerentmanager.data.local.DatabaseProvider
import com.homerentmanager.data.model.RoomOccupancy
import com.homerentmanager.domain.ConflictException
import com.homerentmanager.domain.NotFoundException
import com.homerentmanager.domain.ValidationException
import com.homerentmanager.util.Validators
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class RoomRepository(private val provider: DatabaseProvider) {
    private val dao: RoomDao get() = provider.database.roomDao()

    fun observeOccupancy(): Flow<List<RoomOccupancy>> = dao.observeOccupancy()

    fun observeRoom(roomId: UUID): Flow<RoomOccupancy?> = dao.observeOccupancyById(roomId)

    fun observeAll(): Flow<List<RoomEntity>> = dao.observeAll()

    fun observeCount(): Flow<Int> = dao.observeCount()

    suspend fun getAll(): List<RoomEntity> = dao.getAll()

    suspend fun get(roomId: UUID): RoomEntity? = dao.getById(roomId)

    suspend fun updateRoom(
        roomId: UUID,
        roomNumber: String,
        floor: String,
        defaultRentPaise: Long,
        defaultElectricityRate: Double,
        notes: String,
    ) {
        val cleanNumber = roomNumber.trim()
        if (cleanNumber.isEmpty()) throw ValidationException("Room number is required")
        if (defaultRentPaise <= 0) throw ValidationException("Default rent must be greater than zero")
        if (defaultElectricityRate < 0) throw ValidationException("Electricity rate cannot be negative")

        provider.database.withTransaction {
            val existing = dao.getById(roomId) ?: throw NotFoundException("Room not found")
            val duplicate = dao.getByNumber(cleanNumber)
            if (duplicate != null && duplicate.id != roomId) {
                throw ConflictException("Room number '$cleanNumber' is already in use")
            }
            dao.update(
                existing.copy(
                    roomNumber = cleanNumber,
                    floor = floor.trim(),
                    defaultRentPaise = defaultRentPaise,
                    defaultElectricityRate = defaultElectricityRate,
                    notes = notes.trim(),
                ),
            )
        }
    }
}
