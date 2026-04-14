package com.teamrocket.uttylermaps

import android.annotation.SuppressLint
import com.teamrocket.uttylermaps.BuildConfig
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
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.remote.creation.first
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
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
import kotlin.onSuccess


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
    private var blueDotFloorId: String? = null


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
                val floor = blueDotFloorId
                if (loc == null || floor == null) {
                    android.widget.Toast.makeText(this, "Waiting for location signal...", android.widget.Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }
                navigationManager.navigateTo(dest, loc.latitude, loc.longitude, floor, accessible)
            }
        }
    }




    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        when (prefs.getString("theme_preference", "system")) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        super.onCreate(savedInstanceState)
        isDark = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        title = "Display a Map"

        mapView = MapView(this)



        ui = UIBuilder(this, isDark, mapView)


        setContentView(ui.buildInitialLayout())

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.isNavigationBarContrastEnforced = false

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            private var pressedOnce = false

            override fun handleOnBackPressed() {
                //close search if open
                if (ui.materialSearchView.isShowing) {
                    ui.dismissSearch()
                    return
                }

                // dismiss info panel if showing
                if (navigationManager.isInfoPanelShowing()) {
                    navigationManager.dismissInfoPanel()
                    resetHighlightedLabel()
                    return
                }

                // stop navigation if active
                if (navigationManager.isNavigating) {
                    navigationManager.stopNavigation()
                    return
                }

                // clear filter if active
                if (ui.isFilterActive()) {
                    ui.clearFilter()
                    return
                }

                // double-press to exit
                if (pressedOnce) {
                    finish()
                    return
                }
                pressedOnce = true
                android.widget.Toast.makeText(this@MapActivity, "Press again to exit", android.widget.Toast.LENGTH_SHORT).show()
                Handler(Looper.getMainLooper()).postDelayed({ pressedOnce = false }, 2000)
            }
        })

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
        if (lastLocation == null) {
            android.widget.Toast.makeText(this, "Waiting for location...", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (isFollowingUser) {
            mapView.blueDot.follow(mode = null)
            isFollowingUser = false
            ui.setLocationButtonColor("#16A34A")
        } else {

            val userFloor = floorManager.allFloors.find { it.id == blueDotFloorId }
            if (userFloor != null && floorManager.currentFloor?.id != userFloor.id) {
                floorManager.switchToFloor(userFloor)
                ui.highlightFloor(userFloor)
                syncBlueDotVisibility()
            }

            val mode = if (navigationManager.isNavigating)
                FollowMode.POSITION_AND_PATH_DIRECTION else FollowMode.POSITION_ONLY
            mapView.blueDot.follow(mode)
            isFollowingUser = true
            ui.setLocationButtonColor("#2563EB")

            // Also move camera to location as fallback
            moveToUserLocation()
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
                runOnUiThread {
                    mapView.view.post {
                        mapView.show3dMap(Show3DMapOptions()) { r ->
                            r.onSuccess { onMapReady() }
                            r.onFailure { Log.e("MapLoad", "show3dMap error: $it") }
                        }
                    }
                }
            }.onFailure {
                Log.e("Mappedin", "getMapData error: $it")
            }
        }
    }



    //this code executes when the map is ready
    @SuppressLint("ClickableViewAccessibility")
    private fun onMapReady() {
        //setup the managers
        floorManager = FloorManager(mapView)
        blueDotManager = BlueDotManager(mapView)
        navigationManager = NavigationManager(this, mapView, ui.container, isDark)
        mapReady = true

        floorManager.onFloorChanged = { displayedFloor ->
            val userFloor = blueDotFloorId
            if (userFloor != null && userFloor != displayedFloor.id) {
                // Viewing different floor — hide blue dot
                mapView.blueDot.disable()
            } else {
                // Back on user's floor — re-enable blue dot
                blueDotManager.enable()
                // Re-send position so it shows immediately
                val loc = lastLocation
                val floor = floorManager.allFloors.find { it.id == userFloor }
                if (loc != null && floor != null) {
                    blueDotManager.updatePosition(
                        loc.latitude, loc.longitude,
                        loc.accuracy.toDouble(), floor
                    )
                }
            }
        }

        mapView.mapData.getByType<Space>(MapDataType.SPACE) { result ->
            result.onSuccess { spaces ->
                allSpaces = spaces
                ui.allSpaces = spaces  // keep UI synced
                spaces.forEach { space ->
                    mapView.updateState(space, GeometryUpdateState(interactive = true))
                }
            }
        }
        mapView.mapData.getByType<Space>(MapDataType.SPACE) { result ->
            result.onSuccess { spaces ->
                for (space in spaces.take(20)) {
                    val profiles = space.locationProfiles
                    if (profiles.isNotEmpty()) {
                        val profile = profiles.first()
                        Log.d("SpaceInfo", "name=${space.name}, profileName=${profile}, categories=${profile}")
                    } else {
                        Log.d("SpaceInfo", "name=${space.name}, NO profiles")
                    }
                }
            }
        }
        runOnUiThread {
            ui.loadingIndicator.visibility = View.GONE
            ui.container.findViewWithTag<View>("loadingOverlay")?.let {
                ui.container.removeView(it)
            }
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
        mapView.mapData.search.enable { result ->
            result.onSuccess {
                Log.d("Search", "Search enabled")
            }
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
        val showAnnotations = prefs.getBoolean("show_safety_annotations", true)
        loadAnnotations()
        val stairsSvg = """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><path d="M19 3h-4v4h-4v4H7v4H3v6h4v-4h4v-4h4V9h4V3z"/></svg>"""

        val elevatorSvg = """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><path d="M7 2l5 5H2zm10 0l5 5h-10zM7 22l5-5H2zm10 0l5-5h-10z"/></svg>"""


        mapView.mapData.getByType<Connection>(MapDataType.CONNECTION) { result ->
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
                        syncBlueDotVisibility()
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
        //enable the blue dot after all map loads
        blueDotManager.enable()

        // Listen for space taps
        mapView.on(Events.Click) { payload ->
            resetRunnable?.let { resetHandler.removeCallbacks(it) }

            val coordinate = payload?.coordinate
            Log.d("Coords", "lat=${coordinate?.latitude}, lon=${coordinate?.longitude}")
            val labels = payload?.labels
            Log.d("Highlight", "Click event - labels: ${labels?.size ?: 0}")

            val clickedSpace = payload?.spaces?.firstOrNull()
                ?: payload?.labels?.firstOrNull()?.let { label ->
                    allSpaces.find { it.name == label.text }
                }

            val clickedMarker = payload?.markers?.firstOrNull()
            if (clickedMarker != null) {
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

            // During filter mode only allow clicking filtered spaces
            if (ui.isFilterActive()) {
                if (clickedSpace != null && clickedSpace.name.isNotBlank()) {
                    runOnUiThread {
                        navigationManager.showSpaceInfoPanel(
                            space = clickedSpace,
                            hasLocation = lastLocation != null,
                            onDirections = {
                                val loc = lastLocation
                                val floor = blueDotFloorId  // use blue dot floor

                                if (loc == null || floor == null) return@showSpaceInfoPanel
                                val accessible = prefs.getBoolean("accessible_routes", false)
                                syncBlueDotVisibility()
                                navigationManager.navigateTo(clickedSpace, loc.latitude, loc.longitude, floor, accessible)
                                Handler(Looper.getMainLooper()).postDelayed({ syncBlueDotVisibility() }, 1000)
                            }
                        )
                    }
                }
                return@on
            }

            //no filters
            Log.d("Highlight", "clickedSpace: ${clickedSpace?.name ?: "null"}, highlighted: ${highlightedLabelSpace?.name ?: "null"}")
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
                            val floor = blueDotFloorId  // use blue dot floor, not displayed floor
                            if (loc == null || floor == null) {
                                Log.e("NavTest", "Missing location or floor")
                                return@showSpaceInfoPanel
                            }
                            val accessible = prefs.getBoolean("accessible_routes", false)
                            syncBlueDotVisibility()
                            navigationManager.navigateTo(clickedSpace, loc.latitude, loc.longitude, floor, accessible)
                            syncBlueDotVisibility()
                        }
                    )
                }
            }

        }

        //to track tapping away
        mapView.view.setOnTouchListener { v, event ->
            // block taps in bottom-left watermark area
            if (event.action == android.view.MotionEvent.ACTION_DOWN ||
                event.action == android.view.MotionEvent.ACTION_UP) {
                val density = resources.displayMetrics.density
                val blockWidth = 220 * density
                val blockHeight = 90 * density
                val inBottomLeft =
                    event.x <= blockWidth &&
                            event.y >= (v.height - blockHeight)

                if (inBottomLeft) {
                    Log.d("WatermarkBlock", "Blocked watermark tap")
                    return@setOnTouchListener true
                }
            }
            if (isFollowingUser) {
                isFollowingUser = false
                runOnUiThread { ui.setLocationButtonColor("#16A34A") }
            }
            ui.dismissSearch()

            if (!ui.isFilterActive()) {
                resetRunnable?.let { resetHandler.removeCallbacks(it) }
                resetRunnable = Runnable {
                    resetHighlightedLabel()
                    navigationManager.dismissInfoPanel()
                }
                resetHandler.postDelayed(resetRunnable!!, 300)
            }
            false
        }
        //Set blue dot on first floor
        Handler(Looper.getMainLooper()).postDelayed({
            val firstFloor = floorManager.allFloors.find { it.name.contains("First", ignoreCase = true) }
            setFakeLocation(32.31302445024953, -95.25148019466819, firstFloor)
            Log.d("FakeTest", "Blue dot pinned to First Floor")
        }, 5000)

        //listen for floor changes
        mapView.on(Events.FloorChange) { payload ->
            val newFloor = payload?.floor ?: return@on
            // Update FloorManager's tracked floor without calling setFloor again
            floorManager.currentFloor = floorManager.allFloors.find{it.id == newFloor.id}

            runOnUiThread {
                ui.highlightFloor((floorManager.currentFloor!!))
                syncBlueDotVisibility()
            }
        }


    }//oonmapready

    private fun loadAnnotations() {
        // Remove existing ones first
        mapView.markers.removeAll {  }


        val showAnnotations = prefs.getBoolean("show_safety_annotations", true)
        if (!showAnnotations) return

        mapView.mapData.getByType<com.mappedin.models.Annotation>(MapDataType.ANNOTATION) { result ->
            result.onSuccess { annotations ->
                for (annotation in annotations) {
                    val coord = annotation.coordinate
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
                    )
                }
            }
        }
    }

    private fun setFakeLocation(lat: Double, lon: Double, floor: Floor? = null) {
        val targetFloor = floor ?: floorManager.currentFloor ?: return
        blueDotFloorId = targetFloor.id  // track it here too

        blueDotManager.updatePosition(lat, lon, 3.0, targetFloor)
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

    fun reAddAllLabels() {
        mapView.labels.removeAll()

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
        if (mapReady) {
            loadAnnotations()
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

        val mappedFloor = floorManager.findFloorForLevel(location.floorLevel) ?: return
        blueDotFloorId = mappedFloor.id  // track actual user floor
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
        if (autoSwitch && floorManager.currentFloor?.id != mappedFloor.id) {
            floorManager.switchToFloor(mappedFloor)
            runOnUiThread { ui.highlightFloor(mappedFloor) }
        }
        runOnUiThread {
            ui.highlightFloor(mappedFloor)
        }
        syncBlueDotVisibility()

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
    //to keep blue dot visible after swapping floors
    fun syncBlueDotVisibility() {
        val userFloor = blueDotFloorId ?: return
        val displayedFloor = floorManager.currentFloor?.id ?: return

        if (userFloor != displayedFloor) {
            mapView.blueDot.disable()
        } else {
            blueDotManager.enable()
            val loc = lastLocation
            val floor = floorManager.allFloors.find { it.id == userFloor }
            if (loc != null && floor != null) {
                blueDotManager.updatePosition(loc.latitude, loc.longitude, loc.accuracy.toDouble(), floor)
            }
        }
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
    }

}