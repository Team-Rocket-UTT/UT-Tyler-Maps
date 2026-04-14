package com.teamrocket.uttylermaps

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.mappedin.MapView
import com.mappedin.models.CameraAnimationOptions
import com.mappedin.models.CameraTarget
import com.mappedin.models.EasingFunction
import com.mappedin.models.GetMapDataWithCredentialsOptions
import com.mappedin.models.MapDataType
import com.mappedin.models.PointOfInterest
import com.mappedin.models.Show3DMapOptions
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class DisplayMapDemoActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var loadingIndicator: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Display a Map"

        // Create a FrameLayout to hold both the map view and loading indicator
        val container = FrameLayout(this)

        mapView = MapView(this)
        container.addView(
            mapView.view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        // Add loading indicator
        loadingIndicator = ProgressBar(this)
        val loadingParams =
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        loadingParams.gravity = Gravity.CENTER
        container.addView(loadingIndicator, loadingParams)

        setContentView(container)

        // See Trial API key Terms and Conditions
        // https://developer.mappedin.com/docs/demo-keys-and-maps
        val options =
            GetMapDataWithCredentialsOptions(
                key = "mik_yeBk0Vf0nNJtpesfu560e07e5",
                secret = "mis_2g9ST8ZcSFb5R9fPnsvYhrX3RyRwPtDGbMGweCYKEq385431022",
                mapId = "65c0ff7430b94e3fabd5bb8c",
            )

        // Load the map data.
        mapView.getMapData(options) { result ->
            result
                .onSuccess {
                    Log.d("MappedinDemo", "getMapData success")
                    // Display the map.
                    mapView.show3dMap(Show3DMapOptions()) { r ->
                        r.onSuccess {
                            runOnUiThread {
                                loadingIndicator.visibility = android.view.View.GONE
                            }
                            onMapReady(mapView)
                        }
                        r.onFailure {
                            runOnUiThread {
                                loadingIndicator.visibility = android.view.View.GONE
                            }
                            Log.e("MappedinDemo", "show3dMap error: $it")
                        }
                    }
                }.onFailure {
                    Log.e("MappedinDemo", "getMapData error: $it")
                }
        }
    }

    // Place your code to be called when the map is ready here.
    private fun onMapReady(mapView: MapView) {
        Log.d("MappedinDemo", "show3dMap success - Map displayed")

        val animationDuration = 4000

        // Get the map center as the starting point for bearing calculations.
        mapView.mapData.mapCenter { centerResult ->
            centerResult.onSuccess { mapCenter ->
                if (mapCenter == null) {
                    Log.e("MappedinDemo", "Map center is null")
                    return@onSuccess
                }

                // Get all points of interest.
                mapView.mapData.getByType<PointOfInterest>(MapDataType.POINT_OF_INTEREST) { result ->
                    result.onSuccess { pois ->
                        // Start iterating through POIs with initial position from map center.
                        animateThroughPOIs(
                            mapView = mapView,
                            pois = pois,
                            index = 0,
                            startLat = mapCenter.latitude,
                            startLon = mapCenter.longitude,
                            animationDuration = animationDuration,
                        )
                    }
                    result.onFailure { error ->
                        Log.e("MappedinDemo", "Failed to get POIs: $error")
                    }
                }
            }
            centerResult.onFailure { error ->
                Log.e("MappedinDemo", "Failed to get map center: $error")
            }
        }
    }

    /**
     * Recursively animates through each point of interest.
     */
    private fun animateThroughPOIs(
        mapView: MapView,
        pois: List<PointOfInterest>,
        index: Int,
        startLat: Double,
        startLon: Double,
        animationDuration: Int,
    ) {
        if (index >= pois.size) {
            Log.d("MappedinDemo", "Finished animating through all POIs")
            return
        }

        val poi = pois[index]

        // Label the point of interest.
        mapView.labels.add(target = poi.coordinate, text = poi.name)

        // Calculate the bearing between the current position and the POI.
        val bearing =
            calcBearing(
                startLat,
                startLon,
                poi.coordinate.latitude,
                poi.coordinate.longitude,
            )

        // Animate to the current point of interest.
        mapView.camera.animateTo(
            target =
                CameraTarget(
                    bearing = bearing,
                    pitch = 80.0,
                    zoomLevel = 50.0,
                    center = poi.coordinate,
                ),
            options =
                CameraAnimationOptions(
                    duration = animationDuration,
                    easing = EasingFunction.EASE_OUT,
                ),
        )

        // Wait for the animation to complete before moving to the next POI.
        Handler(Looper.getMainLooper()).postDelayed({
            animateThroughPOIs(
                mapView = mapView,
                pois = pois,
                index = index + 1,
                startLat = poi.coordinate.latitude,
                startLon = poi.coordinate.longitude,
                animationDuration = animationDuration,
            )
        }, animationDuration.toLong())
    }

    /**
     * Calculate the bearing between two points.
     */
    private fun calcBearing(
        startLat: Double,
        startLng: Double,
        destLat: Double,
        destLng: Double,
    ): Double {
        val startLatRad = toRadians(startLat)
        val startLngRad = toRadians(startLng)
        val destLatRad = toRadians(destLat)
        val destLngRad = toRadians(destLng)

        val y = sin(destLngRad - startLngRad) * cos(destLatRad)
        val x =
            cos(startLatRad) * sin(destLatRad) -
                    sin(startLatRad) * cos(destLatRad) * cos(destLngRad - startLngRad)
        var brng = atan2(y, x)
        brng = toDegrees(brng)
        return (brng + 360) % 360
    }

    /** Converts from degrees to radians. */
    private fun toRadians(degrees: Double): Double = degrees * Math.PI / 180

    /** Converts from radians to degrees. */
    private fun toDegrees(radians: Double): Double = radians * 180 / Math.PI
}