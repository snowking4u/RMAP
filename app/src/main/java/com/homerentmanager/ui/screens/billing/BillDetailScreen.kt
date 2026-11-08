package com.homerentmanager.ui.screens.billing

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homerentmanager.AppContainer
import com.homerentmanager.data.entity.PaymentMethod
import com.homerentmanager.data.model.BillWithDetails
import com.homerentmanager.ui.components.DateField
import com.homerentmanager.ui.components.DetailRow
import com.homerentmanager.ui.components.EmptyState
import com.homerentmanager.ui.components.HrmScaffold
import com.homerentmanager.ui.components.LabeledValue
import com.homerentmanager.ui.components.LoadingBox
import com.homerentmanager.ui.components.SectionHeader
import com.homerentmanager.ui.components.StatusChip
import com.homerentmanager.ui.hrmViewModel
import com.homerentmanager.ui.viewmodel.BillDetailViewModel
import com.homerentmanager.util.PdfActions
import com.homerentmanager.util.formatDisplay
import com.homerentmanager.util.formatUnits
import com.homerentmanager.util.label
import com.homerentmanager.util.monthLabel
import com.homerentmanager.util.parseDecimal
import com.homerentmanager.util.parseMoneyToPaise
import com.homerentmanager.util.toRupees
import com.homerentmanager.util.toRupeesNoDecimals
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

@Composable
fun BillDetailScreen(
    container: AppContainer,
    billId: UUID,
    onBack: () -> Unit,
    onOpenTenant: (UUID) -> Unit,
    onOpenRoom: (UUID) -> Unit,
) {
    val viewModel = hrmViewModel(key = billId.toString()) { BillDetailViewModel(it, billId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var paymentOpen by remember { mutableStateOf(false) }
    var receiptSaveId by remember { mutableStateOf<UUID?>(null) }

    val saveInvoiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri: Uri? -> if (uri != null) viewModel.saveInvoice(uri) }

    val saveReceiptLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri: Uri? ->
        val id = receiptSaveId
        if (uri != null && id != null) viewModel.saveReceipt(uri, id)
        receiptSaveId = null
    }

    fun shareInvoice() {
        scope.launch {
            viewModel.buildInvoiceFile()?.let { PdfActions.share(context, it) }
        }
    }

    fun printInvoice() {
        scope.launch {
            val detail = state.detail ?: return@launch
            viewModel.buildInvoiceFile()?.let {
                PdfActions.print(context, it, "Invoice ${detail.bill.billNumber}")
            }
        }
    }

    fun shareReceipt(paymentId: UUID) {
        scope.launch {
            viewModel.buildReceiptFile(paymentId)?.let { PdfActions.share(context, it) }
        }
    }

    fun printReceipt(paymentId: UUID) {
        scope.launch {
            viewModel.buildReceiptFile(paymentId)?.let {
                PdfActions.print(context, it, "Receipt ${it.nameWithoutExtension}")
            }
        }
    }

    HrmScaffold(
        title = state.detail?.let { "Invoice ${it.bill.billNumber}" } ?: "Bill",
        subtitle = state.detail?.let { monthLabel(it.bill.billingMonth) },
        message = message,
        onMessageShown = viewModel::messageShown,
        onBack = onBack,
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@HrmScaffold
        }
        val detail = state.detail
        if (detail == null) {
            EmptyState(
                icon = Icons.Outlined.ReceiptLong,
                title = "Bill not found",
                message = "This bill may no longer be available.",
                modifier = Modifier.padding(padding),
                actionLabel = "Back",
                onAction = onBack,
            )
            return@HrmScaffold
        }

        val bill = detail.bill
        val outstanding = (bill.totalPaise - bill.paidPaise).coerceAtLeast(0)
        val fraction = if (bill.totalPaise <= 0) 0f else
            (bill.paidPaise.toFloat() / bill.totalPaise).coerceIn(0f, 1f)

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                monthLabel(bill.billingMonth),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                bill.status.label(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            bill.totalPaise.toRupees(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Balance ${outstanding.toRupees()} · Due ${bill.dueDate.formatDisplay()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        )
                        Spacer(Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Paid ${bill.paidPaise.toRupeesNoDecimals()} of " +
                                bill.totalPaise.toRupeesNoDecimals(),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            saveInvoiceLauncher.launch("Invoice-${bill.billNumber}.pdf")
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }
                    OutlinedButton(onClick = { shareInvoice() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Share, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Share")
                    }
                    OutlinedButton(onClick = { printInvoice() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Print, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Print")
                    }
                }
            }

            item {
                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Room ${detail.room.roomNumber}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { onOpenRoom(detail.room.id) }) { Text("Open room") }
                        }
                        Text(
                            detail.tenant.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            detail.tenant.mobile.ifBlank { "No mobile number" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { onOpenTenant(detail.tenant.id) }) {
                            Text("Open tenant profile")
                        }
                        DetailRow("Bill date", bill.billDate.formatDisplay())
                        DetailRow("Due date", bill.dueDate.formatDisplay())
                        DetailRow("Tenancy joined", detail.tenancy.joinDate.formatDisplay())
                    }
                }
            }

            item {
                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionHeader(title = "Charges")
                        LabeledValue("Rent", bill.rentPaise.toRupees())
                        if (bill.electricityUnits > 0 || bill.electricityAmountPaise > 0) {
                            LabeledValue(
                                "Electricity",
                                "${bill.electricityUnits.formatUnits()} units × " +
                                    "₹${bill.electricityRate} = " +
                                    bill.electricityAmountPaise.toRupees(),
                            )
                        }
                        if (bill.additionalChargesPaise != 0L) {
                            LabeledValue(
                                "Additional charges",
                                bill.additionalChargesPaise.toRupees(),
                            )
                        }
                        if (bill.discountPaise != 0L) {
                            LabeledValue("Discount", "-${bill.discountPaise.toRupees()}")
                        }
                        Spacer(Modifier.height(4.dp))
                        LabeledValue(
                            "Total",
                            bill.totalPaise.toRupees(),
                            emphasize = true,
                        )
                        LabeledValue("Paid", bill.paidPaise.toRupees())
                        LabeledValue(
                            "Outstanding",
                            outstanding.toRupees(),
                            valueColor = if (outstanding > 0) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            emphasize = true,
                        )
                        if (bill.notes != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                bill.notes.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                if (outstanding > 0) {
                    Button(
                        onClick = { paymentOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Record payment")
                    }
                } else {
                    StatusChip(bill.status, modifier = Modifier.fillMaxWidth())
                }
            }

            item {
                SectionHeader(
                    title = "Payments",
                    actionLabel = if (state.payments.isNotEmpty()) "${state.payments.size}" else null,
                )
            }

            if (state.payments.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Payments,
                        title = "No payments recorded",
                        message = "Record a partial or full payment to update this bill automatically.",
                    )
                }
            } else {
                items(state.payments, key = { it.payment.id }) { payment ->
                    OutlinedCard(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(13.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        payment.payment.amountPaise.toRupees(),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        "${payment.payment.paidAt.formatDisplay()} · " +
                                            payment.payment.method.label(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "Receipt ${payment.payment.receiptNumber}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
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
                            if (payment.payment.reference.isNotBlank()) {
                                Text(
                                    "Ref: ${payment.payment.reference}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    if (paymentOpen && state.detail != null) {
        RecordPaymentDialog(
            detail = state.detail!!,
            onDismiss = { paymentOpen = false },
            onRecord = { amount, date, method, reference, notes ->
                viewModel.recordPayment(amount, date, method, reference, notes)
                paymentOpen = false
            },
        )
    }

    state.lastPaymentId?.let { id ->
        AlertDialog(
            onDismissRequest = viewModel::clearLastPayment,
            title = { Text("Payment recorded") },
            text = {
                Text(
                    "The bill balance has been updated. You can now save, share or print the receipt.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        receiptSaveId = id
                        saveReceiptLauncher.launch("Receipt.pdf")
                        viewModel.clearLastPayment()
                    },
                ) { Text("Save receipt") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::clearLastPayment) { Text("Close") }
            },
        )
    }
}

@Composable
private fun RecordPaymentDialog(
    detail: BillWithDetails,
    onDismiss: () -> Unit,
    onRecord: (Long, LocalDate, PaymentMethod, String, String) -> Unit,
) {
    val outstanding = (detail.bill.totalPaise - detail.bill.paidPaise).coerceAtLeast(0)
    var amount by remember { mutableStateOf((outstanding / 100.0).toString()) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    var reference by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var methodMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record payment") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text(
                    "Outstanding ${outstanding.toRupees()} for room " +
                        detail.room.roomNumber,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                )
                DateField(
                    label = "Payment date",
                    date = date,
                    onDateChange = { date = it },
                )
                Box {
                    OutlinedButton(onClick = { methodMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Method: ${method.label()}", modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = methodMenu, onDismissRequest = { methodMenu = false }) {
                        PaymentMethod.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label()) },
                                onClick = {
                                    method = option
                                    methodMenu = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = reference,
                    onValueChange = { reference = it },
                    label = { Text("Reference / transaction id") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    minLines = 2,
                )
                if (error != null) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = parseMoneyToPaise(amount)
                    error = when {
                        parsed == null || parsed <= 0 -> "Enter a valid amount"
                        parsed > outstanding -> "Amount cannot exceed the outstanding balance"
                        else -> null
                    }
                    if (error == null) {
                        onRecord(parsed!!, date, method, reference, notes)
                    }
                },
            ) { Text("Record") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
