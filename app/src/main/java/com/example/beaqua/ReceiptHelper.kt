package com.example.beaqua

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReceiptHelper {

    fun printReceipt(context: Context, order: Order) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "${context.getString(R.string.app_name)} Receipt - ${order.id}"

        printManager.print(jobName, object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }

                val pdi = PrintDocumentInfo.Builder("receipt.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()
                callback.onLayoutFinished(pdi, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                val pdfDocument = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(300, 500, 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas: Canvas = page.canvas
                val paint = Paint()

                // Drawing logic for receipt
                var y = 40f
                paint.textSize = 14f
                paint.isFakeBoldText = true
                canvas.drawText("BEAQUA RECEIPT", 80f, y, paint)
                
                paint.isFakeBoldText = false
                paint.textSize = 10f
                y += 30f
                canvas.drawText("Order ID: ${order.id}", 20f, y, paint)
                y += 20f
                val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(order.timestamp))
                canvas.drawText("Date: $dateStr", 20f, y, paint)
                if (order.estimatedDeliveryDate > 0L) {
                    y += 20f
                    canvas.drawText(
                        "Estimated delivery: ${DeliveryEta.label(order.estimatedDeliveryDate, order.estimatedDeliveryTimeZoneId)}",
                        20f,
                        y,
                        paint
                    )
                }
                
                y += 30f
                paint.isFakeBoldText = true
                canvas.drawText("Station: ${order.stationName}", 20f, y, paint)
                paint.isFakeBoldText = false
                y += 20f
                canvas.drawText("Customer: ${order.customerName}", 20f, y, paint)
                y += 20f
                canvas.drawText("Address: ${order.customerAddress}", 20f, y, paint)
                
                y += 30f
                canvas.drawText("------------------------------------------", 20f, y, paint)
                y += 20f
                canvas.drawText(
                    "Item: ${if (order.isRefill()) "REFILL - " else ""}${order.productName}",
                    20f,
                    y,
                    paint
                )
                y += 20f
                canvas.drawText(
                    if (order.isRefill()) {
                        "Empty containers: ${order.emptyContainerCount.coerceAtLeast(order.quantity)}"
                    } else {
                        "Qty: ${order.quantity}"
                    },
                    20f,
                    y,
                    paint
                )
                y += 20f
                canvas.drawText("Type: ${order.containerType}", 20f, y, paint)
                if (order.isRefill() && order.refillInstructions.isNotBlank()) {
                    y += 20f
                    canvas.drawText("Notes: ${order.refillInstructions.take(42)}", 20f, y, paint)
                }
                
                y += 30f
                canvas.drawText("------------------------------------------", 20f, y, paint)
                y += 20f
                paint.textSize = 12f
                paint.isFakeBoldText = true
                canvas.drawText("TOTAL: ₱${String.format("%.2f", order.totalPrice)}", 20f, y, paint)
                
                y += 30f
                paint.textSize = 10f
                paint.isFakeBoldText = false
                canvas.drawText("Payment Method: ${order.paymentMethod}", 20f, y, paint)
                y += 20f
                canvas.drawText("Status: PAID", 20f, y, paint)
                
                y += 40f
                canvas.drawText("Thank you for using BeAqua!", 60f, y, paint)

                pdfDocument.finishPage(page)

                try {
                    pdfDocument.writeTo(FileOutputStream(destination.fileDescriptor))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.toString())
                    return
                } finally {
                    pdfDocument.close()
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            }
        }, null)
    }
}
