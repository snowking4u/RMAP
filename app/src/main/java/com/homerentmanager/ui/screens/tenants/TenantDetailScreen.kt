package com.homerentmanager.ui.screens.tenants

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homerentmanager.AppContainer
import com.homerentmanager.data.entity.IdType
import com.homerentmanager.data.entity.TenancyStatus
import com.homerentmanager.data.model.RoomOccupancy
import com.homerentmanager.data.model.TenantSummary
import com.homerentmanager.data.repository.TenantRepository
import com.homerentmanager.ui.components.DateField
import com.homerentmanager.ui.components.EmptyState
import com.homerentmanager.ui.components.HrmScaffold
import com.homerentmanager.ui.components.LabeledValue
import com.homerentmanager.ui.components.LoadingBox
import com.homerentmanager.ui.components.SectionHeader
import com.homerentmanager.ui.components.StatusChip
import com.homerentmanager.ui.components.Tag
import com.homerentmanager.ui.hrmViewModel
import com.homerentmanager.ui.screens.rooms.EndTenancyDialog
import com.homerentmanager.ui.viewmodel.TenantDetailViewModel
import com.homerentmanager.util.formatDisplay
import com.homerentmanager.util.formatDisplayOrDash
import com.homerentmanager.util.label
import com.homerentmanager.util.monthLabel
import com.homerentmanager.util.parseMoneyToPaise
import com.homerentmanager.util.toRupees
import com.homerentmanager.util.toRupeesNoDecimals
import java.time.LocalDate
import java.util.UUID

@Composable
fun TenantDetailScreen(
    container: AppContainer,
    tenantId: UUID,
    onBack: () -> Unit,
    onOpenBill: (UUID) -> Unit,
    onOpenRoom: (UUID) -> Unit,
) {
    val viewModel = hrmViewModel(key = tenantId.toString()) { TenantDetailViewModel(it, tenantId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var editOpen by remember { mutableStateOf(false) }
    var startOpen by remember { mutableStateOf(false) }
    var endTenancyId by remember { mutableStateOf<UUID?>(null) }

    val summary = state.summary
    HrmScaffold(
        title = summary?.tenant?.name ?: "Tenant",
        subtitle = summary?.activeRoomNumber?.let { "Room $it" } ?: "Tenant profile",
        message = message,
        onMessageShown = viewModel::messageShown,
        onBack = onBack,
        actions = {
            if (summary != null) {
                IconButton(onClick = { editOpen = true }) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit tenant")
                }
            }
        },
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@HrmScaffold
        }
        if (summary == null) {
            EmptyState(
                icon = Icons.Outlined.Person,
                title = "Tenant not found",
                message = "This tenant may no longer exist.",
                modifier = Modifier.padding(padding),
                actionLabel = "Back",
                onAction = onBack,
            )
            return@HrmScaffold
        }

        val active = state.tenancies.firstOrNull { it.tenancy.status == TenancyStatus.ACTIVE }
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
                            Tag(if (active != null) "Active tenant" else "Past tenant")
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${state.tenancies.size} tenancy record(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        LabeledValue("Mobile", summary.tenant.mobile.ifBlank { "Not provided" })
                        LabeledValue(
                            "Alternate mobile",
                            summary.tenant.alternateMobile.ifBlank { "Not provided" },
                        )
                        LabeledValue("ID", "${summary.tenant.idType.label()}" +
                            if (summary.tenant.idNumber.isBlank()) "" else
                                " · ${summary.tenant.idNumber}")
                        if (summary.tenant.address.isNotBlank()) {
                            LabeledValue("Address", summary.tenant.address)
                        }
                        if (summary.tenant.notes.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                summary.tenant.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TenantStat(
                        label = "Billed",
                        value = totalBilled.toRupeesNoDecimals(),
                        modifier = Modifier.weight(1f),
                    )
                    TenantStat(
                        label = "Outstanding",
                        value = outstanding.toRupeesNoDecimals(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        if (active != null) {
                            Text(
                                "Currently in Room ${active.room.roomNumber}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Since ${active.tenancy.joinDate.formatDisplay()} · " +
                                    "${active.tenancy.monthlyRentPaise.toRupees()}/month",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LabeledValue(
                                "Security deposit",
                                active.tenancy.securityDepositPaise.toRupees(),
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onOpenRoom(active.room.id) }) { Text("Open room") }
                                OutlinedButton(
                                    onClick = { endTenancyId = active.tenancy.id },
                                ) { Text("End tenancy") }
                            }
                        } else {
                            Text(
                                "No active tenancy",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Assign a vacant room to start billing this tenant again.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = { startOpen = true }) { Text("Start tenancy") }
                        }
                    }
                }
            }

            item {
                SectionHeader(title = "Tenancy records")
            }
            items(state.tenancies, key = { it.tenancy.id }) { detail ->
                OutlinedCard(
                    onClick = { onOpenRoom(detail.room.id) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Room ${detail.room.roomNumber} · ${detail.tenancy.status.label()}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "${detail.tenancy.joinDate.formatDisplayOrDash()} – " +
                                    "${detail.tenancy.leaveDate.formatDisplayOrDash()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            detail.tenancy.monthlyRentPaise.toRupeesNoDecimals(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            item {
                SectionHeader(
                    title = "Bills",
                    actionLabel = if (state.bills.isNotEmpty()) "${state.bills.size}" else null,
                )
            }
            if (state.bills.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Person,
                        title = "No bills yet",
                        message = "Bills issued to this tenant will appear here.",
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
                                    "${monthLabel(detail.bill.billingMonth)} · Room ${detail.room.roomNumber}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    detail.bill.billNumber,
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
            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    if (editOpen && summary != null) {
        EditTenantDialog(
            summary = summary,
            onDismiss = { editOpen = false },
            onSave = { draft ->
                viewModel.updateTenant(draft)
                editOpen = false
            },
        )
    }

    if (startOpen) {
        StartTenancyDialog(
            vacantRooms = state.vacantRooms,
            onDismiss = { startOpen = false },
            onStart = { roomId, rent, deposit, date, notes ->
                viewModel.startTenancy(roomId, rent, deposit, date, notes)
                startOpen = false
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
private fun TenantStat(label: String, value: String, modifier: Modifier = Modifier) {
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
private fun EditTenantDialog(
    summary: TenantSummary,
    onDismiss: () -> Unit,
    onSave: (TenantRepository.TenantDraft) -> Unit,
) {
    var name by remember { mutableStateOf(summary.tenant.name) }
    var mobile by remember { mutableStateOf(summary.tenant.mobile) }
    var alternateMobile by remember { mutableStateOf(summary.tenant.alternateMobile) }
    var address by remember { mutableStateOf(summary.tenant.address) }
    var idType by remember { mutableStateOf(summary.tenant.idType) }
    var idNumber by remember { mutableStateOf(summary.tenant.idNumber) }
    var notes by remember { mutableStateOf(summary.tenant.notes) }
    var idMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit tenant") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it },
                    label = { Text("Mobile") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = alternateMobile,
                    onValueChange = { alternateMobile = it },
                    label = { Text("Alternate mobile") },
                    singleLine = true,
                )
                Box {
                    OutlinedButton(onClick = { idMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("ID: ${idType.label()}", modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = idMenu, onDismissRequest = { idMenu = false }) {
                        IdType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.label()) },
                                onClick = {
                                    idType = type
                                    idMenu = false
                                },
                            )
                        }
                    }
                }
                if (idType != IdType.NONE) {
                    OutlinedTextField(
                        value = idNumber,
                        onValueChange = { idNumber = it },
                        label = { Text("ID number") },
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    minLines = 2,
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    minLines = 2,
                )
                Text(
                    "Historical bills keep the name and rent that applied when they were issued.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        TenantRepository.TenantDraft(
                            name = name,
                            mobile = mobile,
                            alternateMobile = alternateMobile,
                            address = address,
                            idType = idType,
                            idNumber = idNumber,
                            notes = notes,
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun StartTenancyDialog(
    vacantRooms: List<RoomOccupancy>,
    onDismiss: () -> Unit,
    onStart: (UUID, Long, Long, LocalDate, String) -> Unit,
) {
    var roomId by remember { mutableStateOf<UUID?>(vacantRooms.firstOrNull()?.room?.id) }
    var rent by remember { mutableStateOf("") }
    var deposit by remember { mutableStateOf("0") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start tenancy") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                if (vacantRooms.isEmpty()) {
                    Text(
                        "No vacant rooms. End an existing tenancy before starting a new one.",
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    vacantRooms.forEach { occupancy ->
                        FilterChip(
                            selected = roomId == occupancy.room.id,
                            onClick = {
                                roomId = occupancy.room.id
                                if (rent.isBlank()) {
                                    rent = (occupancy.room.defaultRentPaise / 100.0).toString()
                                }
                            },
                            label = { Text("Room ${occupancy.room.roomNumber}") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    OutlinedTextField(
                        value = rent,
                        onValueChange = { rent = it },
                        label = { Text("Monthly rent (₹)") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = deposit,
                        onValueChange = { deposit = it },
                        label = { Text("Security deposit (₹)") },
                        singleLine = true,
                    )
                    DateField(
                        label = "Joining date",
                        date = date,
                        onDateChange = { date = it },
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes") },
                        minLines = 2,
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
                    val id = roomId
                    val rentPaise = parseMoneyToPaise(rent)
                    val depositPaise = parseMoneyToPaise(deposit) ?: 0L
                    if (id == null) {
                        error = "Select a vacant room"
                    } else if (rentPaise == null || rentPaise <= 0) {
                        error = "Enter a valid monthly rent"
                    } else if (depositPaise < 0) {
                        error = "Deposit cannot be negative"
                    } else {
                        onStart(id, rentPaise, depositPaise, date, notes)
                    }
                },
                enabled = vacantRooms.isNotEmpty(),
            ) { Text("Start") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
