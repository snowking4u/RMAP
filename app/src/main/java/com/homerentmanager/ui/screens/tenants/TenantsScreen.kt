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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.homerentmanager.data.entity.IdType
import com.homerentmanager.data.model.TenantSummary
import com.homerentmanager.data.repository.TenantRepository
import com.homerentmanager.ui.components.DateField
import com.homerentmanager.ui.components.EmptyState
import com.homerentmanager.ui.components.HrmDialog
import com.homerentmanager.ui.components.HrmIcons
import com.homerentmanager.ui.components.HrmScaffold
import com.homerentmanager.ui.components.LoadingBox
import com.homerentmanager.ui.components.SearchBox
import com.homerentmanager.ui.components.Tag
import com.homerentmanager.ui.components.hrmPagePadding
import com.homerentmanager.ui.hrmViewModel
import com.homerentmanager.ui.viewmodel.TenantsViewModel
import com.homerentmanager.util.Validators
import com.homerentmanager.util.formatDisplayOrDash
import com.homerentmanager.util.label
import com.homerentmanager.util.parseMoneyToPaise
import com.homerentmanager.util.toRupeesNoDecimals
import java.time.LocalDate
import java.util.UUID

@Composable
fun TenantsScreen(
    container: AppContainer,
    onOpenTenant: (UUID) -> Unit,
) {
    val viewModel = hrmViewModel { TenantsViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var addOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    HrmScaffold(
        title = "Tenants",
        subtitle = "Current and past tenants are kept safely",
        message = message,
        onMessageShown = viewModel::messageShown,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { addOpen = true },
                icon = { Icon(HrmIcons.Tenants, contentDescription = null) },
                text = { Text("Add tenant") },
            )
        },
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@HrmScaffold
        }
        val filtered = state.tenants.filter {
            query.isBlank() ||
                it.tenant.name.contains(query, ignoreCase = true) ||
                it.tenant.mobile.contains(query) ||
                it.activeRoomNumber.orEmpty().contains(query, ignoreCase = true)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = hrmPagePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SearchBox(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search name, mobile or room",
                )
            }
            if (filtered.isEmpty()) {
                item {
                    EmptyState(
                        icon = HrmIcons.Tenants,
                        title = if (state.tenants.isEmpty()) "No tenants yet" else "No matches",
                        message = if (state.tenants.isEmpty()) {
                            "Register a tenant and assign one of the vacant rooms."
                        } else {
                            "Try a different name, mobile number or room number."
                        },
                        actionLabel = if (state.tenants.isEmpty()) "Add tenant" else null,
                        onAction = if (state.tenants.isEmpty()) {
                            { addOpen = true }
                        } else {
                            null
                        },
                    )
                }
            } else {
                items(filtered, key = { it.tenant.id }) { summary ->
                    TenantCard(summary) { onOpenTenant(summary.tenant.id) }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (addOpen) {
        AddTenantDialog(
            vacantRooms = state.vacantRooms.map { it.room },
            onDismiss = { addOpen = false },
            onCreate = { draft, tenancy, onResult ->
                viewModel.createTenant(draft, tenancy, onResult)
            },
        )
    }
}

@Composable
private fun TenantCard(summary: TenantSummary, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Card(
                shape = RoundedCornerShape(50),
                colors = CardDefaults.cardColors(
                    containerColor = if (summary.activeTenancyId != null) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            ) {
                Icon(
                    HrmIcons.Tenants,
                    contentDescription = null,
                    modifier = Modifier.padding(9.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    summary.tenant.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    summary.tenant.mobile.ifBlank { "No mobile number" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (summary.activeTenancyId != null) {
                    Text(
                        "Room ${summary.activeRoomNumber} · since " +
                            summary.activeJoinDate.formatDisplayOrDash(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "No active tenancy",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Tag(
                    text = if (summary.activeTenancyId != null) "Active" else "Past",
                    color = if (summary.activeTenancyId != null) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    container = if (summary.activeTenancyId != null) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                )
                if (summary.activeRentPaise != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        summary.activeRentPaise.toRupeesNoDecimals(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
fun AddTenantDialog(
    vacantRooms: List<com.homerentmanager.data.entity.RoomEntity>,
    onDismiss: () -> Unit,
    onCreate: (
        TenantRepository.TenantDraft,
        TenantRepository.NewTenancy?,
        (Boolean) -> Unit,
    ) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var alternateMobile by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var idType by remember { mutableStateOf(IdType.NONE) }
    var idNumber by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var idMenu by remember { mutableStateOf(false) }

    var assignRoom by remember { mutableStateOf(false) }
    var selectedRoomId by remember { mutableStateOf<UUID?>(null) }
    var rent by remember { mutableStateOf("") }
    var deposit by remember { mutableStateOf("0") }
    var joinDate by remember { mutableStateOf(LocalDate.now()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    HrmDialog(
        title = "Add tenant",
        confirmLabel = "Save tenant",
        onDismiss = onDismiss,
        busy = saving,
        onConfirm = {
            val draft = TenantRepository.TenantDraft(
                name = name,
                mobile = mobile,
                alternateMobile = alternateMobile,
                address = address,
                idType = idType,
                idNumber = idNumber,
                notes = notes,
            )
            val rentPaise = parseMoneyToPaise(rent)
            val depositPaise = parseMoneyToPaise(deposit) ?: 0L
            error = when {
                name.trim().length < 2 -> "Enter the tenant's name"
                !Validators.isValidMobile(mobile) ->
                    "Enter a valid 10-digit mobile number or leave it blank"
                !Validators.isValidMobile(alternateMobile) ->
                    "Enter a valid alternate mobile number or leave it blank"
                assignRoom && vacantRooms.isEmpty() -> "No vacant room is available"
                assignRoom && selectedRoomId == null -> "Select a room"
                assignRoom && (rentPaise == null || rentPaise <= 0) ->
                    "Enter a valid monthly rent"
                assignRoom && depositPaise < 0 -> "Deposit cannot be negative"
                else -> null
            }
            if (error == null) {
                val tenancy = if (assignRoom) {
                    TenantRepository.NewTenancy(
                        roomId = selectedRoomId!!,
                        monthlyRentPaise = rentPaise!!,
                        securityDepositPaise = depositPaise,
                        joinDate = joinDate,
                    )
                } else {
                    null
                }
                saving = true
                onCreate(draft, tenancy) { ok ->
                    saving = false
                    if (ok) {
                        onDismiss()
                    } else {
                        error = "Could not save the tenant. Check the details and try again."
                    }
                }
            }
        },
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Full name *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = mobile,
            onValueChange = { mobile = it },
            label = { Text("Mobile (10 digits)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = alternateMobile,
            onValueChange = { alternateMobile = it },
            label = { Text("Alternate mobile") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Address") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
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
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth().clickable { assignRoom = !assignRoom },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = assignRoom, onCheckedChange = { assignRoom = it })
            Text("Assign a room now")
        }

        if (assignRoom) {
            if (vacantRooms.isEmpty()) {
                Text(
                    "All rooms are occupied. End a tenancy first, or save the tenant without a room.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                vacantRooms.forEach { room ->
                    FilterChip(
                        selected = selectedRoomId == room.id,
                        onClick = {
                            selectedRoomId = room.id
                            if (rent.isBlank()) {
                                rent = (room.defaultRentPaise / 100.0).toString()
                            }
                        },
                        label = { Text("Room ${room.roomNumber}") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            OutlinedTextField(
                value = rent,
                onValueChange = { rent = it },
                label = { Text("Monthly rent (₹) *") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = deposit,
                onValueChange = { deposit = it },
                label = { Text("Security deposit (₹)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            DateField(
                label = "Joining date",
                date = joinDate,
                onDateChange = { joinDate = it },
            )
        }

        if (error != null) {
            Text(
                error.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
