package com.example.beaqua

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeliveryReceiptEditorTest {
    @Test fun cancelDoesNotSaveAndConfirmUsesEditedValues() {
        val saved = AtomicReference<List<DeliveryReceiptItem>>()
        val result = AtomicReference<DeliveryReceipt>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            fun showEditor() {
                scenario.onActivity { activity ->
                    DeliveryReceiptEditor.show(activity, OrderGroup(listOf(Order(productName = "Water", quantity = 2, stationOwnerUsername = "station"))),
                        availableProducts = listOf(Product(id = "mineral", name = "Mineral water", ownerUsername = "station")),
                        save = { items ->
                            saved.set(items)
                            Tasks.forResult(DeliveryReceipt(items = items))
                        }, onSaved = { result.set(it) })
                }
            }
            showEditor()
            onView(withText("Cancel")).inRoot(isDialog()).perform(click())
            assertNull(saved.get())
            showEditor()
            onView(withContentDescription("Water quantity")).inRoot(isDialog()).perform(replaceText("0"), closeSoftKeyboard())
            onView(withText("Confirm & generate")).inRoot(isDialog()).perform(click())
            onView(withContentDescription("Water quantity")).inRoot(isDialog()).check(matches(hasErrorText("Enter a positive whole number")))
            assertNull(saved.get())
            onView(withContentDescription("Select Water")).inRoot(isDialog()).perform(click())
            onView(withText("Confirm & generate")).inRoot(isDialog()).perform(click())
            onView(withText("Select at least one delivered item.")).inRoot(isDialog()).check(matches(isDisplayed()))
            assertNull(saved.get())
            onView(withContentDescription("Select Mineral water")).inRoot(isDialog()).perform(click())
            onView(withContentDescription("Decrease Mineral water quantity")).inRoot(isDialog()).perform(click())
            onView(withContentDescription("Mineral water quantity")).inRoot(isDialog()).check(matches(withText("1")))
            onView(withContentDescription("Increase Mineral water quantity")).inRoot(isDialog()).perform(click())
            onView(withContentDescription("Mineral water quantity")).inRoot(isDialog()).check(matches(withText("2")))
            onView(withContentDescription("Mineral water quantity")).inRoot(isDialog()).perform(replaceText("5"), closeSoftKeyboard())
            val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "receipt-selector.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
            onView(withText("Confirm & generate")).inRoot(isDialog()).perform(click())
            assertEquals(listOf(DeliveryReceiptItem("Mineral water", 5)), saved.get())
            assertEquals(saved.get(), result.get().items)
        }
    }
}
