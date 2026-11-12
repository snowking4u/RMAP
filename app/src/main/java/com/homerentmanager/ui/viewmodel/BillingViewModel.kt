package com.homerentmanager.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.homerentmanager.AppContainer
import com.homerentmanager.data.entity.MeterReadingEntity
import com.homerentmanager.data.entity.SettingsEntity
import com.homerentmanager.data.model.BillWithDetails
import com.homerentmanager.data.model.TenancyWithDetails
import com.homerentmanager.data.repository.BillingRepository
import com.homerentmanager.util.currentMonth
import com.homerentmanager.util.shiftMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class BillingUiState(
    val loading: Boolean = true,
    val month: String = currentMonth(),
    val bills: List<BillWithDetails> = emptyList(),
    val activeTenancies: List<TenancyWithDetails> = emptyList(),
    val settings: SettingsEntity = SettingsEntity(),
    val latestReadings: Map<UUID, MeterReadingEntity> = emptyMap(),
    val billedPaise: Long = 0,
    val collectedPaise: Long = 0,
    val outstandingPaise: Long = 0,
    val generating: Boolean = false,
    val generationVersion: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class BillingViewModel(private val container: AppContainer) : HrmViewModel() {
    private val month = MutableStateFlow(currentMonth())
    private val generating = MutableStateFlow(false)
    private val generationVersion = MutableStateFlow(0)

    private val bills = month.flatMapLatest { container.billingRepository.observeForMonth(it) }
    private val collected = month.flatMapLatest {
        container.billingRepository.observeCollectedForMonth(it)
    }

    private val core = combine(
        bills,
        collected,
        container.tenancyRepository.observeActive(),
        container.settingsRepository.observe(),
        container.electricityRepository.observeLatestPerRoom(),
    ) { bills, collectedPaise, active, settings, readings ->
        BillingUiState(
            loading = false,
            month = month.value,
            bills = bills,
            activeTenancies = active,
            settings = settings ?: SettingsEntity(),
            latestReadings = readings.associateBy { it.roomId },
            billedPaise = bills.sumOf { it.bill.totalPaise },
            collectedPaise = collectedPaise,
            outstandingPaise = bills.sumOf {
                (it.bill.totalPaise - it.bill.paidPaise).coerceAtLeast(0)
            },
        )
    }

    val state = combine(core, generating) { ui, isGenerating ->
        ui.copy(generating = isGenerating)
    }.combine(generationVersion) { ui, version ->
        ui.copy(generationVersion = version)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BillingUiState())

    fun setMonth(value: String) {
        month.value = value
    }

    fun shiftMonth(delta: Long) {
        month.value = shiftMonth(month.value, delta)
    }

    fun today() {
        month.value = currentMonth()
    }

    fun generate(
        requests: List<BillingRepository.BillRequest>,
        onResult: (Boolean) -> Unit = {},
    ) {
        if (requests.isEmpty()) {
            notify("Enter at least one bill to generate")
            onResult(false)
            return
        }
        if (generating.value) return
        viewModelScope.launch {
            generating.value = true
            try {
                val result = container.billingRepository.generateBills(requests)
                val parts = buildList {
                    if (result.createdBillIds.isNotEmpty()) add("${result.createdBillIds.size} bill(s) created")
                    if (result.skipped.isNotEmpty()) add("${result.skipped.size} duplicate(s) skipped")
                    if (result.failures.isNotEmpty()) add("${result.failures.size} failed")
                }
                notify(parts.joinToString(". ").ifBlank { "No bills were created" })
                if (result.createdBillIds.isNotEmpty()) {
                    generationVersion.value = generationVersion.value + 1
                }
                onResult(result.createdBillIds.isNotEmpty())
            } catch (e: Exception) {
                notify(friendlyMessage(e))
                onResult(false)
            } finally {
                generating.value = false
            }
        }
    }
}
