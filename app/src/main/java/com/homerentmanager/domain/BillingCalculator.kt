package com.homerentmanager.domain

import com.homerentmanager.data.entity.BillStatus
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pure billing rules shared by the UI, repositories and PDFs. Amounts are in paise
 * (1 rupee = 100 paise) to avoid floating point drift in financial records.
 */
object BillingCalculator {
    fun units(previousReading: Double, currentReading: Double): Double {
        require(previousReading >= 0) { "Previous reading cannot be negative" }
        require(currentReading >= previousReading) {
            "Current reading must be greater than or equal to the previous reading"
        }
        return round2(currentReading - previousReading)
    }

    fun electricityAmountPaise(units: Double, ratePerUnit: Double): Long {
        require(units >= 0) { "Units cannot be negative" }
        require(ratePerUnit >= 0) { "Rate cannot be negative" }
        return BigDecimal.valueOf(units)
            .multiply(BigDecimal.valueOf(ratePerUnit))
            .multiply(BigDecimal.valueOf(100))
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
    }

    fun billTotalPaise(
        rentPaise: Long,
        electricityPaise: Long,
        additionalChargesPaise: Long,
        discountPaise: Long,
    ): Long {
        require(rentPaise >= 0) { "Rent cannot be negative" }
        require(electricityPaise >= 0) { "Electricity charge cannot be negative" }
        require(additionalChargesPaise >= 0) { "Additional charges cannot be negative" }
        require(discountPaise >= 0) { "Discount cannot be negative" }
        val gross = rentPaise + electricityPaise + additionalChargesPaise
        require(discountPaise <= gross) { "Discount cannot exceed the bill amount" }
        return gross - discountPaise
    }

    fun statusFor(totalPaise: Long, paidPaise: Long): BillStatus = when {
        paidPaise <= 0L -> BillStatus.UNPAID
        paidPaise >= totalPaise -> BillStatus.PAID
        else -> BillStatus.PARTIAL
    }

    fun outstandingPaise(totalPaise: Long, paidPaise: Long): Long =
        (totalPaise - paidPaise).coerceAtLeast(0L)

    fun validatePayment(totalPaise: Long, paidPaise: Long, amountPaise: Long) {
        require(amountPaise > 0) { "Payment amount must be greater than zero" }
        val outstanding = outstandingPaise(totalPaise, paidPaise)
        require(outstanding > 0) { "This bill is already fully paid" }
        require(amountPaise <= outstanding) {
            "Payment cannot exceed the outstanding balance"
        }
    }

    fun round2(value: Double): Double =
        BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()
}
