package com.homerentmanager.ui.viewmodel

import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.homerentmanager.AppContainer
import com.homerentmanager.backup.BackupManager
import com.homerentmanager.data.entity.BillStatus
import com.homerentmanager.data.entity.PaymentMethod
import com.homerentmanager.data.entity.RoomEntity
import com.homerentmanager.data.entity.SettingsEntity
import com.homerentmanager.data.model.BillWithDetails
import com.homerentmanager.data.model.PaymentWithDetails
import com.homerentmanager.data.model.TenancyWithDetails
import com.homerentmanager.util.monthOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class ReportRoomRow(
    val roomNumber: String,
    val floor: String,
    val tenantName: String,
    val occupied: Boolean,
    val billedPaise: Long,
    val collectedPaise: Long,
    val outstandingPaise: Long,
)

data class ReportMethodRow(
    val method: PaymentMethod,
    val count: Int,
    val amountPaise: Long,
)

data class AgingBuckets(
    val notDuePaise: Long,
    val days1To30Paise: Long,
    val days31To60Paise: Long,
    val days61PlusPaise: Long,
) {
    val totalPaise: Long get() = notDuePaise + days1To30Paise + days31To60Paise + days61PlusPaise
}

data class ReportsUiState(
    val loading: Boolean = true,
    val month: String = LocalDate.now().toString().take(7),
    val settings: SettingsEntity = SettingsEntity(),
    val totalRooms: Int = 7,
    val occupiedRooms: Int = 0,
    val activeDepositsPaise: Long = 0,
    val billedPaise: Long = 0,
    val collectedPaise: Long = 0,
    val outstandingMonthPaise: Long = 0,
    val outstandingAllPaise: Long = 0,
    val roomRows: List<ReportRoomRow> = emptyList(),
    val methodRows: List<ReportMethodRow> = emptyList(),
    val aging: AgingBuckets = AgingBuckets(0, 0, 0, 0),
)

class ReportsViewModel(private val container: AppContainer) : HrmViewModel() {
    private val month = MutableStateFlow(
        LocalDate.now().let { "%04d-%02d".format(it.year, it.monthValue) },
    )

    private data class Base(
        val bills: List<BillWithDetails>,
        val payments: List<PaymentWithDetails>,
        val rooms: List<RoomEntity>,
        val activeTenancies: List<TenancyWithDetails>,
        val settings: SettingsEntity,
    )

    private val base = combine(
        container.billingRepository.observeAll(),
        container.billingRepository.observeAllPayments(),
        container.roomRepository.observeAll(),
        container.tenancyRepository.observeActive(),
        container.settingsRepository.observe(),
    ) { bills, payments, rooms, activeTenancies, settings ->
        Base(bills, payments, rooms, activeTenancies, settings ?: SettingsEntity())
    }

    val state = combine(base, month) { base, selectedMonth ->
        val today = LocalDate.now()
        val monthBills = base.bills.filter { it.bill.billingMonth == selectedMonth }
        val monthPayments = base.payments.filter { monthOf(it.payment.paidAt) == selectedMonth }
        val billRoom = base.bills.associate { it.bill.id to it.bill.roomId }
        val outstandingBills = base.bills.filter { it.bill.status != BillStatus.PAID }

        val activeByRoom = base.activeTenancies.associateBy { it.tenancy.roomId }
        val roomRows = base.rooms.map { room ->
            val occupancy = activeByRoom[room.id]
            val billed = monthBills.filter { it.bill.roomId == room.id }.sumOf { it.bill.totalPaise }
            val collected = monthPayments.filter { billRoom[it.payment.billId] == room.id }
                .sumOf { it.payment.amountPaise }
            val outstanding = monthBills.filter { it.bill.roomId == room.id }.sumOf {
                (it.bill.totalPaise - it.bill.paidPaise).coerceAtLeast(0)
            }
            ReportRoomRow(
                roomNumber = room.roomNumber,
                floor = room.floor,
                tenantName = occupancy?.tenant?.name.orEmpty(),
                occupied = occupancy != null,
                billedPaise = billed,
                collectedPaise = collected,
                outstandingPaise = outstanding,
            )
        }

        val aging = AgingBuckets(
            notDuePaise = outstandingBills.filter { it.bill.dueDate >= today }.sumOf {
                (it.bill.totalPaise - it.bill.paidPaise).coerceAtLeast(0)
            },
            days1To30Paise = outstandingBills.filter {
                val days = ChronoUnit.DAYS.between(it.bill.dueDate, today)
                days in 1..30
            }.sumOf { (it.bill.totalPaise - it.bill.paidPaise).coerceAtLeast(0) },
            days31To60Paise = outstandingBills.filter {
                val days = ChronoUnit.DAYS.between(it.bill.dueDate, today)
                days in 31..60
            }.sumOf { (it.bill.totalPaise - it.bill.paidPaise).coerceAtLeast(0) },
            days61PlusPaise = outstandingBills.filter {
                ChronoUnit.DAYS.between(it.bill.dueDate, today) > 60
            }.sumOf { (it.bill.totalPaise - it.bill.paidPaise).coerceAtLeast(0) },
        )

        val methods = PaymentMethod.entries.mapNotNull { method ->
            val matching = monthPayments.filter { it.payment.method == method }
            if (matching.isEmpty()) null else ReportMethodRow(
                method = method,
                count = matching.size,
                amountPaise = matching.sumOf { it.payment.amountPaise },
            )
        }.sortedByDescending { it.amountPaise }

        ReportsUiState(
            loading = false,
            month = selectedMonth,
            settings = base.settings,
            totalRooms = base.rooms.size,
            occupiedRooms = base.activeTenancies.size,
            activeDepositsPaise = base.activeTenancies.sumOf { it.tenancy.securityDepositPaise },
            billedPaise = monthBills.sumOf { it.bill.totalPaise },
            collectedPaise = monthPayments.sumOf { it.payment.amountPaise },
            outstandingMonthPaise = monthBills.sumOf {
                (it.bill.totalPaise - it.bill.paidPaise).coerceAtLeast(0)
            },
            outstandingAllPaise = base.bills.sumOf {
                (it.bill.totalPaise - it.bill.paidPaise).coerceAtLeast(0)
            },
            roomRows = roomRows,
            methodRows = methods,
            aging = aging,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState())

    fun setMonth(value: String) {
        month.value = value
    }

    fun shiftMonth(delta: Long) {
        month.value = com.homerentmanager.util.shiftMonth(month.value, delta)
    }

    fun exportCsv(uri: Uri, kind: BackupManager.CsvKind) = runAction {
        val rows = container.backupManager.exportCsv(uri, kind)
        notify("Exported $rows rows to CSV")
    }
}
