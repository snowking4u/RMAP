package com.homerentmanager.ui.screens.payments

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homerentmanager.AppContainer
import com.homerentmanager.ui.components.EmptyState
import com.homerentmanager.ui.components.HrmScaffold
import com.homerentmanager.ui.components.LoadingBox
import com.homerentmanager.ui.components.MonthSwitcher
import com.homerentmanager.ui.components.StatCard
import com.homerentmanager.ui.components.Tag
import com.homerentmanager.ui.hrmViewModel
import com.homerentmanager.ui.viewmodel.PaymentsViewModel
import com.homerentmanager.util.PdfActions
import com.homerentmanager.util.currentMonth
import com.homerentmanager.util.formatDisplay
import com.homerentmanager.util.label
import com.homerentmanager.util.monthLabel
import com.homerentmanager.util.shiftMonth
import com.homerentmanager.util.toRupees
import com.homerentmanager.util.toRupeesNoDecimals
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun PaymentsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenBill: (UUID) -> Unit,
) {
    val viewModel = hrmViewModel { PaymentsViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var receiptSaveId by remember { mutableStateOf<UUID?>(null) }

    val saveReceiptLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri: Uri? ->
        val id = receiptSaveId
        if (uri != null && id != null) viewModel.saveReceipt(uri, id)
        receiptSaveId = null
    }

    fun shareReceipt(paymentId: UUID) {
        scope.launch {
            viewModel.buildReceiptFile(paymentId)?.let { PdfActions.share(context, it) }
        }
    }

    fun printReceipt(paymentId: UUID) {
        scope.launch {
            viewModel.buildReceiptFile(paymentId)?.let {
                PdfActions.print(context, it, it.nameWithoutExtension)
            }
        }
    }

    HrmScaffold(
        title = "Payments",
        subtitle = state.monthFilter?.let { monthLabel(it) } ?: "All recorded payments",
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                MonthSwitcher(
                    month = state.monthFilter ?: currentMonth(),
                    onPrevious = {
                        viewModel.setMonthFilter(
                            shiftMonth(state.monthFilter ?: currentMonth(), -1),
                        )
                    },
                    onNext = {
                        viewModel.setMonthFilter(
                            shiftMonth(state.monthFilter ?: currentMonth(), 1),
                        )
                    },
                    onToday = { viewModel.setMonthFilter(currentMonth()) },
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { viewModel.setMonthFilter(null) }) {
                        Text("Show all time")
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${state.payments.size} payment(s)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                StatCard(
                    label = "Total received",
                    value = state.totalPaise.toRupeesNoDecimals(),
                    supporting = state.monthFilter?.let { "In ${monthLabel(it)}" } ?: "All time",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.payments.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Payments,
                        title = "No payments recorded",
                        message = "Payments recorded against any bill will be listed here with receipts.",
                    )
                }
            } else {
                items(state.payments, key = { it.payment.id }) { payment ->
                    val room = state.rooms[payment.bill.roomId]
                    Card(
                        onClick = { onOpenBill(payment.bill.id) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        payment.tenant.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "${payment.payment.paidAt.formatDisplay()} · " +
                                            payment.payment.method.label(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    payment.payment.amountPaise.toRupees(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Tag("Room ${room?.roomNumber ?: "—"}")
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    payment.bill.billNumber,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.weight(1f))
                                IconButton(
                                    onClick = {
                                        receiptSaveId = payment.payment.id
                                        saveReceiptLauncher.launch(
                                            "Receipt-${payment.payment.receiptNumber}.pdf",
                                        )
                                    },
                                ) {
                                    Icon(Icons.Outlined.Download, contentDescription = "Save receipt")
                                }
                                IconButton(onClick = { shareReceipt(payment.payment.id) }) {
                                    Icon(Icons.Outlined.Share, contentDescription = "Share receipt")
                                }
                                IconButton(onClick = { printReceipt(payment.payment.id) }) {
                                    Icon(Icons.Outlined.Print, contentDescription = "Print receipt")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
