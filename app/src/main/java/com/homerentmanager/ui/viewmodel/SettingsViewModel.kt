package com.homerentmanager.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.homerentmanager.AppContainer
import com.homerentmanager.BuildConfig
import com.homerentmanager.data.entity.SettingsEntity
import com.homerentmanager.data.local.AppDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SettingsUiState(
    val loading: Boolean = true,
    val settings: SettingsEntity = SettingsEntity(),
    val roomCount: Int = 7,
    val databaseVersion: Int = AppDatabase.VERSION,
    val appVersion: String = BuildConfig.VERSION_NAME,
)

class SettingsViewModel(private val container: AppContainer) : HrmViewModel() {
    val state = combine(
        container.settingsRepository.observe(),
        container.roomRepository.observeCount(),
    ) { settings, roomCount ->
        SettingsUiState(
            loading = false,
            settings = settings ?: SettingsEntity(),
            roomCount = roomCount,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun save(settings: SettingsEntity) = runAction {
        container.settingsRepository.update(settings)
        notify("Settings saved")
    }
}
