package com.homerentmanager.ui.screens.rooms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
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
import com.homerentmanager.ui.components.EmptyState
import com.homerentmanager.ui.components.HrmDialog
import com.homerentmanager.ui.components.HrmIcons
import com.homerentmanager.ui.components.HrmScaffold
import com.homerentmanager.ui.components.LabeledValue
import com.homerentmanager.ui.components.LoadingBox
import com.homerentmanager.ui.components.SectionHeader
import com.homerentmanager.ui.components.StatGrid
import com.homerentmanager.ui.components.StatSpec
import com.homerentmanager.ui.components.StatusChip
import com.homerentmanager.ui.components.Tag
import com.homerentmanager.ui.components.hrmPagePadding
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

@OptIn(ExperimentalLayoutApi::class)
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
                    Icon(HrmIcons.EditRoom, contentDescription = "Edit room")
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
                icon = HrmIcons.Rooms,
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
            contentPadding = hrmPagePadding(),
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
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
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
                                Icon(HrmIcons.AddRoom, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Add tenant")
                            }
                        }
                    }
                }
            }

            item {
                StatGrid(
                    specs = listOf(
                        StatSpec(
                            label = "Default rent",
                            value = occupancy.room.defaultRentPaise.toRupeesNoDecimals(),
                        ),
                        StatSpec(
                            label = "Electricity rate",
                            value = occupancy.room.defaultElectricityRate.formatRate(),
                        ),
                        StatSpec(
                            label = "Lifetime billed",
                            value = totalBilled.toRupeesNoDecimals(),
                        ),
                        StatSpec(
                            label = "Outstanding",
                            value = outstanding.toRupeesNoDecimals(),
                            accent = MaterialTheme.colorScheme.error,
                        ),
                    ),
                )
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
                        icon = HrmIcons.Billing,
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
                            Spacer(Modifier.width(8.dp))
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
                        icon = HrmIcons.Electricity,
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
            onSave = { number, floor, rent, rate, notes, onResult ->
                viewModel.updateRoom(number, floor, rent, rate, notes, onResult)
            },
        )
    }

    endTenancyId?.let { id ->
        EndTenancyDialog(
            defaultDate = LocalDate.now(),
            onDismiss = { endTenancyId = null },
            onConfirm = { date, onResult -> viewModel.endTenancy(id, date, onResult) },
        )
    }
}

@Composable
fun EndTenancyDialog(
    defaultDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, (Boolean) -> Unit) -> Unit,
) {
    var date by remember { mutableStateOf(defaultDate) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    HrmDialog(
        title = "End tenancy",
        confirmLabel = "End tenancy",
        onDismiss = onDismiss,
        busy = saving,
        onConfirm = {
            saving = true
            onConfirm(date) { ok ->
                saving = false
                if (ok) {
                    onDismiss()
                } else {
                    error = "Could not end the tenancy. Please try again."
                }
            }
        },
    ) {
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
        if (error != null) {
            Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
        }
    }
}
