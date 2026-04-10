package com.example.uttylermaps

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Button
import android.widget.ProgressBar
import android.app.Activity
import androidx.activity.result.contract.ActivityResultContracts
import com.mappedin.models.Space
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

import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

//for search
import android.widget.ListView
import android.widget.ArrayAdapter
import android.widget.SearchView
import android.widget.TextView
import androidx.core.view.marginRight
import androidx.core.view.setPadding
import androidx.core.view.isVisible
import com.mappedin.models.Events
import com.mappedin.models.LabelUpdateState
import com.mappedin.models.NavigationTarget

//from https://developer.mappedin.com/android-sdk
class MapActivity : AppCompatActivity(), IALocationListener {
    private lateinit var mapView: MapView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var floorButton: FloatingActionButton
    private lateinit var floorMenu: LinearLayout
    private var mapReady = false
    private lateinit var container: FrameLayout
    var allSpaces: List<Space> = emptyList()
    private var highlightedLabelSpace: Space? = null
    private var resetRunnable: Runnable? = null
    private val resetHandler = Handler(Looper.getMainLooper())
    private val labelMap = mutableMapOf<String, com.mappedin.models.Label>()

    //buttons
    private lateinit var startNavButton: FloatingActionButton
    private lateinit var myLocationButton: FloatingActionButton

    //indoor atlas
    private lateinit var iaLocationManager: IALocationManager
    private var lastLocation: IALocation? = null
    private var hasPermissions = false

    //managers
    private lateinit var floorManager: FloorManager
    private lateinit var blueDotManager: BlueDotManager
    private lateinit var navigationManager: NavigationManager

    //for searching
    private lateinit var searchOverlay: LinearLayout
    private lateinit var searchResults: ListView
    private lateinit var searchAdapter: ArrayAdapter<String>
    private lateinit var topSearchView: SearchView
    private var ignoreNextQueryChange = false
    private var filteredRooms: MutableList<String> = mutableListOf()
    private val searchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val roomName = result.data?.getStringExtra("selected_room")
            if (roomName != null) {
                navigateToRoom(roomName)
            }
        }
    }

    //track highlighted space so we can clear it
    private var highlightedSpace: Space? = null

    private var isDark = false



    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        isDark =
            (resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES

        title = "Display a Map"

        val container = setupUI()
        setContentView(container)

        //initialize IndoorAtlas
        iaLocationManager = IALocationManager.create(this)

        //verify permissions are granted
        val permissions = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissions.add(android.Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }

        requestPermissions(permissions.toTypedArray(), 0)

        loadMap()
    }

    //build the whole layout programmatically
    private fun setupUI(): FrameLayout {
        container = FrameLayout(this)

        container.setPadding(0, 0, 0, 0)




        //map view
        mapView = MapView(this)
        val mapParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply {
            bottomMargin = 160
        }

        container.addView(mapView.view, mapParams)


        //loading spinner
        loadingIndicator = ProgressBar(this)
        val loadingParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.CENTER
        }
        container.addView(loadingIndicator, loadingParams)

        //floating buttons
        buildLocationButton(container)
        buildNavButton(container)

        //floor switcher
        buildFloorButton(container)

        //bottom nav bar
        val bottomNav = buildBottomNav()
        val bottomNavParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
        }
        container.addView(bottomNav, bottomNavParams)

        //search overlay
        buildSearchOverlay(container)

        //fix margins so buttons sit above bottom nav
        ViewCompat.setOnApplyWindowInsetsListener(container) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())

            bottomNav.post {
                val bottomNavHeight = bottomNav.height

                val buttonParams = startNavButton.layoutParams as FrameLayout.LayoutParams
                buttonParams.bottomMargin = bottomNavHeight + 30
                buttonParams.marginEnd = 30
                startNavButton.layoutParams = buttonParams

                val locBtnParams = myLocationButton.layoutParams as FrameLayout.LayoutParams
                locBtnParams.bottomMargin = bottomNavHeight + 30
                locBtnParams.marginStart = 30
                myLocationButton.layoutParams = locBtnParams

                val updatedMapParams = mapView.view.layoutParams as FrameLayout.LayoutParams
                updatedMapParams.bottomMargin = bottomNavHeight
                mapView.view.layoutParams = updatedMapParams

                val floorFabParams = floorButton.layoutParams as FrameLayout.LayoutParams
                floorFabParams.bottomMargin = bottomNavHeight + 250
                floorFabParams.marginEnd = 30
                floorButton.layoutParams = floorFabParams

                val floorMenuParams = floorMenu.layoutParams as FrameLayout.LayoutParams
                floorMenuParams.bottomMargin = bottomNavHeight + 450
                floorMenuParams.marginEnd = 50
                floorMenu.layoutParams = floorMenuParams
            }

            //push search overlay below status bar
            searchOverlay.setPadding(24, statusBar.top + 16, 24, 0)

            insets
        }


        /*
        container.setOnTouchListener { _, _ ->
            topSearchView.clearFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(container.windowToken, 0)
            false
        }
         */

        return container

    }
    // floor switcher button
    private fun buildFloorButton(container: FrameLayout) {
        floorMenu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = android.view.View.GONE
            elevation = 0f
            setPadding(0, 0, 0, 0)
        }
        val menuParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            bottomMargin = 500
            marginStart = 100
        }
        container.addView(floorMenu, menuParams)

        floorButton = FloatingActionButton(this).apply {
            size = FloatingActionButton.SIZE_NORMAL
            //setImageResource()
            backgroundTintList = ColorStateList.valueOf("#6B7280".toColorInt())
            setOnClickListener {
                floorMenu.visibility = if (floorMenu.isVisible)
                    android.view.View.GONE
                else
                    android.view.View.VISIBLE
            }
        }
        val fabParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            bottomMargin = 500
            marginStart = 30
        }
        container.addView(floorButton, fabParams)
    }

    //location
    private fun buildLocationButton(container: FrameLayout) {
        myLocationButton = FloatingActionButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_mylocation)
            backgroundTintList = ColorStateList.valueOf("#16A34A".toColorInt())
            setOnClickListener { moveToUserLocation() }
        }
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            bottomMargin = 100
            marginStart = 30
        }
        container.addView(myLocationButton, params)
    }

    //navigation FAB
    private fun buildNavButton(container: FrameLayout) {
        startNavButton = FloatingActionButton(this).apply {
            size = FloatingActionButton.SIZE_NORMAL
            setImageResource(R.drawable.directions)
            backgroundTintList = ColorStateList.valueOf("#2563EB".toColorInt())
            setOnClickListener { navigationManager.showNavigationDialog(allSpaces) }
        }
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            bottomMargin = 100
            marginEnd = 30
        }
        container.addView(startNavButton, params)
    }

    //bottom navigation bar
    private fun buildBottomNav(): BottomNavigationView {
        return BottomNavigationView(this).apply {
            inflateMenu(R.menu.navigation_bar)

            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_map -> true
                    //R.id.nav_search -> true
                    R.id.nav_settings -> {
                        Log.d("Mappedin", "Settings clicked")
                        startActivity(Intent(this@MapActivity, SettingsActivity::class.java))
                        true
                    }
                    else -> false
                }
            }
        }
    }

    //search overlay with search bar, quick filters, and results list
    private fun buildSearchOverlay(container: FrameLayout) {
        searchOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 0, 24, 0)
        }

        topSearchView = buildSearchView()
        searchOverlay.addView(topSearchView)

        searchOverlay.addView(buildQuickFilterButtons())

        filteredRooms = mutableListOf()
        searchAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, filteredRooms)

        searchResults = ListView(this).apply {
            visibility = android.view.View.GONE
            setBackgroundColor(if (isDark) "#1E1E1E".toColorInt() else android.graphics.Color.WHITE)
            elevation = 12f
            adapter = searchAdapter
        }

        searchResults.setOnItemClickListener { _, _, position, _ ->
            val selectedRoom = filteredRooms[position]
            navigateToRoom(selectedRoom)
            searchResults.visibility = android.view.View.GONE
            filteredRooms.clear()
            searchAdapter.notifyDataSetChanged()
            topSearchView.setQuery(selectedRoom, false)
            topSearchView.clearFocus()

        }

        searchOverlay.addView(searchResults)

        val searchParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP
        }
        container.addView(searchOverlay, searchParams)
    }

    //styled search bar
    private fun buildSearchView(): SearchView {
        return SearchView(this).apply {
            queryHint = "Search rooms, labs, offices..."
            setIconifiedByDefault(false)

            val bgColor = if (isDark) "#1E1E1E".toColorInt() else android.graphics.Color.WHITE
            val textColor = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
            val hintColor = if (isDark) "#AAAAAA".toColorInt() else "#666666".toColorInt()
            val iconColor = if (isDark) "#DDDDDD".toColorInt() else "#555555".toColorInt()

            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 50f
                setColor(bgColor)
            }
            setPadding(24, 12, 24, 12)
            elevation = 16f

            findViewById<android.view.View?>(androidx.appcompat.R.id.search_plate)?.background = null
            findViewById<android.view.View?>(androidx.appcompat.R.id.search_bar)?.background = null
            findViewById<android.view.View?>(androidx.appcompat.R.id.submit_area)?.background = null

            findViewById<TextView?>(androidx.appcompat.R.id.search_src_text)?.apply {
                setTextColor(textColor)
                setHintTextColor(hintColor)
            }

            findViewById<android.widget.ImageView?>(androidx.appcompat.R.id.search_mag_icon)
                ?.setColorFilter(iconColor)

            findViewById<android.widget.ImageView?>(androidx.appcompat.R.id.search_close_btn)
                ?.setColorFilter(iconColor)

            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    if (!query.isNullOrBlank()) {
                        navigateToRoom(query)
                        searchResults.visibility = android.view.View.GONE
                        clearFocus()
                    }
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    val query = newText?.trim()?.lowercase() ?: ""
                    filteredRooms.clear()

                    if (query.isBlank()) {
                        searchResults.visibility = android.view.View.GONE
                    } else {
                        filteredRooms.addAll(
                            allSpaces.map { it.name }
                                .filter { it.isNotBlank() && it.lowercase().contains(query) }
                                .distinct()
                                .sorted()
                        )
                        searchAdapter.notifyDataSetChanged()
                        searchResults.visibility =
                            if (filteredRooms.isEmpty()) android.view.View.GONE
                            else android.view.View.VISIBLE
                    }
                    return true
                }
            })
            setOnQueryTextFocusChangeListener { _, hasFocus ->
                findViewById<TextView?>(androidx.appcompat.R.id.search_src_text)?.apply {
                    isCursorVisible = hasFocus
                }
                if (!hasFocus) {
                    searchResults.visibility = android.view.View.GONE
                }else {
                    // Re-show results if there's already text in the search bar
                    if (query.isNotEmpty() && filteredRooms.isNotEmpty()) {

                        searchResults.visibility = android.view.View.VISIBLE
                    }
                }
            }
        }
    }

    //quick filter buttons row
    private fun buildQuickFilterButtons(): LinearLayout {
        val buttonBg = if (isDark) "#2A2A2A".toColorInt() else "#EEEEEE".toColorInt()
        val buttonText = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(1)

            addView(Button(this@MapActivity).apply {
                text = "Restrooms"
                setBackgroundColor(buttonBg)
                setTextColor(buttonText)
                setOnClickListener { filterRoomsByKeyword("restroom") }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 12
                }
            })

            addView(Button(this@MapActivity).apply {
                text = "Offices"
                setBackgroundColor(buttonBg)
                setTextColor(buttonText)
                setOnClickListener { filterRoomsByKeyword("office") }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 12
                }
            })
            addView(Button(this@MapActivity).apply {
                text = "Labs"
                setBackgroundColor(buttonBg)
                setTextColor(buttonText)
                setOnClickListener { filterRoomsByKeyword("lab") }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 12
                }
            })
            /*
            addView(Button(this@MapActivity).apply {
                text = "Classrooms"
                setBackgroundColor(buttonBg)
                setTextColor(buttonText)
                setOnClickListener { filterClassrooms() }
            })
             */
        }
    }

    private fun filterRoomsByKeyword(keyword: String) {
        //topSearchView.setQuery(keyword, false)
        topSearchView.clearFocus()
        filteredRooms.clear()
        filteredRooms.addAll(
            allSpaces.map { it.name }
                .filter { it.isNotBlank() && it.contains(keyword, ignoreCase = true) }
                .distinct()
                .sorted()
        )
        searchAdapter.notifyDataSetChanged()
        searchResults.visibility =
            if (filteredRooms.isEmpty()) android.view.View.GONE
            else android.view.View.VISIBLE

    }

    private fun filterClassrooms() {
        filteredRooms.clear()
        filteredRooms.addAll(
            allSpaces.map { it.name }
                .filter { it.isNotBlank() && it.any { ch -> ch.isDigit() } }
                .distinct()
                .sorted()
        )
        searchAdapter.notifyDataSetChanged()
        searchResults.visibility =
            if (filteredRooms.isEmpty()) android.view.View.GONE
            else android.view.View.VISIBLE
    }


    private fun loadMap() {
        val options =
            GetMapDataWithCredentialsOptions(
                key = "mik_WHm7lPemUXoBeBY0j5482076a",
                secret = "mis_qGm14reCYjwNXATtwlqz4Zk29t48YRYpEHkrS2RzVdU94251086",
                mapId = "696db8c80f54a6000bdca0ad",
                viewId = if(isDark) "Ptix" else null
            )

        mapView.getMapData(options) { result ->
            result
                .onSuccess {
                    Log.d("Mappedin", "getMapData success")
                    mapView.show3dMap(Show3DMapOptions()) { r ->
                        r.onSuccess {
                            runOnUiThread {
                                loadingIndicator.visibility = android.view.View.GONE
                            }
                            onMapReady()
                        }
                        r.onFailure {
                            runOnUiThread {
                                loadingIndicator.visibility = android.view.View.GONE
                            }
                            Log.e("Mappedin", "show3dMap error: $it")
                        }
                    }
                }.onFailure {
                    runOnUiThread {
                        loadingIndicator.visibility = android.view.View.GONE
                    }
                    Log.e("Mappedin", "getMapData error: $it")
                }
        }
    }


    //this code executes when the map is ready
    private fun onMapReady() {

        //setup the managers
        floorManager = FloorManager(mapView)
        blueDotManager = BlueDotManager(mapView)
        navigationManager = NavigationManager(this, mapView, container, isDark)
        mapReady = true

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

        //add labels to rooms
        mapView.mapData.getByType<Space>(MapDataType.SPACE) { result ->
            result.onSuccess { spaces ->
                for (space in spaces) {
                    if (space.name.isNotEmpty()) {
                        val appearance = if (space.name.contains("Restroom")) {
                            LabelAppearance(color = "#4e7498")
                        } else {
                            LabelAppearance()
                        }

                        mapView.labels.add(
                            target = space,
                            text = space.name,
                            options = AddLabelOptions(
                                labelAppearance = appearance,
                                interactive = true
                            ),
                        ) { result ->
                            result.onSuccess { label ->
                                if (label != null) {
                                    labelMap[space.name] = label
                                }
                            }
                        }
                    }
                }
            }
        }

        //load floors
        mapView.mapData.getByType<Floor>(MapDataType.FLOOR) { result ->
            result.onSuccess { floors ->
                floors.forEachIndexed { index, floor ->
                    Log.d("Floors", "index=$index name=${floor.name} id=${floor.id}")
                }
                floorManager.setFloors(floors)
                runOnUiThread { buildFloorSwitcher() }
                // fake blue dot AFTER floors exist
            }

        }

        // Put this INSIDE the connections callback
        mapView.mapData.getByType<com.mappedin.models.Connection>(MapDataType.CONNECTION) { result ->
            result.onSuccess { connections ->
                Log.d("Connections", "Total connections: ${connections.size}")
                connections.forEach { connection ->
                    Log.d("Connections", connection.toString())
                }

                // Now try cross-floor after connections are confirmed loaded
                val floor1Id = floorManager.allFloors.getOrNull(0)?.id
                val floor2Id = floorManager.allFloors.getOrNull(1)?.id

                val space1 = allSpaces.find { it.name == "Vestibule, Room 199.09" }
                val space2 = allSpaces.find { it.name == "Room 211" }

                Log.d("NavTest", "Testing: ${space1?.name} (${space1?.floor}) -> ${space2?.name} (${space2?.floor})")

                if (space1 != null && space2 != null) {
                    mapView.mapData.getDirections(
                        NavigationTarget.SpaceTarget(space1),
                        NavigationTarget.SpaceTarget(space2),
                    ) { dirResult ->
                        dirResult.onSuccess { directions ->
                            Log.d("NavTest", "Cross-floor: ${directions?.distance ?: "NULL"}")
                        }
                        dirResult.onFailure {
                            Log.e("NavTest", "Failed", it)
                        }
                    }
                }
            }
        }

        blueDotManager.enable()




        // Listen for space click
        mapView.on(Events.Click) { payload ->
            val coordinate = payload?.coordinate
            Log.d("Coords", "lat=${coordinate?.latitude}, lon=${coordinate?.longitude}")
            val labels = payload?.labels
            Log.d("Highlight", "Click event - labels: ${labels?.size ?: 0}")

            val clickedSpace: Space? = when {
                !labels.isNullOrEmpty() -> {
                    val labelName = labels[0].text
                    allSpaces.find { it.name == labelName }
                }
                else -> null
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
                    color = "#2563EB",
                    opacity = 0.4
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

                            Log.d("Navigation", "From floor=${currentFloor.id} to space=${clickedSpace.name} on floor=${clickedSpace.floor}")

                            navigationManager.navigateTo(
                                destination = clickedSpace,
                                userLat = loc.latitude,
                                userLon = loc.longitude,
                                floorId = currentFloor.id
                            )
                        }
                    )
                }
            }
        }


        //to track tapping away
        mapView.view.setOnTouchListener { _, _ ->
            topSearchView.clearFocus()
            searchResults.visibility = android.view.View.GONE
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(mapView.view.windowToken, 0)

            // Schedule reset with small delay - click event can cancel it
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
                //opacity = "initial"
            ))
        }
        highlightedLabelSpace = null
    }

    //build floor buttons from the loaded floors
    private fun buildFloorSwitcher() {
        floorMenu.removeAllViews()

        for (floor in floorManager.allFloors) {
            val iconRes = when {
                floor.name.contains("First", ignoreCase = true) || floor.name.startsWith("1") -> R.drawable.floor_1
                floor.name.contains("Second", ignoreCase = true) || floor.name.startsWith("2") -> R.drawable.floor_2
                else -> R.drawable.floor_1
            }

            val fab = FloatingActionButton(this).apply {
                size = FloatingActionButton.SIZE_MINI
                setImageResource(iconRes)
                backgroundTintList = ColorStateList.valueOf(
                    if (isDark) "#3A3A3A".toColorInt() else "#E0E0E0".toColorInt()
                )
                setOnClickListener {
                    floorManager.switchToFloor(floor)
                    floorMenu.visibility = android.view.View.GONE
                    updateFloorButtonIcon(floor)
                }
            }

            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12
                gravity = Gravity.CENTER_HORIZONTAL
            }

            floorMenu.addView(fab, params)
        }

        floorManager.currentFloor?.let { updateFloorButtonIcon(it) }
    }

    private fun updateFloorButtonIcon(floor: Floor) {
        val iconRes = when {
            floor.name.contains("First", ignoreCase = true) || floor.name.startsWith("1") -> R.drawable.floor_1
            floor.name.contains("Second", ignoreCase = true) || floor.name.startsWith("2") -> R.drawable.floor_2
            else -> R.drawable.floor_1
        }
        floorButton.setImageResource(iconRes)
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
                zoomLevel = 60.0,
                pitch = 0.0
            ),
            CameraAnimationOptions(
                duration = 1000,
                easing = EasingFunction.EASE_OUT
            )
        )
    }

    //open the search screen
    private fun openSearch() {
        val intent = Intent(this, SearchActivity::class.java)

        val roomNames = allSpaces
            .map { it.name }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        intent.putStringArrayListExtra("room_names", ArrayList(roomNames))
        searchLauncher.launch(intent)
    }

    //clear previous highlight and reset it back to default
    private fun clearHighlight() {
        val previous = highlightedSpace ?: return

        mapView.updateState(
            previous,
            GeometryUpdateState(
                color = null,
                opacity = null
            )
        ) { result ->
            result.onFailure {
                Log.e("Mappedin", "Failed to clear highlight", it)
            }
        }

        highlightedSpace = null
    }

    //zoom to a room when user picks one from search
    private fun navigateToRoom(roomName: String) {
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
                updateFloorButtonIcon(targetFloor)
            }
        }

        //clear old highlight before setting new one

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
        if (floorManager.currentFloor?.id != mappedFloor.id) {
            floorManager.switchToFloor(mappedFloor)
            runOnUiThread {
                updateFloorButtonIcon(mappedFloor)
            }
        }

        blueDotManager.updatePosition(
            lat = location.latitude,
            lon = location.longitude,
            accuracy = location.accuracy.toDouble(),
            floor = mappedFloor
        )
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
    }

}