package com.homerentmanager.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.homerentmanager.BuildConfig
import com.homerentmanager.data.entity.BillEntity
import com.homerentmanager.data.entity.BillStatus
import com.homerentmanager.data.entity.MeterReadingEntity
import com.homerentmanager.data.entity.PaymentEntity
import com.homerentmanager.data.entity.PaymentMethod
import com.homerentmanager.data.entity.SettingsEntity
import com.homerentmanager.data.entity.TenantEntity
import com.homerentmanager.data.model.BillWithDetails
import com.homerentmanager.domain.ReceiptBuilder
import com.homerentmanager.domain.ReceiptModel
import com.homerentmanager.util.NumberToWords
import com.homerentmanager.util.formatDisplay
import com.homerentmanager.util.formatUnits
import com.homerentmanager.util.label
import com.homerentmanager.util.monthLabel
import com.homerentmanager.util.toPdfRupees
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Generates A4 PDF invoices and payment receipts with platform PdfDocument APIs. Files are
 * written to app cache for share/print and copied to a user-chosen SAF URI for saving.
 */
class PdfService(private val context: Context) {
    companion object {
        private const val PAGE_WIDTH = 595
        private const val PAGE_HEIGHT = 842
        private const val MARGIN = 40f
        private const val TEAL = 0xFF0F766E.toInt()
        private const val TEAL_LIGHT = 0xFFE6F4F1.toInt()
        private const val INK = 0xFF0F172A.toInt()
        private const val MUTED = 0xFF64748B.toInt()
        private const val LINE = 0xFFE2E8F0.toInt()
        private const val RED = 0xFFB91C1C.toInt()
        private const val AMBER = 0xFFB45309.toInt()
        private const val GREEN = 0xFF15803D.toInt()
        private val generatedFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
    }

    fun invoiceFileName(bill: BillEntity): String =
        "Invoice-${bill.billNumber.replace(Regex("[^A-Za-z0-9_-]"), "_")}.pdf"

    fun receiptFileName(payment: PaymentEntity): String =
        "Receipt-${payment.receiptNumber.replace(Regex("[^A-Za-z0-9_-]"), "_")}.pdf"

    suspend fun createInvoiceFile(
        settings: SettingsEntity,
        detail: BillWithDetails,
        payments: List<PaymentEntity>,
        reading: MeterReadingEntity?,
    ): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "pdfs").apply { mkdirs() }
        val file = File(directory, invoiceFileName(detail.bill))
        FileOutputStream(file).use { output ->
            val document = PdfDocument()
            drawInvoice(document, settings, detail, payments, reading)
            document.writeTo(output)
            document.close()
        }
        file
    }

    suspend fun createReceiptFile(
        settings: SettingsEntity,
        bill: BillEntity,
        tenant: TenantEntity,
        roomNumber: String,
        payment: PaymentEntity,
        billPayments: List<PaymentEntity>,
        reading: MeterReadingEntity?,
    ): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "pdfs").apply { mkdirs() }
        val file = File(directory, receiptFileName(payment))
        FileOutputStream(file).use { output ->
            val document = PdfDocument()
            drawReceipt(
                document = document,
                settings = settings,
                receipt = ReceiptBuilder.build(
                    bill = bill,
                    tenant = tenant,
                    roomNumber = roomNumber,
                    payment = payment,
                    reading = reading,
                    billPayments = billPayments,
                ),
            )
            document.writeTo(output)
            document.close()
        }
        file
    }

    suspend fun saveToUri(file: File, destination: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(destination, "w")?.use { output ->
            file.inputStream().use { it.copyTo(output, 64 * 1024) }
            output.flush()
        } ?: error("Could not open the selected PDF destination")
    }

    fun share(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share PDF"))
    }

    fun print(file: File, jobName: String) {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print(
            jobName,
            PdfPrintAdapter(file),
            PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .build(),
        )
    }

    // ------------------------------------------------------------------------------------
    // Invoice
    // ------------------------------------------------------------------------------------

    private fun drawInvoice(
        document: PdfDocument,
        settings: SettingsEntity,
        detail: BillWithDetails,
        payments: List<PaymentEntity>,
        reading: MeterReadingEntity?,
    ) {
        val bill = detail.bill
        val writer = DocWriter(document, "Invoice ${bill.billNumber}")
        writer.beginPage()
        drawBrandHeader(
            writer,
            settings,
            title = "RENT INVOICE",
            subtitle = bill.billNumber,
        )

        var y = 128f
        writer.drawLabel("BILLED TO", MARGIN, y)
        writer.drawLabel("INVOICE DETAILS", 350f, y)
        y += 16f
        val tenantLines = buildList {
            add(detail.tenant.name)
            if (detail.tenant.mobile.isNotBlank()) add("Mobile: ${detail.tenant.mobile}")
            if (detail.tenant.address.isNotBlank()) add(detail.tenant.address)
            add("Room ${detail.room.roomNumber}" + detail.room.floor.takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty())
        }
        writer.drawTextBlock(tenantLines, MARGIN, y, 250f, 9.5f, 13f, INK)

        val meta = listOf(
            "Billing month" to monthLabel(bill.billingMonth),
            "Bill date" to bill.billDate.formatDisplay(),
            "Due date" to bill.dueDate.formatDisplay(),
            "Tenancy from" to detail.tenancy.joinDate.formatDisplay(),
        )
        var metaY = y
        meta.forEach { (label, value) ->
            writer.drawText(label, 350f, metaY, 8f, MUTED)
            writer.drawText(value, 555f, metaY, 9.5f, INK, align = Paint.Align.RIGHT)
            metaY += 14f
        }
        y += tenantLines.size * 13f + 18f

        val statusColor = when (bill.status) {
            BillStatus.PAID -> GREEN
            BillStatus.PARTIAL -> AMBER
            BillStatus.UNPAID -> RED
        }
        writer.drawStatusPill("Status: ${bill.status.label()}", MARGIN, y, statusColor)
        y += 28f

        writer.drawTableHeader(
            y,
            listOf(
                "Description" to MARGIN,
                "Qty / Units" to 330f,
                "Rate" to 410f,
                "Amount" to 555f,
            ),
            aligns = listOf(Paint.Align.LEFT, Paint.Align.RIGHT, Paint.Align.RIGHT, Paint.Align.RIGHT),
        )
        y += 20f
        val rentLabel = "Monthly rent — ${monthLabel(bill.billingMonth)}"
        writer.drawTableRow(
            y,
            listOf(
                rentLabel to MARGIN,
                "1" to 330f,
                bill.rentPaise.toPdfRupees() to 410f,
                bill.rentPaise.toPdfRupees() to 555f,
            ),
            aligns = listOf(Paint.Align.LEFT, Paint.Align.RIGHT, Paint.Align.RIGHT, Paint.Align.RIGHT),
        )
        y += 22f
        writer.drawTableRow(
            y,
            listOf(
                "Electricity consumption" to MARGIN,
                "${bill.electricityUnits.formatUnits()} units" to 330f,
                "Rs. ${bill.electricityRate.formatRateNumber()}/unit" to 410f,
                bill.electricityAmountPaise.toPdfRupees() to 555f,
            ),
            aligns = listOf(Paint.Align.LEFT, Paint.Align.RIGHT, Paint.Align.RIGHT, Paint.Align.RIGHT),
        )
        y += 22f
        if (reading != null) {
            y += 4f
            writer.drawLabel("ELECTRICITY DETAILS", MARGIN, y)
            y += 16f
            writer.drawSummaryRow(
                "Previous reading",
                reading.previousReading.formatUnits(),
                y,
            )
            y += 16f
            writer.drawSummaryRow(
                "Current reading",
                reading.currentReading.formatUnits(),
                y,
            )
            y += 16f
            writer.drawSummaryRow(
                "Units consumed",
                "${bill.electricityUnits.formatUnits()} units",
                y,
            )
            y += 16f
            writer.drawSummaryRow(
                "Rate per unit",
                "Rs. ${bill.electricityRate.formatRateNumber()}/unit",
                y,
            )
            y += 16f
            writer.drawSummaryRow(
                "Electricity charges",
                bill.electricityAmountPaise.toPdfRupees(),
                y,
            )
            y += 20f
        }
        if (bill.additionalChargesPaise > 0) {
            writer.drawTableRow(
                y,
                listOf(
                    "Additional charges" to MARGIN,
                    "" to 330f,
                    "" to 410f,
                    bill.additionalChargesPaise.toPdfRupees() to 555f,
                ),
                aligns = listOf(Paint.Align.LEFT, Paint.Align.RIGHT, Paint.Align.RIGHT, Paint.Align.RIGHT),
            )
            y += 22f
        }
        if (bill.discountPaise > 0) {
            writer.drawTableRow(
                y,
                listOf(
                    "Discount" to MARGIN,
                    "" to 330f,
                    "" to 410f,
                    "- ${bill.discountPaise.toPdfRupees()}" to 555f,
                ),
                aligns = listOf(Paint.Align.LEFT, Paint.Align.RIGHT, Paint.Align.RIGHT, Paint.Align.RIGHT),
            )
            y += 22f
        }
        y += 4f
        writer.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, LINE)
        y += 18f
        writer.drawText("TOTAL", 350f, y, 10f, MUTED, bold = true)
        writer.drawText(bill.totalPaise.toPdfRupees(), 555f, y, 15f, INK, bold = true, align = Paint.Align.RIGHT)
        y += 18f
        writer.drawText("Paid", 350f, y, 9.5f, MUTED)
        writer.drawText(bill.paidPaise.toPdfRupees(), 555f, y, 9.5f, GREEN, align = Paint.Align.RIGHT)
        y += 15f
        writer.drawText("Balance due", 350f, y, 10.5f, INK, bold = true)
        writer.drawText(
            (bill.totalPaise - bill.paidPaise).coerceAtLeast(0).toPdfRupees(),
            555f,
            y,
            12f,
            if (bill.status == BillStatus.PAID) GREEN else RED,
            bold = true,
            align = Paint.Align.RIGHT,
        )
        y += 30f

        writer.drawLabel("PAYMENT HISTORY", MARGIN, y)
        y += 16f
        if (payments.isEmpty()) {
            writer.drawText("No payments recorded yet.", MARGIN, y, 9.5f, MUTED)
            y += 20f
        } else {
            writer.drawTableHeader(
                y,
                listOf(
                    "Date" to MARGIN,
                    "Receipt" to 125f,
                    "Method" to 290f,
                    "Amount" to 555f,
                ),
                aligns = listOf(Paint.Align.LEFT, Paint.Align.LEFT, Paint.Align.LEFT, Paint.Align.RIGHT),
            )
            y += 19f
            payments.forEach { payment ->
                writer.ensureSpace(26f)
                if (writer.wasNewPage) {
                    writer.drawTableHeader(
                        writer.currentY,
                        listOf(
                            "Date" to MARGIN,
                            "Receipt" to 125f,
                            "Method" to 290f,
                            "Amount" to 555f,
                        ),
                        aligns = listOf(Paint.Align.LEFT, Paint.Align.LEFT, Paint.Align.LEFT, Paint.Align.RIGHT),
                    )
                    writer.currentY += 19f
                }
                writer.drawTableRow(
                    writer.currentY,
                    listOf(
                        payment.paidAt.formatDisplay() to MARGIN,
                        payment.receiptNumber to 125f,
                        payment.method.label() to 290f,
                        payment.amountPaise.toPdfRupees() to 555f,
                    ),
                    aligns = listOf(Paint.Align.LEFT, Paint.Align.LEFT, Paint.Align.LEFT, Paint.Align.RIGHT),
                )
                writer.currentY += 19f
            }
            y = writer.currentY + 8f
        }

        if (!bill.notes.isNullOrBlank()) {
            writer.currentY = y
            writer.ensureSpace(60f)
            if (writer.wasNewPage) y = writer.currentY
            writer.drawLabel("NOTES", MARGIN, y)
            y += 14f
            y = writer.drawWrapped(bill.notes ?: "", MARGIN, y, 555f, 9f, MUTED, 12f)
            y += 8f
        }

        writer.currentY = y
        writer.ensureSpace(70f)
        if (writer.wasNewPage) y = writer.currentY
        writer.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, LINE)
        y += 16f
        writer.drawText(
            "Please pay the outstanding balance by ${bill.dueDate.formatDisplay()}.",
            MARGIN,
            y,
            8.5f,
            MUTED,
        )
        y += 12f
        writer.drawText(
            "This is a computer-generated invoice from ${settings.propertyName}.",
            MARGIN,
            y,
            8.5f,
            MUTED,
        )
        writer.finish()
    }

    // ------------------------------------------------------------------------------------
    // Receipt
    // ------------------------------------------------------------------------------------

    private fun drawReceipt(
        document: PdfDocument,
        settings: SettingsEntity,
        receipt: ReceiptModel,
    ) {
        val bill = receipt.bill
        val payment = receipt.payment
        val tenant = receipt.tenant
        val writer = DocWriter(document, "Receipt ${payment.receiptNumber}")
        writer.beginPage()
        drawBrandHeader(writer, settings, title = "PAYMENT RECEIPT", subtitle = payment.receiptNumber)

        var y = 132f
        writer.drawLabel("RECEIVED FROM", MARGIN, y)
        writer.drawLabel("PAYMENT DETAILS", 350f, y)
        y += 17f
        val tenantLines = buildList {
            add(tenant.name)
            if (tenant.mobile.isNotBlank()) add("Mobile: ${tenant.mobile}")
            if (tenant.address.isNotBlank()) add(tenant.address)
            add("Room ${receipt.roomNumber}")
        }
        writer.drawTextBlock(tenantLines, MARGIN, y, 250f, 9.5f, 13f, INK)
        var metaY = y
        listOf(
            "Paid by" to tenant.name,
            "Payment date" to payment.paidAt.formatDisplay(),
            "Method" to payment.method.label(),
            "Bill number" to bill.billNumber,
            "Billing month" to monthLabel(bill.billingMonth),
            "Reference" to payment.reference.ifBlank { "—" },
        ).forEach { (label, value) ->
            writer.drawText(label, 350f, metaY, 8f, MUTED)
            writer.drawText(value, 555f, metaY, 9.2f, INK, align = Paint.Align.RIGHT)
            metaY += 14f
        }

        y = maxOf(y + tenantLines.size * 13f + 20f, metaY + 12f)
        writer.currentY = y
        writer.ensureSpace(120f)
        y = writer.currentY
        val box = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 78f)
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEAL_LIGHT }
        writer.canvas.drawRoundRect(box, 10f, 10f, boxPaint)
        writer.drawText("AMOUNT RECEIVED", MARGIN + 18f, y + 24f, 9f, TEAL, bold = true)
        writer.drawText(
            receipt.currentPaymentPaise.toPdfRupees(),
            MARGIN + 18f,
            y + 56f,
            25f,
            INK,
            bold = true,
        )
        writer.drawText(
            if (receipt.outstandingPaise == 0L) "Bill fully paid" else "Allocated to this invoice",
            555f,
            y + 56f,
            9f,
            MUTED,
            align = Paint.Align.RIGHT,
        )
        y += 96f
        val wordsBottom = writer.drawWrapped(
            "Amount in words: ${NumberToWords.rupees(receipt.currentPaymentPaise)}",
            MARGIN,
            y,
            555f,
            9f,
            INK,
            12f,
        )
        y = wordsBottom + 18f

        writer.currentY = y
        writer.ensureSpace(120f)
        y = writer.currentY
        writer.drawLabel("BILL BREAKDOWN", MARGIN, y)
        y += 16f
        writer.drawSummaryRow("Rent — ${monthLabel(bill.billingMonth)}", receipt.rentPaise.toPdfRupees(), y)
        y += 16f
        if (receipt.hasElectricity) {
            writer.drawSummaryRow(
                "Previous reading",
                receipt.previousReading?.formatUnits() ?: "Not recorded",
                y,
            )
            y += 16f
            writer.drawSummaryRow(
                "Current reading",
                receipt.currentReading?.formatUnits() ?: "Not recorded",
                y,
            )
            y += 16f
            writer.drawSummaryRow(
                "Units consumed",
                "${receipt.electricityUnits.formatUnits()} units",
                y,
            )
            y += 16f
            writer.drawSummaryRow(
                "Electricity rate",
                "Rs. ${receipt.electricityRate.formatRateNumber()}/unit",
                y,
            )
            y += 16f
            writer.drawSummaryRow(
                "Electricity charge",
                receipt.electricityAmountPaise.toPdfRupees(),
                y,
            )
            y += 16f
        }
        if (receipt.additionalChargesPaise != 0L) {
            writer.drawSummaryRow(
                "Additional charges",
                receipt.additionalChargesPaise.toPdfRupees(),
                y,
            )
            y += 16f
        }
        if (receipt.discountPaise != 0L) {
            writer.drawSummaryRow(
                "Discount",
                "- ${receipt.discountPaise.toPdfRupees()}",
                y,
            )
            y += 16f
        }
        writer.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, LINE)
        y += 17f
        writer.drawText("Original bill total", MARGIN, y, 10.5f, INK, bold = true)
        writer.drawText(
            receipt.billTotalPaise.toPdfRupees(),
            555f,
            y,
            12f,
            INK,
            bold = true,
            align = Paint.Align.RIGHT,
        )
        y += 28f

        writer.currentY = y
        writer.ensureSpace(110f)
        y = writer.currentY
        writer.drawLabel("PAYMENT SUMMARY", MARGIN, y)
        y += 16f
        writer.drawSummaryRow("Previous payments", receipt.previousPaidPaise.toPdfRupees(), y)
        y += 17f
        writer.drawSummaryRow("This payment", receipt.currentPaymentPaise.toPdfRupees(), y)
        y += 17f
        writer.drawSummaryRow("Total paid after this payment", receipt.cumulativePaidPaise.toPdfRupees(), y)
        y += 17f
        writer.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, LINE)
        y += 18f
        writer.drawText("Outstanding after this payment", MARGIN, y, 10.5f, INK, bold = true)
        writer.drawText(
            receipt.outstandingPaise.toPdfRupees(),
            555f,
            y,
            12f,
            if (receipt.outstandingPaise == 0L) GREEN else RED,
            bold = true,
            align = Paint.Align.RIGHT,
        )
        y += 32f

        writer.currentY = y
        writer.ensureSpace(80f)
        y = writer.currentY
        writer.drawLabel("PAYMENTS ON THIS BILL", MARGIN, y)
        y += 16f
        writer.drawTableHeader(
            y,
            listOf(
                "Date" to MARGIN,
                "Receipt" to 125f,
                "Method" to 290f,
                "Amount" to 555f,
            ),
            aligns = listOf(Paint.Align.LEFT, Paint.Align.LEFT, Paint.Align.LEFT, Paint.Align.RIGHT),
        )
        y += 19f
        writer.currentY = y
        receipt.paymentsOnBill.forEach { item ->
            writer.ensureSpace(22f)
            writer.drawTableRow(
                writer.currentY,
                listOf(
                    item.paidAt.formatDisplay() to MARGIN,
                    item.receiptNumber to 125f,
                    item.method.label() to 290f,
                    item.amountPaise.toPdfRupees() to 555f,
                ),
                aligns = listOf(Paint.Align.LEFT, Paint.Align.LEFT, Paint.Align.LEFT, Paint.Align.RIGHT),
                highlight = item.id == payment.id,
            )
            writer.currentY += 19f
        }
        y = writer.currentY + 30f

        writer.currentY = y
        writer.ensureSpace(90f)
        if (writer.wasNewPage) y = writer.currentY
        writer.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, LINE)
        y += 16f
        writer.drawText(
            "Thank you for your payment. This receipt is computer generated and valid without a signature.",
            MARGIN,
            y,
            8.5f,
            MUTED,
        )
        y += 36f
        writer.drawLine(400f, y, 555f, y, MUTED)
        y += 12f
        writer.drawText("Authorised signatory", 400f, y, 8.5f, MUTED)
        writer.drawText(settings.propertyName, 400f, y + 12f, 9f, INK, bold = true)
        writer.finish()
    }

    private fun drawBrandHeader(
        writer: DocWriter,
        settings: SettingsEntity,
        title: String,
        subtitle: String,
    ) {
        val canvas = writer.canvas
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 102f, Paint().apply { color = TEAL })
        writer.drawText(settings.propertyName.ifBlank { "Home Rentals" }, MARGIN, 42f, 20f, Color.WHITE, bold = true)
        if (settings.propertyAddress.isNotBlank()) {
            writer.drawText(settings.propertyAddress.take(95), MARGIN, 61f, 8.5f, 0xFFD1FAE5.toInt())
        }
        val ownerLine = listOfNotNull(
            settings.ownerName.takeIf { it.isNotBlank() },
            settings.ownerMobile.takeIf { it.isNotBlank() }?.let { "Mobile: $it" },
        ).joinToString("  •  ")
        if (ownerLine.isNotBlank()) writer.drawText(ownerLine, MARGIN, 76f, 8.5f, 0xFFD1FAE5.toInt())
        writer.drawText(title, 555f, 42f, 20f, Color.WHITE, bold = true, align = Paint.Align.RIGHT)
        writer.drawText(subtitle, 555f, 61f, 9f, 0xFFD1FAE5.toInt(), align = Paint.Align.RIGHT)
        writer.drawText(
            "Generated ${LocalDateTime.now().format(generatedFormatter)}",
            555f,
            76f,
            7.5f,
            0xFFD1FAE5.toInt(),
            align = Paint.Align.RIGHT,
        )
    }

    // ------------------------------------------------------------------------------------
    // Drawing helper
    // ------------------------------------------------------------------------------------

    private class DocWriter(
        private val document: PdfDocument,
        private val documentLabel: String,
    ) {
        lateinit var canvas: Canvas
        var currentY: Float = MARGIN
        var wasNewPage: Boolean = false
            private set
        private var page: PdfDocument.Page? = null
        private var pageNumber = 0

        fun beginPage() {
            pageNumber += 1
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val newPage = document.startPage(info)
            page = newPage
            canvas = newPage.canvas
            canvas.drawColor(Color.WHITE)
            currentY = MARGIN
            wasNewPage = true
        }

        fun finish() {
            if (page != null) endPage()
        }

        fun ensureSpace(required: Float) {
            if (currentY + required > PAGE_HEIGHT - 58f) {
                endPage()
                beginPage()
                drawText("$documentLabel (continued)", MARGIN, MARGIN, 10f, TEAL, bold = true)
                currentY = MARGIN + 22f
                wasNewPage = true
            } else {
                wasNewPage = false
            }
        }

        private fun endPage() {
            val active = page ?: return
            drawLine(MARGIN, PAGE_HEIGHT - 44f, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 44f, LINE)
            drawText(
                "Home Rent Manager • Page $pageNumber",
                MARGIN,
                PAGE_HEIGHT - 28f,
                7.5f,
                MUTED,
            )
            drawText(
                documentLabel,
                PAGE_WIDTH - MARGIN,
                PAGE_HEIGHT - 28f,
                7.5f,
                MUTED,
                align = Paint.Align.RIGHT,
            )
            document.finishPage(active)
            page = null
        }

        fun drawText(
            text: String,
            x: Float,
            y: Float,
            size: Float = 10f,
            color: Int = INK,
            bold: Boolean = false,
            align: Paint.Align = Paint.Align.LEFT,
        ) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                textSize = size
                textAlign = align
                typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            }
            canvas.drawText(text, x, y, paint)
        }

        fun drawLabel(text: String, x: Float, y: Float) {
            drawText(text, x, y, 7.5f, MUTED, bold = true)
        }

        fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, color: Int) {
            canvas.drawLine(x1, y1, x2, y2, Paint().apply {
                this.color = color
                strokeWidth = 0.7f
                style = Paint.Style.STROKE
            })
        }

        fun drawTextBlock(
            lines: List<String>,
            x: Float,
            startY: Float,
            maxWidth: Float,
            size: Float,
            lineHeight: Float,
            color: Int,
        ): Float {
            var y = startY
            lines.forEachIndexed { index, line ->
                if (index == 0) {
                    drawText(line, x, y, size + 0.7f, color, bold = true)
                } else {
                    y = drawWrapped(line, x, y, maxWidth, size, color, lineHeight)
                    return@forEachIndexed
                }
                y += lineHeight
            }
            return y
        }

        fun drawWrapped(
            text: String,
            x: Float,
            startY: Float,
            maxWidth: Float,
            size: Float,
            color: Int,
            lineHeight: Float,
        ): Float {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                textSize = size
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
            var y = startY
            text.split('\n').forEach { paragraph ->
                if (paragraph.isEmpty()) {
                    y += lineHeight
                } else {
                    val words = paragraph.split(' ')
                    var line = ""
                    words.forEach { word ->
                        val candidate = if (line.isEmpty()) word else "$line $word"
                        if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                            drawText(line, x, y, size, color)
                            y += lineHeight
                            line = word
                        } else {
                            line = candidate
                        }
                    }
                    if (line.isNotEmpty()) {
                        drawText(line, x, y, size, color)
                        y += lineHeight
                    }
                }
            }
            return y
        }

        fun drawStatusPill(text: String, x: Float, y: Float, color: Int) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.WHITE
                textSize = 8f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
            }
            val width = textPaint.measureText(text) + 18f
            canvas.drawRoundRect(RectF(x, y - 13f, x + width, y + 3f), 8f, 8f, paint)
            canvas.drawText(text, x + 9f, y - 3f, textPaint)
        }

        fun drawTableHeader(
            y: Float,
            columns: List<Pair<String, Float>>,
            aligns: List<Paint.Align>,
        ) {
            canvas.drawRect(
                MARGIN,
                y - 13f,
                PAGE_WIDTH - MARGIN,
                y + 5f,
                Paint().apply { color = 0xFFF1F5F9.toInt() },
            )
            columns.forEachIndexed { index, (label, x) ->
                drawText(label, x, y - 1f, 7.8f, MUTED, bold = true, align = aligns[index])
            }
        }

        fun drawTableRow(
            y: Float,
            columns: List<Pair<String, Float>>,
            aligns: List<Paint.Align>,
            highlight: Boolean = false,
        ) {
            if (highlight) {
                canvas.drawRect(
                    MARGIN,
                    y - 13f,
                    PAGE_WIDTH - MARGIN,
                    y + 5f,
                    Paint().apply { color = TEAL_LIGHT },
                )
            }
            columns.forEachIndexed { index, (value, x) ->
                drawText(value, x, y - 1f, 8.8f, INK, align = aligns[index])
            }
            drawLine(MARGIN, y + 6f, PAGE_WIDTH - MARGIN, y + 6f, 0xFFF1F5F9.toInt())
        }

        fun drawSummaryRow(label: String, value: String, y: Float) {
            drawText(label, MARGIN, y, 9.5f, MUTED)
            drawText(value, 555f, y, 9.5f, INK, align = Paint.Align.RIGHT)
        }
    }

    private fun Double.formatRateNumber(): String =
        java.math.BigDecimal.valueOf(this)
            .setScale(2, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
}
