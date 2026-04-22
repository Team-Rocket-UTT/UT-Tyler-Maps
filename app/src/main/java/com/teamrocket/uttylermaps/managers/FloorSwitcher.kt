package com.teamrocket.uttylermaps.managers

import android.util.Log
import com.mappedin.MapView
import com.mappedin.models.Floor

/**
 * Manages the available building floors and handles switching the displayed floor on the map.
 *
 * This class maintains the list of all [Floor] objects retrieved from the Mappedin map data,
 * tracks which floor is currently being displayed, and provides a mapping between IndoorAtlas
 * floor level numbers and Mappedin floor names. A callback [onFloorChanged] is invoked whenever
 * the displayed floor changes, allowing other components (such as the blue dot) to react.
 *
 * @property mapView the [MapView] instance used to issue floor-switch commands
 * @see com.teamrocket.uttylermaps.MapActivity.onMapReady where floors are loaded and the floor switcher UI is built
 * @see com.teamrocket.uttylermaps.MapActivity.syncBlueDotVisibility which reacts to floor changes
 */
class FloorManager(private val mapView: MapView) {

    /**
     * The complete list of floors available in the building, sorted alphabetically by name.
     */
    var allFloors: List<Floor> = emptyList()
        private set

    /**
     * The floor currently being displayed on the map, or `null` if no floor has been set yet.
     */
    var currentFloor: Floor? = null

    /**
     * Optional callback invoked whenever the displayed floor changes via [switchToFloor].
     *
     * Receives the new [Floor] as its parameter. Used by [com.teamrocket.uttylermaps.MapActivity] to toggle blue dot
     * visibility based on whether the user's actual floor matches the displayed floor.
     */
    var onFloorChanged: ((Floor) -> Unit)? = null

    /**
     * Initializes the floor list from the provided Mappedin floor data.
     *
     * Sorts the floors alphabetically by name and sets the first floor in the sorted
     * list as the default [currentFloor]. Logs the names of all loaded floors.
     *
     * @param floors the list of [Floor] objects retrieved from the Mappedin map data
     */
    fun setFloors(floors: List<Floor>) {
        allFloors = floors.sortedBy { it.name }

        if(allFloors.isNotEmpty()){
            currentFloor = allFloors.first() //default to 1st floor
            Log.d("Mappedin", "Loaded floors: ${allFloors.map { it.name }}")
        }
    }

    /**
     * Switches the map display to the specified floor.
     *
     * Updates [currentFloor] and calls [MapView.setFloor] to render the new floor.
     * Logs the result to Logcat on success or failure. Note that the [onFloorChanged]
     * callback is triggered separately by the [com.teamrocket.uttylermaps.MapActivity] when it detects a floor change.
     *
     * @param floor the [Floor] to switch the map display to
     */
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

    /**
     * Maps an IndoorAtlas floor level number to the corresponding Mappedin [Floor] object.
     *
     * IndoorAtlas uses integer floor levels where 1 represents the ground floor and 2
     * represents the second floor. This method translates those levels to the Mappedin
     * floor names used in the UT Tyler building map.
     *
     * @param floorLevel the IndoorAtlas floor level (1 = ground/first floor, 2 = second floor)
     * @return the matching [Floor] object, or `null` if no match is found for the given level
     */
    fun findFloorForLevel(floorLevel: Int): Floor? {
        //maps indoor atlas floor level number to mappedin floor
        //indoor atlas: 0 = ground floor, 1 = second
        val floorName = when (floorLevel) {
            1 -> "First Floor"
            2 -> "Second Floor"
            else -> return null
        }

        return allFloors.find { it.name.equals(floorName, ignoreCase = true) }
    }
}