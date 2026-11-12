package com.homerentmanager.data.repository

import androidx.room.withTransaction
import com.homerentmanager.data.dao.BillDao
import com.homerentmanager.data.dao.MeterReadingDao
import com.homerentmanager.data.dao.PaymentDao
import com.homerentmanager.data.dao.RoomDao
import com.homerentmanager.data.dao.TenancyDao
import com.homerentmanager.data.entity.RoomEntity
import com.homerentmanager.data.local.DatabaseProvider
import com.homerentmanager.data.model.RoomOccupancy
import com.homerentmanager.domain.ConflictException
import com.homerentmanager.domain.NotFoundException
import com.homerentmanager.domain.ValidationException
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class RoomRepository(private val provider: DatabaseProvider) {
    private val dao: RoomDao get() = provider.database.roomDao()
    private val tenancyDao: TenancyDao get() = provider.database.tenancyDao()
    private val billDao: BillDao get() = provider.database.billDao()
    private val paymentDao: PaymentDao get() = provider.database.paymentDao()
    private val readingDao: MeterReadingDao get() = provider.database.meterReadingDao()

    fun observeOccupancy(): Flow<List<RoomOccupancy>> = dao.observeOccupancy()

    fun observeRoom(roomId: UUID): Flow<RoomOccupancy?> = dao.observeOccupancyById(roomId)

    fun observeAll(): Flow<List<RoomEntity>> = dao.observeAll()

    fun observeCount(): Flow<Int> = dao.observeCount()

    suspend fun getAll(): List<RoomEntity> = dao.getAll()

    suspend fun get(roomId: UUID): RoomEntity? = dao.getById(roomId)

    /**
     * Adds a new managed room. New rooms start active and can be archived or, when they have
     * no history at all, permanently deleted later.
     */
    suspend fun addRoom(
        roomNumber: String,
        floor: String,
        defaultRentPaise: Long,
        defaultElectricityRate: Double,
        notes: String,
    ): UUID {
        val cleanNumber = roomNumber.trim()
        if (cleanNumber.isEmpty()) throw ValidationException("Room number is required")
        if (defaultRentPaise <= 0) throw ValidationException("Default rent must be greater than zero")
        if (defaultElectricityRate < 0) throw ValidationException("Electricity rate cannot be negative")

        return provider.database.withTransaction {
            val duplicate = dao.getByNumber(cleanNumber)
            if (duplicate != null) {
                throw ConflictException("Room number '$cleanNumber' is already in use")
            }
            val room = RoomEntity(
                roomNumber = cleanNumber,
                floor = floor.trim(),
                defaultRentPaise = defaultRentPaise,
                defaultElectricityRate = defaultElectricityRate,
                notes = notes.trim(),
                isActive = true,
            )
            dao.insert(room)
            room.id
        }
    }

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

    /**
     * Archives or restores a room. Archiving is refused while a tenancy is still active so
     * billing and meter readings cannot silently point at a retired room. History is never
     * removed: archived rooms stay visible with their old bills and tenancies.
     */
    suspend fun setRoomActive(roomId: UUID, active: Boolean) {
        provider.database.withTransaction {
            val existing = dao.getById(roomId) ?: throw NotFoundException("Room not found")
            if (existing.isActive == active) return@withTransaction
            if (!active && tenancyDao.getActiveForRoom(roomId) != null) {
                throw ConflictException(
                    "End the active tenancy for room ${existing.roomNumber} before archiving it",
                )
            }
            dao.update(existing.copy(isActive = active))
        }
    }

    /**
     * Permanently deletes a room only when it has no tenant/tenancy, bill, payment or meter
     * reading history. Any linked history is preserved and the room must be archived instead.
     */
    suspend fun deleteRoom(roomId: UUID) {
        provider.database.withTransaction {
            val existing = dao.getById(roomId) ?: throw NotFoundException("Room not found")
            val activeTenancy = tenancyDao.getActiveForRoom(roomId)
            val tenancies = tenancyDao.countForRoom(roomId)
            val bills = billDao.countForRoom(roomId)
            val payments = paymentDao.countForRoom(roomId)
            val readings = readingDao.countForRoom(roomId)
            if (activeTenancy != null || tenancies > 0 || bills > 0 || payments > 0 || readings > 0) {
                throw ConflictException(
                    "Room ${existing.roomNumber} has linked history and cannot be deleted. " +
                        "Archive it instead so bills, payments and meter readings stay safe.",
                )
            }
            dao.deleteById(roomId)
        }
    }
}
