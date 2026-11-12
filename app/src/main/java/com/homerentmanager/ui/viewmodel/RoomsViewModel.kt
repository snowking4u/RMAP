package com.homerentmanager.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.homerentmanager.AppContainer
import com.homerentmanager.data.entity.SettingsEntity
import com.homerentmanager.data.model.BillWithDetails
import com.homerentmanager.data.entity.MeterReadingEntity
import com.homerentmanager.data.model.RoomOccupancy
import com.homerentmanager.data.model.TenancyWithDetails
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

data class RoomsUiState(
    val loading: Boolean = true,
    val rooms: List<RoomOccupancy> = emptyList(),
)

class RoomsViewModel(private val container: AppContainer) : HrmViewModel() {
    val state = container.roomRepository.observeOccupancy()
        .map { RoomsUiState(loading = false, rooms = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoomsUiState())

    fun addRoom(
        number: String,
        floor: String,
        rentPaise: Long,
        rate: Double,
        notes: String,
        onResult: (Boolean) -> Unit = {},
    ) = runActionWithResult(onResult) {
        container.roomRepository.addRoom(number, floor, rentPaise, rate, notes)
        notify("Room added")
    }

    fun updateRoom(
        roomId: UUID,
        number: String,
        floor: String,
        rentPaise: Long,
        rate: Double,
        notes: String,
        onResult: (Boolean) -> Unit = {},
    ) = runActionWithResult(onResult) {
        container.roomRepository.updateRoom(roomId, number, floor, rentPaise, rate, notes)
        notify("Room updated")
    }

    fun setRoomActive(roomId: UUID, active: Boolean, onResult: (Boolean) -> Unit = {}) =
        runActionWithResult(onResult) {
            container.roomRepository.setRoomActive(roomId, active)
            notify(if (active) "Room restored" else "Room archived. Its history is preserved.")
        }
}

data class RoomDetailUiState(
    val loading: Boolean = true,
    val occupancy: RoomOccupancy? = null,
    val tenancies: List<TenancyWithDetails> = emptyList(),
    val readings: List<MeterReadingEntity> = emptyList(),
    val bills: List<BillWithDetails> = emptyList(),
    val settings: SettingsEntity = SettingsEntity(),
)

class RoomDetailViewModel(
    private val container: AppContainer,
    private val roomId: UUID,
) : HrmViewModel() {
    val state = combine(
        container.roomRepository.observeRoom(roomId),
        container.tenancyRepository.observeForRoom(roomId),
        container.electricityRepository.observeForRoom(roomId),
        container.billingRepository.observeForRoom(roomId),
        container.settingsRepository.observe(),
    ) { occupancy, tenancies, readings, bills, settings ->
        RoomDetailUiState(
            loading = false,
            occupancy = occupancy,
            tenancies = tenancies,
            readings = readings,
            bills = bills,
            settings = settings ?: SettingsEntity(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoomDetailUiState())

    fun updateRoom(
        number: String,
        floor: String,
        rentPaise: Long,
        rate: Double,
        notes: String,
        onResult: (Boolean) -> Unit = {},
    ) = runActionWithResult(onResult) {
        container.roomRepository.updateRoom(roomId, number, floor, rentPaise, rate, notes)
        notify("Room updated")
    }

    fun endTenancy(tenancyId: UUID, leaveDate: LocalDate, onResult: (Boolean) -> Unit = {}) =
        runActionWithResult(onResult) {
            container.tenantRepository.endTenancy(tenancyId, leaveDate)
            notify("Tenancy ended. Historical bills are preserved.")
        }
}
