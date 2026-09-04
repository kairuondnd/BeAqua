package com.example.beaqua

import com.mapbox.maps.MapView
import com.mapbox.maps.Style

object MapStyleHelper {
    const val CUSTOMER_MARKER_COLOR = "#1565C0"
    const val STATION_MARKER_COLOR = "#00838F"
    const val MARKER_STROKE_COLOR = "#FFFFFF"

    /**
     * Loads CARTO Voyager as a colorful, geographically meaningful raster basemap. Water, parks,
     * roads, buildings, and labels retain their map semantics while raster rendering avoids the
     * complex vector-label shaders that fail on some lower-end devices and emulators.
     */
    fun loadReadableStyle(
        mapView: MapView,
        showPointOfInterestLabels: Boolean = true,
        onLoaded: (Style) -> Unit = {}
    ) {
        val tileVariant = if (showPointOfInterestLabels) "voyager" else "voyager_nolabels"
        val styleJson = """
            {
              "version": 8,
              "light": {
                "anchor": "viewport",
                "color": "#F7FBFC",
                "intensity": 1.0
              },
              "sources": {
                "beaqua-voyager": {
                  "type": "raster",
                  "tiles": [
                    "https://a.basemaps.cartocdn.com/rastertiles/$tileVariant/{z}/{x}/{y}@2x.png"
                  ],
                  "tileSize": 512,
                  "attribution": "© OpenStreetMap contributors © CARTO"
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
                  "id": "beaqua-voyager-layer",
                  "type": "raster",
                  "source": "beaqua-voyager",
                  "minzoom": 0,
                  "maxzoom": 22,
                  "paint": {
                    "raster-brightness-min": 0.05,
                    "raster-brightness-max": 1.0,
                    "raster-contrast": 0.08,
                    "raster-saturation": 0.16,
                    "raster-fade-duration": 120,
                    "raster-emissive-strength": 1.0
                  }
                }
              ]
            }
        """.trimIndent()

        mapView.mapboxMap.loadStyle(styleJson, onLoaded)
    }
}
