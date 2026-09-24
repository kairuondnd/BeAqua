package com.example.beaqua

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mapbox.maps.MapView
import com.mapbox.maps.CameraOptions
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MapStyleHelperTest {
    @Test fun bothBasemapsLoadWithAuthenticatedTiles() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val intent = Intent(context, PickLocationActivity::class.java)
            .putExtra("LATITUDE", 14.565).putExtra("LONGITUDE", 120.985)
        ActivityScenario.launch<PickLocationActivity>(intent).use { scenario ->
            for (showLabels in listOf(true, false)) {
                val loaded = CountDownLatch(1)
                val tilesLoaded = CountDownLatch(1)
                var mapLoadedSubscription: com.mapbox.common.Cancelable? = null
                scenario.onActivity { activity ->
                    activity.findViewById<MapView>(R.id.mapView).mapboxMap.setCamera(CameraOptions.Builder().zoom(12.0).build())
                    mapLoadedSubscription = activity.findViewById<MapView>(R.id.mapView).mapboxMap.subscribeMapLoaded { tilesLoaded.countDown() }
                    MapStyleHelper.loadReadableStyle(activity.findViewById<MapView>(R.id.mapView), showLabels) { style ->
                        val json = style.styleJSON
                        assertTrue("Mapbox raster source is present", json.contains("beaqua-mapbox"))
                        assertTrue("CARTO tiles have been replaced", !json.contains("cartocdn"))
                        assertTrue("Tiles use the selected Mapbox style", json.contains(if (showLabels) "streets-v12" else "light-v11"))
                        loaded.countDown()
                    }
                }
                assertTrue("Basemap style should load", loaded.await(15, TimeUnit.SECONDS))
                assertTrue("Map tiles should finish loading", tilesLoaded.await(45, TimeUnit.SECONDS))
                scenario.onActivity { mapLoadedSubscription?.cancel() }
                Thread.sleep(500)
                if (showLabels) {
                    val captured = CountDownLatch(1)
                    var colorfulPixels = 0
                    var sampledPixels = 0
                    scenario.onActivity { activity ->
                        activity.findViewById<MapView>(R.id.mapView).snapshot { bitmap ->
                            bitmap?.let {
                                // Sample the map itself, excluding the blue buttons and pin overlays.
                                for (y in 0 until it.height step 8) for (x in 0 until it.width step 8) {
                                    val pixel = it.getPixel(x, y)
                                    val r = Color.red(pixel)
                                    val g = Color.green(pixel)
                                    val b = Color.blue(pixel)
                                    if (maxOf(r, g, b) - minOf(r, g, b) > 45) colorfulPixels++
                                    sampledPixels++
                                }
                            }
                            captured.countDown()
                        }
                    }
                    assertTrue("Map snapshot should complete", captured.await(5, TimeUnit.SECONDS))
                    assertTrue("Water and parks must retain visible color; check the emulator graphics renderer if this fails",
                        sampledPixels > 0 && colorfulPixels > sampledPixels / 20)
                }
                instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                    File(context.getExternalFilesDir(null), "map-${if (showLabels) "streets" else "light"}-preview.png")
                        .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
        }
    }
}
