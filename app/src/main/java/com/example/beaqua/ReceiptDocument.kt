package com.example.beaqua

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.CancellationSignal
import android.print.PageRange
import android.print.PrintAttributes
import android.print.pdf.PrintedPdfDocument
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToLong

/** Measured, paginated layout shared by the print preview and exported PDF. */
class ReceiptDocument(
    private val context: Context,
    private val attributes: PrintAttributes,
    private val orders: List<Order>
) {
    private val ink = Color.rgb(16, 43, 65)
    private val muted = Color.rgb(83, 103, 119)
    private val accent = Color.rgb(0, 117, 159)
    private val pale = Color.rgb(235, 247, 251)
    private val lineColor = Color.rgb(213, 225, 232)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pages = mutableListOf<MutableList<(Canvas) -> Unit>>()
    private var width = 0f
    private var height = 0f
    private var y = 0f
    private var inTable = false
    private val deliveryReceipt get() = orders.first().deliveryReceipt
    private val bodyBottom get() = height - 42f
    val pageCount: Int get() = pages.size

    init {
        require(orders.isNotEmpty())
        val dimensions = PrintedPdfDocument(context, attributes)
        try {
            width = dimensions.pageContentRect.width().toFloat()
            height = dimensions.pageContentRect.height().toFloat()
        } finally {
            dimensions.close()
        }
        require(width >= 260 && height >= 320) { "Choose A4, Letter, or a larger paper size for this receipt." }
        buildLayout()
    }

    private fun configure(size: Float, bold: Boolean = false, color: Int = ink) {
        paint.textSize = size
        paint.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        paint.color = color
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
    }

    private fun text(value: String, x: Float, baseline: Float, size: Float = 11f,
                     bold: Boolean = false, color: Int = ink, right: Boolean = false) {
        pages.last().add { canvas ->
            configure(size, bold, color)
            canvas.drawText(value, if (right) x - paint.measureText(value) else x, baseline, paint)
        }
    }

    private fun rectangle(left: Float, top: Float, right: Float, bottom: Float, color: Int) {
        pages.last().add { canvas ->
            configure(10f, color = color)
            canvas.drawRect(left, top, right, bottom, paint)
        }
    }

    private fun wrap(value: String, availableWidth: Float, size: Float = 11f, bold: Boolean = false): List<String> {
        configure(size, bold)
        return value.split('\n').flatMap { paragraph ->
            val result = mutableListOf<String>()
            var remaining = paragraph.trim()
            if (remaining.isEmpty()) result.add("")
            while (remaining.isNotEmpty()) {
                var count = paint.breakText(remaining, true, availableWidth, null).coerceAtLeast(1)
                if (count < remaining.length) {
                    val space = remaining.lastIndexOf(' ', count - 1)
                    if (space > 0) count = space
                }
                result.add(remaining.take(count))
                remaining = remaining.drop(count).trimStart()
            }
            result
        }
    }

    private fun newPage() {
        pages.add(mutableListOf())
        rectangle(0f, 0f, width, 4f, accent)
        text("BeAqua", 0f, 36f, 25f, true, accent)
        text(if (deliveryReceipt != null) "DELIVERY RECEIPT" else "ORDER RECEIPT", width, 33f, 10f, true, muted, right = true)
        y = 58f
        if (pages.size > 1) {
            text("Order details continued", 0f, y, 10f, color = muted)
            y += 24f
            if (inTable) tableHeading()
        }
    }

    private fun ensureSpace(space: Float) {
        if (y + space > bodyBottom) newPage()
    }

    private fun paragraph(value: String, size: Float = 11f, bold: Boolean = false, color: Int = ink) {
        wrap(value, width, size, bold).forEach { line ->
            ensureSpace(size + 6f)
            text(line, 0f, y + size, size, bold, color)
            y += size + 6f
        }
    }

    private fun field(label: String, value: String) {
        ensureSpace(38f)
        paragraph(label.uppercase(Locale.ROOT), 8f, true, muted)
        paragraph(value.ifBlank { "Not provided" })
        y += 8f
    }

    private fun tableHeading() {
        rectangle(0f, y, width, y + 26f, pale)
        text("ITEM", 8f, y + 17f, 9f, true, accent)
        if (deliveryReceipt != null) {
            text("QUANTITY", width - 8f, y + 17f, 9f, true, accent, true)
        } else {
            text("QTY", width * .59f, y + 17f, 9f, true, accent, true)
            text("UNIT PRICE", width * .78f, y + 17f, 9f, true, accent, true)
            text("AMOUNT", width - 8f, y + 17f, 9f, true, accent, true)
        }
        y += 36f
    }

    private fun money(cents: Long) = String.format(Locale.US, "₱%,.2f", cents / 100.0)
    private fun cents(amount: Double) = (amount * 100).roundToLong()

    private fun buildLayout() {
        val first = orders.first()
        val zone = TimeZone.getTimeZone(first.estimatedDeliveryTimeZoneId)
        fun date(timestamp: Long, pattern: String): String = SimpleDateFormat(pattern, Locale.US)
            .apply { timeZone = zone }.format(Date(timestamp))

        newPage()
        paragraph(first.stationName.ifBlank { first.stationOwnerUsername }, 17f, true)
        y += 8f
        field("Order reference", deliveryReceipt?.reference ?: first.id)
        field("Ordered on", if (first.timestamp > 0) date(first.timestamp, "MMM d, yyyy 'at' h:mm a z") else "Not recorded")
        deliveryReceipt?.let { receipt ->
            field("Delivery confirmed", date(receipt.issuedAt, "MMM d, yyyy 'at' h:mm a z"))
        }
        field("Deliver to", "${first.customerName}\n${first.customerAddress.ifBlank { "Address not provided" }}")
        if (first.estimatedDeliveryDate > 0) {
            field("Estimated delivery", date(first.estimatedDeliveryDate, "EEEE, MMM d, yyyy"))
        }
        field("Payment", "${first.paymentMethod}  |  ${when {
            orders.all { it.isPaid } -> "PAID"
            orders.any { it.isPaid } -> "PARTIALLY PAID"
            else -> "UNPAID"
        }}")

        // Fee shares can contain fractions of a cent. Round cumulative item amounts
        // so displayed lines still add up to the stored checkout total.
        val total = cents(orders.sumOf { it.totalPrice })
        val delivery = cents(orders.sumOf { it.deliveryFee })
        val rush = cents(orders.sumOf { it.rushOrderFee })
        val subtotal = total - delivery - rush
        deliveryReceipt?.let { receipt ->
            buildConfirmedItems(receipt.items)
            buildPaymentSummary(total, delivery, rush, originalCheckout = true)
            closingNote()
            return
        }
        var cumulative = 0.0
        var allocated = 0L
        ensureSpace(64f)
        inTable = true
        tableHeading()
        orders.forEachIndexed { index, item ->
            cumulative += item.totalPrice - item.deliveryFee - item.rushOrderFee
            val nextAllocated = if (index == orders.lastIndex) subtotal else cents(cumulative)
            val amount = nextAllocated - allocated
            allocated = nextAllocated
            val description = buildString {
                append(item.productName.ifBlank { "Water item" })
                if (item.containerType.isNotBlank() && !item.containerType.equals(item.productName, true)) {
                    append("\n${item.containerType}")
                }
                if (item.isRefill()) {
                    append("\nRefill exchange: ${item.emptyContainerCount.coerceAtLeast(item.quantity)} empty containers")
                    if (item.refillInstructions.isNotBlank()) append("\nNotes: ${item.refillInstructions}")
                }
            }
            val lines = wrap(description, width * .48f - 16f)
            // Keep a normal item row together; exceptionally long notes may span pages.
            val rowHeight = lines.size * 17f + 17f
            ensureSpace(if (rowHeight <= bodyBottom - 118f) rowHeight else 22f)
            lines.forEachIndexed { lineIndex, line ->
                ensureSpace(17f)
                text(line, 8f, y + 11f)
                if (lineIndex == 0) {
                    text(item.quantity.toString(), width * .59f, y + 11f, right = true)
                    val unit = if (item.quantity > 0) money((amount.toDouble() / item.quantity).roundToLong()) else "-"
                    text(unit, width * .78f, y + 11f, right = true)
                    text(money(amount), width - 8f, y + 11f, bold = true, right = true)
                }
                y += 17f
            }
            y += 7f
            rectangle(0f, y, width, y + .6f, lineColor)
            y += 10f
        }
        inTable = false
        buildPaymentSummary(total, delivery, rush)
        closingNote()
    }

    private fun buildPaymentSummary(total: Long, delivery: Long, rush: Long, originalCheckout: Boolean = false) {
        ensureSpace(if (originalCheckout) 174f else 136f)
        y += 6f
        if (originalCheckout) {
            paragraph("ORDER PAYMENT SUMMARY", 9f, true, accent)
            paragraph("Amounts from the original checkout.", 9f, color = muted)
        }
        fun totalLine(label: String, value: Long) {
            text(label, 8f, y + 11f, color = muted)
            text(money(value), width - 8f, y + 11f, right = true)
            y += 22f
        }
        totalLine("Items subtotal", total - delivery - rush)
        totalLine("Delivery fee", delivery)
        if (rush != 0L) totalLine("Rush delivery fee", rush)
        rectangle(0f, y + 4f, width, y + 48f, pale)
        text("TOTAL", 12f, y + 32f, 12f, true, accent)
        text(money(total), width - 12f, y + 33f, 21f, true, accent, true)
        y += 64f
    }

    private fun closingNote() {
        val changed = deliveryReceipt?.itemsChanged == true
        val supportNote = (if (changed) "The water station changed the items or quantities when confirming this delivery. " else "") +
            "For any concerns, contact the water station through Open Chat on its page in BeAqua."
        val noteHeight = wrap(supportNote, width, 10f).size * 16f
        ensureSpace(50f + noteHeight)
        paragraph("Thank you for choosing BeAqua.", 11f, true, accent)
        y += 6f
        paragraph(if (changed) "RECEIPT UPDATED" else "NEED HELP?", 9f, true, accent)
        paragraph(supportNote, 10f, color = muted)
    }

    private fun buildConfirmedItems(items: List<DeliveryReceiptItem>) {
        ensureSpace(64f)
        inTable = true
        tableHeading()
        for (item in items) {
            val lines = wrap(item.name, width * .8f - 16f, 12f)
            val rowHeight = lines.size * 19f + 17f
            ensureSpace(if (rowHeight <= bodyBottom - 118f) rowHeight else 22f)
            lines.forEachIndexed { index, line ->
                ensureSpace(19f)
                text(line, 8f, y + 12f, 12f)
                if (index == 0) text(item.quantity.toString(), width - 8f, y + 12f, 12f, true, right = true)
                y += 19f
            }
            y += 7f
            rectangle(0f, y, width, y + .6f, lineColor)
            y += 10f
        }
        inTable = false
    }

    fun write(output: OutputStream, ranges: Array<out PageRange> = arrayOf(PageRange.ALL_PAGES),
              cancellationSignal: CancellationSignal? = null): Array<PageRange> {
        val written = mutableListOf<PageRange>()
        val document = PrintedPdfDocument(context, attributes)
        try {
            pages.forEachIndexed { index, commands ->
                cancellationSignal?.throwIfCanceled()
                if (ranges.none { index in it.start..it.end }) return@forEachIndexed
                val page = document.startPage(index)
                val canvas = page.canvas
                canvas.save()
                // PrintedPdfDocument already offsets and clips to the printable content area.
                commands.forEach { it(canvas) }
                configure(9f, color = muted)
                canvas.drawText(if (deliveryReceipt != null) "BeAqua  /  Delivery receipt" else "BeAqua  /  Order receipt", 0f, height - 12f, paint)
                val number = "${index + 1} / ${pages.size}"
                canvas.drawText(number, width - paint.measureText(number), height - 12f, paint)
                canvas.restore()
                document.finishPage(page)
                written.add(PageRange(index, index))
            }
            cancellationSignal?.throwIfCanceled()
            document.writeTo(output)
        } finally {
            document.close()
        }
        return written.toTypedArray()
    }
}
