package com.homerentmanager.domain

import com.homerentmanager.data.entity.BillEntity
import com.homerentmanager.data.entity.MeterReadingEntity
import com.homerentmanager.data.entity.PaymentEntity
import com.homerentmanager.data.entity.TenantEntity

/**
 * Immutable snapshot of a single payment receipt.
 *
 * The receipt is deliberately built from the values stored on the bill and on the linked
 * meter reading. It never reads the room's current electricity rate, so re-printing an old
 * receipt always reproduces the original charge. Payment totals are reconstructed from the
 * payment history up to and including the displayed payment, which means an old receipt
 * keeps showing the balance that existed on the day it was issued.
 */
data class ReceiptModel(
    val bill: BillEntity,
    val tenant: TenantEntity,
    val roomNumber: String,
    val payment: PaymentEntity,
    val previousReading: Double?,
    val currentReading: Double?,
    val rentPaise: Long,
    val electricityUnits: Double,
    val electricityRate: Double,
    val electricityAmountPaise: Long,
    val additionalChargesPaise: Long,
    val discountPaise: Long,
    val billTotalPaise: Long,
    val previousPaidPaise: Long,
    val currentPaymentPaise: Long,
    val cumulativePaidPaise: Long,
    val outstandingPaise: Long,
    val paymentsOnBill: List<PaymentEntity>,
) {
    val hasElectricity: Boolean
        get() = electricityAmountPaise != 0L || electricityUnits != 0.0 ||
            previousReading != null || currentReading != null

    val hasReading: Boolean
        get() = previousReading != null && currentReading != null
}

object ReceiptBuilder {
    /**
     * Builds a receipt snapshot. [billPayments] may be in any order; it is sorted by payment
     * date and creation time so historical cumulative totals are deterministic.
     */
    fun build(
        bill: BillEntity,
        tenant: TenantEntity,
        roomNumber: String,
        payment: PaymentEntity,
        reading: MeterReadingEntity?,
        billPayments: List<PaymentEntity>,
    ): ReceiptModel {
        val ordered = billPayments.sortedWith(
            compareBy<PaymentEntity>({ it.paidAt }, { it.createdAt }, { it.id }),
        )
        val index = ordered.indexOfFirst { it.id == payment.id }
        val paymentsUpTo = when {
            index >= 0 -> ordered.take(index + 1)
            else -> (ordered.filter {
                it.paidAt.isBefore(payment.paidAt) ||
                    (it.paidAt == payment.paidAt && !it.createdAt.isAfter(payment.createdAt))
            } + payment).distinctBy { it.id }
                .sortedWith(compareBy<PaymentEntity>({ it.paidAt }, { it.createdAt }, { it.id }))
        }
        val cumulativePaid = paymentsUpTo.sumOf { it.amountPaise }
        val currentPayment = paymentsUpTo.firstOrNull { it.id == payment.id } ?: payment
        val previousPaid = (cumulativePaid - currentPayment.amountPaise).coerceAtLeast(0)
        val outstanding = (bill.totalPaise - cumulativePaid).coerceAtLeast(0)

        return ReceiptModel(
            bill = bill,
            tenant = tenant,
            roomNumber = roomNumber,
            payment = payment,
            previousReading = reading?.previousReading,
            currentReading = reading?.currentReading,
            rentPaise = bill.rentPaise,
            electricityUnits = bill.electricityUnits,
            electricityRate = bill.electricityRate,
            electricityAmountPaise = bill.electricityAmountPaise,
            additionalChargesPaise = bill.additionalChargesPaise,
            discountPaise = bill.discountPaise,
            billTotalPaise = bill.totalPaise,
            previousPaidPaise = previousPaid,
            currentPaymentPaise = currentPayment.amountPaise,
            cumulativePaidPaise = cumulativePaid,
            outstandingPaise = outstanding,
            paymentsOnBill = ordered,
        )
    }
}
