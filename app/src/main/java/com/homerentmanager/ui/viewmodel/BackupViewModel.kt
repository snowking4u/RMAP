package com.homerentmanager.ui.viewmodel

import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.homerentmanager.AppContainer
import com.homerentmanager.backup.BackupManager
import com.homerentmanager.data.entity.SettingsEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

sealed interface ValidationState {
    data object None : ValidationState
    data object Validating : ValidationState
    data class Valid(val backup: BackupManager.ValidatedBackup) : ValidationState
    data class Invalid(val message: String) : ValidationState
}

data class BackupUiState(
    val loading: Boolean = true,
    val settings: SettingsEntity = SettingsEntity(),
    val autoInfo: BackupManager.AutoBackupInfo? = null,
    val validation: ValidationState = ValidationState.None,
    val busy: Boolean = false,
    val restoreResult: BackupManager.RestoreOutcome? = null,
    val lastBackupAt: Instant? = null,
    val lastBackupStatus: String = "No manual backup yet",
    val lastBackupFileName: String = "",
    val lastBackupSizeBytes: Long = 0,
)

class BackupViewModel(private val container: AppContainer) : HrmViewModel() {
    private val autoInfo = MutableStateFlow<BackupManager.AutoBackupInfo?>(null)
    private val validation = MutableStateFlow<ValidationState>(ValidationState.None)
    private val busy = MutableStateFlow(false)
    private val restoreResult = MutableStateFlow<BackupManager.RestoreOutcome?>(null)

    val state = combine(
        container.settingsRepository.observe(),
        autoInfo,
        validation,
        busy,
        restoreResult,
    ) { settings, info, validationState, isBusy, restore ->
        val value = settings ?: SettingsEntity()
        BackupUiState(
            loading = false,
            settings = value,
            autoInfo = info,
            validation = validationState,
            busy = isBusy,
            restoreResult = restore,
            lastBackupAt = value.lastBackupAt,
            lastBackupStatus = value.lastBackupStatus,
            lastBackupFileName = value.lastBackupFileName,
            lastBackupSizeBytes = value.lastBackupSizeBytes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    init {
        refreshAutoInfo()
    }

    fun refreshAutoInfo() {
        viewModelScope.launch {
            runCatching { container.backupManager.autoBackupInfo() }
                .onSuccess { autoInfo.value = it }
        }
    }

    fun createBackup(destination: Uri, password: CharArray, displayName: String) {
        if (password.size < 8) {
            notify("Recovery password must be at least 8 characters")
            return
        }
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            try {
                val result = container.backupManager.createBackup(destination, password, displayName)
                notify("Encrypted backup saved: ${result.fileName}")
                refreshAutoInfo()
            } catch (e: Exception) {
                notify(friendlyMessage(e))
            } finally {
                password.fill('\u0000')
                busy.value = false
            }
        }
    }

    fun validate(source: Uri, password: CharArray) {
        if (busy.value) return
        validation.value = ValidationState.Validating
        viewModelScope.launch {
            try {
                val result = container.backupManager.validateBackup(source, password)
                validation.value = ValidationState.Valid(result)
                notify("Backup validated successfully. Review the summary before restoring.")
            } catch (e: Exception) {
                validation.value = ValidationState.Invalid(friendlyMessage(e))
                notify(friendlyMessage(e))
            } finally {
                password.fill('\u0000')
            }
        }
    }

    fun restore() {
        val valid = (validation.value as? ValidationState.Valid)?.backup
        if (valid == null) {
            notify("Validate a backup before restoring")
            return
        }
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            try {
                val result = container.backupManager.restoreBackup(valid)
                validation.value = ValidationState.None
                restoreResult.value = result
                notify("Restore completed successfully")
                refreshAutoInfo()
            } catch (e: Exception) {
                notify(friendlyMessage(e))
            } finally {
                busy.value = false
            }
        }
    }

    fun clearValidation() {
        (validation.value as? ValidationState.Valid)?.backup?.discard()
        validation.value = ValidationState.None
    }

    fun clearRestoreResult() {
        restoreResult.value = null
    }

    fun exportCsv(destination: Uri, kind: BackupManager.CsvKind) {
        viewModelScope.launch {
            try {
                val rows = container.backupManager.exportCsv(destination, kind)
                notify("Exported $rows rows to CSV")
            } catch (e: Exception) {
                notify(friendlyMessage(e))
            }
        }
    }

    override fun onCleared() {
        (validation.value as? ValidationState.Valid)?.backup?.discard()
        super.onCleared()
    }
}
