package com.homerentmanager.data.repository

import androidx.room.withTransaction
import com.homerentmanager.data.dao.MeterReadingDao
import com.homerentmanager.data.dao.RoomDao
import com.homerentmanager.data.dao.TenancyDao
import com.homerentmanager.data.entity.MeterReadingEntity
import com.homerentmanager.data.local.DatabaseProvider
import com.homerentmanager.domain.BillingCalculator
import com.homerentmanager.domain.NotFoundException
import com.homerentmanager.domain.ValidationException
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

class ElectricityRepository(private val provider: DatabaseProvider) {
    private val dao: MeterReadingDao get() = provider.database.meterReadingDao()
    private val roomDao: RoomDao get() = provider.database.roomDao()
    private val tenancyDao: TenancyDao get() = provider.database.tenancyDao()

    fun observeForRoom(roomId: UUID): Flow<List<MeterReadingEntity>> = dao.observeForRoom(roomId)

    fun observeAll(): Flow<List<MeterReadingEntity>> = dao.observeAll()

    suspend fun latestForRoom(roomId: UUID): MeterReadingEntity? = dao.getLatestForRoom(roomId)

    suspend fun latestForTenancy(tenancyId: UUID): MeterReadingEntity? =
        dao.getLatestForTenancy(tenancyId)

    /**
     * Appends an immutable meter reading. If the room is occupied the active tenancy is
     * linked automatically. Readings used by a bill are created inside the billing
     * transaction instead.
     */
    suspend fun addReading(
        roomId: UUID,
        readingDate: LocalDate,
        previousReading: Double,
        currentReading: Double,
        ratePerUnit: Double,
    ): MeterReadingEntity {
        if (ratePerUnit < 0) throw ValidationException("Electricity rate cannot be negative")
        val units = BillingCalculator.units(previousReading, currentReading)
        val amount = BillingCalculator.electricityAmountPaise(units, ratePerUnit)
        return provider.database.withTransaction {
            roomDao.getById(roomId) ?: throw NotFoundException("Room not found")
            val tenancy = tenancyDao.getActiveForRoom(roomId)
                ?: throw ValidationException("An active tenancy is required before recording a reading")
            val reading = MeterReadingEntity(
                roomId = roomId,
                tenancyId = tenancy.id,
                readingDate = readingDate,
                previousReading = previousReading,
                currentReading = currentReading,
                units = units,
                ratePerUnit = ratePerUnit,
                amountPaise = amount,
            )
            dao.insert(reading)
            reading
        }
    }
}
