package com.example.beaqua

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions
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

    private val stationMarkers = mutableMapOf<String, View>()
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
            if (isFinishing || isDestroyed) return@loadReadableStyle
            loadCustomerAndStations()
        }
    }

    private fun loadCustomerAndStations() {
        val username = intent.getStringExtra("USERNAME").orEmpty()
        if (username.isBlank()) {
            loading.visibility = View.GONE
            showLoadError("Sign in again to load your delivery location")
            return
        }

        loading.visibility = View.VISIBLE
        FirebaseHelper.getUser(username)
            .addOnSuccessListener { customerSnapshot ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                val loadedCustomer = customerSnapshot.toObject(User::class.java)
                FirebaseHelper.getApprovedStationOwners()
                    .addOnSuccessListener { stationSnapshot ->
                        displayLocations(loadedCustomer, stationSnapshot.toObjects(User::class.java))
                    }
                    .addOnFailureListener {
                        if (isFinishing || isDestroyed) return@addOnFailureListener
                        loading.visibility = View.GONE
                        showLoadError("Could not load water stations")
                    }
            }
            .addOnFailureListener {
                if (isFinishing || isDestroyed) return@addOnFailureListener
                loading.visibility = View.GONE
                showLoadError("Could not load your location")
            }
    }

    private fun displayStations(allStations: List<User>) {
        mapView.viewAnnotationManager.removeAllViewAnnotations()
        stationMarkers.clear()
        stationsByUsername.clear()
        selectedStation = null

        val customerPoint = customer?.toPointOrNull()
        if (customerPoint != null) {
            addLocationMarker(customerPoint, isCustomer = true, description = "You — your delivery location") {
                Toast.makeText(this, customer?.address?.takeIf { it.isNotBlank() }?.let { "You: $it" }
                    ?: "You — your delivery location", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(
                this,
                "Set your delivery location in Profile to calculate nearby distances",
                Toast.LENGTH_LONG
            ).show()
        }

        val nearbyStations = allStations.filter { it.username.isNotBlank() }.distinctBy { it.username }.mapNotNull { station ->
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
            stationMarkers[station.user.username] = addLocationMarker(
                station.point, isCustomer = false,
                description = "Water station: ${station.user.name.ifBlank { station.user.username }}"
            ) { selectStation(station, moveCamera = true) }
        }

        stationCount.text = when (nearbyStations.size) {
            1 -> "1 station • Tap a water drop for details"
            else -> "${nearbyStations.size} stations • Tap a water drop for details"
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

    internal fun displayLocations(customer: User?, stations: List<User>) {
        if (isFinishing || isDestroyed) return
        this.customer = customer
        loading.visibility = View.GONE
        displayStations(stations)
    }

    private fun addLocationMarker(point: Point, isCustomer: Boolean, description: String, onClick: () -> Unit): View {
        // Native Android views provide accessible click targets without querying/decoding map features.
        return mapView.viewAnnotationManager.addViewAnnotation(
            R.layout.view_station_map_marker,
            viewAnnotationOptions { geometry(point); allowOverlap(true); ignoreCameraPadding(true) }
        ).apply {
            contentDescription = description
            findViewById<ImageView>(R.id.ivStationMapMarker).apply {
                setImageResource(if (isCustomer) R.drawable.ic_home else R.drawable.ic_map_water_station)
                imageTintList = if (isCustomer) ColorStateList.valueOf(Color.parseColor("#1565C0")) else null
            }
            findViewById<TextView>(R.id.tvStationMapMarkerLabel).visibility = if (isCustomer) View.VISIBLE else View.GONE
            setOnClickListener { if (!isFinishing && !isDestroyed) onClick() }
        }
    }

    private fun selectStation(station: NearbyStation, moveCamera: Boolean) {
        selectedStation = station
        selectedCard.visibility = View.VISIBLE
        stationMarkers.forEach { (username, marker) -> marker.isSelected = username == station.user.username }
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

    override fun onDestroy() {
        if (::mapView.isInitialized) mapView.viewAnnotationManager.removeAllViewAnnotations()
        stationMarkers.clear()
        super.onDestroy()
    }
}
