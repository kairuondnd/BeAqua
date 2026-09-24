package com.example.beaqua

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.widget.NestedScrollView
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.mapbox.api.geocoding.v5.GeocodingCriteria
import com.mapbox.api.geocoding.v5.MapboxGeocoding
import com.mapbox.api.geocoding.v5.models.CarmenFeature
import com.mapbox.api.geocoding.v5.models.GeocodingResponse
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import okhttp3.Call as OkHttpCall
import okhttp3.Callback as OkHttpCallback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response as OkHttpResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.util.Locale
import java.util.UUID

class PickLocationActivity : AppCompatActivity() {

    companion object {
        private const val SEARCH_DELAY_MS = 450L
        private const val MIN_SEARCH_CHARACTERS = 3
        private const val SEARCH_BOX_RESULT_LIMIT = 10
        private const val MAX_SEARCH_RESULTS = 15
        private const val DEFAULT_LATITUDE = 12.8797
        private const val DEFAULT_LONGITUDE = 121.7740

        // Bounding box keeps ambiguous barangay and street names inside the Philippines.
        private const val PHILIPPINES_WEST = 116.0
        private const val PHILIPPINES_SOUTH = 4.0
        private const val PHILIPPINES_EAST = 127.0
        private const val PHILIPPINES_NORTH = 22.0
    }

    private lateinit var mapView: MapView
    private lateinit var confirmButton: Button
    private lateinit var searchView: SearchView
    private lateinit var searchProgress: ProgressBar
    private lateinit var searchHelper: TextView
    private lateinit var searchResultsScroll: NestedScrollView
    private lateinit var searchResultsContainer: LinearLayout
    private lateinit var selectedAddress: TextView

    private val searchHandler = Handler(Looper.getMainLooper())
    private val searchHttpClient = OkHttpClient()
    private val gson = Gson()
    private var pendingSearch: Runnable? = null
    private var activeSearchBoxCall: OkHttpCall? = null
    private var activeLegacySearchCall: MapboxGeocoding? = null
    private var activeReverseCall: MapboxGeocoding? = null
    private var activeRetrieveCall: OkHttpCall? = null
    private var searchSessionToken = UUID.randomUUID().toString()
    private var pendingSearchBoxResults: List<LocationSuggestion>? = null
    private var pendingLegacyResults: List<LocationSuggestion>? = null
    private var searchBoxFailed = false
    private var legacySearchFailed = false
    private var latestSearchQuery = ""
    private var suppressQueryCallback = false
    private var username: String? = null

    private enum class SuggestionSource { SEARCH_BOX, LEGACY_GEOCODER }

    private data class LocationSuggestion(
        val id: String,
        val title: String,
        val subtitle: String,
        val fullAddress: String,
        val featureType: String,
        val source: SuggestionSource,
        val point: Point? = null
    )

    private data class SearchBoxSuggestResponse(
        val suggestions: List<SearchBoxSuggestion> = emptyList()
    )

    private data class SearchBoxSuggestion(
        val name: String = "",
        @SerializedName("name_preferred") val preferredName: String? = null,
        @SerializedName("mapbox_id") val mapboxId: String = "",
        @SerializedName("feature_type") val featureType: String = "",
        val address: String? = null,
        @SerializedName("full_address") val fullAddress: String? = null,
        @SerializedName("place_formatted") val placeFormatted: String? = null
    )

    private data class SearchBoxRetrieveResponse(
        val features: List<SearchBoxFeature> = emptyList()
    )

    private data class SearchBoxFeature(
        val geometry: SearchBoxGeometry? = null,
        val properties: SearchBoxFeatureProperties? = null
    )

    private data class SearchBoxGeometry(
        val coordinates: List<Double> = emptyList()
    )

    private data class SearchBoxFeatureProperties(
        val name: String = "",
        @SerializedName("feature_type") val featureType: String = "",
        @SerializedName("full_address") val fullAddress: String? = null,
        @SerializedName("place_formatted") val placeFormatted: String? = null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_NO
        com.mapbox.common.MapboxOptions.accessToken = getString(R.string.mapbox_access_token)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pick_location)

        username = intent.getStringExtra("USERNAME")
        mapView = findViewById(R.id.mapView)
        confirmButton = findViewById(R.id.btnConfirmLocation)
        searchView = findViewById(R.id.searchView)
        searchProgress = findViewById(R.id.locationSearchProgress)
        searchHelper = findViewById(R.id.tvLocationSearchHelper)
        searchResultsScroll = findViewById(R.id.locationSearchResultsScroll)
        searchResultsContainer = findViewById(R.id.locationSearchResults)
        selectedAddress = findViewById(R.id.tvSelectedMapAddress)

        MapStyleHelper.loadReadableStyle(mapView)

        val hasStartingLocation = intent.hasExtra("LATITUDE") && intent.hasExtra("LONGITUDE")
        val initialLat = intent.getDoubleExtra("LATITUDE", DEFAULT_LATITUDE)
        val initialLon = intent.getDoubleExtra("LONGITUDE", DEFAULT_LONGITUDE)
        mapView.mapboxMap.setCamera(
            CameraOptions.Builder()
                .center(Point.fromLngLat(initialLon, initialLat))
                .zoom(if (hasStartingLocation) 16.0 else 5.5)
                .build()
        )

        intent.getStringExtra("ADDRESS")
            ?.takeIf(String::isNotBlank)
            ?.let { selectedAddress.text = it }

        configureSearchView()

        findViewById<MaterialButton>(R.id.btnBackFromPick).setOnClickListener { finish() }
        confirmButton.setOnClickListener {
            val center = mapView.mapboxMap.cameraState.center
            reverseGeocode(center.latitude(), center.longitude())
        }
    }

    private fun configureSearchView() {
        searchView.queryHint = "Street, barangay, landmark, or city"
        searchView.findViewById<SearchView.SearchAutoComplete>(
            androidx.appcompat.R.id.search_src_text
        )?.apply {
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_hint))
            textSize = 15f
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                val normalized = query.orEmpty().trim()
                cancelPendingSearch()
                if (normalized.length >= MIN_SEARCH_CHARACTERS) {
                    performSearch(normalized)
                } else {
                    showSearchHint("Enter at least 3 characters")
                }
                hideKeyboard()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (suppressQueryCallback) return true
                val normalized = newText.orEmpty().trim()
                cancelPendingSearch()
                if (normalized.length < MIN_SEARCH_CHARACTERS) {
                    latestSearchQuery = ""
                    showSearchHint("Type a street, barangay, landmark, or city")
                    return true
                }

                latestSearchQuery = normalized
                pendingSearch = Runnable { performSearch(normalized) }.also {
                    searchHandler.postDelayed(it, SEARCH_DELAY_MS)
                }
                return true
            }
        })
    }

    private fun cancelPendingSearch() {
        pendingSearch?.let(searchHandler::removeCallbacks)
        pendingSearch = null
        activeSearchBoxCall?.cancel()
        activeSearchBoxCall = null
        activeLegacySearchCall?.cancelCall()
        activeLegacySearchCall = null
        activeRetrieveCall?.cancel()
        activeRetrieveCall = null
    }

    private fun performSearch(query: String) {
        latestSearchQuery = query
        pendingSearchBoxResults = null
        pendingLegacyResults = null
        searchBoxFailed = false
        legacySearchFailed = false
        showSearchLoading()
        searchWithSearchBox(query)
        searchWithLegacyGeocoder(query)
    }

    private fun searchWithSearchBox(query: String) {
        val urlBuilder = HttpUrl.Builder()
            .scheme("https")
            .host("api.mapbox.com")
            .addPathSegments("search/searchbox/v1/suggest")
            .addQueryParameter("q", query)
            .addQueryParameter("access_token", getString(R.string.mapbox_access_token))
            .addQueryParameter("session_token", searchSessionToken)
            .addQueryParameter("country", "ph")
            .addQueryParameter("language", "en")
            .addQueryParameter(
                "bbox",
                "$PHILIPPINES_WEST,$PHILIPPINES_SOUTH,$PHILIPPINES_EAST,$PHILIPPINES_NORTH"
            )
            .addQueryParameter(
                "types",
                "address,poi,place,city,locality,neighborhood,street,district,postcode"
            )
            .addQueryParameter("limit", SEARCH_BOX_RESULT_LIMIT.toString())

        val request = Request.Builder().url(urlBuilder.build()).get().build()
        val call = searchHttpClient.newCall(request)
        activeSearchBoxCall = call
        call.enqueue(object : OkHttpCallback {
            override fun onFailure(call: OkHttpCall, error: IOException) {
                if (call.isCanceled()) return
                runOnUiThread {
                    if (query != latestSearchQuery) return@runOnUiThread
                    activeSearchBoxCall = null
                    searchBoxFailed = true
                    pendingSearchBoxResults = emptyList()
                    renderCombinedSearchResults(query)
                }
            }

            override fun onResponse(call: OkHttpCall, response: OkHttpResponse) {
                val parsed = runCatching {
                    response.use {
                        if (!it.isSuccessful) return@use null
                        gson.fromJson(
                            it.body?.string().orEmpty(),
                            SearchBoxSuggestResponse::class.java
                        )
                    }
                }.getOrNull()

                runOnUiThread {
                    if (call.isCanceled() || query != latestSearchQuery) return@runOnUiThread
                    activeSearchBoxCall = null
                    searchBoxFailed = parsed == null
                    pendingSearchBoxResults = parsed?.suggestions.orEmpty()
                        .filter { it.mapboxId.isNotBlank() && it.name.isNotBlank() }
                        .map { suggestion ->
                            val title = suggestion.preferredName
                                ?.takeIf(String::isNotBlank)
                                ?: suggestion.name
                            val subtitle = suggestion.fullAddress
                                ?.takeIf(String::isNotBlank)
                                ?: suggestion.placeFormatted?.takeIf(String::isNotBlank)
                                ?: suggestion.address.orEmpty()
                            val fullAddress = when {
                                subtitle.isBlank() -> title
                                subtitle.startsWith(title, ignoreCase = true) -> subtitle
                                else -> "$title, $subtitle"
                            }
                            LocationSuggestion(
                                id = suggestion.mapboxId,
                                title = title,
                                subtitle = subtitle,
                                fullAddress = fullAddress,
                                featureType = suggestion.featureType,
                                source = SuggestionSource.SEARCH_BOX
                            )
                        }
                    renderCombinedSearchResults(query)
                }
            }
        })
    }

    private fun searchWithLegacyGeocoder(query: String) {
        val builder = MapboxGeocoding.builder()
            .accessToken(getString(R.string.mapbox_access_token))
            .query(query)
            .mode(GeocodingCriteria.MODE_PLACES)
            .bbox(
                PHILIPPINES_WEST,
                PHILIPPINES_SOUTH,
                PHILIPPINES_EAST,
                PHILIPPINES_NORTH
            )
            .geocodingTypes(
                GeocodingCriteria.TYPE_ADDRESS,
                GeocodingCriteria.TYPE_POI,
                GeocodingCriteria.TYPE_NEIGHBORHOOD,
                GeocodingCriteria.TYPE_LOCALITY,
                GeocodingCriteria.TYPE_PLACE,
                GeocodingCriteria.TYPE_DISTRICT
            )
            .country("PH")
            .languages(Locale.ENGLISH)
            .limit(10)
            .autocomplete(true)
            .fuzzyMatch(true)

        val client = builder.build()
        activeLegacySearchCall = client
        client.enqueueCall(object : Callback<GeocodingResponse> {
            override fun onResponse(
                call: Call<GeocodingResponse>,
                response: Response<GeocodingResponse>
            ) {
                if (call.isCanceled || query != latestSearchQuery) return
                activeLegacySearchCall = null
                legacySearchFailed = !response.isSuccessful
                pendingLegacyResults = if (response.isSuccessful) {
                    response.body()?.features().orEmpty()
                        .mapNotNull(::legacySuggestion)
                } else {
                    emptyList()
                }
                renderCombinedSearchResults(query)
            }

            override fun onFailure(call: Call<GeocodingResponse>, error: Throwable) {
                if (call.isCanceled) return
                if (query != latestSearchQuery) return
                activeLegacySearchCall = null
                legacySearchFailed = true
                pendingLegacyResults = emptyList()
                renderCombinedSearchResults(query)
            }
        })
    }

    private fun legacySuggestion(feature: CarmenFeature): LocationSuggestion? {
        val point = feature.center() ?: return null
        val placeName = feature.placeName().orEmpty().ifBlank {
            feature.text().orEmpty().ifBlank { return null }
        }
        val title = feature.text().orEmpty().ifBlank { placeName.substringBefore(',') }
        val subtitle = placeName.removePrefix(title).trimStart(' ', ',')
        return LocationSuggestion(
            id = feature.id().orEmpty().ifBlank {
                "$placeName:${point.longitude()},${point.latitude()}"
            },
            title = title,
            subtitle = subtitle,
            fullAddress = placeName,
            featureType = feature.placeType()?.firstOrNull().orEmpty(),
            source = SuggestionSource.LEGACY_GEOCODER,
            point = point
        )
    }

    private fun renderCombinedSearchResults(query: String) {
        val searchBoxResults = pendingSearchBoxResults ?: return
        val legacyResults = pendingLegacyResults ?: return
        if (query != latestSearchQuery) return

        val combined = (searchBoxResults + legacyResults)
            .distinctBy { normalizeSearchText(it.fullAddress) }
            .sortedWith(
                compareBy<LocationSuggestion> { matchPriority(query, it) }
                    .thenBy { it.title.length }
            )
            .take(MAX_SEARCH_RESULTS)

        when {
            combined.isNotEmpty() -> showSearchResults(combined)
            searchBoxFailed && legacySearchFailed -> {
                showSearchHint("Could not search. Check your connection and try again.")
            }
            else -> showSearchHint(
                "No Philippine locations found. Try adding a city, barangay, or landmark."
            )
        }
    }

    private fun matchPriority(query: String, suggestion: LocationSuggestion): Int {
        val normalizedQuery = normalizeSearchText(query)
        val normalizedTitle = normalizeSearchText(suggestion.title)
        val geographicResult = suggestion.featureType.lowercase(Locale.ENGLISH) in setOf(
            "address", "street", "neighborhood", "locality", "place", "city", "district"
        )
        return when {
            normalizedTitle == normalizedQuery -> 0
            geographicResult && normalizedTitle.startsWith(normalizedQuery) -> 1
            geographicResult && normalizedTitle.contains(normalizedQuery) -> 2
            normalizedTitle.startsWith(normalizedQuery) -> 3
            normalizedTitle.contains(normalizedQuery) -> 4
            else -> 5
        }
    }

    private fun normalizeSearchText(value: String): String = value
        .lowercase(Locale.ENGLISH)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private fun showSearchLoading() {
        searchProgress.visibility = View.VISIBLE
        searchHelper.visibility = View.VISIBLE
        searchHelper.text = "Searching addresses and places across the Philippines..."
        searchResultsScroll.visibility = View.GONE
        searchResultsContainer.removeAllViews()
    }

    private fun showSearchHint(message: String) {
        searchProgress.visibility = View.GONE
        searchResultsScroll.visibility = View.GONE
        searchResultsContainer.removeAllViews()
        searchHelper.visibility = View.VISIBLE
        searchHelper.text = message
    }

    private fun showSearchResults(results: List<LocationSuggestion>) {
        searchProgress.visibility = View.GONE
        searchHelper.visibility = View.GONE
        searchResultsContainer.removeAllViews()

        results.forEachIndexed { index, result ->
            val row = TextView(this).apply {
                text = if (result.subtitle.isBlank()) {
                    result.title
                } else {
                    "${result.title}\n${result.subtitle}"
                }
                setTextColor(getColor(R.color.text_primary))
                textSize = 14f
                maxLines = 3
                setPadding(dp(18), dp(11), dp(18), dp(11))
                isClickable = true
                isFocusable = true
                contentDescription = "Select ${result.fullAddress}"
                val selectable = TypedValue()
                if (theme.resolveAttribute(
                        android.R.attr.selectableItemBackground,
                        selectable,
                        true
                    )
                ) {
                    setBackgroundResource(selectable.resourceId)
                }
                setOnClickListener { selectSearchResult(result) }
            }
            searchResultsContainer.addView(row)

            if (index < results.lastIndex) {
                searchResultsContainer.addView(
                    View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dp(1)
                        )
                        setBackgroundColor(getColor(R.color.divider))
                    }
                )
            }
        }
        searchResultsScroll.scrollTo(0, 0)
        searchResultsScroll.visibility = View.VISIBLE
    }

    private fun selectSearchResult(result: LocationSuggestion) {
        result.point?.let {
            applySelectedResult(result, it)
            return
        }
        retrieveSearchBoxResult(result)
    }

    private fun retrieveSearchBoxResult(result: LocationSuggestion) {
        activeRetrieveCall?.cancel()
        searchProgress.visibility = View.VISIBLE
        searchHelper.visibility = View.VISIBLE
        searchHelper.text = "Opening the exact location..."
        searchResultsScroll.visibility = View.GONE

        val url = HttpUrl.Builder()
            .scheme("https")
            .host("api.mapbox.com")
            .addPathSegments("search/searchbox/v1/retrieve")
            .addPathSegment(result.id)
            .addQueryParameter("access_token", getString(R.string.mapbox_access_token))
            .addQueryParameter("session_token", searchSessionToken)
            .addQueryParameter("language", "en")
            .build()
        val call = searchHttpClient.newCall(Request.Builder().url(url).get().build())
        activeRetrieveCall = call
        call.enqueue(object : OkHttpCallback {
            override fun onFailure(call: OkHttpCall, error: IOException) {
                if (call.isCanceled()) return
                runOnUiThread { showRetrieveFailure(result) }
            }

            override fun onResponse(call: OkHttpCall, response: OkHttpResponse) {
                val feature = runCatching {
                    response.use {
                        if (!it.isSuccessful) return@use null
                        gson.fromJson(
                            it.body?.string().orEmpty(),
                            SearchBoxRetrieveResponse::class.java
                        ).features.firstOrNull()
                    }
                }.getOrNull()
                val coordinates = feature?.geometry?.coordinates.orEmpty()
                val point = if (coordinates.size >= 2) {
                    Point.fromLngLat(coordinates[0], coordinates[1])
                } else {
                    null
                }

                runOnUiThread {
                    if (call.isCanceled()) return@runOnUiThread
                    activeRetrieveCall = null
                    if (point == null) {
                        showRetrieveFailure(result)
                        return@runOnUiThread
                    }
                    val properties = feature?.properties
                    val resolvedTitle = properties?.name
                        ?.takeIf(String::isNotBlank)
                        ?: result.title
                    val resolvedAddress = properties?.fullAddress
                        ?.takeIf(String::isNotBlank)
                        ?: properties?.placeFormatted?.takeIf(String::isNotBlank)
                        ?: result.fullAddress
                    searchSessionToken = UUID.randomUUID().toString()
                    applySelectedResult(
                        result.copy(
                            title = resolvedTitle,
                            fullAddress = resolvedAddress,
                            featureType = properties?.featureType
                                ?.takeIf(String::isNotBlank)
                                ?: result.featureType,
                            point = point
                        ),
                        point
                    )
                }
            }
        })
    }

    private fun showRetrieveFailure(result: LocationSuggestion) {
        activeRetrieveCall = null
        searchProgress.visibility = View.GONE
        searchHelper.visibility = View.VISIBLE
        searchHelper.text = "Could not open that result. Choose another match or try again."
        searchResultsScroll.visibility = View.VISIBLE
        Toast.makeText(
            this,
            "Could not open ${result.title}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun applySelectedResult(result: LocationSuggestion, point: Point) {
        searchProgress.visibility = View.GONE
        suppressQueryCallback = true
        searchView.setQuery(result.title, false)
        suppressQueryCallback = false
        searchView.clearFocus()
        hideKeyboard()
        searchResultsScroll.visibility = View.GONE
        searchHelper.visibility = View.VISIBLE
        searchHelper.text = "Result selected. Move the map to fine-tune the pin."
        selectedAddress.text = result.fullAddress
        mapView.mapboxMap.setCamera(
            CameraOptions.Builder()
                .center(point)
                .zoom(zoomForType(result.featureType))
                .build()
        )
    }

    private fun zoomForType(featureType: String): Double {
        return when (featureType.lowercase(Locale.ENGLISH)) {
            "address", "poi" -> 17.0
            "street", "postcode" -> 16.0
            "neighborhood" -> 15.5
            "locality" -> 14.5
            "place", "city" -> 12.5
            "district" -> 10.5
            else -> 14.0
        }
    }

    private fun reverseGeocode(lat: Double, lon: Double) {
        activeReverseCall?.cancelCall()
        confirmButton.isEnabled = false
        confirmButton.text = "Finding exact address..."
        selectedAddress.text = "Checking the pin's street address..."

        val client = MapboxGeocoding.builder()
            .accessToken(getString(R.string.mapbox_access_token))
            .query(Point.fromLngLat(lon, lat))
            .mode(GeocodingCriteria.MODE_PLACES)
            .geocodingTypes(
                GeocodingCriteria.TYPE_ADDRESS,
                GeocodingCriteria.TYPE_POI,
                GeocodingCriteria.TYPE_NEIGHBORHOOD,
                GeocodingCriteria.TYPE_LOCALITY,
                GeocodingCriteria.TYPE_PLACE
            )
            .languages(Locale.ENGLISH)
            .reverseMode(GeocodingCriteria.REVERSE_MODE_DISTANCE)
            .limit(1)
            .build()

        activeReverseCall = client
        client.enqueueCall(object : Callback<GeocodingResponse> {
            override fun onResponse(
                call: Call<GeocodingResponse>,
                response: Response<GeocodingResponse>
            ) {
                if (call.isCanceled) return
                activeReverseCall = null
                setConfirmLoading(false)
                if (!response.isSuccessful) {
                    selectedAddress.text = "Could not identify this pin"
                    Toast.makeText(
                        this@PickLocationActivity,
                        "Address lookup failed. Try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }

                val address = response.body()?.features()?.firstOrNull()?.placeName()
                    ?.takeIf(String::isNotBlank)
                    ?: String.format(Locale.US, "Pin at %.6f, %.6f", lat, lon)
                selectedAddress.text = address
                returnLocation(lat, lon, address)
            }

            override fun onFailure(call: Call<GeocodingResponse>, error: Throwable) {
                if (call.isCanceled) return
                activeReverseCall = null
                setConfirmLoading(false)
                selectedAddress.text = "Address lookup failed"
                Toast.makeText(
                    this@PickLocationActivity,
                    "Could not get the address. Check your connection and try again.",
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    private fun setConfirmLoading(loading: Boolean) {
        confirmButton.isEnabled = !loading
        confirmButton.text = if (loading) "Finding exact address..." else "Confirm this location"
    }

    private fun returnLocation(lat: Double, lon: Double, address: String) {
        val resultIntent = Intent().apply {
            putExtra("LATITUDE", lat)
            putExtra("LONGITUDE", lon)
            putExtra("ADDRESS", address)
        }
        val currentUsername = username.orEmpty()
        if (currentUsername.isBlank()) {
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
            return
        }

        confirmButton.isEnabled = false
        FirebaseHelper.updateUserLocation(currentUsername, lat, lon, address)
            .addOnSuccessListener {
                setResult(Activity.RESULT_OK, resultIntent)
                Toast.makeText(this, "Location updated successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                confirmButton.isEnabled = true
                Toast.makeText(this, "Failed to update location", Toast.LENGTH_SHORT).show()
            }
    }

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(searchView.windowToken, 0)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        cancelPendingSearch()
        activeReverseCall?.cancelCall()
        activeRetrieveCall?.cancel()
        super.onDestroy()
    }
}
