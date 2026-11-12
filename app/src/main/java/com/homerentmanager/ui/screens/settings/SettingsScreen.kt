package com.homerentmanager.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homerentmanager.AppContainer
import com.homerentmanager.ui.components.HrmIcons
import com.homerentmanager.ui.components.HrmScaffold
import com.homerentmanager.ui.components.InfoBanner
import com.homerentmanager.ui.components.LabeledValue
import com.homerentmanager.ui.components.LoadingBox
import com.homerentmanager.ui.components.SectionHeader
import com.homerentmanager.ui.components.hrmPagePadding
import com.homerentmanager.ui.hrmViewModel
import com.homerentmanager.ui.viewmodel.SettingsViewModel
import com.homerentmanager.util.parseDecimal

@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel = hrmViewModel { SettingsViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    HrmScaffold(
        title = "Settings",
        subtitle = "Property, billing and backup preferences",
        message = message,
        onMessageShown = viewModel::messageShown,
        onBack = onBack,
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@HrmScaffold
        }
        val settings = state.settings
        var ownerName by remember(settings) { mutableStateOf(settings.ownerName) }
        var ownerMobile by remember(settings) { mutableStateOf(settings.ownerMobile) }
        var propertyName by remember(settings) { mutableStateOf(settings.propertyName) }
        var propertyAddress by remember(settings) { mutableStateOf(settings.propertyAddress) }
        var rate by remember(settings) {
            mutableStateOf(settings.defaultElectricityRate.toString())
        }
        var dueDay by remember(settings) { mutableStateOf(settings.billDueDay.toString()) }
        var invoicePrefix by remember(settings) { mutableStateOf(settings.invoicePrefix) }
        var receiptPrefix by remember(settings) { mutableStateOf(settings.receiptPrefix) }
        var reminderDays by remember(settings) {
            mutableStateOf(settings.backupReminderDays.toString())
        }
        var error by remember { mutableStateOf<String?>(null) }
        var saving by remember { mutableStateOf(false) }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = hrmPagePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        SectionHeader(title = "Property details")
                        OutlinedTextField(
                            value = propertyName,
                            onValueChange = { propertyName = it },
                            label = { Text("Property name *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = propertyAddress,
                            onValueChange = { propertyAddress = it },
                            label = { Text("Property address") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = ownerName,
                            onValueChange = { ownerName = it },
                            label = { Text("Owner name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = ownerMobile,
                            onValueChange = { ownerMobile = it },
                            label = { Text("Owner mobile") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item {
                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        SectionHeader(title = "Billing defaults")
                        OutlinedTextField(
                            value = rate,
                            onValueChange = { rate = it },
                            label = { Text("Default electricity rate (₹/unit)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = dueDay,
                            onValueChange = { dueDay = it },
                            label = { Text("Bill due day (1–28)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = invoicePrefix,
                            onValueChange = { invoicePrefix = it },
                            label = { Text("Invoice prefix") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = receiptPrefix,
                            onValueChange = { receiptPrefix = it },
                            label = { Text("Receipt prefix") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "New bills use the room's rate when set, otherwise this default. " +
                                "Prefixes and numbering are snapshotted into each generated document.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        SectionHeader(title = "Backup reminders")
                        OutlinedTextField(
                            value = reminderDays,
                            onValueChange = { reminderDays = it },
                            label = { Text("Remind me every N days (1–365)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Reminders are local notifications only. Android decides when Auto " +
                                "Backup runs; manual encrypted backups are always under your control.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (error != null) {
                item {
                    InfoBanner(
                        title = "Check the form",
                        text = error.orEmpty(),
                        warning = true,
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        val parsedRate = parseDecimal(rate)
                        val parsedDueDay = dueDay.trim().toIntOrNull()
                        val parsedReminder = reminderDays.trim().toIntOrNull()
                        error = when {
                            propertyName.isBlank() -> "Property name is required"
                            parsedRate == null || parsedRate < 0 -> "Enter a valid electricity rate"
                            parsedDueDay == null || parsedDueDay !in 1..28 ->
                                "Due day must be between 1 and 28"
                            parsedReminder == null || parsedReminder !in 1..365 ->
                                "Backup reminder must be between 1 and 365 days"
                            else -> null
                        }
                        if (error == null) {
                            saving = true
                            viewModel.save(
                                settings.copy(
                                    ownerName = ownerName.trim(),
                                    ownerMobile = ownerMobile.trim(),
                                    propertyName = propertyName.trim(),
                                    propertyAddress = propertyAddress.trim(),
                                    defaultElectricityRate = parsedRate!!,
                                    billDueDay = parsedDueDay!!,
                                    invoicePrefix = invoicePrefix.trim(),
                                    receiptPrefix = receiptPrefix.trim(),
                                    backupReminderDays = parsedReminder!!,
                                )
                            ) { ok ->
                                saving = false
                                if (!ok) error = "Could not save settings. Please try again."
                            }
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(HrmIcons.Save, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (saving) "Saving…" else "Save settings")
                }
            }

            item {
                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        SectionHeader(title = "About")
                        LabeledValue("App version", state.appVersion)
                        LabeledValue("Database version", state.databaseVersion.toString())
                        LabeledValue("Managed rooms", state.roomCount.toString())
                        LabeledValue("Storage", "Offline SQLite database on this device")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Home Rent Manager works fully offline. There is no account, server, " +
                                "advertising or analytics. Rental data stays on this device unless " +
                                "you explicitly export or back it up.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}
