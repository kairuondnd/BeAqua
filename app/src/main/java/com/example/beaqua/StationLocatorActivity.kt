package com.example.beaqua

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.gson.JsonObject
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.OnCircleAnnotationClickListener
import com.mapbox.maps.plugin.annotation.generated.createCircleAnnotationManager
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class StationLocatorActivity : AppCompatActivity() {

    companion object {
        const val RESULT_STATION_USERNAME = "STATION_USERNAME"
    }

    private lateinit var mapView: MapView
    private lateinit var loading: ProgressBar
    private lateinit var stationCount: TextView
    private lateinit var emptyMessage: TextView
    private lateinit var selectedCard: MaterialCardView
    private lateinit var selectedName: TextView
    private lateinit var selectedAddress: TextView
    private lateinit var selectedDistance: TextView
    private lateinit var selectedDeliveryFee: TextView
    private lateinit var viewProductsButton: MaterialButton

    private var stationAnnotations: CircleAnnotationManager? = null
    private var customerAnnotations: CircleAnnotationManager? = null
    private var customer: User? = null
    private var selectedStation: NearbyStation? = null
    private val stationsByUsername = mutableMapOf<String, NearbyStation>()

    private data class NearbyStation(
        val user: User,
        val point: Point,
        val distanceKm: Double?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_NO
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_station_locator)

        mapView = findViewById(R.id.mapView)
        loading = findViewById(R.id.stationMapProgress)
        stationCount = findViewById(R.id.tvStationMapCount)
        emptyMessage = findViewById(R.id.tvStationMapEmpty)
        selectedCard = findViewById(R.id.cardSelectedStation)
        selectedName = findViewById(R.id.tvSelectedStationName)
        selectedAddress = findViewById(R.id.tvSelectedStationAddress)
        selectedDistance = findViewById(R.id.tvSelectedStationDistance)
        selectedDeliveryFee = findViewById(R.id.tvSelectedStationDeliveryFee)
        viewProductsButton = findViewById(R.id.btnMapViewProducts)

        findViewById<MaterialButton>(R.id.btnBackLocator).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnRecenterStations).setOnClickListener {
            frameVisibleLocations()
        }
        viewProductsButton.setOnClickListener {
            val station = selectedStation ?: return@setOnClickListener
            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(RESULT_STATION_USERNAME, station.user.username)
            )
            finish()
        }

        MapStyleHelper.loadReadableStyle(mapView) {
            setupAnnotations()
            loadCustomerAndStations()
        }
    }

    private fun setupAnnotations() {
        stationAnnotations = mapView.annotations.createCircleAnnotationManager().also { manager ->
            manager.addClickListener(OnCircleAnnotationClickListener { annotation ->
                val username = annotation.getData()
                    ?.asJsonObject
                    ?.get("station_username")
                    ?.asString
                val nearbyStation = username?.let(stationsByUsername::get)
                if (nearbyStation != null) {
                    selectStation(nearbyStation, moveCamera = true)
                }
                true
            })
        }
        customerAnnotations = mapView.annotations.createCircleAnnotationManager()
    }

    private fun loadCustomerAndStations() {
        val username = intent.getStringExtra("USERNAME").orEmpty()
        if (username.isBlank()) {
            Toast.makeText(this, "Customer session was not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loading.visibility = View.VISIBLE
        FirebaseHelper.getUser(username)
            .addOnSuccessListener { customerSnapshot ->
                customer = customerSnapshot.toObject(User::class.java)
                FirebaseHelper.getApprovedStationOwners()
                    .addOnSuccessListener { stationSnapshot ->
                        loading.visibility = View.GONE
                        displayStations(stationSnapshot.toObjects(User::class.java))
                    }
                    .addOnFailureListener {
                        loading.visibility = View.GONE
                        showLoadError("Could not load water stations")
                    }
            }
            .addOnFailureListener {
                loading.visibility = View.GONE
                showLoadError("Could not load your location")
            }
    }

    private fun displayStations(allStations: List<User>) {
        stationAnnotations?.deleteAll()
        customerAnnotations?.deleteAll()
        stationsByUsername.clear()

        val customerPoint = customer?.toPointOrNull()
        if (customerPoint != null) {
            customerAnnotations?.create(
                CircleAnnotationOptions()
                    .withPoint(customerPoint)
                    .withCircleRadius(11.0)
                    .withCircleColor(MapStyleHelper.CUSTOMER_MARKER_COLOR)
                    .withCircleStrokeColor(MapStyleHelper.MARKER_STROKE_COLOR)
                    .withCircleStrokeWidth(4.0)
            )
        } else {
            Toast.makeText(
                this,
                "Set your delivery location in Profile to calculate nearby distances",
                Toast.LENGTH_LONG
            ).show()
        }

        val nearbyStations = allStations.mapNotNull { station ->
            val point = station.toPointOrNull() ?: return@mapNotNull null
            NearbyStation(
                user = station,
                point = point,
                distanceKm = customerPoint?.let {
                    distanceKm(it.latitude(), it.longitude(), point.latitude(), point.longitude())
                }
            )
        }.sortedWith(
            compareBy<NearbyStation> { it.distanceKm ?: Double.MAX_VALUE }
                .thenBy { it.user.name }
        )

        nearbyStations.forEach { station ->
            stationsByUsername[station.user.username] = station
            val data = JsonObject().apply {
                addProperty("station_username", station.user.username)
            }
            stationAnnotations?.create(
                CircleAnnotationOptions()
                    .withPoint(station.point)
                    .withCircleRadius(10.0)
                    .withCircleColor(MapStyleHelper.STATION_MARKER_COLOR)
                    .withCircleStrokeColor(MapStyleHelper.MARKER_STROKE_COLOR)
                    .withCircleStrokeWidth(3.0)
                    .withData(data)
            )
        }

        stationCount.text = when (nearbyStations.size) {
            1 -> "1 station • Blue: you • Teal: station"
            else -> "${nearbyStations.size} stations • Blue: you • Teal: stations"
        }
        emptyMessage.visibility = if (nearbyStations.isEmpty()) View.VISIBLE else View.GONE
        selectedCard.visibility = if (nearbyStations.isEmpty()) View.GONE else View.VISIBLE

        if (nearbyStations.isNotEmpty()) {
            selectStation(nearbyStations.first(), moveCamera = false)
            frameVisibleLocations()
        } else if (customerPoint != null) {
            mapView.mapboxMap.setCamera(
                CameraOptions.Builder().center(customerPoint).zoom(13.0).build()
            )
        }
    }

    private fun selectStation(station: NearbyStation, moveCamera: Boolean) {
        selectedStation = station
        selectedName.text = station.user.name.ifBlank { station.user.username }
        selectedAddress.text = station.user.address.ifBlank { "Address not provided" }
        selectedDistance.text = station.distanceKm?.let(::formatDistance)
            ?: "Distance unavailable"
        selectedDeliveryFee.text = String.format(
            Locale.getDefault(),
            "Delivery fee: ₱%.2f",
            station.user.deliveryFee
        )
        if (moveCamera) {
            mapView.mapboxMap.setCamera(
                CameraOptions.Builder().center(station.point).zoom(14.0).build()
            )
        }
    }

    private fun frameVisibleLocations() {
        val points = stationsByUsername.values.map { it.point }.toMutableList()
        customer?.toPointOrNull()?.let(points::add)
        when (points.size) {
            0 -> return
            1 -> mapView.mapboxMap.setCamera(
                CameraOptions.Builder().center(points.first()).zoom(13.0).build()
            )
            else -> {
                val density = resources.displayMetrics.density
                val camera = mapView.mapboxMap.cameraForCoordinates(
                    points,
                    EdgeInsets(
                        120.0 * density,
                        48.0 * density,
                        300.0 * density,
                        48.0 * density
                    ),
                    null,
                    null
                )
                mapView.mapboxMap.setCamera(camera)
            }
        }
    }

    private fun User.toPointOrNull(): Point? {
        val lat = latitude ?: return null
        val lon = longitude ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0 || (lat == 0.0 && lon == 0.0)) {
            return null
        }
        return Point.fromLngLat(lon, lat)
    }

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0
        val latitudeDelta = Math.toRadians(lat2 - lat1)
        val longitudeDelta = Math.toRadians(lon2 - lon1)
        val a = sin(latitudeDelta / 2).pow(2) +
            cos(Math.toRadians(lat1)) *
            cos(Math.toRadians(lat2)) *
            sin(longitudeDelta / 2).pow(2)
        return earthRadiusKm * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun formatDistance(distanceKm: Double): String {
        return if (distanceKm < 1.0) {
            "${(distanceKm * 1000).toInt()} m away"
        } else {
            String.format(Locale.getDefault(), "%.1f km away", distanceKm)
        }
    }

    private fun showLoadError(message: String) {
        emptyMessage.text = message
        emptyMessage.visibility = View.VISIBLE
        selectedCard.visibility = View.GONE
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
