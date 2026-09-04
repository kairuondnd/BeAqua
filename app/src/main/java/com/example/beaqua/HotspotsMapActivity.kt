package com.example.beaqua

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.OnPolygonAnnotationClickListener
import com.mapbox.maps.plugin.annotation.generated.PolygonAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolygonAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPolygonAnnotationManager
import java.util.*
import kotlin.math.*

class HotspotsMapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var btnBack: MaterialButton
    private lateinit var toggleTimeRange: MaterialButtonToggleGroup
    private lateinit var summaryCard: MaterialCardView
    private lateinit var tvHotspotTitle: TextView
    private lateinit var tvHotspotDescription: TextView
    
    private lateinit var currentUsername: String
    private var polygonAnnotationManager: PolygonAnnotationManager? = null
    
    private var currentHotspots = mutableListOf<HotspotGroup>()

    private class HotspotGroup(var centerLat: Double, var centerLon: Double, initialOrder: Order) {
        val orders = mutableListOf<Order>()
        var areaName: String = ""

        init {
            addOrder(initialOrder)
            areaName = getAreaFromAddress(initialOrder.customerAddress)
        }

        fun addOrder(order: Order) {
            orders.add(order)
            // Re-calculate centroid for accuracy
            centerLat = orders.mapNotNull { it.customerLat }.average()
            centerLon = orders.mapNotNull { it.customerLon }.average()
        }

        fun getTotalRevenue() = orders.sumOf { it.totalPrice }
        fun getOrderCount() = orders.size
        
        private fun getAreaFromAddress(address: String): String {
            val parts = address.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            return when {
                parts.size >= 4 -> parts[parts.size - 4] // Usually Barangay/Neighborhood
                parts.size >= 2 -> parts[0]
                else -> address
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_NO
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hotspots_map)

        currentUsername = intent.getStringExtra("USERNAME") ?: ""
        mapView = findViewById(R.id.hotspotsMapView)
        btnBack = findViewById(R.id.btnBackHotspotsMap)
        toggleTimeRange = findViewById(R.id.toggleTimeRange)
        summaryCard = findViewById(R.id.summaryCard)
        tvHotspotTitle = findViewById(R.id.tvHotspotTitle)
        tvHotspotDescription = findViewById(R.id.tvHotspotDescription)

        btnBack.setOnClickListener { finish() }

        MapStyleHelper.loadReadableStyle(mapView, showPointOfInterestLabels = false) {
            val annotationApi = mapView.annotations
            polygonAnnotationManager = annotationApi.createPolygonAnnotationManager()
            
            polygonAnnotationManager?.addClickListener(OnPolygonAnnotationClickListener { annotation ->
                val hotspot = annotation.getData()?.asJsonObject?.get("hotspot_index")?.asInt?.let {
                    currentHotspots.getOrNull(it)
                }
                
                if (hotspot != null) {
                    showHotspotDetails(hotspot)
                }
                true
            })

            loadHotspots(7) // Default to 7 days
        }

        toggleTimeRange.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnWeekly -> loadHotspots(7)
                    R.id.btnMonthly -> loadHotspots(30)
                }
            }
        }
    }

    private fun showHotspotDetails(hotspot: HotspotGroup) {
        tvHotspotTitle.text = hotspot.areaName
        val details = "Total Revenue: ₱${String.format(Locale.US, "%,.2f", hotspot.getTotalRevenue())}\n" +
                      "Total Orders: ${hotspot.getOrderCount()}\n" +
                      "Avg. Order Value: ₱${String.format(Locale.US, "%,.2f", hotspot.getTotalRevenue() / hotspot.getOrderCount())}"
        tvHotspotDescription.text = details
    }

    private fun loadHotspots(days: Int) {
        val now = System.currentTimeMillis()
        val timeRangeMillis = days.toLong() * 24 * 60 * 60 * 1000

        // Reset UI
        tvHotspotTitle.text = "Sales Hotspots"
        tvHotspotDescription.text =
            "Yellow to red shows increasing sales activity. Select a hot zone for details."

        FirebaseHelper.ordersCollection
            .whereEqualTo("stationOwnerUsername", currentUsername)
            .whereEqualTo("status", "Delivered")
            .get()
            .addOnSuccessListener { result ->
                val orders = result.toObjects(Order::class.java)
                val filteredOrders = orders.filter { now - it.timestamp <= timeRangeMillis }
                
                if (filteredOrders.isEmpty()) {
                    polygonAnnotationManager?.deleteAll()
                    Toast.makeText(this, "No delivered orders in the last $days days.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                displayHotspotsOnMap(filteredOrders)
            }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371 // Earth radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun createCirclePolygon(center: Point, radiusInKm: Double): List<Point> {
        val points = mutableListOf<Point>()
        val degreesBetweenPoints = 10.0
        val numberOfPoints = (360 / degreesBetweenPoints).toInt()
        val distRadians = radiusInKm / 6371.0
        val centerLatRad = Math.toRadians(center.latitude())
        val centerLonRad = Math.toRadians(center.longitude())

        for (i in 0..numberOfPoints) {
            val angle = Math.toRadians(i * degreesBetweenPoints)
            val latRad = asin(sin(centerLatRad) * cos(distRadians) + cos(centerLatRad) * sin(distRadians) * cos(angle))
            val lonRad = centerLonRad + atan2(sin(angle) * sin(distRadians) * cos(centerLatRad), cos(distRadians) - sin(centerLatRad) * sin(latRad))
            points.add(Point.fromLngLat(Math.toDegrees(lonRad), Math.toDegrees(latRad)))
        }
        return points
    }

    private fun displayHotspotsOnMap(orders: List<Order>) {
        polygonAnnotationManager?.deleteAll()
        currentHotspots.clear()

        // Geographic Clustering Logic (5km radius grouping for better accuracy)
        for (order in orders) {
            val lat = order.customerLat ?: continue
            val lon = order.customerLon ?: continue

            var belongsToGroup = false
            for (hotspot in currentHotspots) {
                if (calculateDistance(lat, lon, hotspot.centerLat, hotspot.centerLon) <= 5.0) {
                    hotspot.addOrder(order)
                    belongsToGroup = true
                    break
                }
            }

            if (!belongsToGroup) {
                currentHotspots.add(HotspotGroup(lat, lon, order))
            }
        }

        val highestRevenue = currentHotspots.maxOfOrNull { it.getTotalRevenue() }
            ?.coerceAtLeast(1.0) ?: 1.0

        currentHotspots.forEachIndexed { index, hotspot ->
            val totalRevenue = hotspot.getTotalRevenue()

            // Radius scales with sales volume (revenue)
            // Minimum 1km, grows by 1km for every 5000 in revenue
            val radiusKm = 1.0 + (totalRevenue / 5000.0)
            val circlePoints = createCirclePolygon(Point.fromLngLat(hotspot.centerLon, hotspot.centerLat), radiusKm)

            val polygonOptions = PolygonAnnotationOptions()
                .withPoints(listOf(circlePoints))
                .withFillColor(hotspotColor(totalRevenue / highestRevenue))
                .withFillOpacity(0.52)
                .withFillOutlineColor("#7C2D12")
                .withData(com.google.gson.JsonObject().apply { addProperty("hotspot_index", index) })
            
            polygonAnnotationManager?.create(polygonOptions)
        }

        // Center camera on the most significant hotspot
        if (currentHotspots.isNotEmpty()) {
            val bestHotspot = currentHotspots.maxByOrNull { it.getTotalRevenue() }!!
            mapView.mapboxMap.setCamera(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(bestHotspot.centerLon, bestHotspot.centerLat))
                    .zoom(11.0)
                    .build()
            )
        }
    }

    private fun hotspotColor(relativeRevenue: Double): String = when {
        relativeRevenue >= 0.67 -> "#D32F2F"
        relativeRevenue >= 0.34 -> "#F57C00"
        else -> "#FBC02D"
    }
}
