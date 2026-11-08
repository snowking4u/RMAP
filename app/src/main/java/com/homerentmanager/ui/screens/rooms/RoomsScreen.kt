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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

    HrmScaffold(
        title = "Rooms",
        subtitle = "Exactly seven rooms are managed by this app",
        message = message,
        onMessageShown = viewModel::messageShown,
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@HrmScaffold
        }
        val occupied = state.rooms.count { it.activeTenancyId != null }
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
                        value = (state.rooms.size - occupied).toString(),
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
                )
            }
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
}

@Composable
private fun RoomCard(
    occupancy: RoomOccupancy,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
) {
    val room = occupancy.room
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
                        containerColor = if (occupied) {
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
                    text = if (occupied) "Occupied" else "Vacant",
                    color = if (occupied) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    container = if (occupied) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                )
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit room")
                }
            }
            Spacer(Modifier.height(10.dp))
            if (occupied) {
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
            } else {
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

@Composable
fun RoomEditDialog(
    occupancy: RoomOccupancy,
    onDismiss: () -> Unit,
    onSave: (number: String, floor: String, rentPaise: Long, rate: Double, notes: String) -> Unit,
) {
    val room = occupancy.room
    var number by remember { mutableStateOf(room.roomNumber) }
    var floor by remember { mutableStateOf(room.floor) }
    var rent by remember { mutableStateOf((room.defaultRentPaise / 100.0).toString()) }
    var rate by remember { mutableStateOf(room.defaultElectricityRate.toString()) }
    var notes by remember { mutableStateOf(room.notes) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit room") },
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
                    "Room number changes update this room only. Historical bills keep the room they were issued for.",
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
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
