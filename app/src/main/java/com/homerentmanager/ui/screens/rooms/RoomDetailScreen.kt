package com.homerentmanager.ui.screens.rooms

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homerentmanager.AppContainer
import com.homerentmanager.data.entity.TenancyStatus
import com.homerentmanager.ui.components.ClickableRow
import com.homerentmanager.ui.components.DateField
import com.homerentmanager.ui.components.DetailRow
import com.homerentmanager.ui.components.EmptyState
import com.homerentmanager.ui.components.HrmScaffold
import com.homerentmanager.ui.components.LabeledValue
import com.homerentmanager.ui.components.LoadingBox
import com.homerentmanager.ui.components.SectionHeader
import com.homerentmanager.ui.components.StatusChip
import com.homerentmanager.ui.components.Tag
import com.homerentmanager.ui.hrmViewModel
import com.homerentmanager.ui.viewmodel.RoomDetailViewModel
import com.homerentmanager.util.formatDisplay
import com.homerentmanager.util.formatDisplayOrDash
import com.homerentmanager.util.formatRate
import com.homerentmanager.util.formatUnits
import com.homerentmanager.util.label
import com.homerentmanager.util.monthLabel
import com.homerentmanager.util.toRupees
import com.homerentmanager.util.toRupeesNoDecimals
import java.time.LocalDate
import java.util.UUID

@Composable
fun RoomDetailScreen(
    container: AppContainer,
    roomId: UUID,
    onBack: () -> Unit,
    onOpenBill: (UUID) -> Unit,
    onOpenTenant: (UUID) -> Unit,
    onAddTenant: () -> Unit,
) {
    val viewModel = hrmViewModel(key = roomId.toString()) { RoomDetailViewModel(it, roomId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var editOpen by remember { mutableStateOf(false) }
    var endTenancyId by remember { mutableStateOf<UUID?>(null) }

    HrmScaffold(
        title = state.occupancy?.room?.let { "Room ${it.roomNumber}" } ?: "Room",
        subtitle = state.occupancy?.room?.floor,
        message = message,
        onMessageShown = viewModel::messageShown,
        onBack = onBack,
        actions = {
            if (state.occupancy != null) {
                IconButton(onClick = { editOpen = true }) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit room")
                }
            }
        },
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@HrmScaffold
        }
        val occupancy = state.occupancy
        if (occupancy == null) {
            EmptyState(
                icon = Icons.Outlined.MeetingRoom,
                title = "Room not found",
                message = "This room may have been removed. Return to the rooms list.",
                modifier = Modifier.padding(padding),
                actionLabel = "Back",
                onAction = onBack,
            )
            return@HrmScaffold
        }

        val activeTenancy = state.tenancies.firstOrNull { it.tenancy.status == TenancyStatus.ACTIVE }
        val totalBilled = state.bills.sumOf { it.bill.totalPaise }
        val outstanding = state.bills.sumOf {
            (it.bill.totalPaise - it.bill.paidPaise).coerceAtLeast(0)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Tag(if (activeTenancy != null) "Occupied" else "Vacant")
                            Spacer(Modifier.width(8.dp))
                            Text(
                                occupancy.room.floor.ifBlank { "Floor not set" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        if (activeTenancy != null) {
                            Text(
                                activeTenancy.tenant.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Joined ${activeTenancy.tenancy.joinDate.formatDisplay()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            LabeledValue("Monthly rent", activeTenancy.tenancy.monthlyRentPaise.toRupees())
                            LabeledValue(
                                "Security deposit",
                                activeTenancy.tenancy.securityDepositPaise.toRupees(),
                            )
                            if (activeTenancy.tenant.mobile.isNotBlank()) {
                                LabeledValue("Mobile", activeTenancy.tenant.mobile)
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onOpenTenant(activeTenancy.tenant.id) }) {
                                    Text("Open tenant")
                                }
                                OutlinedButton(
                                    onClick = { endTenancyId = activeTenancy.tenancy.id },
                                ) { Text("End tenancy") }
                            }
                        } else {
                            Text(
                                "This room is vacant",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Assign a tenant to start billing. Previous tenancies and bills stay in history.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = onAddTenant) {
                                Icon(Icons.Outlined.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Add tenant")
                            }
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoomStat(
                        label = "Default rent",
                        value = occupancy.room.defaultRentPaise.toRupeesNoDecimals(),
                        modifier = Modifier.weight(1f),
                    )
                    RoomStat(
                        label = "Electricity rate",
                        value = occupancy.room.defaultElectricityRate.formatRate(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoomStat(
                        label = "Lifetime billed",
                        value = totalBilled.toRupeesNoDecimals(),
                        modifier = Modifier.weight(1f),
                    )
                    RoomStat(
                        label = "Outstanding",
                        value = outstanding.toRupeesNoDecimals(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (occupancy.room.notes.isNotBlank()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Room notes", style = MaterialTheme.typography.labelLarge)
                            Text(
                                occupancy.room.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    title = "Bills for this room",
                    actionLabel = if (state.bills.isNotEmpty()) "${state.bills.size}" else null,
                )
            }
            if (state.bills.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.MeetingRoom,
                        title = "No bills yet",
                        message = "Bills generated for this room will appear here, including old tenants.",
                    )
                }
            } else {
                items(state.bills, key = { it.bill.id }) { detail ->
                    OutlinedCard(
                        onClick = { onOpenBill(detail.bill.id) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    monthLabel(detail.bill.billingMonth),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    detail.tenant.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    detail.bill.totalPaise.toRupeesNoDecimals(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                StatusChip(detail.bill.status)
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    title = "Meter readings",
                    actionLabel = if (state.readings.isNotEmpty()) "History" else null,
                )
            }
            if (state.readings.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.ElectricBolt,
                        title = "No readings recorded",
                        message = "Add readings from the Electricity screen to bill consumption accurately.",
                    )
                }
            } else {
                items(state.readings, key = { it.id }) { reading ->
                    OutlinedCard(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(13.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        reading.readingDate.formatDisplay(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "${reading.previousReading.formatUnits()} → " +
                                            "${reading.currentReading.formatUnits()} units",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
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
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (reading.billId != null) {
                                Spacer(Modifier.height(2.dp))
                                Tag("Linked to bill", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader(title = "Tenancy history")
            }
            items(state.tenancies, key = { it.tenancy.id }) { detail ->
                ClickableRow(
                    title = detail.tenant.name,
                    subtitle = "${detail.tenancy.joinDate.formatDisplayOrDash()} – " +
                        "${detail.tenancy.leaveDate.formatDisplayOrDash()} · " +
                        "${detail.tenancy.status.label()}",
                    trailing = detail.tenancy.monthlyRentPaise.toRupeesNoDecimals(),
                    onClick = { onOpenTenant(detail.tenant.id) },
                )
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    if (editOpen && state.occupancy != null) {
        RoomEditDialog(
            occupancy = state.occupancy!!,
            onDismiss = { editOpen = false },
            onSave = { number, floor, rent, rate, notes ->
                viewModel.updateRoom(number, floor, rent, rate, notes)
                editOpen = false
            },
        )
    }

    endTenancyId?.let { id ->
        EndTenancyDialog(
            defaultDate = LocalDate.now(),
            onDismiss = { endTenancyId = null },
            onConfirm = { date ->
                viewModel.endTenancy(id, date)
                endTenancyId = null
            },
        )
    }
}

@Composable
private fun RoomStat(label: String, value: String, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun EndTenancyDialog(
    defaultDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    var date by remember { mutableStateOf(defaultDate) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("End tenancy") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "The tenant will no longer be billed for this room. Existing bills, payments " +
                        "and tenancy history are preserved.",
                    style = MaterialTheme.typography.bodySmall,
                )
                DateField(
                    label = "Leaving date",
                    date = date,
                    onDateChange = { date = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(date) }) { Text("End tenancy") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
