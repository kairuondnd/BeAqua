package com.example.beaqua

import android.content.Intent
import android.widget.ImageView
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReceiptActivityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun receiptNotificationLinksRemainDistinctForDifferentOrdersAndAccounts() {
        val first = ReceiptActivity.intent(context, "customer", "ORDER-1")
        assertFalse(first.filterEquals(ReceiptActivity.intent(context, "customer", "ORDER-2")))
        assertFalse(first.filterEquals(ReceiptActivity.intent(context, "other", "ORDER-1")))
        val target = NotificationHelper.customerDestination(context, "customer", BeAquaNotification(
            type = BeAquaNotification.TYPE_RECEIPT_UPDATED, orderId = "ORDER-1"))
        assertTrue(first.filterEquals(target))
        assertEquals("ORDER-1", target.getStringExtra("ORDER_ID"))
    }

    @Test fun savedReceiptRendersWithoutSubmittingAnOrder() {
        // No account/order extras: do not query Firebase during this isolated preview test.
        ActivityScenario.launch<ReceiptActivity>(Intent(context, ReceiptActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                activity.displayReceipt(listOf(Order(
                    id = "TEST-RECEIPT", productName = "Purified water", quantity = 2,
                    customerName = "Sample Customer", customerAddress = "Sample address", stationName = "Water Station",
                    totalPrice = 50.0, isPaid = true, status = "Delivered", timestamp = 1790200000000L,
                    deliveryReceipt = DeliveryReceipt(reference = "TEST-RECEIPT", issuedAt = 1790200000000L,
                        items = listOf(DeliveryReceiptItem("Purified water", 3)), itemsChanged = true)
                )))
            }
            fun hasRenderedPage(view: View): Boolean =
                (view is ImageView && view.contentDescription == "Receipt page 1 of 1" && view.drawable != null) ||
                    (view is ViewGroup && (0 until view.childCount).any { hasRenderedPage(view.getChildAt(it)) })
            var rendered = false
            val deadline = System.currentTimeMillis() + 15000
            while (!rendered && System.currentTimeMillis() < deadline) {
                instrumentation.waitForIdleSync()
                scenario.onActivity { rendered = hasRenderedPage(it.window.decorView) }
                if (!rendered) Thread.sleep(100)
            }
            assertTrue("Receipt page should be visible", rendered)
            instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                File(context.getExternalFilesDir(null), "receipt-notification-preview.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
        }
    }
}
