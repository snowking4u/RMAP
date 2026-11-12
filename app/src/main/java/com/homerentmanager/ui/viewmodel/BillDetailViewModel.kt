package com.homerentmanager.ui.viewmodel

import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.homerentmanager.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.homerentmanager.data.entity.PaymentMethod
import com.homerentmanager.data.entity.SettingsEntity
import com.homerentmanager.data.model.BillWithDetails
import com.homerentmanager.data.model.PaymentWithDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.time.LocalDate
import java.util.UUID

data class BillDetailUiState(
    val loading: Boolean = true,
    val detail: BillWithDetails? = null,
    val payments: List<PaymentWithDetails> = emptyList(),
    val settings: SettingsEntity = SettingsEntity(),
    val lastPaymentId: UUID? = null,
)

class BillDetailViewModel(
    private val container: AppContainer,
    private val billId: UUID,
) : HrmViewModel() {
    private val lastPaymentId = MutableStateFlow<UUID?>(null)

    val state = combine(
        container.billingRepository.observeById(billId),
        container.billingRepository.observePaymentsForBill(billId),
        container.settingsRepository.observe(),
        lastPaymentId,
    ) { detail, payments, settings, lastPayment ->
        BillDetailUiState(
            loading = false,
            detail = detail,
            payments = payments,
            settings = settings ?: SettingsEntity(),
            lastPaymentId = lastPayment,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BillDetailUiState())

    fun recordPayment(
        amountPaise: Long,
        paidAt: LocalDate,
        method: PaymentMethod,
        reference: String,
        notes: String,
        onResult: (Boolean) -> Unit = {},
    ) = runActionWithResult(onResult) {
        val payment = container.billingRepository.recordPayment(
            billId = billId,
            amountPaise = amountPaise,
            paidAt = paidAt,
            method = method,
            reference = reference,
            notes = notes,
        )
        lastPaymentId.value = payment.id
        notify("Payment recorded. Receipt ${payment.receiptNumber}")
    }

    fun deleteBill(onResult: (Boolean) -> Unit = {}) = runActionWithResult(onResult) {
        container.billingRepository.deleteBill(billId)
        notify("Bill deleted. Its meter reading was reset for regeneration.")
    }

    fun regenerateBill(onResult: (UUID?) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val newBillId = container.billingRepository.regenerateBill(billId)
                notify("Bill regenerated with the original meter reading.")
                onResult(newBillId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notify(friendlyMessage(e))
                onResult(null)
            }
        }
    }

    fun clearLastPayment() {
        lastPaymentId.value = null
    }

    suspend fun buildInvoiceFile(): File? {
        return try {
            val detail = container.billingRepository.observeById(billId).first()
                ?: error("Bill not found")
            val payments = container.billingRepository.observePaymentsForBill(billId).first()
                .map { it.payment }
            val reading = container.billingRepository.getReadingForBill(billId)
            val settings = container.settingsRepository.get()
            container.pdfService.createInvoiceFile(settings, detail, payments, reading)
        } catch (e: Exception) {
            notify(friendlyMessage(e))
            null
        }
    }

    suspend fun buildReceiptFile(paymentId: UUID): File? {
        return try {
            val payment = container.billingRepository.getPaymentWithDetails(paymentId)
                ?: error("Payment not found")
            val detail = container.billingRepository.observeById(billId).first()
                ?: error("Bill not found")
            val allPayments = container.billingRepository.getPaymentsForBill(billId)
            val reading = container.billingRepository.getReadingForBill(billId)
            val settings = container.settingsRepository.get()
            container.pdfService.createReceiptFile(
                settings = settings,
                bill = detail.bill,
                tenant = detail.tenant,
                roomNumber = detail.room.roomNumber,
                payment = payment.payment,
                billPayments = allPayments,
                reading = reading,
            )
        } catch (e: Exception) {
            notify(friendlyMessage(e))
            null
        }
    }

    fun saveInvoice(uri: Uri) = runAction {
        val file = buildInvoiceFile() ?: return@runAction
        container.pdfService.saveToUri(file, uri)
        notify("Invoice PDF saved")
    }

    fun saveReceipt(uri: Uri, paymentId: UUID) = runAction {
        val file = buildReceiptFile(paymentId) ?: return@runAction
        container.pdfService.saveToUri(file, uri)
        notify("Receipt PDF saved")
    }
}
