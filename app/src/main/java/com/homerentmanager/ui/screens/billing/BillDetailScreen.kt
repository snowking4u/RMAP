package com.homerentmanager.ui.screens.billing

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homerentmanager.AppContainer
import com.homerentmanager.data.entity.PaymentMethod
import com.homerentmanager.data.model.BillWithDetails
import com.homerentmanager.ui.components.DateField
import com.homerentmanager.ui.components.DetailRow
import com.homerentmanager.ui.components.EmptyState
import com.homerentmanager.ui.components.HrmDialog
import com.homerentmanager.ui.components.HrmIcons
import com.homerentmanager.ui.components.HrmScaffold
import com.homerentmanager.ui.components.LabeledValue
import com.homerentmanager.ui.components.LoadingBox
import com.homerentmanager.ui.components.SectionHeader
import com.homerentmanager.ui.components.StatusChip
import com.homerentmanager.ui.components.hrmPagePadding
import com.homerentmanager.ui.hrmViewModel
import com.homerentmanager.ui.viewmodel.BillDetailViewModel
import com.homerentmanager.util.PdfActions
import com.homerentmanager.util.formatDisplay
import com.homerentmanager.util.formatUnits
import com.homerentmanager.util.label
import com.homerentmanager.util.monthLabel
import com.homerentmanager.util.parseMoneyToPaise
import com.homerentmanager.util.toRupees
import com.homerentmanager.util.toRupeesNoDecimals
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
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
    var actionsOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var regenerateOpen by remember { mutableStateOf(false) }

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
        actions = {
            Box {
                IconButton(onClick = { actionsOpen = true }) {
                    Icon(HrmIcons.More, contentDescription = "Bill actions")
                }
                DropdownMenu(
                    expanded = actionsOpen,
                    onDismissRequest = { actionsOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Regenerate bill") },
                        onClick = {
                            actionsOpen = false
                            regenerateOpen = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete bill") },
                        onClick = {
                            actionsOpen = false
                            deleteOpen = true
                        },
                    )
                }
            }
        },
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@HrmScaffold
        }
        val detail = state.detail
        if (detail == null) {
            EmptyState(
                icon = HrmIcons.Billing,
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
        val fraction = if (bill.totalPaise <= 0) {
            0f
        } else {
            (bill.paidPaise.toFloat() / bill.totalPaise).coerceIn(0f, 1f)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = hrmPagePadding(),
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
                        Icon(HrmIcons.DownloadPdf, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }
                    OutlinedButton(onClick = { shareInvoice() }, modifier = Modifier.weight(1f)) {
                        Icon(HrmIcons.Share, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Share")
                    }
                    OutlinedButton(onClick = { printInvoice() }, modifier = Modifier.weight(1f)) {
                        Icon(HrmIcons.Print, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Print")
                    }
                }
            }

            item {
                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Room ${detail.room.roomNumber}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
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
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            TextButton(onClick = { onOpenRoom(detail.room.id) }) {
                                Text("Open room")
                            }
                            TextButton(onClick = { onOpenTenant(detail.tenant.id) }) {
                                Text("Open tenant profile")
                            }
                        }
                        DetailRow("Bill date", bill.billDate.formatDisplay())
                        DetailRow("Due date", bill.dueDate.formatDisplay())
                        DetailRow("Tenancy joined", detail.tenancy.joinDate.formatDisplay())
                    }
                }
            }

            item {
                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
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
                        Icon(HrmIcons.Payments, contentDescription = null)
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
                        icon = HrmIcons.Payments,
                        title = "No payments recorded",
                        message = "Record a partial or full payment to update this bill automatically.",
                    )
                }
            } else {
                items(state.payments, key = { it.payment.id }) { payment ->
                    OutlinedCard(
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(13.dp)) {
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
                            if (payment.payment.reference.isNotBlank()) {
                                Text(
                                    "Ref: ${payment.payment.reference}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                IconButton(
                                    onClick = {
                                        receiptSaveId = payment.payment.id
                                        saveReceiptLauncher.launch(
                                            "Receipt-${payment.payment.receiptNumber}.pdf",
                                        )
                                    },
                                ) {
                                    Icon(
                                        HrmIcons.DownloadPdf,
                                        contentDescription = "Save receipt",
                                    )
                                }
                                IconButton(onClick = { shareReceipt(payment.payment.id) }) {
                                    Icon(HrmIcons.Share, contentDescription = "Share receipt")
                                }
                                IconButton(onClick = { printReceipt(payment.payment.id) }) {
                                    Icon(HrmIcons.Print, contentDescription = "Print receipt")
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    if (deleteOpen) {
        val hasPayments = state.payments.isNotEmpty()
        HrmDialog(
            title = "Delete bill",
            confirmLabel = "Delete",
            onDismiss = { deleteOpen = false },
            confirmEnabled = !hasPayments,
            onConfirm = {
                viewModel.deleteBill { ok ->
                    deleteOpen = false
                    if (ok) onBack()
                }
            },
        ) {
            Text(
                if (hasPayments) {
                    "This bill has ${state.payments.size} payment record(s). " +
                        "It cannot be deleted because historical payments must be preserved."
                } else {
                    "Delete this bill? The original meter reading and tenancy history are " +
                        "preserved, and you can generate this billing month again."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    if (regenerateOpen) {
        val hasPayments = state.payments.isNotEmpty()
        HrmDialog(
            title = "Regenerate bill",
            confirmLabel = "Regenerate",
            onDismiss = { regenerateOpen = false },
            confirmEnabled = !hasPayments,
            onConfirm = {
                viewModel.regenerateBill { newBillId ->
                    regenerateOpen = false
                    if (newBillId != null) onBack()
                }
            },
        ) {
            Text(
                if (hasPayments) {
                    "This bill has ${state.payments.size} payment record(s). " +
                        "It cannot be regenerated because historical payments must be preserved."
                } else {
                    "Replace this bill with a regenerated bill for the same billing period? " +
                        "It will keep the original meter readings, receive a new unique id, " +
                        "and will not create a duplicate meter reading."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    if (paymentOpen && state.detail != null) {
        RecordPaymentDialog(
            detail = state.detail!!,
            onDismiss = { paymentOpen = false },
            onRecord = { amount, date, method, reference, notes, onResult ->
                viewModel.recordPayment(amount, date, method, reference, notes, onResult)
            },
        )
    }

    state.lastPaymentId?.let { id ->
        HrmDialog(
            title = "Payment recorded",
            confirmLabel = "Save receipt",
            onDismiss = viewModel::clearLastPayment,
            onConfirm = {
                receiptSaveId = id
                saveReceiptLauncher.launch("Receipt.pdf")
                viewModel.clearLastPayment()
            },
        ) {
            Text(
                "The bill balance has been updated. You can now save, share or print the receipt.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun RecordPaymentDialog(
    detail: BillWithDetails,
    onDismiss: () -> Unit,
    onRecord: (Long, LocalDate, PaymentMethod, String, String, (Boolean) -> Unit) -> Unit,
) {
    val outstanding = (detail.bill.totalPaise - detail.bill.paidPaise).coerceAtLeast(0)
    var amount by remember { mutableStateOf((outstanding / 100.0).toString()) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    var reference by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var methodMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    HrmDialog(
        title = "Record payment",
        confirmLabel = "Record",
        onDismiss = onDismiss,
        busy = saving,
        onConfirm = {
            val parsed = parseMoneyToPaise(amount)
            error = when {
                parsed == null || parsed <= 0 -> "Enter a valid amount"
                parsed > outstanding -> "Amount cannot exceed the outstanding balance"
                else -> null
            }
            if (error == null) {
                saving = true
                onRecord(parsed!!, date, method, reference, notes) { ok ->
                    saving = false
                    if (ok) {
                        onDismiss()
                    } else {
                        error = "Could not record the payment. Please try again."
                    }
                }
            }
        },
    ) {
        Text(
            "Outstanding ${outstanding.toRupees()} for room ${detail.room.roomNumber}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount (₹)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
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
            Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
        }
    }
}
