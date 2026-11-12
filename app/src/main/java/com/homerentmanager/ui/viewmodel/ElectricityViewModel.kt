package com.homerentmanager.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.homerentmanager.AppContainer
import com.homerentmanager.data.entity.MeterReadingEntity
import com.homerentmanager.data.entity.SettingsEntity
import com.homerentmanager.data.model.RoomOccupancy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.util.UUID

data class ElectricityUiState(
    val loading: Boolean = true,
    val rooms: List<RoomOccupancy> = emptyList(),
    val selectedRoomId: UUID? = null,
    val selectedRoom: RoomOccupancy? = null,
    val readings: List<MeterReadingEntity> = emptyList(),
    val settings: SettingsEntity = SettingsEntity(),
    val defaultRate: Double = 8.0,
    val latestReading: MeterReadingEntity? = null,
    val hasActiveTenancy: Boolean = false,
    val savedAt: Long = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ElectricityViewModel(private val container: AppContainer) : HrmViewModel() {
    private val rooms = container.roomRepository.observeOccupancy()
        .map { occupancy -> occupancy.filter { it.room.isActive } }
    private val selected = MutableStateFlow<UUID?>(null)
    private val savedAt = MutableStateFlow(0L)

    private val effectiveRoomId = combine(rooms, selected) { roomList, chosen ->
        chosen?.takeIf { id -> roomList.any { it.room.id == id } }
            ?: roomList.firstOrNull { it.activeTenancyId != null }?.room?.id
            ?: roomList.firstOrNull()?.room?.id
    }

    private val readings = effectiveRoomId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else container.electricityRepository.observeForRoom(id)
    }

    val state = combine(
        rooms,
        effectiveRoomId,
        readings,
        container.settingsRepository.observe(),
        savedAt,
    ) { roomList, roomId, readingList, settings, saved ->
        val settingsValue = settings ?: SettingsEntity()
        val room = roomList.firstOrNull { it.room.id == roomId }
        ElectricityUiState(
            loading = false,
            rooms = roomList,
            selectedRoomId = roomId,
            selectedRoom = room,
            readings = readingList,
            settings = settingsValue,
            defaultRate = room?.room?.defaultElectricityRate ?: settingsValue.defaultElectricityRate,
            latestReading = readingList.firstOrNull(),
            hasActiveTenancy = room?.activeTenancyId != null,
            savedAt = saved,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ElectricityUiState())

    fun selectRoom(roomId: UUID) {
        selected.value = roomId
    }

    fun addReading(
        roomId: UUID,
        date: LocalDate,
        previous: Double,
        current: Double,
        rate: Double,
        onResult: (Boolean) -> Unit = {},
    ) = runActionWithResult(onResult) {
        container.electricityRepository.addReading(roomId, date, previous, current, rate)
        savedAt.value = System.currentTimeMillis()
        notify("Meter reading saved. Billing history is preserved.")
    }
}
