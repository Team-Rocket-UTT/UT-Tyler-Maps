package com.teamrocket.uttylermaps

import android.util.Log
import com.mappedin.MapView
import com.mappedin.models.Floor

//manages the floors and switching between them
class FloorManager(private val mapView: MapView) {

    var allFloors: List<Floor> = emptyList()
        private set

    var currentFloor: Floor? = null


    var onFloorChanged: ((Floor) -> Unit)? = null

    fun setFloors(floors: List<Floor>) {
        allFloors = floors.sortedBy { it.name }

        if(allFloors.isNotEmpty()){
            currentFloor = allFloors.first() //default to 1st floor
            Log.d("Mappedin", "Loaded floors: ${allFloors.map { it.name }}")
        }
    }

    fun switchToFloor(floor: Floor) {
        currentFloor = floor

        mapView.setFloor(floor.id) { result ->
            result.onSuccess {
                Log.d("Mappedin", "Floor switched to ${floor.name}")
            }
            result.onFailure{
                Log.e("Mappedin", "Failed to switch floor", it)
            }

        }
    }

    //maps indoor atlas floor level number to mappedin floor
    //indoor atlas: 0 = ground floor, 1 = second
    fun findFloorForLevel(floorLevel: Int): Floor? {

        val floorName = when (floorLevel) {
            0 -> "First Floor"
            1 -> "Second Floor"
            else -> return null
        }

        return allFloors.find { it.name.equals(floorName, ignoreCase = true) }
    }
}