package com.example.beaqua

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GcashPaymentFlowTest {
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    @Test fun missingReceiptBlocksSubmissionAndSurvivesRecreation() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), GcashPaymentActivity::class.java)
            .putExtra("stationName", "Test station").putExtra("amount", 100.0)
        ActivityScenario.launch<GcashPaymentActivity>(intent).use { scenario ->
            repeat(2) {
                scenario.onActivity { activity ->
                    val views = descendants(activity.window.decorView)
                    views.filterIsInstance<Button>().single { it.text.contains("submit order") }.performClick()
                    assertTrue(views.filterIsInstance<TextView>().any { it.text.contains("Please attach your GCash receipt") })
                    assertFalse(activity.isFinishing)
                }
                if (it == 0) scenario.recreate()
            }
        }
    }

    @Test fun rejectionReasonAndChatDisappearWhenHistoryCardIsRecycled() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_BeAqua)
            val rejected = Order(id = "rejected", checkoutId = "one", status = "Rejected", rejectionReason = "Amount does not match", timestamp = 2)
            val pending = Order(id = "pending", checkoutId = "two", status = "Pending", timestamp = 1)
            val adapter = UserHistoryAdapter(listOf(rejected, pending)) {}
            val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
            adapter.onBindViewHolder(holder, 0)
            assertTrue(holder.tvOrderDetails.text.contains("Amount does not match"))
            assertEquals(View.VISIBLE, holder.itemView.findViewById<View>(R.id.btnRejectionChat).visibility)
            adapter.onBindViewHolder(holder, 1)
            assertFalse(holder.tvOrderDetails.text.contains("Amount does not match"))
            assertEquals(View.GONE, holder.itemView.findViewById<View>(R.id.btnRejectionChat).visibility)
        }
    }
}
