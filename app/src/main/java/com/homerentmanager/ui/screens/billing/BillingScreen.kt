package com.homerentmanager.ui.screens.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homerentmanager.AppContainer
import com.homerentmanager.data.model.TenancyWithDetails
import com.homerentmanager.data.repository.BillingRepository
import com.homerentmanager.ui.components.EmptyState
import com.homerentmanager.ui.components.HrmScaffold
import com.homerentmanager.ui.components.LoadingBox
import com.homerentmanager.ui.components.MonthSwitcher
import com.homerentmanager.ui.components.SectionHeader
import com.homerentmanager.ui.components.StatCard
import com.homerentmanager.ui.components.StatusChip
import com.homerentmanager.ui.components.Tag
import com.homerentmanager.ui.hrmViewModel
import com.homerentmanager.ui.viewmodel.BillingViewModel
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
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
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
            contentPadding = PaddingValues(16.dp),
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(
                        label = "Billed",
                        value = state.billedPaise.toRupeesNoDecimals(),
                        supporting = "${state.bills.size} bill(s)",
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = "Collected",
                        value = state.collectedPaise.toRupeesNoDecimals(),
                        supporting = "Against these bills",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(
                        label = "Outstanding",
                        value = state.outstandingPaise.toRupeesNoDecimals(),
                        supporting = "For ${monthLabel(state.month)}",
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = "Active tenancies",
                        value = state.activeTenancies.size.toString(),
                        supporting = "${state.bills.size} billed this month",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                SectionHeader(title = "Bills in ${monthLabel(state.month)}")
            }

            if (state.bills.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.ReceiptLong,
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
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                                Text(
                                    "Rent ${detail.bill.rentPaise.toRupeesNoDecimals()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (detail.bill.electricityAmountPaise > 0) {
                                    Text(
                                        " · Electricity ${detail.bill.electricityAmountPaise.toRupeesNoDecimals()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                Text(
                                    detail.bill.totalPaise.toRupeesNoDecimals(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            if (detail.bill.paidPaise > 0) {
                                Text(
                                    "Paid ${detail.bill.paidPaise.toRupeesNoDecimals()} · " +
                                        "Balance ${(detail.bill.totalPaise - detail.bill.paidPaise).toRupeesNoDecimals()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
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
            onDismiss = { generateOpen = false },
            onGenerate = { requests ->
                viewModel.generate(requests)
                generateOpen = false
            },
        )
    }
}

@Composable
private fun GenerateBillsDialog(
    month: String,
    tenancies: List<TenancyWithDetails>,
    onDismiss: () -> Unit,
    onGenerate: (List<BillingRepository.BillRequest>) -> Unit,
) {
    val selected = remember { mutableStateMapOf<UUID, Boolean>() }
    val readings = remember { mutableStateMapOf<UUID, String>() }
    val rates = remember { mutableStateMapOf<UUID, String>() }
    tenancies.forEach { detail ->
        if (detail.tenancy.id !in selected) selected[detail.tenancy.id] = true
        if (detail.tenancy.id !in rates) {
            rates[detail.tenancy.id] = detail.room.defaultElectricityRate.toString()
        }
    }
    var additional by remember { mutableStateOf("0") }
    var discount by remember { mutableStateOf("0") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate ${monthLabel(month)} bills") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
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
                                }
                            }
                            if (checked) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = readings[id].orEmpty(),
                                        onValueChange = { readings[id] = it },
                                        label = { Text("Current reading") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedTextField(
                                        value = rates[id].orEmpty(),
                                        onValueChange = { rates[id] = it },
                                        label = { Text("₹/unit") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = additional,
                        onValueChange = { additional = it },
                        label = { Text("Extra charges (₹)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = discount,
                        onValueChange = { discount = it },
                        label = { Text("Discount (₹)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes for these bills") },
                    minLines = 2,
                )
                Tag("Leaving the reading blank bills rent only")
                if (error != null) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val chosen = tenancies.filter { selected[it.tenancy.id] == true }
                    if (chosen.isEmpty()) {
                        error = "Select at least one tenancy"
                        return@TextButton
                    }
                    val extra = parseMoneyToPaise(additional)
                    val disc = parseMoneyToPaise(discount)
                    if (extra == null || extra < 0) {
                        error = "Enter valid extra charges"
                        return@TextButton
                    }
                    if (disc == null || disc < 0) {
                        error = "Enter a valid discount"
                        return@TextButton
                    }
                    val requests = mutableListOf<BillingRepository.BillRequest>()
                    for (detail in chosen) {
                        val id = detail.tenancy.id
                        val currentText = readings[id].orEmpty().trim()
                        val parsedRate = parseDecimal(rates[id].orEmpty())
                        if (parsedRate == null || parsedRate < 0) {
                            error = "Enter a valid rate for room ${detail.room.roomNumber}"
                            return@TextButton
                        }
                        val current = if (currentText.isBlank()) null else parseDecimal(currentText)
                        if (currentText.isNotBlank() && current == null) {
                            error = "Enter a valid reading for room ${detail.room.roomNumber}"
                            return@TextButton
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
                    onGenerate(requests)
                },
                enabled = tenancies.isNotEmpty(),
            ) { Text("Generate") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
