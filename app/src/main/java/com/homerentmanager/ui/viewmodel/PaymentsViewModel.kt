package com.homerentmanager.ui.viewmodel

import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.homerentmanager.AppContainer
import com.homerentmanager.data.entity.RoomEntity
import com.homerentmanager.data.entity.SettingsEntity
import com.homerentmanager.data.model.PaymentWithDetails
import com.homerentmanager.util.monthOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.util.UUID

data class PaymentsUiState(
    val loading: Boolean = true,
    val payments: List<PaymentWithDetails> = emptyList(),
    val rooms: Map<UUID, RoomEntity> = emptyMap(),
    val settings: SettingsEntity = SettingsEntity(),
    val monthFilter: String? = null,
    val totalPaise: Long = 0,
)

class PaymentsViewModel(private val container: AppContainer) : HrmViewModel() {
    private val monthFilter = MutableStateFlow<String?>(null)

    val state = combine(
        container.billingRepository.observeAllPayments(),
        container.roomRepository.observeAll(),
        container.settingsRepository.observe(),
        monthFilter,
    ) { payments, rooms, settings, filter ->
        val filtered = if (filter == null) payments else payments.filter {
            monthOf(it.payment.paidAt) == filter
        }
        PaymentsUiState(
            loading = false,
            payments = filtered,
            rooms = rooms.associateBy { it.id },
            settings = settings ?: SettingsEntity(),
            monthFilter = filter,
            totalPaise = filtered.sumOf { it.payment.amountPaise },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaymentsUiState())

    fun setMonthFilter(month: String?) {
        monthFilter.value = month
    }

    suspend fun buildReceiptFile(paymentId: UUID): File? {
        return try {
            val payment = container.billingRepository.getPaymentWithDetails(paymentId)
                ?: error("Payment not found")
            val room = container.roomRepository.get(payment.bill.roomId)
                ?: error("Room not found")
            val settings = container.settingsRepository.get()
            val billPayments = container.billingRepository.getPaymentsForBill(payment.bill.id)
            container.pdfService.createReceiptFile(
                settings = settings,
                bill = payment.bill,
                tenant = payment.tenant,
                roomNumber = room.roomNumber,
                payment = payment.payment,
                billPayments = billPayments,
            )
        } catch (e: Exception) {
            notify(friendlyMessage(e))
            null
        }
    }

    fun saveReceipt(uri: Uri, paymentId: UUID) = runAction {
        val file = buildReceiptFile(paymentId) ?: return@runAction
        container.pdfService.saveToUri(file, uri)
        notify("Receipt PDF saved")
    }
}
