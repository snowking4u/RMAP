package com.homerentmanager.ui.screens.dashboard

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
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homerentmanager.AppContainer
import com.homerentmanager.data.model.PaymentWithDetails
import com.homerentmanager.ui.Routes
import com.homerentmanager.ui.components.EmptyState
import com.homerentmanager.ui.components.HrmScaffold
import com.homerentmanager.ui.components.LoadingBox
import com.homerentmanager.ui.components.SectionHeader
import com.homerentmanager.ui.components.StatCard
import com.homerentmanager.ui.components.StatusChip
import com.homerentmanager.ui.hrmViewModel
import com.homerentmanager.ui.viewmodel.DashboardViewModel
import com.homerentmanager.util.formatDisplay
import com.homerentmanager.util.formatDisplayDateTimeOrDash
import com.homerentmanager.util.label
import com.homerentmanager.util.monthLabel
import com.homerentmanager.util.toRupees
import com.homerentmanager.util.toRupeesNoDecimals
import java.util.UUID

@Composable
fun DashboardScreen(
    container: AppContainer,
    onNavigate: (String) -> Unit,
    onOpenBill: (UUID) -> Unit,
) {
    val viewModel = hrmViewModel { DashboardViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    HrmScaffold(
        title = "Home Rent Manager",
        subtitle = state.propertyName,
        message = message,
        onMessageShown = viewModel::messageShown,
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
                CollectionHero(
                    month = state.month,
                    billed = state.billedPaise,
                    collected = state.collectedPaise,
                    outstanding = state.outstandingMonthPaise,
                )
            }

            if (state.needsBackup) {
                item {
                    BackupReminderCard(
                        lastBackup = state.lastBackupAt.formatDisplayDateTimeOrDash(),
                        onClick = { onNavigate(Routes.BACKUP) },
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(
                        label = "Occupied rooms",
                        value = "${state.occupiedRooms}/${state.totalRooms}",
                        icon = Icons.Outlined.MeetingRoom,
                        supporting = if (state.occupiedRooms == state.totalRooms) "All rooms occupied" else
                            "${state.totalRooms - state.occupiedRooms} vacant",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(Routes.ROOMS) },
                    )
                    StatCard(
                        label = "Active tenants",
                        value = state.activeTenants.toString(),
                        icon = Icons.Outlined.People,
                        supporting = "Currently living",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(Routes.TENANTS) },
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(
                        label = "Collected this month",
                        value = state.collectedPaise.toRupeesNoDecimals(),
                        icon = Icons.Outlined.Payments,
                        accent = MaterialTheme.colorScheme.tertiary,
                        supporting = "Payments received",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(Routes.PAYMENTS) },
                    )
                    StatCard(
                        label = "Outstanding total",
                        value = state.outstandingTotalPaise.toRupeesNoDecimals(),
                        icon = Icons.Outlined.WarningAmber,
                        accent = MaterialTheme.colorScheme.error,
                        supporting = "Carry forward ${state.carryForwardPaise.toRupeesNoDecimals()}",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(Routes.BILLING) },
                    )
                }
            }

            item {
                SectionHeader(
                    title = "Quick actions",
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction(
                        label = "Billing",
                        icon = Icons.Outlined.ReceiptLong,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(Routes.BILLING) },
                    )
                    QuickAction(
                        label = "Electricity",
                        icon = Icons.Outlined.ElectricBolt,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(Routes.ELECTRICITY) },
                    )
                    QuickAction(
                        label = "Reports",
                        icon = Icons.Outlined.Schedule,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(Routes.REPORTS) },
                    )
                    QuickAction(
                        label = "Backup",
                        icon = Icons.Outlined.Backup,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(Routes.BACKUP) },
                    )
                }
            }

            item {
                SectionHeader(
                    title = "Overdue bills",
                    actionLabel = if (state.overdueBills.isNotEmpty()) "View all" else null,
                    onAction = if (state.overdueBills.isNotEmpty()) {
                        { onNavigate(Routes.BILLING) }
                    } else {
                        null
                    },
                )
            }

            if (state.overdueBills.isEmpty()) {
                item {
                    OutlinedCard(shape = RoundedCornerShape(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "No overdue bills. Every due bill is either paid or not due yet.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            } else {
                items(state.overdueBills, key = { it.bill.id }) { detail ->
                    OverdueRow(detail) { onOpenBill(detail.bill.id) }
                }
            }

            item {
                SectionHeader(
                    title = "Recent payments",
                    actionLabel = "See all",
                    onAction = { onNavigate(Routes.PAYMENTS) },
                )
            }

            if (state.recentPayments.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Payments,
                        title = "No payments yet",
                        message = "Record a payment from any bill to see it here.",
                    )
                }
            } else {
                items(state.recentPayments, key = { it.payment.id }) { payment ->
                    PaymentRow(payment) { onOpenBill(payment.payment.billId) }
                }
            }

            item {
                SectionHeader(
                    title = "This month's bills",
                    actionLabel = "Open billing",
                    onAction = { onNavigate(Routes.BILLING) },
                )
            }

            if (state.recentBills.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.ReceiptLong,
                        title = "No bills for ${monthLabel(state.month)}",
                        message = "Generate monthly bills from the Billing screen.",
                        actionLabel = "Generate bills",
                        onAction = { onNavigate(Routes.BILLING) },
                    )
                }
            } else {
                items(state.recentBills, key = { it.bill.id }) { detail ->
                    BillRow(detail) { onOpenBill(detail.bill.id) }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun CollectionHero(
    month: String,
    billed: Long,
    collected: Long,
    outstanding: Long,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                monthLabel(month),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                collected.toRupees(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "collected of ${billed.toRupeesNoDecimals()} billed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(14.dp))
            val fraction = if (billed <= 0) 0f else (collected.toFloat() / billed).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f),
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Outstanding ${outstanding.toRupeesNoDecimals()}",
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${(fraction * 100).toInt()}% collected",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun BackupReminderCard(lastBackup: String, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Backup,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Backup recommended",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    "Last manual backup: $lastBackup. Create an encrypted backup to Google Drive.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onClick) { Text("Backup") }
        }
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            Modifier.padding(vertical = 14.dp, horizontal = 6.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun OverdueRow(
    detail: com.homerentmanager.data.model.BillWithDetails,
    onClick: () -> Unit,
) {
    OutlinedCard(onClick = onClick, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Room ${detail.room.roomNumber} · ${detail.tenant.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Due ${detail.bill.dueDate.formatDisplay()} · ${monthLabel(detail.bill.billingMonth)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (detail.bill.totalPaise - detail.bill.paidPaise).toRupeesNoDecimals(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
                StatusChip(detail.bill.status)
            }
        }
    }
}

@Composable
private fun PaymentRow(payment: PaymentWithDetails, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    payment.tenant.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${payment.payment.paidAt.formatDisplay()} · ${payment.payment.method.label()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                payment.payment.amountPaise.toRupees(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun BillRow(
    detail: com.homerentmanager.data.model.BillWithDetails,
    onClick: () -> Unit,
) {
    OutlinedCard(onClick = onClick, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Room ${detail.room.roomNumber} · ${detail.tenant.name}",
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
