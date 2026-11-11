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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homerentmanager.AppContainer
import com.homerentmanager.data.model.RoomOccupancy
import com.homerentmanager.ui.components.HrmScaffold
import com.homerentmanager.ui.components.LoadingBox
import com.homerentmanager.ui.components.StatCard
import com.homerentmanager.ui.components.Tag
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
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Add Room") },
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(
                        label = "Occupied",
                        value = occupied.toString(),
                        supporting = "Rooms with a tenant",
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = "Vacant",
                        value = (activeRooms.size - occupied).toString(),
                        supporting = "Ready to rent",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            items(state.rooms, key = { it.room.id }) { occupancy ->
                RoomCard(
                    occupancy = occupancy,
                    onOpen = { onOpenRoom(occupancy.room.id) },
                    onEdit = { editing = occupancy },
                    onToggleActive = {
                        viewModel.setRoomActive(occupancy.room.id, !occupancy.room.isActive)
                    },
                )
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    editing?.let { room ->
        RoomEditDialog(
            occupancy = room,
            onDismiss = { editing = null },
            onSave = { number, floor, rentPaise, rate, notes ->
                viewModel.updateRoom(room.room.id, number, floor, rentPaise, rate, notes)
                editing = null
            },
        )
    }

    if (adding) {
        AddRoomDialog(
            onDismiss = { adding = false },
            onSave = { number, floor, rentPaise, rate, notes ->
                viewModel.addRoom(number, floor, rentPaise, rate, notes)
                adding = false
            },
        )
    }
}

@Composable
private fun RoomCard(
    occupancy: RoomOccupancy,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
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
                        "Rent ${room.defaultRentPaise.toRupeesNoDecimals()} · ${room.defaultElectricityRate.formatRate()}/unit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit room")
                }
                IconButton(onClick = onToggleActive) {
                    Icon(
                        if (active) Icons.Outlined.Archive else Icons.Outlined.Unarchive,
                        contentDescription = if (active) "Archive room" else "Restore room",
                    )
                }
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
        }
    }
}

@Composable
fun RoomEditDialog(
    occupancy: RoomOccupancy,
    onDismiss: () -> Unit,
    onSave: (number: String, floor: String, rentPaise: Long, rate: Double, notes: String) -> Unit,
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
    onSave: (number: String, floor: String, rentPaise: Long, rate: Double, notes: String) -> Unit,
) {
    RoomFormDialog(
        title = "Add room",
        initial = null,
        confirmLabel = "Add Room",
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
    onSave: (number: String, floor: String, rentPaise: Long, rate: Double, notes: String) -> Unit,
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text("Room number") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = floor,
                    onValueChange = { floor = it },
                    label = { Text("Floor") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = rent,
                    onValueChange = { rent = it },
                    label = { Text("Default rent (₹)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = rate,
                    onValueChange = { rate = it },
                    label = { Text("Electricity rate (₹/unit)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    minLines = 2,
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
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val rentPaise = parseMoneyToPaise(rent)
                    val parsedRate = parseDecimal(rate)
                    if (number.isBlank()) {
                        error = "Room number is required"
                    } else if (rentPaise == null || rentPaise <= 0) {
                        error = "Enter a valid default rent"
                    } else if (parsedRate == null || parsedRate < 0) {
                        error = "Enter a valid electricity rate"
                    } else {
                        onSave(number.trim(), floor.trim(), rentPaise, parsedRate, notes.trim())
                    }
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
