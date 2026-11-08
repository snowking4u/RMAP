package com.homerentmanager.util

import com.homerentmanager.data.entity.BillStatus
import com.homerentmanager.data.entity.IdType
import com.homerentmanager.data.entity.PaymentMethod
import com.homerentmanager.data.entity.TenancyStatus

fun BillStatus.label(): String = when (this) {
    BillStatus.UNPAID -> "Unpaid"
    BillStatus.PARTIAL -> "Partially paid"
    BillStatus.PAID -> "Paid"
}

fun TenancyStatus.label(): String = when (this) {
    TenancyStatus.ACTIVE -> "Active"
    TenancyStatus.ENDED -> "Ended"
}

fun PaymentMethod.label(): String = when (this) {
    PaymentMethod.CASH -> "Cash"
    PaymentMethod.UPI -> "UPI"
    PaymentMethod.BANK_TRANSFER -> "Bank transfer"
    PaymentMethod.CHEQUE -> "Cheque"
    PaymentMethod.CARD -> "Card"
    PaymentMethod.OTHER -> "Other"
}

fun IdType.label(): String = when (this) {
    IdType.NONE -> "Not provided"
    IdType.AADHAAR -> "Aadhaar"
    IdType.PAN -> "PAN"
    IdType.DRIVING_LICENSE -> "Driving licence"
    IdType.PASSPORT -> "Passport"
    IdType.VOTER_ID -> "Voter ID"
    IdType.OTHER -> "Other"
}

fun String.toIdType(): IdType =
    IdType.entries.firstOrNull { it.label() == this } ?: IdType.NONE

fun String.toPaymentMethod(): PaymentMethod =
    PaymentMethod.entries.firstOrNull { it.label() == this } ?: PaymentMethod.CASH
