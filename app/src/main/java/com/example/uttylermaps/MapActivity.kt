package com.example.uttylermaps

import android.R.attr.text
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Button
import android.widget.ProgressBar
import com.mappedin.models.Space
import androidx.appcompat.app.AppCompatActivity
import com.mappedin.MapView
import com.mappedin.models.AddLabelOptions
import com.mappedin.models.Doors
import com.mappedin.models.DoorsUpdateState
import com.mappedin.models.Floor
import com.mappedin.models.FloorStack
import com.mappedin.models.GeometryUpdateState
import com.mappedin.models.UpdateState
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import com.mappedin.models.Directions
import com.mappedin.models.NavigationTarget
import kotlin.math.roundToInt
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

//from https://developer.mappedin.com/android-sdk
class MapActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var floorSwitcherLayout:  LinearLayout

    private var allFloors: List<Floor> = emptyList()// floorstack stores floor
    private var currentFloor: Floor? = null
    private var allSpaces: List<Space> = emptyList()

    //for navigation
    private var startSpace: Space? = null
    private var endSpace: Space? = null
    private lateinit var startNavButton: FloatingActionButton
    private var currentDirections: Directions? = null

    private var isDark = false

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        isDark =
            (resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES

       // window.navigationBarColor = getColor(R.color.bottom_nav_color)
        title = "Display a Map"

        // Create a FrameLayout to hold both the map view and loading indicator
        val container = FrameLayout(this)
        container.setPadding(
            0,
            0,
            0,
            0
        )

        //create a LinearLayout to switch floors
        floorSwitcherLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val floorParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = 80
        }



        mapView = MapView(this)
        val mapParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply {
            bottomMargin = 160
        }

        container.addView(mapView.view, mapParams)

        container.addView(floorSwitcherLayout, floorParams)

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
                key = "mik_WHm7lPemUXoBeBY0j5482076a",
                secret = "mis_qGm14reCYjwNXATtwlqz4Zk29t48YRYpEHkrS2RzVdU94251086",
                mapId = "696db8c80f54a6000bdca0ad",
                viewId = if(isDark) "Ptix" else null
            )

        // Load the map data.
        mapView.getMapData(options) { result ->
            result
                .onSuccess {
                    Log.d("Mappedin", "getMapData success")
                    // Display the map.
                    mapView.show3dMap(Show3DMapOptions()) { r ->
                        r.onSuccess {
                            runOnUiThread {
                                //Map is laoded and ready
                                loadingIndicator.visibility = android.view.View.GONE
                            }
                            onMapReady(mapView)
                        }
                        r.onFailure {
                            //error showing map
                            runOnUiThread {
                                loadingIndicator.visibility = android.view.View.GONE
                            }
                            Log.e("Mappedin", "show3dMap error: $it")
                        }
                    }
                }.onFailure {// error loading map
                    runOnUiThread {
                        loadingIndicator.visibility = android.view.View.GONE
                    }
                    Log.e("Mappedin", "getMapData error: $it")
                }
        }


        //add navigation button
        startNavButton = FloatingActionButton(this).apply {
            size = FloatingActionButton.SIZE_NORMAL
            setImageResource(R.drawable.directions)
            backgroundTintList = ColorStateList.valueOf("#2563EB".toColorInt())
            //text = "Start Navigation"
            setOnClickListener {
                showNavigationDialog()
            }
        }

        val navButtonParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            bottomMargin = 100
            marginEnd = 30
        }

        container.addView(startNavButton, navButtonParams)

        //bottom navigation(android) bar
        val bottomNav = BottomNavigationView(this).apply {
            inflateMenu(R.menu.navigation_bar)

            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_map -> {
                        true
                    }

                    R.id.nav_search -> {
                        //showNavigationDialog()
                        true
                    }

                    R.id.nav_settings -> {
                        Log.d("Mappedin", "Settings clicked")
                        startActivity(Intent(this@MapActivity, SettingsActivity::class.java))

                        true
                    }

                    else -> false
                }
            }
        }
        val bottomNavParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        container.addView(bottomNav, bottomNavParams)



        ViewCompat.setOnApplyWindowInsetsListener(container) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottomInset = systemBars.bottom

            bottomNav.post {
                val bottomNavHeight = bottomNav.height

                val buttonParams = startNavButton.layoutParams as FrameLayout.LayoutParams
                buttonParams.bottomMargin = bottomNavHeight + 30
                buttonParams.marginEnd = 30
                startNavButton.layoutParams = buttonParams

                val updatedMapParams = mapView.view.layoutParams as FrameLayout.LayoutParams
                updatedMapParams.bottomMargin = bottomNavHeight
                mapView.view.layoutParams = updatedMapParams
            }

            insets
        }

    }


    // this code executes when the map is ready
    private fun onMapReady(mapView: MapView) {


        //make doors visible

        mapView.updateState(
            Doors.INTERIOR,
            DoorsUpdateState(
                visible = true,
                color = if(isDark) "black" else "brown",
                topColor = "brown",
                opacity = 0.5
            ),

            )

        //add labels to areas
        mapView.mapData.getByType<Space>(MapDataType.SPACE) { result ->
            result.onSuccess { spaces ->
                spaces.forEach { space ->
                    mapView.updateState(
                        space,
                        GeometryUpdateState(
                            interactive = true
                        )
                    ) { result ->
                        result.onFailure {
                            Log.e("Mappedin", "Failed to update space", it)
                        }
                    }
                }
            }
        }


        //add labels
        mapView.mapData.getByType<Space>(MapDataType.SPACE) { result ->
            result.onSuccess { spaces ->
                allSpaces = spaces //add all the spaces
                for (space in spaces) {
                    if (space.name.isNotEmpty()) {
                        if (space.name.contains("Restroom")) {
                            val color = "#4e7498"
                            val appearance =
                                LabelAppearance(
                                    color = color,
                                    //icon = space.images.firstOrNull()?.url ?: svgIcon,
                                )
                            mapView.labels.add(
                                target = space,
                                text = space.name,
                                options = AddLabelOptions(
                                    labelAppearance = appearance,
                                    interactive = true
                                ),
                            )
                        } else {
                            val color = "blue"
                            val appearance =
                                LabelAppearance(
                                    //color = color,
                                    //icon = space.images.firstOrNull()?.url ?: svgIcon,
                                )
                            mapView.labels.add(
                                target = space,
                                text = space.name,
                                options = AddLabelOptions(
                                    labelAppearance = appearance,
                                    interactive = true
                                ),
                            )
                        }
                    }
                }
            }
        }

        //load floors
        mapView.mapData.getByType<Floor>(MapDataType.FLOOR){ result->
            result.onSuccess { floors ->
                allFloors = floors.sortedBy { it.name } //?: emptyList()

                if(allFloors.isNotEmpty()){
                    currentFloor = allFloors.first() //default to 1st floor
                    Log.d("Mappedin", "Loaded floors: ${allFloors.map { it.name }}")
                }

                //after floors load
                runOnUiThread {
                    floorSwitcher()
                }



            }

            result.onFailure {
                Log.e("Mappedin", "failed to load floors", it)
            }
        }
        //animate pois
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
    private fun floorSwitcher(){
        floorSwitcherLayout.removeAllViews()

        for (floor in allFloors) {
            val button = Button(this).apply {
                text = floor.name
                setOnClickListener {
                    switchToFloor(floor)
                }
            }
            floorSwitcherLayout.addView(button)
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
    private fun getNavigationBarHeight(): Int {
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
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

    private fun getAndDrawDirections(start: Space, end: Space) {
        mapView.paths.removeAll()

        mapView.mapData.getDirections(
            NavigationTarget.SpaceTarget(start),
            NavigationTarget.SpaceTarget(end),
        ) { result ->
            result.onSuccess { directions ->
                if (directions != null) {
                    currentDirections = directions

                    mapView.navigation.draw(directions) { drawResult ->
                        drawResult.onSuccess {
                            showTurnByTurnDialog(directions)
                        }
                        drawResult.onFailure {
                            Log.e("Mappedin", "Failed to draw navigation", it)
                        }
                    }
                }
            }

            result.onFailure {
                Log.e("Mappedin", "Failed to get directions", it)
            }
        }
    }

    private fun formatInstruction(instruction: Any): String {
        return instruction.toString()
    }

    private fun buildInstructionText(directions: Directions): List<String> {
        val instructions = directions.instructions
        val steps = mutableListOf<String>()

        for (i in instructions.indices) {

            val instruction = instructions[i]
            val nextInstruction =
                if (i < instructions.size - 1) instructions[i + 1] else null

            val distance = nextInstruction?.distance?.toInt() ?: 0

            val type = instruction.action.type.name
            val bearing = instruction.action.bearing?.name

            // Skip useless tiny steps
            if (distance < 1 && type == "TURN") continue


            val text = when (type) {

                "DEPARTURE" ->
                    "Start and go $distance meters"

                "TURN" -> {
                    val direction = when {
                        bearing?.contains("RIGHT") == true -> "Turn right"
                        bearing?.contains("LEFT") == true -> "Turn left"
                        else -> "Continue"
                    }

                    "$direction and go $distance meters"
                }

                "TAKE_CONNECTION" ->
                    "Take the stairs or elevator"

                "EXIT_CONNECTION" ->
                    "Exit the stairs or elevator"

                "ARRIVAL" ->
                    "You have arrived"

                else ->
                    "Continue for $distance meters"
            }

            steps.add("${steps.size + 1}. $text")
        }

        return steps
    }

    private fun showTurnByTurnDialog(directions: Directions) {
        val steps = buildInstructionText(directions)

        AlertDialog.Builder(this)
            .setTitle("Turn-by-Turn Directions")
            .setMessage(steps.joinToString("\n\n"))
            .setPositiveButton("OK", null)
            .show()
    }

    //start navigation window
    private fun showNavigationDialog() {
        val roomNames = allSpaces
            .map { it.name }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }
        //start room
        val startInput = AutoCompleteTextView(this).apply {
            hint = "Start room"
            setAdapter(
                ArrayAdapter(
                    this@MapActivity,
                    android.R.layout.simple_dropdown_item_1line,
                    roomNames
                )
            )
        }
        //destination
        val endInput = AutoCompleteTextView(this).apply {
            hint = "Destination room"
            setAdapter(
                ArrayAdapter(
                    this@MapActivity,
                    android.R.layout.simple_dropdown_item_1line,
                    roomNames
                )
            )
        }

        //preload previous choices
        startSpace?.let { startInput.setText(it.name) }
        endSpace?.let { endInput.setText(it.name) }


        layout.addView(startInput)
        layout.addView(endInput)

        AlertDialog.Builder(this)
            .setTitle("Start Navigation")
            .setView(layout)
            .setPositiveButton("Start") { _, _ ->
                val startName = startInput.text.toString().trim()
                val endName = endInput.text.toString().trim()

                val selectedStart = allSpaces.find {
                    it.name.equals(startName, ignoreCase = true)
                }

                val selectedEnd = allSpaces.find {
                    it.name.equals(endName, ignoreCase = true)
                }

                if (selectedStart == null || selectedEnd == null) {
                    Log.e("Mappedin", "Could not find selected spaces")
                    return@setPositiveButton
                }

                if (selectedStart == selectedEnd) {
                    Log.e("Mappedin", "Start and destination are the same")
                    return@setPositiveButton
                }

                startSpace = selectedStart
                endSpace = selectedEnd

                getAndDrawDirections(selectedStart, selectedEnd)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}


/*
        // Get all floor stacks
        mapView.mapData.getByType<FloorStack>(MapDataType.FLOOR_STACK) { result ->
            result.onSuccess { stacks ->
                floorStacks = stacks?.sortedBy { it.name } ?: emptyList()

                // Get all floors
                mapView.mapData.getByType<Floor>(MapDataType.FLOOR) { floorsResult ->
                    floorsResult.onSuccess { floors ->
                        allFloors = floors ?: emptyList()
                        Log.d("MappedinDemo", "Floors: $floors")
                    }
                }
            }
        }

   // }
   */