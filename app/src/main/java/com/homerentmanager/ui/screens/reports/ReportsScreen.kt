package com.homerentmanager.ui.screens.reports

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Icon
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
import com.homerentmanager.backup.BackupManager
import com.homerentmanager.ui.components.EmptyState
import com.homerentmanager.ui.components.HrmIcons
import com.homerentmanager.ui.components.HrmScaffold
import com.homerentmanager.ui.components.LabeledValue
import com.homerentmanager.ui.components.LoadingBox
import com.homerentmanager.ui.components.MonthSwitcher
import com.homerentmanager.ui.components.SectionHeader
import com.homerentmanager.ui.components.StatGrid
import com.homerentmanager.ui.components.StatSpec
import com.homerentmanager.ui.components.hrmPagePadding
import com.homerentmanager.ui.hrmViewModel
import com.homerentmanager.ui.viewmodel.ReportRoomRow
import com.homerentmanager.ui.viewmodel.ReportsViewModel
import com.homerentmanager.util.label
import com.homerentmanager.util.monthLabel
import com.homerentmanager.util.toRupeesNoDecimals

@Composable
fun ReportsScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel = hrmViewModel { ReportsViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var pendingCsv by remember { mutableStateOf(BackupManager.CsvKind.BILLS) }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri: Uri? -> if (uri != null) viewModel.exportCsv(uri, pendingCsv) }

    HrmScaffold(
        title = "Reports",
        subtitle = "Monthly performance, dues and exports",
        message = message,
        onMessageShown = viewModel::messageShown,
        onBack = onBack,
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
                    onToday = { viewModel.shiftMonth(0) },
                )
            }
            item {
                StatGrid(
                    specs = listOf(
                        StatSpec(
                            label = "Billed",
                            value = state.billedPaise.toRupeesNoDecimals(),
                            supporting = monthLabel(state.month),
                        ),
                        StatSpec(
                            label = "Collected",
                            value = state.collectedPaise.toRupeesNoDecimals(),
                            supporting = "Payments in month",
                        ),
                        StatSpec(
                            label = "Month outstanding",
                            value = state.outstandingMonthPaise.toRupeesNoDecimals(),
                            supporting = "Unpaid for month",
                            accent = MaterialTheme.colorScheme.error,
                        ),
                        StatSpec(
                            label = "Total outstanding",
                            value = state.outstandingAllPaise.toRupeesNoDecimals(),
                            supporting = "All unpaid bills",
                            accent = MaterialTheme.colorScheme.error,
                        ),
                        StatSpec(
                            label = "Occupancy",
                            value = "${state.occupiedRooms}/${state.totalRooms}",
                            supporting = "Active tenancies",
                        ),
                        StatSpec(
                            label = "Deposits held",
                            value = state.activeDepositsPaise.toRupeesNoDecimals(),
                            supporting = "Active tenancies",
                        ),
                    ),
                )
            }

            item {
                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        SectionHeader(title = "Outstanding ageing")
                        LabeledValue("Not due yet", state.aging.notDuePaise.toRupeesNoDecimals())
                        LabeledValue(
                            "1–30 days overdue",
                            state.aging.days1To30Paise.toRupeesNoDecimals(),
                        )
                        LabeledValue(
                            "31–60 days overdue",
                            state.aging.days31To60Paise.toRupeesNoDecimals(),
                        )
                        LabeledValue(
                            "61+ days overdue",
                            state.aging.days61PlusPaise.toRupeesNoDecimals(),
                        )
                        Spacer(Modifier.height(4.dp))
                        LabeledValue(
                            "Total",
                            state.aging.totalPaise.toRupeesNoDecimals(),
                            emphasize = true,
                        )
                    }
                }
            }

            item { SectionHeader(title = "Room performance") }
            items(state.roomRows, key = { it.roomNumber }) { row ->
                RoomReportRow(row)
            }

            item { SectionHeader(title = "Payments by method") }
            if (state.methodRows.isEmpty()) {
                item {
                    EmptyState(
                        icon = HrmIcons.Payments,
                        title = "No payments this month",
                        message = "Method breakdown appears once payments are recorded.",
                    )
                }
            } else {
                items(state.methodRows, key = { it.method.name }) { row ->
                    OutlinedCard(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                row.method.label(),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${row.count} payment(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                row.amountPaise.toRupeesNoDecimals(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            item { SectionHeader(title = "Export CSV") }
            items(BackupManager.CsvKind.entries, key = { it.name }) { kind ->
                OutlinedButton(
                    onClick = {
                        pendingCsv = kind
                        csvLauncher.launch("${kind.fileStem}.csv")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(HrmIcons.DownloadPdf, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Export ${kind.label}")
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun RoomReportRow(row: ReportRoomRow) {
    OutlinedCard(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Room ${row.roomNumber}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    row.floor,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (row.occupied) row.tenantName else "Vacant",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row {
                MiniStat("Billed", row.billedPaise.toRupeesNoDecimals(), Modifier.weight(1f))
                MiniStat("Collected", row.collectedPaise.toRupeesNoDecimals(), Modifier.weight(1f))
                MiniStat(
                    "Outstanding",
                    row.outstandingPaise.toRupeesNoDecimals(),
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
