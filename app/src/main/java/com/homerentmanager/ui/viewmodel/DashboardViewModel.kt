package com.homerentmanager.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.homerentmanager.AppContainer
import com.homerentmanager.data.entity.SettingsEntity
import com.homerentmanager.data.model.BillWithDetails
import com.homerentmanager.data.model.PaymentWithDetails
import com.homerentmanager.util.currentMonth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class DashboardUiState(
    val loading: Boolean = true,
    val month: String = currentMonth(),
    val propertyName: String = "Home Rentals",
    val ownerName: String = "",
    val billedPaise: Long = 0,
    val collectedPaise: Long = 0,
    val outstandingMonthPaise: Long = 0,
    val outstandingTotalPaise: Long = 0,
    val carryForwardPaise: Long = 0,
    val occupiedRooms: Int = 0,
    val totalRooms: Int = 7,
    val activeTenants: Int = 0,
    val recentPayments: List<PaymentWithDetails> = emptyList(),
    val recentBills: List<BillWithDetails> = emptyList(),
    val overdueBills: List<BillWithDetails> = emptyList(),
    val lastBackupAt: java.time.Instant? = null,
    val backupReminderDays: Int = 7,
    val needsBackup: Boolean = false,
)

class DashboardViewModel(container: AppContainer) : HrmViewModel() {
    val state = combine(
        container.roomRepository.observeOccupancy(),
        container.billingRepository.observeAll(),
        container.billingRepository.observeAllPayments(),
        container.settingsRepository.observe(),
        container.tenancyRepository.observeActiveCount(),
    ) { rooms, bills, payments, settings, activeTenants ->
        val month = currentMonth()
        val today = LocalDate.now()
        val settingsValue = settings ?: SettingsEntity()
        val monthBills = bills.filter { it.bill.billingMonth == month }
        val monthPayments = payments.filter {
            it.payment.paidAt.year == today.year && it.payment.paidAt.monthValue == today.monthValue
        }
        DashboardUiState(
            loading = false,
            month = month,
            propertyName = settingsValue.propertyName.ifBlank { "Home Rentals" },
            ownerName = settingsValue.ownerName,
            billedPaise = monthBills.sumOf { it.bill.totalPaise },
            collectedPaise = monthPayments.sumOf { it.payment.amountPaise },
            outstandingMonthPaise = monthBills.sumOf {
                (it.bill.totalPaise - it.bill.paidPaise).coerceAtLeast(0)
            },
            outstandingTotalPaise = bills.sumOf {
                (it.bill.totalPaise - it.bill.paidPaise).coerceAtLeast(0)
            },
            carryForwardPaise = bills.filter { it.bill.billingMonth < month }.sumOf {
                (it.bill.totalPaise - it.bill.paidPaise).coerceAtLeast(0)
            },
            occupiedRooms = rooms.count { it.activeTenancyId != null },
            totalRooms = rooms.size,
            activeTenants = activeTenants,
            recentPayments = payments.take(5),
            recentBills = monthBills.sortedByDescending { it.bill.createdAt }.take(5),
            overdueBills = bills
                .filter { it.bill.dueDate < today && it.bill.status.name != "PAID" }
                .sortedBy { it.bill.dueDate }
                .take(5),
            lastBackupAt = settingsValue.lastBackupAt,
            backupReminderDays = settingsValue.backupReminderDays,
            needsBackup = settingsValue.lastBackupAt == null ||
                java.time.Duration.between(
                    settingsValue.lastBackupAt,
                    java.time.Instant.now(),
                ).toDays() >= settingsValue.backupReminderDays,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(),
    )
}
