package com.homerentmanager.domain

import com.homerentmanager.data.entity.BillStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BillingCalculatorTest {
    @Test
    fun `units are the difference rounded to two decimals`() {
        assertEquals(42.35, BillingCalculator.units(100.0, 142.345), 0.0001)
        assertEquals(0.0, BillingCalculator.units(20.0, 20.0), 0.0001)
    }

    @Test
    fun `units reject a lower current reading`() {
        assertThrows(IllegalArgumentException::class.java) {
            BillingCalculator.units(50.0, 49.9)
        }
    }

    @Test
    fun `electricity amount uses paise and rounds half up`() {
        assertEquals(33600L, BillingCalculator.electricityAmountPaise(42.0, 8.0))
        assertEquals(12345L, BillingCalculator.electricityAmountPaise(12.345, 10.0))
        assertEquals(1L, BillingCalculator.electricityAmountPaise(0.005, 1.0))
        assertEquals(0L, BillingCalculator.electricityAmountPaise(0.0, 9.5))
    }

    @Test
    fun `bill total adds charges and subtracts discount`() {
        assertEquals(1_250_000L, BillingCalculator.billTotalPaise(800_000, 450_000, 0, 0))
        assertEquals(1_240_000L, BillingCalculator.billTotalPaise(800_000, 450_000, 40_000, 50_000))
    }

    @Test
    fun `discount cannot exceed the gross amount`() {
        assertThrows(IllegalArgumentException::class.java) {
            BillingCalculator.billTotalPaise(100, 0, 0, 101)
        }
    }

    @Test
    fun `status reflects partial and full payments`() {
        assertEquals(BillStatus.UNPAID, BillingCalculator.statusFor(1000, 0))
        assertEquals(BillStatus.PARTIAL, BillingCalculator.statusFor(1000, 1))
        assertEquals(BillStatus.PARTIAL, BillingCalculator.statusFor(1000, 999))
        assertEquals(BillStatus.PAID, BillingCalculator.statusFor(1000, 1000))
    }

    @Test
    fun `payment validation rejects overpayment and settled bills`() {
        assertThrows(IllegalArgumentException::class.java) {
            BillingCalculator.validatePayment(1000, 900, 101)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BillingCalculator.validatePayment(1000, 1000, 10)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BillingCalculator.validatePayment(1000, 0, 0)
        }
        BillingCalculator.validatePayment(1000, 500, 500)
    }
}
