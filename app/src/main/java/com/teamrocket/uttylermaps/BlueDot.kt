package com.teamrocket.uttylermaps

import android.util.Log
import com.mappedin.MapView
import com.mappedin.models.BlueDotOptions
import com.mappedin.models.BlueDotPositionUpdate
import com.mappedin.models.BlueDotUpdateOptions
import com.mappedin.models.Floor

class BlueDotManager(private val mapView: MapView) {

    //setup the blue dot with options
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

    //send a new position to the blue dot
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