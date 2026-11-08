package com.homerentmanager.ui.screens.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homerentmanager.AppContainer
import com.homerentmanager.backup.BackupManager
import com.homerentmanager.ui.components.HrmScaffold
import com.homerentmanager.ui.components.InfoBanner
import com.homerentmanager.ui.components.LabeledValue
import com.homerentmanager.ui.components.LoadingBox
import com.homerentmanager.ui.components.SectionHeader
import com.homerentmanager.ui.hrmViewModel
import com.homerentmanager.ui.viewmodel.BackupViewModel
import com.homerentmanager.ui.viewmodel.ValidationState
import com.homerentmanager.util.formatDisplayDateTimeOrDash
import com.homerentmanager.util.formatDisplayOrDash
import com.homerentmanager.util.toRupeesNoDecimals
import java.time.Instant

@Composable
fun BackupScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel = hrmViewModel { BackupViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var createUri by remember { mutableStateOf<Uri?>(null) }
    var validateUri by remember { mutableStateOf<Uri?>(null) }
    var restoreConfirm by remember { mutableStateOf(false) }
    var pendingCsv by remember { mutableStateOf(BackupManager.CsvKind.BILLS) }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri: Uri? -> createUri = uri }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> validateUri = uri }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri: Uri? -> if (uri != null) viewModel.exportCsv(uri, pendingCsv) }

    HrmScaffold(
        title = "Backup & restore",
        subtitle = "Encrypted, versioned and validated",
        message = message,
        onMessageShown = viewModel::messageShown,
        onBack = onBack,
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@HrmScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Lock, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Your data stays private",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Manual backups are encrypted with AES-256-GCM using a password only you " +
                                "know. The app never stores the password, and losing it means the backup " +
                                "cannot be recovered.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            item {
                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        SectionHeader(title = "Manual encrypted backup")
                        Text(
                            "Save a backup to Google Drive or any document provider you choose. " +
                                "The backup contains a snapshot of the database and settings, never " +
                                "cached or temporary files.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        LabeledValue(
                            "Last backup",
                            state.lastBackupAt.formatDisplayDateTimeOrDash(),
                        )
                        LabeledValue("Status", state.lastBackupStatus)
                        if (state.lastBackupFileName.isNotBlank()) {
                            LabeledValue("File", state.lastBackupFileName)
                        }
                        if (state.lastBackupSizeBytes > 0) {
                            LabeledValue(
                                "Size",
                                "${state.lastBackupSizeBytes / 1024} KB",
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val stamp = java.time.format.DateTimeFormatter
                                        .ofPattern("yyyyMMdd-HHmmss")
                                        .format(java.time.LocalDateTime.now())
                                    createLauncher.launch(
                                        "HomeRentManager-backup-$stamp.hrmbackup",
                                    )
                                },
                                enabled = !state.busy,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Outlined.CloudUpload, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Create backup")
                            }
                            OutlinedButton(
                                onClick = { openLauncher.launch(arrayOf("*/*")) },
                                enabled = !state.busy,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Outlined.FileOpen, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Restore")
                            }
                        }
                        if (state.busy) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.height(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Working…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            when (val validation = state.validation) {
                is ValidationState.Validating -> item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Decrypting and validating backup…")
                    }
                }

                is ValidationState.Invalid -> item {
                    InfoBanner(
                        title = "Backup could not be validated",
                        text = validation.message,
                        warning = true,
                    )
                }

                is ValidationState.Valid -> item {
                    val backup = validation.backup
                    OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            SectionHeader(title = "Validated backup")
                            LabeledValue(
                                "Created",
                                Instant.ofEpochMilli(backup.header.createdAtMillis)
                                    .formatDisplayDateTimeOrDash(),
                            )
                            LabeledValue("Format version", backup.header.formatVersion.toString())
                            LabeledValue("Database version", backup.header.dbVersion.toString())
                            LabeledValue("App version", backup.header.appVersion)
                            Spacer(Modifier.height(6.dp))
                            LabeledValue("Rooms", backup.stats.rooms.toString())
                            LabeledValue("Tenants", backup.stats.tenants.toString())
                            LabeledValue("Tenancies", backup.stats.tenancies.toString())
                            LabeledValue("Bills", backup.stats.bills.toString())
                            LabeledValue("Payments", backup.stats.payments.toString())
                            LabeledValue("Readings", backup.stats.readings.toString())
                            backup.warnings.forEach { warning ->
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    warning,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { restoreConfirm = true },
                                    enabled = !state.busy,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Outlined.Restore, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Restore now")
                                }
                                OutlinedButton(
                                    onClick = viewModel::clearValidation,
                                    modifier = Modifier.weight(1f),
                                ) { Text("Discard") }
                            }
                        }
                    }
                }

                ValidationState.None -> Unit
            }

            state.restoreResult?.let { result ->
                item {
                    InfoBanner(
                        title = "Restore completed",
                        text = "Restored ${result.stats.rooms} rooms, ${result.stats.tenants} tenants, " +
                            "${result.stats.bills} bills and ${result.stats.payments} payments. " +
                            "A safety copy of the previous data was saved as ${result.safetyCopyName}.",
                    )
                }
            }

            item {
                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        SectionHeader(title = "Android Auto Backup")
                        val info = state.autoInfo
                        if (info == null) {
                            Text(
                                "Reading system backup configuration…",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            LabeledValue("Status", if (info.allowed) "Enabled" else "Disabled")
                            LabeledValue(
                                "Estimated size",
                                "${info.estimatedBytes / 1024} KB of ${info.quotaBytes / 1024 / 1024} MB quota",
                            )
                            Spacer(Modifier.height(6.dp))
                            val fraction = if (info.quotaBytes <= 0) 0f else
                                (info.estimatedBytes.toFloat() / info.quotaBytes).coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                info.statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (info.warning != null) {
                                Spacer(Modifier.height(8.dp))
                                InfoBanner(
                                    title = "Auto Backup notice",
                                    text = info.warning,
                                    warning = true,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        InfoBanner(
                            title = "What Android controls",
                            text = "Auto Backup is scheduled by Android, usually about once a day " +
                                "while the device is charging and on Wi-Fi. The app cannot force, " +
                                "query or guarantee a system backup, and the 25 MB quota is shared " +
                                "per app. Restoring after reinstall depends on the device, Google " +
                                "account and Android version. Use encrypted manual backups for " +
                                "guaranteed control.",
                            warning = true,
                            icon = Icons.Outlined.WarningAmber,
                        )
                    }
                }
            }

            item {
                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        SectionHeader(title = "Export CSV")
                        Text(
                            "Plain CSV files are not encrypted. Share or store them carefully.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        BackupManager.CsvKind.entries.forEach { kind ->
                            TextButton(
                                onClick = {
                                    pendingCsv = kind
                                    csvLauncher.launch("${kind.fileStem}.csv")
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Outlined.Download, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(kind.label, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                            }
                        }
                    }
                }
            }

            item {
                InfoBanner(
                    title = "Restore safety",
                    text = "A backup is fully validated before restore. The live database is " +
                        "snapshotted first and all tables are replaced in one transaction, so a " +
                        "failure rolls back and leaves your data unchanged. Only the last three " +
                        "safety copies are kept in app-private storage.",
                    icon = Icons.Outlined.Backup,
                )
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    createUri?.let { uri ->
        BackupPasswordDialog(
            title = "Protect this backup",
            confirmLabel = "Create backup",
            requireConfirmation = true,
            onDismiss = { createUri = null },
            onConfirm = { password ->
                viewModel.createBackup(uri, password, "manual-backup.hrmbackup")
                createUri = null
            },
        )
    }

    validateUri?.let { uri ->
        BackupPasswordDialog(
            title = "Open encrypted backup",
            confirmLabel = "Validate",
            requireConfirmation = false,
            onDismiss = { validateUri = null },
            onConfirm = { password ->
                viewModel.validate(uri, password)
                validateUri = null
            },
        )
    }

    if (restoreConfirm) {
        AlertDialog(
            onDismissRequest = { restoreConfirm = false },
            title = { Text("Restore this backup?") },
            text = {
                Text(
                    "All current rooms, tenants, tenancies, bills, payments and settings will be " +
                        "replaced by the validated backup. A safety copy is created first. This " +
                        "cannot be undone from inside the app.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.restore()
                        restoreConfirm = false
                    },
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { restoreConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun BackupPasswordDialog(
    title: String,
    confirmLabel: String,
    requireConfirmation: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    if (requireConfirmation) {
                        "Use at least 8 characters. This password is never stored and cannot be recovered."
                    } else {
                        "Enter the password that was used when this backup was created."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Recovery password") },
                    singleLine = true,
                    visualTransformation = if (visible) {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(onClick = { visible = !visible }) {
                            Text(if (visible) "Hide" else "Show")
                        }
                    },
                )
                if (requireConfirmation) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = { Text("Confirm password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
                if (error != null) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    error = when {
                        requireConfirmation && password.length < 8 ->
                            "Password must be at least 8 characters"
                        !requireConfirmation && password.isEmpty() ->
                            "Enter the backup password"
                        requireConfirmation && password != confirmation ->
                            "Passwords do not match"
                        else -> null
                    }
                    if (error == null) onConfirm(password.toCharArray())
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
