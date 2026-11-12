package com.homerentmanager.ui.screens.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homerentmanager.AppContainer
import com.homerentmanager.data.entity.MeterReadingEntity
import com.homerentmanager.data.model.TenancyWithDetails
import com.homerentmanager.data.repository.BillingRepository
import com.homerentmanager.ui.components.EmptyState
import com.homerentmanager.ui.components.HrmDialog
import com.homerentmanager.ui.components.HrmIcons
import com.homerentmanager.ui.components.HrmScaffold
import com.homerentmanager.ui.components.LoadingBox
import com.homerentmanager.ui.components.MonthSwitcher
import com.homerentmanager.ui.components.SectionHeader
import com.homerentmanager.ui.components.StatGrid
import com.homerentmanager.ui.components.StatSpec
import com.homerentmanager.ui.components.StatusChip
import com.homerentmanager.ui.components.Tag
import com.homerentmanager.ui.components.hrmPagePadding
import com.homerentmanager.ui.hrmViewModel
import com.homerentmanager.ui.viewmodel.BillingViewModel
import com.homerentmanager.util.formatUnits
import com.homerentmanager.util.monthLabel
import com.homerentmanager.util.parseDecimal
import com.homerentmanager.util.parseMoneyToPaise
import com.homerentmanager.util.toRupeesNoDecimals
import java.util.UUID

@Composable
fun BillingScreen(
    container: AppContainer,
    onOpenBill: (UUID) -> Unit,
) {
    val viewModel = hrmViewModel { BillingViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var generateOpen by remember { mutableStateOf(false) }

    HrmScaffold(
        title = "Billing",
        subtitle = monthLabel(state.month),
        message = message,
        onMessageShown = viewModel::messageShown,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { generateOpen = true },
                icon = { Icon(HrmIcons.AddRoom, contentDescription = null) },
                text = { Text("Generate bills") },
            )
        },
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@HrmScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = hrmPagePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                MonthSwitcher(
                    month = state.month,
                    onPrevious = { viewModel.shiftMonth(-1) },
                    onNext = { viewModel.shiftMonth(1) },
                    onToday = viewModel::today,
                )
            }
            item {
                StatGrid(
                    specs = listOf(
                        StatSpec(
                            label = "Billed",
                            value = state.billedPaise.toRupeesNoDecimals(),
                            supporting = "${state.bills.size} bill(s)",
                        ),
                        StatSpec(
                            label = "Collected",
                            value = state.collectedPaise.toRupeesNoDecimals(),
                            supporting = "Against these bills",
                        ),
                        StatSpec(
                            label = "Outstanding",
                            value = state.outstandingPaise.toRupeesNoDecimals(),
                            supporting = "For ${monthLabel(state.month)}",
                            accent = MaterialTheme.colorScheme.error,
                        ),
                        StatSpec(
                            label = "Active tenancies",
                            value = state.activeTenancies.size.toString(),
                            supporting = "${state.bills.size} billed this month",
                        ),
                    ),
                )
            }

            item {
                SectionHeader(title = "Bills in ${monthLabel(state.month)}")
            }

            if (state.bills.isEmpty()) {
                item {
                    EmptyState(
                        icon = HrmIcons.Billing,
                        title = "No bills for this month",
                        message = if (state.activeTenancies.isEmpty()) {
                            "There are no active tenancies to bill. Assign tenants to rooms first."
                        } else {
                            "Generate this month's bills for ${state.activeTenancies.size} active tenancy(ies)."
                        },
                        actionLabel = if (state.activeTenancies.isNotEmpty()) "Generate bills" else null,
                        onAction = if (state.activeTenancies.isNotEmpty()) {
                            { generateOpen = true }
                        } else {
                            null
                        },
                    )
                }
            } else {
                items(state.bills, key = { it.bill.id }) { detail ->
                    Card(
                        onClick = { onOpenBill(detail.bill.id) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Room ${detail.room.roomNumber} · ${detail.tenant.name}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        detail.bill.billNumber,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                StatusChip(detail.bill.status)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Rent ${detail.bill.rentPaise.toRupeesNoDecimals()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (detail.bill.electricityAmountPaise > 0) {
                                        Text(
                                            "Electricity " +
                                                detail.bill.electricityAmountPaise.toRupeesNoDecimals(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (detail.bill.paidPaise > 0) {
                                        Text(
                                            "Paid ${detail.bill.paidPaise.toRupeesNoDecimals()} · " +
                                                "Balance " +
                                                (detail.bill.totalPaise - detail.bill.paidPaise)
                                                    .toRupeesNoDecimals(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        detail.bill.totalPaise.toRupeesNoDecimals(),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        "Total",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (detail.bill.notes != null) {
                                Text(
                                    detail.bill.notes.orEmpty(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (generateOpen) {
        GenerateBillsDialog(
            month = state.month,
            tenancies = state.activeTenancies,
            latestReadings = state.latestReadings,
            onDismiss = { generateOpen = false },
            onGenerate = { requests, onResult -> viewModel.generate(requests, onResult) },
        )
    }
}

@Composable
private fun GenerateBillsDialog(
    month: String,
    tenancies: List<TenancyWithDetails>,
    latestReadings: Map<UUID, MeterReadingEntity>,
    onDismiss: () -> Unit,
    onGenerate: (List<BillingRepository.BillRequest>, (Boolean) -> Unit) -> Unit,
) {
    val tenancyIds = tenancies.map { it.tenancy.id }
    val selected = remember(tenancyIds) {
        mutableStateMapOf<UUID, Boolean>().apply {
            tenancyIds.forEach { put(it, true) }
        }
    }
    val readings = remember(tenancyIds) { mutableStateMapOf<UUID, String>() }
    val rates = remember(tenancyIds) {
        mutableStateMapOf<UUID, String>().apply {
            tenancies.forEach { put(it.tenancy.id, it.room.defaultElectricityRate.toString()) }
        }
    }
    var additional by remember { mutableStateOf("0") }
    var discount by remember { mutableStateOf("0") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    HrmDialog(
        title = "Generate ${monthLabel(month)} bills",
        confirmLabel = "Generate",
        onDismiss = onDismiss,
        busy = saving,
        confirmEnabled = tenancies.isNotEmpty(),
        onConfirm = {
            val chosen = tenancies.filter { selected[it.tenancy.id] == true }
            if (chosen.isEmpty()) {
                error = "Select at least one tenancy"
                return@HrmDialog
            }
            val extra = parseMoneyToPaise(additional)
            val disc = parseMoneyToPaise(discount)
            if (extra == null || extra < 0) {
                error = "Enter valid extra charges"
                return@HrmDialog
            }
            if (disc == null || disc < 0) {
                error = "Enter a valid discount"
                return@HrmDialog
            }
            val requests = mutableListOf<BillingRepository.BillRequest>()
            for (detail in chosen) {
                val id = detail.tenancy.id
                val currentText = readings[id].orEmpty().trim()
                val parsedRate = parseDecimal(rates[id].orEmpty())
                if (parsedRate == null || parsedRate < 0) {
                    error = "Enter a valid rate for room ${detail.room.roomNumber}"
                    return@HrmDialog
                }
                val current = if (currentText.isBlank()) null else parseDecimal(currentText)
                if (currentText.isNotBlank() && current == null) {
                    error = "Enter a valid reading for room ${detail.room.roomNumber}"
                    return@HrmDialog
                }
                val previous = latestReadings[detail.room.id]?.currentReading ?: 0.0
                if (current != null && current < previous) {
                    error = "Current reading for room ${detail.room.roomNumber} cannot be " +
                        "lower than the previous reading ${previous.formatUnits()}"
                    return@HrmDialog
                }
                requests += BillingRepository.BillRequest(
                    tenancyId = id,
                    billingMonth = month,
                    currentReading = current,
                    ratePerUnit = parsedRate,
                    additionalChargesPaise = extra,
                    discountPaise = disc,
                    notes = notes,
                )
            }
            saving = true
            onGenerate(requests) { ok ->
                saving = false
                if (ok) {
                    onDismiss()
                } else {
                    error = "Could not generate bills. Please check the readings and try again."
                }
            }
        },
    ) {
        if (tenancies.isEmpty()) {
            Text(
                "No active tenancies. Assign a tenant to a room before generating bills.",
                color = MaterialTheme.colorScheme.error,
            )
        }
        tenancies.forEach { detail ->
            val id = detail.tenancy.id
            val checked = selected[id] == true
            OutlinedCard(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { selected[id] = it },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Room ${detail.room.roomNumber} · ${detail.tenant.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Rent ${detail.tenancy.monthlyRentPaise.toRupeesNoDecimals()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val previous = latestReadings[detail.room.id]?.currentReading
                            Text(
                                if (previous == null) {
                                    "Previous reading: none (starts at 0)"
                                } else {
                                    "Previous reading: ${previous.formatUnits()}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    if (checked) {
                        OutlinedTextField(
                            value = readings[id].orEmpty(),
                            onValueChange = { readings[id] = it },
                            label = { Text("Current reading") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = rates[id].orEmpty(),
                            onValueChange = { rates[id] = it },
                            label = { Text("Rate (₹/unit)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = additional,
            onValueChange = { additional = it },
            label = { Text("Extra charges (₹)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = discount,
            onValueChange = { discount = it },
            label = { Text("Discount (₹)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes for these bills") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Tag("Leaving the reading blank bills rent only")
        if (error != null) {
            Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
        }
    }
}
