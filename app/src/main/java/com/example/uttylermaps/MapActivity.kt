package com.example.uttylermaps

import com.example.uttylermaps.BuildConfig
import android.os.Bundle
import android.util.Log
import android.app.Activity
import androidx.activity.result.contract.ActivityResultContracts
import com.mappedin.models.Space
import com.mappedin.models.ConnectionType
import androidx.appcompat.app.AppCompatActivity
import com.mappedin.MapView
import com.mappedin.models.AddLabelOptions
import com.mappedin.models.Doors
import com.mappedin.models.DoorsUpdateState
import com.mappedin.models.Floor
import com.mappedin.models.GeometryUpdateState
import com.mappedin.models.GetMapDataWithCredentialsOptions
import com.mappedin.models.LabelAppearance
import com.mappedin.models.MapDataType
import com.mappedin.models.Show3DMapOptions
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.mappedin.models.CameraAnimationOptions
import com.mappedin.models.CameraTarget
import com.mappedin.models.EasingFunction
import com.mappedin.models.PointOfInterest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.view.View
import com.mappedin.models.Coordinate
import com.indooratlas.android.sdk.IALocation
import com.indooratlas.android.sdk.IALocationListener
import com.indooratlas.android.sdk.IALocationManager
import com.indooratlas.android.sdk.IALocationRequest
import com.mappedin.models.AddMarkerOptions
import com.mappedin.models.CollisionRankingTier
import com.mappedin.models.Connection
import com.mappedin.models.Events
import com.mappedin.models.FloorUpdateState
import com.mappedin.models.FollowMode


//from https://developer.mappedin.com/android-sdk
class MapActivity : AppCompatActivity(), IALocationListener {
    private lateinit var mapView: MapView
    lateinit var ui: UIBuilder

    private var mapReady = false
    var allSpaces: List<Space> = emptyList()
    private var highlightedLabelSpace: Space? = null
    private var resetRunnable: Runnable? = null
    private val resetHandler = Handler(Looper.getMainLooper())
    private val labelMap = mutableMapOf<String, com.mappedin.models.Label>()

    // indoor atlas
    private lateinit var iaLocationManager: IALocationManager
    private var lastLocation: IALocation? = null
    private var hasPermissions = false
    var isFollowingUser = false

    // managers
    lateinit var floorManager: FloorManager
    private lateinit var blueDotManager: BlueDotManager
    private lateinit var navigationManager: NavigationManager


    private var isDark = false

    private val prefs by lazy {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
    }

    private val annotationMarkerMap = mutableMapOf<String, com.mappedin.models.Annotation>()

    private val navLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val originRoom = result.data?.getStringExtra("origin_room")
            val destRoom = result.data?.getStringExtra("dest_room") ?: return@registerForActivityResult

            val dest = allSpaces.find { it.name == destRoom } ?: return@registerForActivityResult
            val accessible = prefs.getBoolean("accessible_routes", false)

            if (originRoom != null) {
                val origin = allSpaces.find { it.name == originRoom }
                if (origin != null) {
                    navigationManager.navigateFromSpace(origin, dest, accessible)
                }
            } else {
                val loc = lastLocation
                val floor = floorManager.currentFloor
                if (loc == null || floor == null) {
                    android.widget.Toast.makeText(this, "Waiting for location signal...", android.widget.Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }
                navigationManager.navigateTo(dest, loc.latitude, loc.longitude, floor.id, accessible)
            }
        }
    }




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isDark = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        title = "Display a Map"


        mapView = MapView(this)
        ui = UIBuilder(this, isDark, mapView)
        setContentView(ui.buildInitialLayout())

        iaLocationManager = IALocationManager.create(this)

        val permissions = mutableListOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissions.add(android.Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }
        requestPermissions(permissions.toTypedArray(), 0)

        loadMap()
    }

    private fun enableFollowMode() {
        if (lastLocation == null) return
        if (isFollowingUser) {
            mapView.blueDot.follow(mode = null)
            isFollowingUser = false
            ui.setLocationButtonColor("#16A34A")
        } else {
            val mode = if (navigationManager.isNavigating)
                FollowMode.POSITION_AND_PATH_DIRECTION else FollowMode.POSITION_ONLY
            mapView.blueDot.follow(mode)
            isFollowingUser = true
            ui.setLocationButtonColor("#2563EB")
        }
    }

    private fun loadMap() {
        val options = GetMapDataWithCredentialsOptions(
            key = BuildConfig.MAPPEDIN_KEY,
            secret = BuildConfig.MAPPEDIN_SECRET,
            mapId = BuildConfig.MAPPEDIN_MAP_ID,
            viewId = if (isDark) "Ptix" else null
        )

        mapView.getMapData(options) { result ->
            result.onSuccess {
                mapView.show3dMap(Show3DMapOptions()) { r ->
                    r.onSuccess { onMapReady() }
                    r.onFailure { Log.e("Mappedin", "show3dMap error: $it") }
                }
            }.onFailure {
                Log.e("Mappedin", "getMapData error: $it")
            }
        }
    }



    //this code executes when the map is ready
    private fun onMapReady() {

        //setup the managers
        floorManager = FloorManager(mapView)
        blueDotManager = BlueDotManager(mapView)
        navigationManager = NavigationManager(this, mapView, ui.container, isDark)
        mapReady = true

        mapView.mapData.getByType<Space>(MapDataType.SPACE) { result ->
            result.onSuccess { spaces ->
                allSpaces = spaces
                ui.allSpaces = spaces  // keep UIBuilder in sync
                spaces.forEach { space ->
                    mapView.updateState(space, GeometryUpdateState(interactive = true))
                }
            }
        }

        runOnUiThread {
            ui.loadingIndicator.visibility = View.GONE
            ui.buildControls(
                onLocationClick = { enableFollowMode() },
                onNavClick = {
                    val intent = Intent(this, NavigationActivity::class.java)
                    val roomNames = allSpaces.map { it.name }.filter { it.isNotBlank() }.distinct().sorted()
                    intent.putStringArrayListExtra("room_names", ArrayList(roomNames))
                    navLauncher.launch(intent)
                },
                onSettingsClick = { startActivity(Intent(this, SettingsActivity::class.java)) },
                onSearchSubmit = { query -> navigateToRoom(query) },
                onSearchItemClick = { room -> navigateToRoom(room) },

            )
        }

        //make doors visible
        mapView.updateState(
            Doors.INTERIOR,
            DoorsUpdateState(
                visible = true,
                color = if(isDark) "black" else "grey",
                topColor = "brown",
                opacity = 0.5
            ),
        )

        //make spaces interactive (clickable)
        mapView.mapData.getByType<Space>(MapDataType.SPACE) { result ->
            result.onSuccess { spaces ->
                allSpaces = spaces
                spaces.forEach { space ->
                    mapView.updateState(
                        space,
                        GeometryUpdateState(interactive = true)
                    ) { result ->
                        result.onFailure {
                            Log.e("Mappedin", "Failed to update space", it)
                        }
                    }
                }
            }
        }
        // Define SVG icons for each category
        val restroomIcon = """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="white">
          <path d="M5.5 22v-7.5H4V9c0-1.1.9-2 2-2h3c1.1 0 2 .9 2 2v5.5H9.5V22h-4zm12.5 0v-6h3l-2.54-7.63C18.18 7.55 17.42 7 16.56 7h-.12c-.86 0-1.63.55-1.9 1.37L12 16h3v6h3zM7.5 6c1.11 0 2-.89 2-2s-.89-2-2-2-2 .89-2 2 .89 2 2 2zm9 0c1.11 0 2-.89 2-2s-.89-2-2-2-2 .89-2 2 .89 2 2 2z"/>
        </svg>
        """

        val elevatorIcon = """
<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
  <path d="M12 8H17C17.5523 8 18 8.44772 18 9V19C18 19.5523 17.5523 20 17 20H12M12 8H7C6.44772 8 6 8.44772 6 9V19C6 19.5523 6.44772 20 7 20H12M12 8V20M7.5 4.5L9 3L10.5 4.5M13.5 3L15 4.5L16.5 3" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
</svg>
"""

        val stairsIcon ="""<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="white"><path d="M19 3h-4v4h-4v4H7v4H3v6h4v-4h4v-4h4V9h4V3z"/></svg>"""







        //add labels to rooms
        mapView.mapData.getByType<Space>(MapDataType.SPACE) { result ->
            result.onSuccess { spaces ->
                for (space in spaces) {
                    if (space.name.isNotEmpty()) {
                        val isAmenity = space.name.contains("Restroom", ignoreCase = true) ||
                            space.name.contains("Elevator", ignoreCase = true) ||
                            space.name.contains("Stair", ignoreCase = true)

                        val icon = when {
                            space.name.contains("Restroom", ignoreCase = true) -> restroomIcon
                            space.name.contains("Elevator", ignoreCase = true) -> elevatorIcon
                            space.name.contains("Stair", ignoreCase = true) -> stairsIcon
                            else -> null
                        }
                        val bgColor = when {
                            space.name.contains("Restroom", ignoreCase = true) -> "#4e7498"
                            space.name.contains("Elevator", ignoreCase = true) -> "#15803D"
                            space.name.contains("Stair", ignoreCase = true) -> "#15803D"
                            else -> null
                        }

                        if (isAmenity && icon != null) {
                            mapView.labels.add(
                                target = space,
                                text = space.name,
                                options = AddLabelOptions(
                                    labelAppearance = LabelAppearance(
                                        icon = icon,
                                        color = bgColor,
                                        iconVisibleAtZoomLevel = .7
                                    ),
                                    interactive = true,
                                    rank = if(isAmenity) CollisionRankingTier.HIGH else null
                                ),
                            ) { result ->
                                result.onSuccess { label ->
                                    if (label != null) labelMap[space.name] = label
                                }
                            }
                        } else {
                            // Regular room just text label
                            mapView.labels.add(
                                target = space,
                                text = space.name,
                                options = AddLabelOptions(
                                    labelAppearance = LabelAppearance(),
                                    interactive = true,
                                ),
                            ) { result ->
                                result.onSuccess { label ->
                                    if (label != null) labelMap[space.name] = label
                                }
                            }
                        }
                    }
                }
            }
        }




        //add safety annotations
        mapView.mapData.getByType<com.mappedin.models.Annotation>(MapDataType.ANNOTATION) { result ->
            result.onSuccess { annotations ->
                for (annotation in annotations) {
                    val coord = annotation.coordinate ?: continue
                    val iconUrl = annotation.icon?.url

                    val iconHtml = if (iconUrl != null) {
                        """<img src="$iconUrl" width="28" height="28" style="display:block;"/>"""
                    } else {
                                """<div style="background:red;color:white;border-radius:50%;
                    width:28px;height:28px;display:flex;align-items:center;
                    justify-content:center;font-size:14px;">⚠</div>"""
                    }

                    mapView.markers.add(
                        target = coord,
                        html = iconHtml,
                        options = com.mappedin.models.AddMarkerOptions(
                            interactive = AddMarkerOptions.Interactive.True,
                            rank = AddMarkerOptions.Rank.Tier(CollisionRankingTier.MEDIUM)

                        )
                    ) { markerResult ->
                        markerResult.onSuccess { marker ->
                            if (marker != null) {
                                annotationMarkerMap[marker.id] = annotation

                            }
                        }
                    }
                }
            }
        }
        val stairsSvg = """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><path d="M19 3h-4v4h-4v4H7v4H3v6h4v-4h4v-4h4V9h4V3z"/></svg>"""

        val elevatorSvg = """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><path d="M7 2l5 5H2zm10 0l5 5h-10zM7 22l5-5H2zm10 0l5-5h-10z"/></svg>"""


        mapView.mapData.getByType<com.mappedin.models.Connection>(MapDataType.CONNECTION) { result ->
            result.onSuccess { connections ->
                for (connection in connections) {
                    val icon = if (connection.type == Connection.ConnectionType.STAIRS) {
                        """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="white"><path d="M19 3h-4v4h-4v4H7v4H3v6h4v-4h4v-4h4V9h4V3z"/></svg>"""
                    } else {
                        """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="white"><path d="M7 2l5 5H2zm10 0l5 5h-10zM7 22l5-5H2zm10 0l5-5h-10z"/></svg>"""
                    }

                    connection.coordinates.forEach { coordinate ->
                        mapView.labels.add(
                            target = coordinate,
                            text = "",
                            options = AddLabelOptions(
                                labelAppearance = LabelAppearance(
                                    color = "#15803D",
                                    icon = icon,

                                ),
                                rank = CollisionRankingTier.MEDIUM
                            )
                        )
                    }
                }
            }
        }

        //load floors
        mapView.mapData.getByType<Floor>(MapDataType.FLOOR) { result ->
            result.onSuccess { floors ->
                floorManager.setFloors(floors)
                runOnUiThread {
                    ui.buildFloorSwitcher(floors) { floor ->
                        floorManager.switchToFloor(floor)
                    }
                    floorManager.currentFloor?.let { ui.highlightFloor(it) }
                }
            }

            result.onSuccess { floors ->
                for (floor in floors) {
                    mapView.updateState(
                        floor,
                        FloorUpdateState(
                            markers = FloorUpdateState.Markers(enabled = true)
                        )
                    )
                }
            }


        }



        blueDotManager.enable()




        // Listen for space click
        mapView.on(Events.Click) { payload ->
            //first cancel previous ones
            resetRunnable?.let { resetHandler.removeCallbacks { it }}

            val coordinate = payload?.coordinate
            Log.d("Coords", "lat=${coordinate?.latitude}, lon=${coordinate?.longitude}")
            val labels = payload?.labels
            Log.d("Highlight", "Click event - labels: ${labels?.size ?: 0}")

            /*
            val clickedSpace: Space? = when {
                !labels.isNullOrEmpty() -> {
                    val labelName = labels[0].text
                    allSpaces.find { it.name == labelName }
                }
                else -> null
            }
             */
            //check if they tapped a room first then the name
            val clickedSpace = payload?.spaces?.firstOrNull()
                ?: payload?.labels?.firstOrNull()?.let { label ->
                    allSpaces.find { it.name == label.text }
                }

            val clickedMarker = payload?.markers?.firstOrNull()
            if (clickedMarker != null) {
                // Find which annotation this marker belongs to
                val markerId = clickedMarker.id
                val annotation = annotationMarkerMap[markerId]
                if (annotation != null) {
                    runOnUiThread {
                        android.widget.Toast.makeText(
                            this,
                            "${annotation.type} (${annotation.group})",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@on
                }
            }

            Log.d("Highlight", "clickedSpace: ${clickedSpace?.name ?: "null"}, highlighted: ${highlightedLabelSpace?.name ?: "null"}")

            // Reset previous highlight
            resetHighlightedLabel()

            if (clickedSpace == null) {
                runOnUiThread { navigationManager.dismissInfoPanel() }
                return@on
            }

            if (clickedSpace.name.isNotBlank()) {
                resetRunnable?.let { resetHandler.removeCallbacks(it) }

                mapView.updateState(clickedSpace, GeometryUpdateState(
                    color = "#BF5700",
                    opacity = 0.6
                ))
                highlightedLabelSpace = clickedSpace

                runOnUiThread {
                    navigationManager.showSpaceInfoPanel(
                        space = clickedSpace,
                        hasLocation = lastLocation != null,
                        onDirections = {
                            val loc = lastLocation
                            val currentFloor = floorManager.currentFloor

                            if (loc == null || currentFloor == null) {
                                Log.e("NavTest", "Missing location or floor")
                                return@showSpaceInfoPanel
                            }
                            val accessible = prefs.getBoolean("accessible_routes", false)

                            Log.d("Navigation", "From floor=${currentFloor.id} to space=${clickedSpace.name} on floor=${clickedSpace.floor}")

                            navigationManager.navigateTo(
                                destination = clickedSpace,
                                userLat = loc.latitude,
                                userLon = loc.longitude,
                                floorId = currentFloor.id,
                                accessible = accessible

                            )
                        }
                    )
                }
            }
        }


        //to track tapping away
        mapView.view.setOnTouchListener { _, _ ->
            if (isFollowingUser) {
                isFollowingUser = false
                runOnUiThread { ui.setLocationButtonColor("#16A34A") }
            }
            ui.dismissSearch()

            resetRunnable?.let { resetHandler.removeCallbacks(it) }
            resetRunnable = Runnable {
                resetHighlightedLabel()
                navigationManager.dismissInfoPanel()
            }
            resetHandler.postDelayed(resetRunnable!!, 300)
            false
        }

    }

    private fun setFakeLocation(lat: Double, lon: Double) {
        val floor = floorManager.currentFloor ?: return

        blueDotManager.updatePosition(
            lat = lat,
            lon = lon,
            accuracy = 3.0,
            floor = floor
        )

        lastLocation = IALocation.from(android.location.Location("fake").apply {
            latitude = lat
            longitude = lon
            accuracy = 3.0f
        })
    }

    private fun resetHighlightedLabel() {
        highlightedLabelSpace?.let { prev ->
            mapView.updateState(prev, GeometryUpdateState(
                color = "initial",
                opacity = 1.0
            ))
        }
        highlightedLabelSpace = null
    }

    //go to user location on map
    private fun moveToUserLocation() {
        val location = lastLocation

        if (location == null) {
            Log.e("Location", "No location yet")
            return
        }

        mapView.camera.animateTo(
            CameraTarget(
                center = Coordinate(location.latitude, location.longitude),
                zoomLevel = 20.0,
                pitch = 0.0
            ),
            CameraAnimationOptions(
                duration = 1000,
                easing = EasingFunction.EASE_OUT
            )
        )
    }

    //zoom to a room when user picks one from search
    private fun navigateToRoom(roomName: String) {
        ui.searchHistory.addSearch(roomName)
        val space = allSpaces.find {
            it.name.equals(roomName, ignoreCase = true)
        }

        if (space == null) {
            Log.e("Mappedin", "Could not find space: $roomName")
            return
        }

        //switch to correct floor

        if (space.floor != null && floorManager.currentFloor?.id != space.floor) {
            // Find the matching Floor object from your FloorManager
            val targetFloor = floorManager.allFloors.find { it.id == space.floor }
            if (targetFloor != null) {
                floorManager.switchToFloor(targetFloor)
                ui.highlightFloor(targetFloor)
            }
        }

        //clear old highlight before setting new one
        resetHighlightedLabel()
        mapView.updateState(space, GeometryUpdateState(
            color = "#BF5700",
            opacity = 0.6
        ))
        highlightedLabelSpace = space

        //zoom camera to the room
        mapView.camera.animateTo(
            CameraTarget(
                center = space.center,
                zoomLevel = 20.0,
                pitch = 0.0
            ),
            CameraAnimationOptions(
                duration = 1000,
                easing = EasingFunction.EASE_OUT
            )
        )
    }


    // --- indoor atlas callbacks ---

    override fun onResume() {
        super.onResume()
        if(hasPermissions) {
            iaLocationManager.requestLocationUpdates(
                IALocationRequest.create(),
                this
            )
        }
    }

    override fun onPause() {
        iaLocationManager.removeLocationUpdates(this)
        super.onPause()
    }

    override fun onDestroy() {
        iaLocationManager.destroy()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == 0 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            hasPermissions = true
            iaLocationManager.requestLocationUpdates(
                IALocationRequest.create(),
                this
            )
        }
    }

    override fun onLocationChanged(location: IALocation) {

        lastLocation = location

        if (!mapReady) {
            Log.d("BlueDot", "Map not ready yet, skipping location update")
            return
        }

        val mappedFloor = floorManager.findFloorForLevel(location.floorLevel)
        if (mappedFloor == null) {
            Log.w("BlueDot", "No Mappedin floor found for IA floorLevel=${location.floorLevel}")
            return
        }

        Log.d(
            "BlueDot",
            "IA floorLevel=${location.floorLevel}, mappedFloor=${mappedFloor.name}"
        )
        //if auto floor switch is enabled, switch floors
        val autoSwitch = prefs.getBoolean("auto_floor_switch", true)
        if (autoSwitch) {
            floorManager.switchToFloor(mappedFloor)
        }
        runOnUiThread {
            ui.highlightFloor(mappedFloor)
        }

        blueDotManager.updatePosition(
            lat = location.latitude,
            lon = location.longitude,
            accuracy = location.accuracy.toDouble(),
            floor = mappedFloor
        )
        if (navigationManager.isNavigating) {
            navigationManager.updateNavigationPath(
                location.latitude, location.longitude, mappedFloor.id
            )
        }

    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
    }

}