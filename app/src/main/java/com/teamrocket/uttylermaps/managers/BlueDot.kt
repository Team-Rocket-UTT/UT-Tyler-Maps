package com.teamrocket.uttylermaps.managers

import android.util.Log
import com.mappedin.MapView
import com.mappedin.models.BlueDotOptions
import com.mappedin.models.BlueDotPositionUpdate
import com.mappedin.models.BlueDotUpdateOptions
import com.mappedin.models.Floor

/**
 * Manages the blue dot indicator that represents the user's current indoor position on the map.
 *
 * This class wraps the Mappedin SDK's blue dot API, providing methods to enable the indicator
 * with custom styling and to update its position as new location fixes arrive from IndoorAtlas.
 * Device-level position watching is disabled since positioning is handled externally by
 * [IALocationManager][com.indooratlas.android.sdk.IALocationManager].
 *
 * @property mapView the [MapView] instance on which the blue dot is rendered
 * @see com.teamrocket.uttylermaps.MapActivity.onLocationChanged for the source of position updates
 * @see com.teamrocket.uttylermaps.MapActivity.syncBlueDotVisibility for floor-based visibility toggling
 */
class BlueDotManager(private val mapView: MapView) {

    /**
     * Enables the blue dot on the map with custom appearance options.
     *
     * Configures the dot with:
     * - A semi-transparent blue accuracy ring
     * - A dark navy fill color (`#002F6C`)
     * - A 12-pixel radius
     * - An initial state of [BlueDotOptions.InitialState.INACTIVE]
     * - Device position watching disabled (positioning is provided by IndoorAtlas)
     *
     * Logs the result to Logcat on success or failure.
     */
    fun enable() {
        val options = BlueDotOptions(
            accuracyRing = BlueDotOptions.AccuracyRing(color = "#2266ff", opacity = 0.25),
            color = "#002F6C",
            //heading = BlueDotOptions.Heading(color = "#2266ff", opacity = 0.6),
            initialState = BlueDotOptions.InitialState.INACTIVE,
            radius = 12.0,
            watchDevicePosition = false //we handle position ourselves with indoor atlas
        )

        mapView.blueDot.enable(options) { result ->
            result.fold(
                onSuccess = {
                    Log.d("BlueDot", "Blue Dot enabled")
                },
                onFailure = { error ->
                    Log.e("BlueDot", "Error enabling Blue Dot: ${error.message}")
                }
            )
        }
    }

    /**
     * Updates the blue dot's position on the map with an animated transition.
     *
     * Constructs a [BlueDotPositionUpdate] from the provided coordinates, accuracy, and
     * floor, then sends it to the Mappedin SDK. The update is animated for smooth visual
     * movement between positions.
     *
     * @param lat the latitude of the user's current position
     * @param lon the longitude of the user's current position
     * @param accuracy the position accuracy in meters (used to size the accuracy ring)
     * @param floor the [Floor] the user is currently on, used to associate the dot with the correct map level
     */
    fun updatePosition(lat: Double, lon: Double, accuracy: Double, floor: Floor) {

        Log.d("BlueDot", "Updating: lat=$lat, lon=$lon, floorId=${floor.id}, floorName=${floor.name}")
        val position = BlueDotPositionUpdate(
            accuracy = BlueDotPositionUpdate.Accuracy.Value(accuracy),
            floorId = BlueDotPositionUpdate.FloorId.Id(floor.id),
            latitude = BlueDotPositionUpdate.Latitude.Value(lat),
            longitude = BlueDotPositionUpdate.Longitude.Value(lon)
        )

        mapView.blueDot.update(position, BlueDotUpdateOptions(animate = true)) { result ->
            result.fold(
                onSuccess = {
                    Log.d("BlueDot", "Blue Dot updated on ${floor.name}")
                },
                onFailure = { error ->
                    Log.e("BlueDot", "Blue Dot update failed: ${error.message}")
                }
            )
        }
    }
}