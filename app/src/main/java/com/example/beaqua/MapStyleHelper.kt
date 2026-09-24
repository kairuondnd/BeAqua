package com.example.beaqua

import android.net.Uri
import com.mapbox.maps.MapView
import com.mapbox.maps.Style

object MapStyleHelper {
    const val CUSTOMER_MARKER_COLOR = "#1565C0"
    const val STATION_MARKER_COLOR = "#00838F"
    const val MARKER_STROKE_COLOR = "#FFFFFF"

    /**
     * Uses authenticated Mapbox raster tiles with the app's existing public access token.
     * Raster rendering avoids the vector-label shaders that fail on some devices and emulators.
     * The light style keeps the hotspot overlays readable; other screens use street maps.
     */
    fun loadReadableStyle(
        mapView: MapView,
        showPointOfInterestLabels: Boolean = true,
        onLoaded: (Style) -> Unit = {}
    ) {
        val mapStyle = if (showPointOfInterestLabels) "streets-v12" else "light-v11"
        // Keep street labels neutral while making water, parks, and main roads more distinct.
        val saturation = if (showPointOfInterestLabels) 0.20 else 0.0
        val contrast = if (showPointOfInterestLabels) 0.08 else 0.0
        val accessToken = Uri.encode(mapView.context.getString(R.string.mapbox_access_token).trim())
        val tileUrl = "https://api.mapbox.com/styles/v1/mapbox/$mapStyle/tiles/512/" +
            "{z}/{x}/{y}@2x?access_token=$accessToken"
        val styleJson = """
            {
              "version": 8,
              "sources": {
                "beaqua-mapbox": {
                  "type": "raster",
                  "tiles": [
                    "$tileUrl"
                  ],
                  "tileSize": 512,
                  "maxzoom": 22,
                  "attribution": "<a href=\"https://www.mapbox.com/about/maps/\">© Mapbox</a> <a href=\"https://www.openstreetmap.org/copyright\">© OpenStreetMap</a> <a href=\"https://apps.mapbox.com/feedback/\">Improve this map</a>"
                }
              },
              "layers": [
                {
                  "id": "beaqua-map-background",
                  "type": "background",
                  "paint": {
                    "background-color": "#DCEFF8"
                  }
                },
                {
                  "id": "beaqua-mapbox-layer",
                  "type": "raster",
                  "source": "beaqua-mapbox",
                  "minzoom": 0,
                  "paint": {
                    "raster-brightness-min": 0.0,
                    "raster-brightness-max": 1.0,
                    "raster-opacity": 1.0,
                    "raster-contrast": $contrast,
                    "raster-saturation": $saturation,
                    "raster-fade-duration": 120
                  }
                }
              ]
            }
        """.trimIndent()

        mapView.mapboxMap.loadStyle(styleJson, onLoaded)
    }
}
