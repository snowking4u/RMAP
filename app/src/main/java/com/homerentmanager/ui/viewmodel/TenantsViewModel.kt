package com.homerentmanager.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.homerentmanager.AppContainer
import com.homerentmanager.data.model.BillWithDetails
import com.homerentmanager.data.model.RoomOccupancy
import com.homerentmanager.data.model.TenancyWithDetails
import com.homerentmanager.data.model.TenantSummary
import com.homerentmanager.data.repository.TenantRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.util.UUID

data class TenantsUiState(
    val loading: Boolean = true,
    val tenants: List<TenantSummary> = emptyList(),
    val vacantRooms: List<RoomOccupancy> = emptyList(),
)

class TenantsViewModel(private val container: AppContainer) : HrmViewModel() {
    val state = combine(
        container.tenantRepository.observeSummaries(),
        container.roomRepository.observeOccupancy(),
    ) { tenants, rooms ->
        TenantsUiState(
            loading = false,
            tenants = tenants,
            vacantRooms = rooms.filter { it.activeTenancyId == null },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TenantsUiState())

    fun createTenant(draft: TenantRepository.TenantDraft, tenancy: TenantRepository.NewTenancy?) =
        runAction {
            container.tenantRepository.createTenant(draft, tenancy)
            notify("Tenant saved")
        }

    fun updateTenant(tenantId: UUID, draft: TenantRepository.TenantDraft) = runAction {
        container.tenantRepository.updateTenant(tenantId, draft)
        notify("Tenant updated")
    }
}

data class TenantDetailUiState(
    val loading: Boolean = true,
    val summary: TenantSummary? = null,
    val tenancies: List<TenancyWithDetails> = emptyList(),
    val bills: List<BillWithDetails> = emptyList(),
    val vacantRooms: List<RoomOccupancy> = emptyList(),
)

class TenantDetailViewModel(
    private val container: AppContainer,
    private val tenantId: UUID,
) : HrmViewModel() {
    val state = combine(
        container.tenantRepository.observeSummary(tenantId),
        container.tenantRepository.observeTenancies(tenantId),
        container.billingRepository.observeForTenant(tenantId),
        container.roomRepository.observeOccupancy(),
    ) { summary, tenancies, bills, rooms ->
        TenantDetailUiState(
            loading = false,
            summary = summary,
            tenancies = tenancies,
            bills = bills,
            vacantRooms = rooms.filter { it.activeTenancyId == null },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TenantDetailUiState())

    fun updateTenant(draft: TenantRepository.TenantDraft) = runAction {
        container.tenantRepository.updateTenant(tenantId, draft)
        notify("Tenant updated")
    }

    fun startTenancy(
        roomId: UUID,
        rentPaise: Long,
        depositPaise: Long,
        joinDate: LocalDate,
        notes: String,
    ) = runAction {
        container.tenantRepository.startTenancy(
            tenantId,
            TenantRepository.NewTenancy(roomId, rentPaise, depositPaise, joinDate, notes),
        )
        notify("Tenancy started")
    }

    fun endTenancy(tenancyId: UUID, leaveDate: LocalDate) = runAction {
        container.tenantRepository.endTenancy(tenancyId, leaveDate)
        notify("Tenancy ended. Bills remain linked to this tenancy.")
    }
}
