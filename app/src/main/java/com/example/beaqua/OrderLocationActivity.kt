package com.example.beaqua

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions

/** Read-only delivery location recorded at checkout, never the owner's editable profile pin. */
class OrderLocationActivity : AppCompatActivity() {
    private lateinit var map: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BackNavigation.install(this)
        val lat = intent.getDoubleExtra("LATITUDE", Double.NaN)
        val lon = intent.getDoubleExtra("LONGITUDE", Double.NaN)
        if (!OrderLocation.hasPin(Order(customerLat = lat, customerLon = lon))) {
            Toast.makeText(this, "No valid delivery pin saved for this order.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val customer = intent.getStringExtra("CUSTOMER").orEmpty().ifBlank { "Customer" }
        val address = intent.getStringExtra("ADDRESS").orEmpty().ifBlank { "Address not provided" }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val padding = (16 * resources.displayMetrics.density).toInt()
        root.addView(Button(this).apply {
            text = "Back to orders"
            setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        })
        root.addView(TextView(this).apply {
            text = "$customer\nLocation: $address"
            textSize = 16f
            setPadding(padding, padding, padding, padding)
        })
        map = MapView(this)
        root.addView(map, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(Button(this).apply {
            text = "Open in Google Maps"
            setOnClickListener {
                openNavigation("https://www.google.com/maps/dir/?api=1&destination=$lat,$lon", "com.google.android.apps.maps")
            }
        })
        root.addView(Button(this).apply {
            text = "Open in Waze"
            setOnClickListener { openNavigation("https://waze.com/ul?ll=$lat,$lon&navigate=yes", "com.waze") }
        })
        setContentView(root)
        val point = Point.fromLngLat(lon, lat)
        map.mapboxMap.setCamera(CameraOptions.Builder().center(point).zoom(16.0).build())
        MapStyleHelper.loadReadableStyle(map) {
            if (isFinishing || isDestroyed) return@loadReadableStyle
            map.viewAnnotationManager.addViewAnnotation(R.layout.view_station_map_marker,
                viewAnnotationOptions { geometry(point); allowOverlap(true); ignoreCameraPadding(true) }
            ).apply {
                contentDescription = "$customer delivery pin"
                findViewById<ImageView>(R.id.ivStationMapMarker).setImageResource(R.drawable.ic_home)
                findViewById<TextView>(R.id.tvStationMapMarkerLabel).text = customer
            }
        }
    }

    private fun openNavigation(url: String, packageName: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(packageName))
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, "Install the navigation app or a browser to continue.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        if (::map.isInitialized) map.viewAnnotationManager.removeAllViewAnnotations()
        super.onDestroy()
    }
}
