package com.example.beaqua

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mapbox.maps.MapView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class StationLocatorMarkerTest {
    @Test fun customerAndStationMarkersCanBeTappedRepeatedly() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // No username: no customer, station, or order requests are sent to Firebase.
        ActivityScenario.launch<StationLocatorActivity>(Intent(context, StationLocatorActivity::class.java)).use { scenario ->
            var styleReady = false
            val deadline = SystemClock.elapsedRealtime() + 15000
            while (!styleReady && SystemClock.elapsedRealtime() < deadline) {
                scenario.onActivity { styleReady = it.findViewById<MapView>(R.id.mapView).mapboxMap.getStyle() != null }
                if (!styleReady) Thread.sleep(100)
            }
            assertTrue(styleReady)
            val customer = User(username = "sample-customer", name = "Customer", latitude = 14.487, longitude = 121.030)
            val first = User(username = "sample-first", name = "First Water Station", latitude = 14.482, longitude = 121.026, deliveryFee = 10.0)
            val second = User(username = "sample-second", name = "Second Water Station", latitude = 14.479, longitude = 121.035, deliveryFee = 20.0)
            scenario.onActivity { it.displayLocations(customer, listOf(first, second)) }
            fun tap(user: User) {
                scenario.onActivity { it.findViewById<View>(R.id.btnRecenterStations).performClick() }
                Thread.sleep(500)
                var x = 0f
                var y = 0f
                scenario.onActivity { activity ->
                    val matches = arrayListOf<View>()
                    val description = if (user == customer) "You — your delivery location" else "Water station: ${user.name}"
                    activity.window.decorView.findViewsWithText(matches, description, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION)
                    assertEquals("One accessible marker for $description", 1, matches.size)
                    val marker = matches.single()
                    assertTrue(marker.isShown)
                    val position = IntArray(2)
                    marker.getLocationOnScreen(position)
                    x = position[0] + marker.width / 2f
                    y = position[1] + marker.height / 2f
                }
                val now = SystemClock.uptimeMillis()
                MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0).let { instrumentation.uiAutomation.injectInputEvent(it, true); it.recycle() }
                MotionEvent.obtain(now, now + 80, MotionEvent.ACTION_UP, x, y, 0).let { instrumentation.uiAutomation.injectInputEvent(it, true); it.recycle() }
                instrumentation.waitForIdleSync()
                Thread.sleep(300)
            }
            repeat(3) {
                tap(customer)
                scenario.onActivity { assertEquals(first.name, it.findViewById<TextView>(R.id.tvSelectedStationName).text.toString()) }
                tap(second)
                scenario.onActivity { assertEquals(second.name, it.findViewById<TextView>(R.id.tvSelectedStationName).text.toString()) }
                tap(first)
                scenario.onActivity { assertEquals(first.name, it.findViewById<TextView>(R.id.tvSelectedStationName).text.toString()) }
            }
            scenario.onActivity { it.findViewById<View>(R.id.btnRecenterStations).performClick() }
            Thread.sleep(2500)
            instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                File(context.getExternalFilesDir(null), "station-map-markers-preview.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            scenario.onActivity {
                it.findViewById<View>(R.id.btnMapViewProducts).performClick()
                assertTrue("Selecting products should close the locator", it.isFinishing)
            }
        }
    }
}
