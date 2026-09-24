package com.example.beaqua

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import java.io.FileOutputStream

object ReceiptHelper {
    fun printReceipt(context: Context, order: Order) {
        val reference = order.deliveryReceipt?.reference
        if (reference.isNullOrBlank()) {
            printReceipt(context, listOf(order))
            return
        }
        // History rows hold one item; load the complete receipt before summing charges.
        FirebaseHelper.ordersCollection
            .whereEqualTo("customerName", order.customerName)
            .get()
            .addOnSuccessListener { snapshot ->
                val receiptOrders = snapshot.documents.mapNotNull { document ->
                    document.toObject(Order::class.java)?.apply { id = document.id }
                }.filter {
                    it.deliveryReceipt?.reference == reference &&
                        it.stationOwnerUsername == order.stationOwnerUsername
                }
                if (receiptOrders.isEmpty()) {
                    Toast.makeText(context, "Could not load the complete receipt. Please try again.", Toast.LENGTH_LONG).show()
                } else {
                    printReceipt(context, receiptOrders)
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Could not load the receipt. Please check your connection and try again.", Toast.LENGTH_LONG).show()
            }
    }

    fun printReceipt(context: Context, orders: List<Order>) {
        require(orders.isNotEmpty())
        // Freeze the values while Android prepares the preview and print job.
        val receiptOrders = orders.map { order ->
            order.copy(deliveryReceipt = order.deliveryReceipt?.let { receipt ->
                receipt.copy(items = receipt.items.map { it.copy() })
            })
        }
        val reference = receiptOrders.first().deliveryReceipt?.reference ?: receiptOrders.first().id
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val defaults = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setMinMargins(PrintAttributes.Margins(500, 500, 500, 500))
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build()
        printManager.print("BeAqua Receipt - $reference", object : PrintDocumentAdapter() {
            private var attributes = defaults
            private var receipt: ReceiptDocument? = null

            override fun onLayout(
                oldAttributes: PrintAttributes?, newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                try {
                    attributes = newAttributes
                    receipt = ReceiptDocument(context, attributes, receiptOrders)
                    val filename = "BeAqua-Receipt-${reference.replace(Regex("[^A-Za-z0-9_-]"), "_")}.pdf"
                    callback.onLayoutFinished(
                        PrintDocumentInfo.Builder(filename)
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .setPageCount(receipt!!.pageCount).build(),
                        oldAttributes != newAttributes
                    )
                } catch (error: Exception) {
                    callback.onLayoutFailed(error.message ?: "Could not prepare receipt")
                }
            }

            override fun onWrite(
                pages: Array<out PageRange>, destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?, callback: WriteResultCallback
            ) {
                try {
                    val document = receipt ?: ReceiptDocument(context, attributes, receiptOrders)
                    val written = FileOutputStream(destination.fileDescriptor).use { output ->
                        document.write(output, pages, cancellationSignal)
                    }
                    callback.onWriteFinished(written)
                } catch (_: OperationCanceledException) {
                    callback.onWriteCancelled()
                } catch (error: Exception) {
                    callback.onWriteFailed(error.message ?: "Could not print receipt")
                }
            }
        }, defaults)
    }
}
