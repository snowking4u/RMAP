package com.homerentmanager.domain

import com.homerentmanager.data.entity.BillEntity
import com.homerentmanager.data.entity.BillStatus
import com.homerentmanager.data.entity.MeterReadingEntity
import com.homerentmanager.data.entity.PaymentEntity
import com.homerentmanager.data.entity.PaymentMethod
import com.homerentmanager.data.entity.TenantEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class ReceiptBuilderTest {
    private val roomId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val tenantId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val tenancyId = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val billId = UUID.fromString("44444444-4444-4444-4444-444444444444")

    private val tenant = TenantEntity(
        id = tenantId,
        name = "Asha Rao",
        mobile = "9876543210",
        address = "12 Lake Road",
    )

    private fun bill(
        rentPaise: Long = 800_000,
        units: Double = 12.5,
        rate: Double = 8.0,
        electricityPaise: Long = 10_000,
        additional: Long = 2_500,
        discount: Long = 500,
        total: Long = 812_000,
        paid: Long = 0,
    ) = BillEntity(
        id = billId,
        billNumber = "INV-202601-1-ABCD",
        roomId = roomId,
        tenantId = tenantId,
        tenancyId = tenancyId,
        billingMonth = "2026-01",
        billDate = LocalDate.of(2026, 1, 1),
        dueDate = LocalDate.of(2026, 1, 5),
        rentPaise = rentPaise,
        electricityUnits = units,
        electricityRate = rate,
        electricityAmountPaise = electricityPaise,
        additionalChargesPaise = additional,
        discountPaise = discount,
        totalPaise = total,
        paidPaise = paid,
        status = when {
            paid <= 0 -> BillStatus.UNPAID
            paid >= total -> BillStatus.PAID
            else -> BillStatus.PARTIAL
        },
    )

    private fun payment(
        id: String,
        amount: Long,
        paidAt: LocalDate,
        createdAt: Instant,
    ) = PaymentEntity(
        id = UUID.fromString(id),
        receiptNumber = "RCP-$id",
        billId = billId,
        tenantId = tenantId,
        amountPaise = amount,
        paidAt = paidAt,
        method = PaymentMethod.UPI,
        reference = "UPI-$id",
        createdAt = createdAt,
    )

    private fun reading(
        previous: Double = 100.0,
        current: Double = 112.5,
        units: Double = 12.5,
        rate: Double = 99.0,
        amount: Long = 1_237_500,
    ) = MeterReadingEntity(
        roomId = roomId,
        tenancyId = tenancyId,
        billId = billId,
        readingDate = LocalDate.of(2026, 1, 1),
        previousReading = previous,
        currentReading = current,
        units = units,
        ratePerUnit = rate,
        amountPaise = amount,
    )

    @Test
    fun `receipt includes the complete electricity breakdown from stored bill values`() {
        val model = ReceiptBuilder.build(
            bill = bill(),
            tenant = tenant,
            roomNumber = "1",
            payment = payment(
                "55555555-5555-5555-5555-555555555555",
                812_000,
                LocalDate.of(2026, 1, 5),
                Instant.parse("2026-01-05T10:00:00Z"),
            ),
            reading = reading(),
            billPayments = emptyList(),
        )

        assertEquals(800_000L, model.rentPaise)
        assertEquals(100.0, model.previousReading!!, 0.0001)
        assertEquals(112.5, model.currentReading!!, 0.0001)
        assertEquals(12.5, model.electricityUnits, 0.0001)
        assertEquals(8.0, model.electricityRate, 0.0001)
        assertEquals(10_000L, model.electricityAmountPaise)
        assertEquals(2_500L, model.additionalChargesPaise)
        assertEquals(500L, model.discountPaise)
        assertEquals(812_000L, model.billTotalPaise)
        assertTrue(model.hasElectricity)
        assertTrue(model.hasReading)
    }

    @Test
    fun `historical receipt totals use payments up to that payment not the current paid total`() {
        val first = payment(
            "55555555-5555-5555-5555-555555555551",
            300_000,
            LocalDate.of(2026, 1, 3),
            Instant.parse("2026-01-03T09:00:00Z"),
        )
        val second = payment(
            "55555555-5555-5555-5555-555555555552",
            512_000,
            LocalDate.of(2026, 1, 9),
            Instant.parse("2026-01-09T09:00:00Z"),
        )
        // The bill is fully paid now, but the first receipt must show the old balance.
        val fullyPaidBill = bill(paid = 812_000)

        val firstReceipt = ReceiptBuilder.build(
            bill = fullyPaidBill,
            tenant = tenant,
            roomNumber = "1",
            payment = first,
            reading = reading(),
            billPayments = listOf(first, second),
        )
        assertEquals(0L, firstReceipt.previousPaidPaise)
        assertEquals(300_000L, firstReceipt.currentPaymentPaise)
        assertEquals(300_000L, firstReceipt.cumulativePaidPaise)
        assertEquals(512_000L, firstReceipt.outstandingPaise)

        val secondReceipt = ReceiptBuilder.build(
            bill = fullyPaidBill,
            tenant = tenant,
            roomNumber = "1",
            payment = second,
            reading = reading(),
            billPayments = listOf(second, first), // deliberately unsorted
        )
        assertEquals(300_000L, secondReceipt.previousPaidPaise)
        assertEquals(512_000L, secondReceipt.currentPaymentPaise)
        assertEquals(812_000L, secondReceipt.cumulativePaidPaise)
        assertEquals(0L, secondReceipt.outstandingPaise)
    }

    @Test
    fun `partial payments never show a negative outstanding balance`() {
        val only = payment(
            "55555555-5555-5555-5555-555555555553",
            500_000,
            LocalDate.of(2026, 1, 4),
            Instant.parse("2026-01-04T09:00:00Z"),
        )
        val model = ReceiptBuilder.build(
            bill = bill(paid = 500_000),
            tenant = tenant,
            roomNumber = "1",
            payment = only,
            reading = reading(),
            billPayments = listOf(only),
        )
        assertEquals(0L, model.previousPaidPaise)
        assertEquals(500_000L, model.cumulativePaidPaise)
        assertEquals(312_000L, model.outstandingPaise)
    }

    @Test
    fun `a later meter rate never changes the historical receipt rate or amount`() {
        val model = ReceiptBuilder.build(
            bill = bill(units = 10.0, rate = 7.5, electricityPaise = 7_500),
            tenant = tenant,
            roomNumber = "1",
            payment = payment(
                "55555555-5555-5555-5555-555555555554",
                500_000,
                LocalDate.of(2026, 1, 4),
                Instant.parse("2026-01-04T09:00:00Z"),
            ),
            reading = reading(units = 10.0, rate = 99.0, amount = 990_000),
            billPayments = emptyList(),
        )
        assertEquals(7.5, model.electricityRate, 0.0001)
        assertEquals(7_500L, model.electricityAmountPaise)
        assertEquals(7_500L, model.bill.electricityAmountPaise)
    }

    @Test
    fun `receipt without a linked reading still shows the stored electricity amount`() {
        val model = ReceiptBuilder.build(
            bill = bill(),
            tenant = tenant,
            roomNumber = "1",
            payment = payment(
                "55555555-5555-5555-5555-555555555555",
                812_000,
                LocalDate.of(2026, 1, 5),
                Instant.parse("2026-01-05T10:00:00Z"),
            ),
            reading = null,
            billPayments = emptyList(),
        )
        assertNull(model.previousReading)
        assertNull(model.currentReading)
        assertTrue(model.hasElectricity)
        assertEquals(10_000L, model.electricityAmountPaise)
    }
}
