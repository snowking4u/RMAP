package com.homerentmanager.ui.screens.electricity

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homerentmanager.AppContainer
import com.homerentmanager.domain.BillingCalculator
import com.homerentmanager.ui.components.DateField
import com.homerentmanager.ui.components.EmptyState
import com.homerentmanager.ui.components.HrmScaffold
import com.homerentmanager.ui.components.LoadingBox
import com.homerentmanager.ui.components.SectionHeader
import com.homerentmanager.ui.components.Tag
import com.homerentmanager.ui.hrmViewModel
import com.homerentmanager.ui.viewmodel.ElectricityViewModel
import com.homerentmanager.util.formatDisplay
import com.homerentmanager.util.formatRate
import com.homerentmanager.util.formatUnits
import com.homerentmanager.util.parseDecimal
import com.homerentmanager.util.toRupeesNoDecimals
import java.time.LocalDate

@Composable
fun ElectricityScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel = hrmViewModel { ElectricityViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var date by remember { mutableStateOf(LocalDate.now()) }
    var previous by remember { mutableStateOf("0") }
    var current by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.selectedRoomId, state.latestReading?.id) {
        previous = state.latestReading?.currentReading?.formatUnits() ?: "0"
        current = ""
        if (rate.isBlank()) rate = state.defaultRate.toString()
    }
    LaunchedEffect(state.defaultRate) {
        if (rate.isBlank()) rate = state.defaultRate.toString()
    }

    val previousValue = parseDecimal(previous)
    val currentValue = parseDecimal(current)
    val rateValue = parseDecimal(rate)
    val previewUnits = if (previousValue != null && currentValue != null && currentValue >= previousValue) {
        runCatching { BillingCalculator.units(previousValue, currentValue) }.getOrNull()
    } else {
        null
    }
    val previewAmount = if (previewUnits != null && rateValue != null && rateValue >= 0) {
        runCatching { BillingCalculator.electricityAmountPaise(previewUnits, rateValue) }.getOrNull()
    } else {
        null
    }

    HrmScaffold(
        title = "Electricity",
        subtitle = "Meter readings and consumption history",
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
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.rooms.forEach { occupancy ->
                        FilterChip(
                            selected = state.selectedRoomId == occupancy.room.id,
                            onClick = { viewModel.selectRoom(occupancy.room.id) },
                            label = { Text("Room ${occupancy.room.roomNumber}") },
                        )
                    }
                }
            }

            item {
                val room = state.selectedRoom
                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                room?.room?.let { "Room ${it.roomNumber}" } ?: "Select a room",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.weight(1f))
                            Tag(if (state.hasActiveTenancy) "Occupied" else "Vacant")
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Latest reading: " +
                                (state.latestReading?.let {
                                    "${it.readingDate.formatDisplay()} · ${it.currentReading.formatUnits()} units"
                                } ?: "none recorded"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Default rate: ${state.defaultRate.formatRate()} per unit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!state.hasActiveTenancy) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "An active tenancy is required to add a reading. Readings made during " +
                                    "billing are linked to the bill automatically.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader(title = "Add reading")
            }

            item {
                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        DateField(
                            label = "Reading date",
                            date = date,
                            onDateChange = { date = it },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            OutlinedTextField(
                                value = previous,
                                onValueChange = {},
                                label = { Text("Previous (auto)") },
                                singleLine = true,
                                readOnly = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = current,
                                onValueChange = { current = it },
                                label = { Text("Current") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        OutlinedTextField(
                            value = rate,
                            onValueChange = { rate = it },
                            label = { Text("Rate per unit (₹)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (previewUnits != null) {
                            Text(
                                "${previewUnits.formatUnits()} units · " +
                                    "${previewAmount?.toRupeesNoDecimals() ?: "—"}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (error != null) {
                            Text(
                                error.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Button(
                            onClick = {
                                val roomId = state.selectedRoomId
                                val prev = parseDecimal(previous)
                                val curr = parseDecimal(current)
                                val parsedRate = parseDecimal(rate)
                                error = when {
                                    roomId == null -> "Select a room"
                                    !state.hasActiveTenancy -> "This room needs an active tenancy"
                                    prev == null || prev < 0 -> "Enter a valid previous reading"
                                    curr == null || curr < prev ->
                                        "Current reading must be at least the previous reading"
                                    parsedRate == null || parsedRate < 0 -> "Enter a valid rate"
                                    else -> null
                                }
                                if (error == null) {
                                    viewModel.addReading(
                                        roomId = roomId!!,
                                        date = date,
                                        previous = prev!!,
                                        current = curr!!,
                                        rate = parsedRate!!,
                                    )
                                }
                            },
                        ) {
                            Text("Save reading")
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    title = "Readings for this room",
                    actionLabel = if (state.readings.isNotEmpty()) "${state.readings.size}" else null,
                )
            }

            if (state.readings.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.ElectricBolt,
                        title = "No readings",
                        message = "Saved meter readings will be listed here with units and charges.",
                    )
                }
            } else {
                items(state.readings, key = { it.id }) { reading ->
                    OutlinedCard(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    reading.readingDate.formatDisplay(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "${reading.previousReading.formatUnits()} → " +
                                        "${reading.currentReading.formatUnits()} · " +
                                        "${reading.ratePerUnit.formatRate()}/unit",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (reading.billId != null) {
                                    Spacer(Modifier.height(2.dp))
                                    Tag(
                                        "Billed",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        container = MaterialTheme.colorScheme.primaryContainer,
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${reading.units.formatUnits()} units",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    reading.amountPaise.toRupeesNoDecimals(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}
