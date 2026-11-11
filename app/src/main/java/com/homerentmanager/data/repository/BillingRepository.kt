package com.homerentmanager.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.homerentmanager.data.dao.BillDao
import com.homerentmanager.data.dao.MeterReadingDao
import com.homerentmanager.data.dao.PaymentDao
import com.homerentmanager.data.dao.RoomDao
import com.homerentmanager.data.dao.SettingsDao
import com.homerentmanager.data.dao.TenancyDao
import com.homerentmanager.data.dao.TenantDao
import com.homerentmanager.data.entity.BillEntity
import com.homerentmanager.data.entity.MeterReadingEntity
import com.homerentmanager.data.entity.PaymentEntity
import com.homerentmanager.data.entity.PaymentMethod
import com.homerentmanager.data.entity.SettingsEntity
import com.homerentmanager.data.local.DatabaseProvider
import com.homerentmanager.data.model.BillWithDetails
import com.homerentmanager.data.model.PaymentWithDetails
import com.homerentmanager.domain.BillingCalculator
import com.homerentmanager.domain.ConflictException
import com.homerentmanager.domain.NotFoundException
import com.homerentmanager.domain.ValidationException
import com.homerentmanager.util.Validators
import com.homerentmanager.util.monthEnd
import com.homerentmanager.util.monthStart
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import java.util.UUID

class BillingRepository(private val provider: DatabaseProvider) {
    private val billDao: BillDao get() = provider.database.billDao()
    private val paymentDao: PaymentDao get() = provider.database.paymentDao()
    private val roomDao: RoomDao get() = provider.database.roomDao()
    private val tenantDao: TenantDao get() = provider.database.tenantDao()
    private val tenancyDao: TenancyDao get() = provider.database.tenancyDao()
    private val readingDao: MeterReadingDao get() = provider.database.meterReadingDao()
    private val settingsDao: SettingsDao get() = provider.database.settingsDao()

    data class BillRequest(
        val tenancyId: UUID,
        val billingMonth: String,
        val currentReading: Double? = null,
        val ratePerUnit: Double,
        val additionalChargesPaise: Long = 0,
        val discountPaise: Long = 0,
        val notes: String = "",
    )

    data class BulkGenerationResult(
        val createdBillIds: List<UUID> = emptyList(),
        val skipped: List<String> = emptyList(),
        val failures: List<String> = emptyList(),
    )

    fun observeAll(): Flow<List<BillWithDetails>> = billDao.observeAllWithDetails()

    fun observeForMonth(month: String): Flow<List<BillWithDetails>> =
        billDao.observeForMonth(month)

    fun observeForRoom(roomId: UUID): Flow<List<BillWithDetails>> = billDao.observeForRoom(roomId)

    fun observeForTenant(tenantId: UUID): Flow<List<BillWithDetails>> =
        billDao.observeForTenant(tenantId)

    fun observeById(billId: UUID): Flow<BillWithDetails?> = billDao.observeById(billId)

    fun observeRecent(limit: Int = 5): Flow<List<BillWithDetails>> =
        billDao.observeRecentWithDetails(limit)

    fun observeOutstanding(): Flow<List<BillWithDetails>> =
        billDao.observeOutstandingWithDetails()

    fun observeBilledForMonth(month: String): Flow<Long> = billDao.observeBilledForMonth(month)

    fun observeOutstandingForMonth(month: String): Flow<Long> =
        billDao.observeOutstandingForMonth(month)

    fun observeOutstandingTotal(): Flow<Long> = billDao.observeOutstandingTotal()

    fun observeCarriedForward(month: String): Flow<Long> = billDao.observeCarriedForward(month)

    fun observePaymentsForBill(billId: UUID): Flow<List<PaymentWithDetails>> =
        paymentDao.observeForBill(billId)

    fun observePaymentsForMonth(month: String): Flow<List<PaymentWithDetails>> =
        paymentDao.observeBetweenWithDetails(monthStart(month), monthEnd(month))

    fun observeCollectedForMonth(month: String): Flow<Long> =
        paymentDao.observeCollectedBetween(monthStart(month), monthEnd(month))

    fun observeRecentPayments(limit: Int = 5): Flow<List<PaymentWithDetails>> =
        paymentDao.observeRecentWithDetails(limit)

    fun observeAllPayments(): Flow<List<PaymentWithDetails>> = paymentDao.observeAllWithDetails()

    suspend fun getBill(billId: UUID): BillEntity? = billDao.getById(billId)

    suspend fun getPaymentsForBill(billId: UUID): List<PaymentEntity> =
        paymentDao.getForBill(billId)

    suspend fun getPaymentWithDetails(paymentId: UUID): PaymentWithDetails? =
        paymentDao.getWithDetails(paymentId)

    /** Returns the historical meter reading linked to a bill, if the bill included one. */
    suspend fun getReadingForBill(billId: UUID): MeterReadingEntity? =
        readingDao.getForBill(billId)

    /**
     * Creates one bill (and, when readings are supplied, the linked meter reading) in one
     * transaction. The unique (room, month) index plus this check make duplicates impossible.
     */
    suspend fun generateBill(request: BillRequest): UUID {
        if (!Validators.isValidMonth(request.billingMonth)) {
            throw ValidationException("Billing month must use the yyyy-MM format")
        }
        if (request.ratePerUnit < 0) throw ValidationException("Electricity rate cannot be negative")
        if (request.additionalChargesPaise < 0) throw ValidationException("Additional charges cannot be negative")
        if (request.discountPaise < 0) throw ValidationException("Discount cannot be negative")

        return try {
            provider.database.withTransaction {
                val tenancy = tenancyDao.getById(request.tenancyId)
                    ?: throw NotFoundException("Tenancy not found")
                val room = roomDao.getById(tenancy.roomId)
                    ?: throw NotFoundException("Room not found")
                val tenant = tenantDao.getById(tenancy.tenantId)
                    ?: throw NotFoundException("Tenant not found")
                val settings = settingsDao.get() ?: SettingsEntity()

                if (billDao.getForRoomMonth(room.id, request.billingMonth) != null) {
                    throw ConflictException(
                        "A bill already exists for room ${room.roomNumber} for " +
                            com.homerentmanager.util.monthLabel(request.billingMonth),
                    )
                }

                val latest = readingDao.getLatestForRoom(room.id)
                val previousReading = latest?.currentReading ?: 0.0
                val current = request.currentReading
                val units: Double
                val electricityPaise: Long
                if (current != null) {
                    units = BillingCalculator.units(previousReading, current)
                    electricityPaise = BillingCalculator.electricityAmountPaise(
                        units,
                        request.ratePerUnit,
                    )
                } else {
                    units = 0.0
                    electricityPaise = 0
                }

                val total = BillingCalculator.billTotalPaise(
                    rentPaise = tenancy.monthlyRentPaise,
                    electricityPaise = electricityPaise,
                    additionalChargesPaise = request.additionalChargesPaise,
                    discountPaise = request.discountPaise,
                )

                val yearMonth = YearMonth.parse(request.billingMonth)
                val dueDay = settings.billDueDay.coerceIn(1, yearMonth.lengthOfMonth())
                val bill = BillEntity(
                    billNumber = billNumber(
                        settings.invoicePrefix,
                        request.billingMonth,
                        room.roomNumber,
                        tenancy.id,
                    ),
                    roomId = room.id,
                    tenantId = tenant.id,
                    tenancyId = tenancy.id,
                    billingMonth = request.billingMonth,
                    billDate = LocalDate.now(),
                    dueDate = yearMonth.atDay(dueDay),
                    rentPaise = tenancy.monthlyRentPaise,
                    electricityUnits = units,
                    electricityRate = request.ratePerUnit,
                    electricityAmountPaise = electricityPaise,
                    additionalChargesPaise = request.additionalChargesPaise,
                    discountPaise = request.discountPaise,
                    totalPaise = total,
                    paidPaise = 0,
                    notes = request.notes.trim().ifBlank { null },
                )
                billDao.insert(bill)

                if (current != null) {
                    readingDao.insert(
                        MeterReadingEntity(
                            roomId = room.id,
                            tenancyId = tenancy.id,
                            billId = bill.id,
                            readingDate = LocalDate.now(),
                            previousReading = previousReading,
                            currentReading = current,
                            units = units,
                            ratePerUnit = request.ratePerUnit,
                            amountPaise = electricityPaise,
                        ),
                    )
                }
                bill.id
            }
        } catch (e: SQLiteConstraintException) {
            throw ConflictException(
                "A bill already exists for this room and month, or the bill number collided. " +
                    "No data was changed.",
            )
        }
    }

    /**
     * Generates bills independently so one duplicate does not roll back the others.
     * Every bill is still created in its own atomic transaction.
     */
    suspend fun generateBills(requests: List<BillRequest>): BulkGenerationResult {
        val created = mutableListOf<UUID>()
        val skipped = mutableListOf<String>()
        val failed = mutableListOf<String>()
        requests.forEach { request ->
            try {
                created += generateBill(request)
            } catch (e: ConflictException) {
                skipped += e.message ?: "Duplicate bill skipped"
            } catch (e: Exception) {
                failed += e.message ?: "Bill generation failed"
            }
        }
        return BulkGenerationResult(created, skipped, failed)
    }

    /**
     * Inserts a payment and updates the bill's paid total/status atomically. Overpayment,
     * zero payments and payments against a settled bill are rejected.
     */
    suspend fun recordPayment(
        billId: UUID,
        amountPaise: Long,
        paidAt: LocalDate,
        method: PaymentMethod,
        reference: String,
        notes: String,
    ): PaymentEntity = try {
        provider.database.withTransaction {
            val bill = billDao.getById(billId) ?: throw NotFoundException("Bill not found")
            BillingCalculator.validatePayment(bill.totalPaise, bill.paidPaise, amountPaise)
            val settings = settingsDao.get() ?: SettingsEntity()
            val tenant = tenantDao.getById(bill.tenantId)
                ?: throw NotFoundException("Tenant not found")

            val payment = PaymentEntity(
                receiptNumber = receiptNumber(settings.receiptPrefix, paidAt, tenant.id),
                billId = bill.id,
                tenantId = bill.tenantId,
                amountPaise = amountPaise,
                paidAt = paidAt,
                method = method,
                reference = reference.trim(),
                notes = notes.trim(),
            )
            paymentDao.insert(payment)

            val newPaid = bill.paidPaise + amountPaise
            billDao.updatePaymentState(
                billId = bill.id,
                paidPaise = newPaid,
                status = BillingCalculator.statusFor(bill.totalPaise, newPaid),
                updatedAt = Instant.now(),
            )
            payment
        }
    } catch (e: SQLiteConstraintException) {
        throw ConflictException("Could not record the payment because of a receipt number collision. Please retry.")
    }

    private fun billNumber(prefix: String, month: String, roomNumber: String, tenancyId: UUID): String {
        val cleanPrefix = prefix.trim().uppercase(Locale.ENGLISH)
            .filter { it.isLetterOrDigit() || it == '-' }
            .ifBlank { "INV" }
        val cleanRoom = roomNumber.uppercase(Locale.ENGLISH).filter { it.isLetterOrDigit() }
            .ifBlank { "ROOM" }
        val suffix = tenancyId.toString().replace("-", "").take(4).uppercase(Locale.ENGLISH)
        return "$cleanPrefix-${month.replace("-", "")}-$cleanRoom-$suffix"
    }

    private fun receiptNumber(prefix: String, paidAt: LocalDate, tenantId: UUID): String {
        val cleanPrefix = prefix.trim().uppercase(Locale.ENGLISH)
            .filter { it.isLetterOrDigit() || it == '-' }
            .ifBlank { "RCP" }
        val suffix = tenantId.toString().replace("-", "").take(4).uppercase(Locale.ENGLISH)
        return "$cleanPrefix-${paidAt.toString().replace("-", "")}-$suffix-${UUID.randomUUID().toString().take(4).uppercase(Locale.ENGLISH)}"
    }
}
