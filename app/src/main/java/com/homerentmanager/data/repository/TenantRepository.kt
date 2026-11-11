package com.homerentmanager.data.repository

import androidx.room.withTransaction
import com.homerentmanager.data.dao.RoomDao
import com.homerentmanager.data.dao.TenancyDao
import com.homerentmanager.data.dao.TenantDao
import com.homerentmanager.data.entity.IdType
import com.homerentmanager.data.entity.TenancyEntity
import com.homerentmanager.data.entity.TenancyStatus
import com.homerentmanager.data.entity.TenantEntity
import com.homerentmanager.data.local.DatabaseProvider
import com.homerentmanager.data.model.TenancyWithDetails
import com.homerentmanager.data.model.TenantSummary
import com.homerentmanager.domain.ConflictException
import com.homerentmanager.domain.NotFoundException
import com.homerentmanager.domain.ValidationException
import com.homerentmanager.util.Validators
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class TenantRepository(private val provider: DatabaseProvider) {
    private val tenantDao: TenantDao get() = provider.database.tenantDao()
    private val tenancyDao: TenancyDao get() = provider.database.tenancyDao()
    private val roomDao: RoomDao get() = provider.database.roomDao()

    fun observeSummaries(): Flow<List<TenantSummary>> = tenantDao.observeSummaries()

    fun observeSummary(tenantId: UUID): Flow<TenantSummary?> =
        tenantDao.observeSummaryById(tenantId)

    fun observeAll(): Flow<List<TenantEntity>> = tenantDao.observeAll()

    fun observeTenancies(tenantId: UUID): Flow<List<TenancyWithDetails>> =
        tenancyDao.observeForTenant(tenantId)

    suspend fun get(tenantId: UUID): TenantEntity? = tenantDao.getById(tenantId)

    data class TenantDraft(
        val name: String,
        val mobile: String,
        val alternateMobile: String,
        val address: String,
        val idType: IdType,
        val idNumber: String,
        val notes: String,
    )

    data class NewTenancy(
        val roomId: UUID,
        val monthlyRentPaise: Long,
        val securityDepositPaise: Long,
        val joinDate: LocalDate,
        val notes: String = "",
    )

    /** Registers a tenant, optionally starting their first tenancy in the same transaction. */
    suspend fun createTenant(draft: TenantDraft, tenancy: NewTenancy?): UUID {
        validateDraft(draft)
        if (tenancy != null) validateTenancyInput(tenancy)

        return provider.database.withTransaction {
            val now = Instant.now()
            val tenant = TenantEntity(
                name = Validators.normalizeName(draft.name),
                mobile = Validators.normalizeMobile(draft.mobile),
                alternateMobile = Validators.normalizeMobile(draft.alternateMobile),
                address = draft.address.trim(),
                idType = draft.idType,
                idNumber = draft.idNumber.trim(),
                notes = draft.notes.trim(),
                isActive = tenancy != null,
                createdAt = now,
                updatedAt = now,
            )
            tenantDao.insert(tenant)

            if (tenancy != null) {
                val room = roomDao.getById(tenancy.roomId)
                    ?: throw NotFoundException("Room not found")
                if (!room.isActive) {
                    throw ConflictException("Room ${room.roomNumber} is archived. Restore it first.")
                }
                if (tenancyDao.getActiveForRoom(tenancy.roomId) != null) {
                    throw ConflictException("Room ${room.roomNumber} is already occupied")
                }
                tenancyDao.insert(
                    TenancyEntity(
                        roomId = tenancy.roomId,
                        tenantId = tenant.id,
                        joinDate = tenancy.joinDate,
                        monthlyRentPaise = tenancy.monthlyRentPaise,
                        securityDepositPaise = tenancy.securityDepositPaise,
                        status = TenancyStatus.ACTIVE,
                        notes = tenancy.notes.trim(),
                    ),
                )
            }
            tenant.id
        }
    }

    suspend fun updateTenant(tenantId: UUID, draft: TenantDraft) {
        validateDraft(draft)
        provider.database.withTransaction {
            val existing = tenantDao.getById(tenantId)
                ?: throw NotFoundException("Tenant not found")
            tenantDao.update(
                existing.copy(
                    name = Validators.normalizeName(draft.name),
                    mobile = Validators.normalizeMobile(draft.mobile),
                    alternateMobile = Validators.normalizeMobile(draft.alternateMobile),
                    address = draft.address.trim(),
                    idType = draft.idType,
                    idNumber = draft.idNumber.trim(),
                    notes = draft.notes.trim(),
                    updatedAt = Instant.now(),
                ),
            )
        }
    }

    /** Starts a new tenancy for an existing tenant. Old tenancies are never modified. */
    suspend fun startTenancy(tenantId: UUID, input: NewTenancy): UUID {
        validateTenancyInput(input)
        return provider.database.withTransaction {
            val tenant = tenantDao.getById(tenantId)
                ?: throw NotFoundException("Tenant not found")
            val room = roomDao.getById(input.roomId)
                ?: throw NotFoundException("Room not found")
            if (!room.isActive) {
                throw ConflictException("Room ${room.roomNumber} is archived. Restore it first.")
            }
            if (tenancyDao.getActiveForTenant(tenantId) != null) {
                throw ConflictException(
                    "This tenant already has an active tenancy. End it before starting another.",
                )
            }
            if (tenancyDao.getActiveForRoom(input.roomId) != null) {
                throw ConflictException("Room ${room.roomNumber} is already occupied")
            }
            val tenancy = TenancyEntity(
                roomId = input.roomId,
                tenantId = tenantId,
                joinDate = input.joinDate,
                monthlyRentPaise = input.monthlyRentPaise,
                securityDepositPaise = input.securityDepositPaise,
                status = TenancyStatus.ACTIVE,
                notes = input.notes.trim(),
            )
            tenancyDao.insert(tenancy)
            tenantDao.update(tenant.copy(isActive = true, updatedAt = Instant.now()))
            tenancy.id
        }
    }

    /**
     * Ends a tenancy while preserving it and every linked bill. The room becomes vacant,
     * so a replacement tenant can be assigned without losing history.
     */
    suspend fun endTenancy(tenancyId: UUID, leaveDate: LocalDate) {
        provider.database.withTransaction {
            val tenancy = tenancyDao.getById(tenancyId)
                ?: throw NotFoundException("Tenancy not found")
            if (tenancy.status != TenancyStatus.ACTIVE) {
                throw ConflictException("This tenancy has already ended")
            }
            if (leaveDate < tenancy.joinDate) {
                throw ValidationException("Leaving date cannot be before the joining date")
            }
            tenancyDao.update(
                tenancy.copy(
                    leaveDate = leaveDate,
                    status = TenancyStatus.ENDED,
                ),
            )
            val tenant = tenantDao.getById(tenancy.tenantId)
            if (tenant != null && tenancyDao.getActiveForTenant(tenancy.tenantId) == null) {
                tenantDao.update(tenant.copy(isActive = false, updatedAt = Instant.now()))
            }
        }
    }

    private fun validateDraft(draft: TenantDraft) {
        Validators.requireName(draft.name)
        Validators.requireValidMobile(draft.mobile)
        Validators.requireValidMobile(draft.alternateMobile)
        if (draft.idNumber.isNotBlank() && draft.idType == IdType.NONE) {
            throw ValidationException("Choose an ID type for the ID number")
        }
    }

    private fun validateTenancyInput(input: NewTenancy) {
        if (input.monthlyRentPaise <= 0) throw ValidationException("Monthly rent must be greater than zero")
        if (input.securityDepositPaise < 0) throw ValidationException("Security deposit cannot be negative")
    }
}
