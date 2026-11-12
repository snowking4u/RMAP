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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homerentmanager.AppContainer
import com.homerentmanager.data.model.RoomOccupancy
import com.homerentmanager.ui.components.ConfirmDialog
import com.homerentmanager.ui.components.HrmDialog
import com.homerentmanager.ui.components.HrmIcons
import com.homerentmanager.ui.components.HrmScaffold
import com.homerentmanager.ui.components.LoadingBox
import com.homerentmanager.ui.components.StatGrid
import com.homerentmanager.ui.components.StatSpec
import com.homerentmanager.ui.components.Tag
import com.homerentmanager.ui.components.hrmPagePadding
import com.homerentmanager.ui.hrmViewModel
import com.homerentmanager.ui.viewmodel.RoomsViewModel
import com.homerentmanager.util.formatDisplayOrDash
import com.homerentmanager.util.formatRate
import com.homerentmanager.util.parseDecimal
import com.homerentmanager.util.parseMoneyToPaise
import com.homerentmanager.util.toRupeesNoDecimals
import java.util.UUID

@Composable
fun RoomsScreen(
    container: AppContainer,
    onOpenRoom: (UUID) -> Unit,
) {
    val viewModel = hrmViewModel { RoomsViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<RoomOccupancy?>(null) }
    var adding by remember { mutableStateOf(false) }
    var archiving by remember { mutableStateOf<RoomOccupancy?>(null) }
    var deleting by remember { mutableStateOf<RoomOccupancy?>(null) }

    val activeRooms = state.rooms.filter { it.room.isActive }
    val archivedRooms = state.rooms.size - activeRooms.size
    val occupied = activeRooms.count { it.activeTenancyId != null }

    HrmScaffold(
        title = "Rooms",
        subtitle = buildString {
            append("${activeRooms.size} active room(s)")
            if (archivedRooms > 0) append(" · $archivedRooms archived")
        },
        message = message,
        onMessageShown = viewModel::messageShown,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(HrmIcons.AddRoom, contentDescription = null) },
                text = { Text("Add room") },
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
                StatGrid(
                    specs = listOf(
                        StatSpec(
                            label = "Occupied",
                            value = occupied.toString(),
                            supporting = "Rooms with a tenant",
                            icon = HrmIcons.Rooms,
                        ),
                        StatSpec(
                            label = "Vacant",
                            value = (activeRooms.size - occupied).toString(),
                            supporting = "Ready to rent",
                            icon = HrmIcons.AddRoom,
                        ),
                    ),
                )
            }

            items(state.rooms, key = { it.room.id }) { occupancy ->
                RoomCard(
                    occupancy = occupancy,
                    onOpen = { onOpenRoom(occupancy.room.id) },
                    onEdit = { editing = occupancy },
                    onToggleActive = {
                        if (occupancy.room.isActive) {
                            archiving = occupancy
                        } else {
                            viewModel.setRoomActive(occupancy.room.id, true)
                        }
                    },
                    onDelete = { deleting = occupancy },
                )
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    editing?.let { room ->
        RoomEditDialog(
            occupancy = room,
            onDismiss = { editing = null },
            onSave = { number, floor, rentPaise, rate, notes, onResult ->
                viewModel.updateRoom(room.room.id, number, floor, rentPaise, rate, notes, onResult)
            },
        )
    }

    if (adding) {
        AddRoomDialog(
            onDismiss = { adding = false },
            onSave = { number, floor, rentPaise, rate, notes, onResult ->
                viewModel.addRoom(number, floor, rentPaise, rate, notes, onResult)
            },
        )
    }

    archiving?.let { room ->
        ConfirmDialog(
            title = "Archive room ${room.room.roomNumber}?",
            message = "The room will be hidden from new tenancies. All bills, payments and " +
                "tenancy history stay safe and the room can be restored later.",
            confirmLabel = "Archive",
            onConfirm = {
                viewModel.setRoomActive(room.room.id, false)
                archiving = null
            },
            onDismiss = { archiving = null },
        )
    }

    deleting?.let { room ->
        ConfirmDialog(
            title = "Delete room ${room.room.roomNumber}?",
            message = "This permanently deletes the room only if it has no tenants, tenancies, " +
                "bills, payments or meter readings. Rooms with history are blocked; archive " +
                "them instead to keep their data safe.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.deleteRoom(room.room.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun RoomCard(
    occupancy: RoomOccupancy,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit,
) {
    val room = occupancy.room
    val active = room.isActive
    val occupied = occupancy.activeTenancyId != null
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (occupied && active) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        room.roomNumber,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        room.floor.ifBlank { "Floor not set" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Rent ${room.defaultRentPaise.toRupeesNoDecimals()} · " +
                            "${room.defaultElectricityRate.formatRate()}/unit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Tag(
                    text = when {
                        !active -> "Archived"
                        occupied -> "Occupied"
                        else -> "Vacant"
                    },
                    color = if (occupied && active) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    container = if (occupied && active) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            when {
                !active -> Text(
                    "Archived room. Its bills and tenancy history are kept. Restore it to assign a tenant again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                occupied -> {
                    Text(
                        occupancy.activeTenantName.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Since ${occupancy.activeJoinDate.formatDisplayOrDash()} · " +
                            "${(occupancy.activeRentPaise ?: 0).toRupeesNoDecimals()}/month",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.PersonOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "No active tenant. Tap to add one from the Tenants screen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onEdit) {
                    Icon(HrmIcons.EditRoom, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Edit")
                }
                TextButton(onClick = onToggleActive) {
                    Icon(
                        if (active) HrmIcons.Archive else HrmIcons.Unarchive,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (active) "Archive" else "Restore")
                }
                TextButton(onClick = onDelete) {
                    Icon(
                        HrmIcons.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
fun RoomEditDialog(
    occupancy: RoomOccupancy,
    onDismiss: () -> Unit,
    onSave: (
        number: String,
        floor: String,
        rentPaise: Long,
        rate: Double,
        notes: String,
        onResult: (Boolean) -> Unit,
    ) -> Unit,
) {
    RoomFormDialog(
        title = "Edit room",
        initial = occupancy,
        confirmLabel = "Save",
        helper = "Room number changes update this room only. Historical bills keep the room they were issued for.",
        onDismiss = onDismiss,
        onSave = onSave,
    )
}

@Composable
private fun AddRoomDialog(
    onDismiss: () -> Unit,
    onSave: (
        number: String,
        floor: String,
        rentPaise: Long,
        rate: Double,
        notes: String,
        onResult: (Boolean) -> Unit,
    ) -> Unit,
) {
    RoomFormDialog(
        title = "Add room",
        initial = null,
        confirmLabel = "Add room",
        helper = "New rooms start active. You can archive them later without deleting any history.",
        onDismiss = onDismiss,
        onSave = onSave,
    )
}

@Composable
private fun RoomFormDialog(
    title: String,
    initial: RoomOccupancy?,
    confirmLabel: String,
    helper: String,
    onDismiss: () -> Unit,
    onSave: (
        number: String,
        floor: String,
        rentPaise: Long,
        rate: Double,
        notes: String,
        onResult: (Boolean) -> Unit,
    ) -> Unit,
) {
    val room = initial?.room
    var number by remember { mutableStateOf(room?.roomNumber.orEmpty()) }
    var floor by remember { mutableStateOf(room?.floor.orEmpty()) }
    var rent by remember {
        mutableStateOf(room?.defaultRentPaise?.let { (it / 100.0).toString() }.orEmpty())
    }
    var rate by remember {
        mutableStateOf(room?.defaultElectricityRate?.toString() ?: "8.0")
    }
    var notes by remember { mutableStateOf(room?.notes.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    HrmDialog(
        title = title,
        confirmLabel = confirmLabel,
        onDismiss = onDismiss,
        busy = saving,
        onConfirm = {
            val rentPaise = parseMoneyToPaise(rent)
            val parsedRate = parseDecimal(rate)
            error = when {
                number.isBlank() -> "Room number is required"
                rentPaise == null || rentPaise <= 0 -> "Enter a valid default rent"
                parsedRate == null || parsedRate < 0 -> "Enter a valid electricity rate"
                else -> null
            }
            if (error == null) {
                saving = true
                onSave(number.trim(), floor.trim(), rentPaise!!, parsedRate!!, notes.trim()) { ok ->
                    saving = false
                    if (ok) {
                        onDismiss()
                    } else {
                        error = "Could not save the room. Check the details and try again."
                    }
                }
            }
        },
    ) {
        OutlinedTextField(
            value = number,
            onValueChange = { number = it },
            label = { Text("Room number *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = floor,
            onValueChange = { floor = it },
            label = { Text("Floor") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = rent,
            onValueChange = { rent = it },
            label = { Text("Default rent (₹) *") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = rate,
            onValueChange = { rate = it },
            label = { Text("Electricity rate (₹/unit) *") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Text(
                error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            helper,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
