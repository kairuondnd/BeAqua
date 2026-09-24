package com.example.beaqua

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReceiptDocumentTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val attributes = PrintAttributes.Builder()
        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
        .setResolution(PrintAttributes.Resolution("pdf", "PDF", 300, 300))
        .setMinMargins(PrintAttributes.Margins(500, 500, 500, 500))
        .setColorMode(PrintAttributes.COLOR_MODE_COLOR).build()
    private val order = Order(
        id = "WATER-STATION-2512-F8EE", stationName = "Water Station",
        customerName = "Sample Customer", customerAddress = "London Merville, Parañaque, Philippines",
        productName = "Purified drinking water", containerType = "5-gallon container",
        quantity = 2, totalPrice = 55.0, deliveryFee = 5.0, isPaid = true,
        timestamp = 1790077020000L,
        estimatedDeliveryDate = DeliveryEta.fromDate(2026, 8, 23)
    )

    private fun render(name: String, receipt: ReceiptDocument): File {
        val file = File(context.getExternalFilesDir(null), "$name.pdf")
        file.outputStream().use { receipt.write(it) }
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
            assertEquals(receipt.pageCount, pdf.pageCount)
            for (index in 0 until pdf.pageCount) {
                pdf.openPage(index).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    File(context.getExternalFilesDir(null), "$name-${index + 1}.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
        }
        return file
    }

    @Test fun singleAndGroupedReceiptsRenderOnOnePage() {
        val single = ReceiptDocument(context, attributes, listOf(order))
        assertEquals(1, single.pageCount)
        render("receipt-single", single)
        val group = ReceiptDocument(context, attributes, listOf(order, order.copy(
            id = "WATER-STATION-1234-ABCD", productName = "Mineral drinking water",
            quantity = 3, totalPrice = 95.0, deliveryFee = 5.0
        )))
        assertEquals(1, group.pageCount)
        render("receipt-group", group)
    }

    @Test fun confirmedDeliveryKeepsEditedItemsAndRestoresOrderDetailsAndSupport() {
        val receipt = DeliveryReceipt(
            reference = "DELIVERY-123", issuedAt = order.timestamp, issuedBy = "station",
            items = listOf(DeliveryReceiptItem("Confirmed drinking water", 4), DeliveryReceiptItem("Extra container", 1)),
            itemsChanged = true
        )
        val document = ReceiptDocument(context, attributes, listOf(order.copy(deliveryReceipt = receipt)))
        assertEquals(1, document.pageCount)
        val file = render("receipt-confirmed", document)
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
                pdf.openPage(0).use { page ->
                    val text = page.textContents.joinToString(" ") { it.text }
                    assertTrue(text.contains("Confirmed drinking water"))
                    assertTrue(text.contains("Extra container"))
                    assertFalse(text.contains("UNIT PRICE"))
                    assertTrue(text.contains("TOTAL"))
                    assertTrue(text.contains("55.00"))
                    assertTrue(text.contains("PAID"))
                    assertTrue(text.contains(order.customerName))
                    assertTrue(text.contains(order.customerAddress))
                    assertTrue(text.contains("DELIVERY CONFIRMED"))
                    assertTrue(text.contains("ORDERED ON"))
                    assertTrue(text.contains("ESTIMATED DELIVERY"))
                    assertTrue(text.contains("Open Chat"))
                    assertTrue(text.contains("RECEIPT UPDATED"))
                    assertFalse(text.contains(order.productName))
                }
            }
        }
        val long = receipt.copy(items = (1..65).map { DeliveryReceiptItem("Confirmed item $it", it) })
        val multiple = ReceiptDocument(context, attributes, listOf(order.copy(deliveryReceipt = long)))
        assertTrue(multiple.pageCount > 1)
        render("receipt-confirmed-long", multiple)
    }

    @Test fun unchangedReceiptDoesNotClaimThereWereChanges() {
        val receipt = DeliveryReceipt(reference = order.id, issuedAt = order.timestamp,
            items = listOf(DeliveryReceiptItem(order.productName, order.quantity)))
        val file = render("receipt-unchanged", ReceiptDocument(context, attributes, listOf(order.copy(deliveryReceipt = receipt))))
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
                pdf.openPage(0).use { page ->
                    assertFalse(page.textContents.joinToString(" ") { it.text }.contains("RECEIPT UPDATED"))
                }
            }
        }
    }

    @Test fun longOrdersPaginateAndSelectedPagesAndCancellationWork() {
        val orders = (1..24).map { index -> order.copy(
            id = "ITEM-$index", productName = "Item $index - Premium purified drinking water with reusable container",
            customerAddress = "Unit 1205, Building B, A very long residential street address, Barangay Merville, Parañaque City, Metro Manila, Philippines",
            offeringType = OFFERING_REFILL,
            refillInstructions = "Please collect the empty containers from the front desk and ring the doorbell on arrival.",
            isPaid = false
        ) }
        val receipt = ReceiptDocument(context, attributes, orders)
        assertTrue(receipt.pageCount > 1)
        render("receipt-long", receipt)
        val selected = File(context.getExternalFilesDir(null), "receipt-selected.pdf")
        selected.outputStream().use { output ->
            assertArrayEquals(arrayOf(PageRange(1, 1)), receipt.write(output, arrayOf(PageRange(1, 1))))
        }
        PdfRenderer(ParcelFileDescriptor.open(selected, ParcelFileDescriptor.MODE_READ_ONLY)).use {
            assertEquals(1, it.pageCount)
        }
        try {
            receipt.write(ByteArrayOutputStream(), cancellationSignal = CancellationSignal().apply { cancel() })
            fail("Cancelled printing should stop")
        } catch (_: OperationCanceledException) { /* Expected. */ }
    }
}
