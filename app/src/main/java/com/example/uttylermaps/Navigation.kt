package com.example.uttylermaps

import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.mappedin.MapView
import com.mappedin.models.AddMarkerOptions
import com.mappedin.models.Coordinate
import com.mappedin.models.Directions
import com.mappedin.models.NavigationTarget
import com.mappedin.models.PathSectionHighlightOptions
import com.mappedin.models.Space
import com.mappedin.models.NavigationOptions
import com.mappedin.models.AddPathOptions
import com.mappedin.models.CollisionRankingTier
import com.mappedin.models.GetDirectionsOptions

class NavigationManager(
    private val activity: MapActivity,
    private val mapView: MapView,
    private val container: FrameLayout,
    private val isDark: Boolean,

) {
    private var infoPanel: LinearLayout? = null
    private var navigationPanel: LinearLayout? = null
    private var activeDestination: Space? = null
    private var lastPathUpdateTime = 0L
    private val PATH_UPDATE_INTERVAL = 4000L

    private var navigationPlanPanel: LinearLayout? = null

    private var currentDirections: Directions? = null
    private var isRerouting = false
    private var activeFloorId: String? = null
    var isNavigating = false
        private set
    private var selectedOrigin: Space? = null // null = user location
    private var selectedDestination: Space? = null
    private var activeField: String = "destination" // which field is being edited
    private var useAccessible = false

    private val bgColor get() = if (isDark) "#1E1E1E".toColorInt() else android.graphics.Color.WHITE
    private val textColor get() = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK

    private val navOptions = NavigationOptions(
        pathOptions = AddPathOptions(
            color = "#002F6C",
            displayArrowsOnPath = true,
            animateDrawing = true
        ),
        createMarkers = NavigationOptions.CreateMarkers.withCustomMarkers(
            departure = NavigationOptions.CreateMarkers.CreateMarkerValue.CustomMarker(
                template = """<div style="width:1px;height:1px;opacity:0;"></div>"""
            )
        )
    )

    // Show room info when user taps a space
    fun showSpaceInfoPanel(space: Space, hasLocation: Boolean, onDirections: () -> Unit) {
        dismissInfoPanel()
        dismissNavigationPanel()

        infoPanel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 36, 48, 36)
            elevation = 24f
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadii = floatArrayOf(32f, 32f, 32f, 32f, 0f, 0f, 0f, 0f)
                setColor(bgColor)
            }

            addView(TextView(activity).apply {
                text = space.name
                setTextColor(this@NavigationManager.textColor)
                textSize = 20f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })

            if (hasLocation) {
                addView(Button(activity).apply {
                    text = "Directions"
                    setTextColor(android.graphics.Color.WHITE)
                    isAllCaps = false
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 24f
                        setColor("#2563EB".toColorInt())
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 24 }
                    setOnClickListener { onDirections() }
                })
            }

            addView(Button(activity).apply {
                text = "Close"
                isAllCaps = false
                setTextColor(this@NavigationManager.textColor)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 8
                    bottomMargin = 120
                }
                setOnClickListener { dismissInfoPanel() }
            })
        }

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM }

        container.addView(infoPanel, params)
    }

    fun dismissInfoPanel() {
        infoPanel?.let { container.removeView(it) }
        infoPanel = null
    }
    private fun buildPlanPanel(
        destination: Space,
        directions: Directions,
        userLat: Double,
        userLon: Double,
        floorId: String,
        accessible: Boolean
    ) {
        val distanceText = if (directions.distance < 1000)
            "${directions.distance.toInt()} m"
        else
            "${"%.1f".format(directions.distance / 1000)} km"

        val walkSeconds = (directions.distance / 1.4).toInt()
        val timeText = if (walkSeconds < 60) "$walkSeconds sec"
        else "${walkSeconds / 60} min"

        val originLabel = if (selectedOrigin != null) selectedOrigin!!.name else "Your location"

        navigationPlanPanel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
            elevation = 24f
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadii = floatArrayOf(32f, 32f, 32f, 32f, 0f, 0f, 0f, 0f)
                setColor(bgColor)
            }

            // Fields row with dots + inputs + swap
            val fieldsRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(24, 32, 16, 16)

                // Dots column
                addView(buildDotsColumn())

                // Text fields column
                val inputsColumn = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                val inputBg = if (isDark) "#303134".toColorInt() else "#F1F3F4".toColorInt()
                val inputText = if (isDark) "#E8EAED".toColorInt() else "#202124".toColorInt()

                val originField = TextView(activity).apply {
                    text = originLabel
                    textSize = 15f
                    setTextColor(inputText)
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 24f
                        setColor(inputBg)
                    }
                    setPadding(36, 20, 36, 20)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                inputsColumn.addView(originField)

                inputsColumn.addView(android.view.View(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 12
                    )
                })

                val destField = TextView(activity).apply {
                    text = destination.name
                    textSize = 15f
                    setTextColor(inputText)
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 24f
                        setColor(inputBg)
                    }
                    setPadding(36, 20, 36, 20)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                inputsColumn.addView(destField)

                addView(inputsColumn)

                // Swap button
                addView(Button(activity).apply {
                    text = "⇅"
                    textSize = 20f
                    setTextColor(this@NavigationManager.textColor)
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    ).apply { marginStart = 8 }
                    setOnClickListener {
                        val temp = originField.text
                        originField.text = destField.text
                        destField.text = temp

                        val tempSpace = selectedOrigin
                        selectedOrigin = selectedDestination
                        selectedDestination = tempSpace
                    }
                })
            }
            addView(fieldsRow)

            // Divider
            addView(android.view.View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 2
                ).apply {
                    marginStart = 24
                    marginEnd = 24
                }
                setBackgroundColor(if (isDark) "#3C4043".toColorInt() else "#E0E0E0".toColorInt())
            })

            // Distance and time row
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(36, 20, 36, 8)

                addView(TextView(activity).apply {
                    text = distanceText
                    textSize = 22f
                    setTextColor(this@NavigationManager.textColor)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                })

                addView(TextView(activity).apply {
                    text = "  ·  "
                    textSize = 18f
                    setTextColor(if (isDark) "#9AA0A6".toColorInt() else "#5F6368".toColorInt())
                })

                addView(TextView(activity).apply {
                    text = "$timeText walk"
                    textSize = 18f
                    setTextColor(if (isDark) "#9AA0A6".toColorInt() else "#5F6368".toColorInt())
                })
            })

            // Start button
            addView(Button(activity).apply {
                text = "Start"
                isAllCaps = false
                textSize = 16f
                setTextColor(android.graphics.Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 28f
                    setColor("#2563EB".toColorInt())
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 16
                    marginStart = 24
                    marginEnd = 24
                }
                setPadding(0, 28, 0, 28)
                setOnClickListener {
                    dismissPlanPanel()
                    navigateTo(destination, userLat, userLon, floorId, accessible)
                }
            })

            // Close button
            addView(Button(activity).apply {
                text = "Close"
                isAllCaps = false
                setTextColor(this@NavigationManager.textColor)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 4
                    bottomMargin = 8
                }
                setOnClickListener { dismissPlanPanel() }
            })
        }

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
            bottomMargin = 180
        }
        container.addView(navigationPlanPanel, params)
    }
    private fun buildDotsColumn(): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply { marginEnd = 16 }

            addView(android.view.View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(20, 20).apply { topMargin = 28 }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor("#4CAF50".toColorInt())
                }
            })

            addView(android.view.View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(3, 0, 1f).apply {
                    topMargin = 4
                    bottomMargin = 4
                }
                setBackgroundColor(if (isDark) "#5F6368".toColorInt() else "#DADCE0".toColorInt())
            })

            addView(android.view.View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(20, 20).apply { bottomMargin = 28 }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor("#2563EB".toColorInt())
                }
            })
        }
    }

    private fun buildField(label: String, dotColor: Int): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 20, 16, 20)

            addView(android.view.View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(24, 24).apply {
                    marginEnd = 20
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(dotColor)
                }
            })

            addView(TextView(activity).apply {
                text = label
                textSize = 16f
                setTextColor(this@NavigationManager.textColor)
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
            })
        }
    }

    fun dismissPlanPanel() {
        navigationPlanPanel?.let { container.removeView(it) }
        navigationPlanPanel = null
    }

    fun showNavigationPlanPanel(
        destination: Space?,
        userLat: Double?,
        userLon: Double?,
        floorId: String?,
        accessible: Boolean
    ) {
        dismissInfoPanel()
        dismissNavigationPanel()
        dismissPlanPanel()

        if (destination != null) {
            // Already have a destination — fetch route and show
            fetchAndShowPlan(destination, userLat, userLon, floorId, accessible)
        } else {
            // No destination yet — show panel with destination picker
            buildPlanPanelWithPicker(userLat, userLon, floorId, accessible)
        }
    }

    private fun buildPlanPanelWithPicker(
        userLat: Double?,
        userLon: Double?,
        floorId: String?,
        accessible: Boolean
    ) {
        selectedOrigin = null
        selectedDestination = null

        val filteredNames = mutableListOf<String>()
        val adapter = SearchResultAdapter(activity, filteredNames, isDark)

        val inputBg = if (isDark) "#303134".toColorInt() else "#F1F3F4".toColorInt()
        val inputText = if (isDark) "#E8EAED".toColorInt() else "#202124".toColorInt()
        val inputHint = if (isDark) "#9AA0A6".toColorInt() else "#5F6368".toColorInt()

        lateinit var originInput: android.widget.EditText
        lateinit var destInput: android.widget.EditText
        lateinit var resultsList: ListView

        originInput = android.widget.EditText(activity).apply {
            hint = "Your location"
            setText("Your location")
            textSize = 15f
            setTextColor(inputText)
            setHintTextColor(inputHint)
            isSingleLine = true
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 24f
                setColor(inputBg)
            }
            setPadding(36, 20, 36, 20)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    activeField = "origin"
                    setText("")
                }
            }
        }

        destInput = android.widget.EditText(activity).apply {
            hint = "Choose destination"
            textSize = 15f
            setTextColor(inputText)
            setHintTextColor(inputHint)
            isSingleLine = true
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 24f
                setColor(inputBg)
            }
            setPadding(36, 20, 36, 20)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) activeField = "destination"
            }
        }

        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString()?.trim()?.lowercase() ?: ""
                filteredNames.clear()
                if (q.isNotBlank() && q != "your location") {
                    filteredNames.addAll(
                        activity.allSpaces.map { it.name }
                            .filter { it.isNotBlank() && it.lowercase().contains(q) }
                            .distinct()
                            .sorted()
                            .take(8)
                    )
                }
                adapter.notifyDataSetChanged()
                resultsList.visibility =
                    if (filteredNames.isEmpty()) android.view.View.GONE
                    else android.view.View.VISIBLE
            }
        }
        originInput.addTextChangedListener(watcher)
        destInput.addTextChangedListener(watcher)

        navigationPlanPanel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
            elevation = 24f
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadii = floatArrayOf(32f, 32f, 32f, 32f, 0f, 0f, 0f, 0f)
                setColor(bgColor)
            }

            // Fields row
            val fieldsRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(24, 32, 16, 16)

                addView(buildDotsColumn())

                val inputsColumn = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                inputsColumn.addView(originInput)
                inputsColumn.addView(android.view.View(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 12
                    )
                })
                inputsColumn.addView(destInput)

                addView(inputsColumn)

                addView(Button(activity).apply {
                    text = "⇅"
                    textSize = 20f
                    setTextColor(this@NavigationManager.textColor)
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    ).apply { marginStart = 8 }
                    setOnClickListener {
                        val tempSpace = selectedOrigin
                        selectedOrigin = selectedDestination
                        selectedDestination = tempSpace

                        val tempText = originInput.text.toString()
                        originInput.setText(destInput.text.toString())
                        destInput.setText(
                            if (tempText == "Your location" || tempText.isBlank()) ""
                            else tempText
                        )
                        if (selectedOrigin == null) originInput.setText("Your location")
                    }
                })
            }
            addView(fieldsRow)

            resultsList = ListView(activity).apply {
                visibility = android.view.View.GONE
                val bg = if (isDark) "#303134".toColorInt() else "#FFFFFF".toColorInt()
                divider = null
                dividerHeight = 0
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 16f
                    setColor(bg)
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 600
                ).apply {
                    marginStart = 24
                    marginEnd = 24
                }
            }
            resultsList.adapter = adapter
            resultsList.setOnItemClickListener { _, _, position, _ ->
                val selectedName = filteredNames[position]
                val selectedSpace = activity.allSpaces.find { it.name == selectedName }
                if (selectedSpace != null) {
                    if (activeField == "origin") {
                        selectedOrigin = selectedSpace
                        originInput.setText(selectedName)
                        originInput.clearFocus()
                        destInput.requestFocus()
                    } else {
                        selectedDestination = selectedSpace
                        destInput.setText(selectedName)
                        destInput.clearFocus()
                    }
                    filteredNames.clear()
                    adapter.notifyDataSetChanged()
                    resultsList.visibility = android.view.View.GONE
                    tryFetchRoute(userLat, userLon, floorId, accessible)
                }
            }
            addView(resultsList)

            addView(Button(activity).apply {
                text = "Close"
                isAllCaps = false
                setTextColor(this@NavigationManager.textColor)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 8
                    bottomMargin = 8
                }
                setOnClickListener { dismissPlanPanel() }
            })
        }

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
            bottomMargin = 180
        }
        container.addView(navigationPlanPanel, params)
    }
    private fun tryFetchRoute(
        userLat: Double?,
        userLon: Double?,
        floorId: String?,
        accessible: Boolean
    ) {
        val dest = selectedDestination ?: return

        val origin: NavigationTarget = if (selectedOrigin != null) {
            NavigationTarget.SpaceTarget(selectedOrigin!!)
        } else {
            if (userLat == null || userLon == null || floorId == null) {
                android.widget.Toast.makeText(activity, "Waiting for location...", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            NavigationTarget.CoordinateTarget(Coordinate(userLat, userLon, floorId))
        }

        val options = GetDirectionsOptions(accessible = accessible)

        mapView.mapData.getDirections(origin, NavigationTarget.SpaceTarget(dest), options) { result ->
            result.onSuccess { directions ->
                if (directions != null) {
                    activity.runOnUiThread {
                        dismissPlanPanel()
                        buildPlanPanel(
                            dest, directions,
                            userLat ?: 0.0, userLon ?: 0.0,
                            floorId ?: "", accessible
                        )
                    }
                }
            }
            result.onFailure {
                activity.runOnUiThread {
                    android.widget.Toast.makeText(activity, "No route found", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchAndShowPlan(
        destination: Space,
        userLat: Double?,
        userLon: Double?,
        floorId: String?,
        accessible: Boolean
    ) {
        if (userLat == null || userLon == null || floorId == null) {
            activity.runOnUiThread {
                android.widget.Toast.makeText(activity, "Waiting for location...", android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }

        val origin = NavigationTarget.CoordinateTarget(
            Coordinate(userLat, userLon, floorId)
        )

        val options = GetDirectionsOptions(accessible = accessible)

        mapView.mapData.getDirections(origin, NavigationTarget.SpaceTarget(destination), options) { result ->
            result.onSuccess { directions ->
                if (directions == null) {
                    activity.runOnUiThread {
                        android.widget.Toast.makeText(activity, "No route found", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    return@onSuccess
                }

                activity.runOnUiThread {
                    buildPlanPanel(destination, directions, userLat, userLon, floorId, accessible)
                }
            }

            result.onFailure {
                activity.runOnUiThread {
                    android.widget.Toast.makeText(activity, "Could not find route", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    // Start navigation from user's location to a space
    fun navigateTo(destination: Space, userLat: Double, userLon: Double, floorId: String, accessible: Boolean = false) {
        dismissInfoPanel()

        val origin = NavigationTarget.CoordinateTarget(
            Coordinate(userLat, userLon, floorId)
        )
        useAccessible = accessible
        val options = GetDirectionsOptions(accessible = accessible)

        mapView.mapData.getDirections(origin, NavigationTarget.SpaceTarget(destination), options) { result ->
            result.onSuccess { directions ->
                if (directions != null) {
                    currentDirections = directions
                    activeDestination = destination
                    activeFloorId = floorId
                    isNavigating = true

                    mapView.navigation.draw(directions, navOptions) { drawResult ->
                        drawResult.onSuccess {
                            addInstructionMarkers(directions, destination.name)
                            activity.runOnUiThread {
                                showNavigationPanel(destination, directions)
                            }
                        }
                    }

                } else {
                    fallbackNavigate(destination, userLat, userLon, floorId)
                }
            }
            result.onFailure {
                fallbackNavigate(destination, userLat, userLon, floorId)
            }
        }
    }

    private fun fallbackNavigate(destination: Space, userLat: Double, userLon: Double, floorId: String) {

        val nearestSpace = activity.allSpaces
            .filter { it.floor == floorId }
            .minByOrNull { space ->
                val dLat = (space.center?.latitude ?: 0.0) - userLat
                val dLon = (space.center?.longitude ?: 0.0) - userLon
                dLat * dLat + dLon * dLon
            }

        if (nearestSpace == null) {
            Log.e("Navigation", "No space found on floor $floorId")
            activity.runOnUiThread {
                android.widget.Toast.makeText(activity, "No route found", android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }

        Log.d("Navigation", "Fallback using space: '${nearestSpace.name}' id=${nearestSpace.id}")

        val options = GetDirectionsOptions(accessible = useAccessible)
        mapView.mapData.getDirections(
            NavigationTarget.SpaceTarget(nearestSpace),
            NavigationTarget.SpaceTarget(destination),
            options
        ) { result ->
            result.onSuccess { directions ->
                if (directions != null) {
                    drawNavigation(destination, directions)
                } else {
                    activity.runOnUiThread {
                        android.widget.Toast.makeText(activity, "No route found to ${destination.name}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            result.onFailure {
                Log.e("Navigation", "Fallback also failed", it)
            }
        }
    }

    private fun drawNavigation(destination: Space, directions: Directions) {
        mapView.navigation.clear()
        mapView.paths.removeAll()
        mapView.markers.removeAll()

        mapView.navigation.draw(directions, navOptions) { drawResult ->
            drawResult.onSuccess {
                activeDestination = destination
                currentDirections = directions
                isNavigating = true
                addInstructionMarkers(directions, destination.name)
                activity.runOnUiThread {
                    showNavigationPanel(destination, directions)
                }
            }
        }
    }

    private fun showNavigationPanel(destination: Space, directions: Directions) {
        dismissNavigationPanel()

        val distanceText = if (directions.distance < 1000) "${directions.distance.toInt()}m"
        else "${"%.1f".format(directions.distance / 1000)}km"

        navigationPanel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 36, 48, 36)
            elevation = 24f
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadii = floatArrayOf(32f, 32f, 32f, 32f, 0f, 0f, 0f, 0f)
                setColor(bgColor)
            }

            // Title
            addView(TextView(activity).apply {
                text = "Navigating to ${destination.name}"
                setTextColor(this@NavigationManager.textColor)
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })

            // Distance
            addView(TextView(activity).apply {
                text = distanceText
                setTextColor(if (isDark) "#AAAAAA".toColorInt() else "#666666".toColorInt())
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 8 }
            })

            // Turn-by-turn instructions
            val instructions = directions.instructions
            if (instructions.isNotEmpty()) {
                val scrollView = android.widget.ScrollView(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        400  // max height for the instruction list
                    ).apply { topMargin = 16 }
                }

                val stepsColumn = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 8, 0, 8)
                }

                for (i in instructions.indices) {
                    val instruction = instructions[i]
                    val isLast = i == instructions.size - 1
                    val nextInstruction = if (!isLast) instructions[i + 1] else null

                    val stepText = if (isLast) {
                        "Arrive at ${destination.name}"
                    } else {
                        formatInstruction(instruction, nextInstruction, isLast, destination.name)
                    }

                    /*val iconRes = when {
                        isLast -> android.R.drawable.ic_menu_myplaces
                        stepText.contains("Turn", ignoreCase = true) -> android.R.drawable.ic_menu_directions
                        //else -> android.R.drawable.ic_menu_forward
                    }


                     */
                    stepsColumn.addView(LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(0, 16, 0, 16)

                        // Step number circle
                        addView(TextView(activity).apply {
                            text = "${i + 1}"
                            textSize = 13f
                            gravity = android.view.Gravity.CENTER
                            setTextColor(android.graphics.Color.WHITE)
                            background = android.graphics.drawable.GradientDrawable().apply {
                                shape = android.graphics.drawable.GradientDrawable.OVAL
                                setColor("#2563EB".toColorInt())
                                setSize(64, 64)
                            }
                            layoutParams = LinearLayout.LayoutParams(64, 64).apply {
                                marginEnd = 20
                            }
                        })

                        // Step text
                        addView(TextView(activity).apply {
                            text = stepText
                            textSize = 14f
                            setTextColor(this@NavigationManager.textColor)
                            layoutParams = LinearLayout.LayoutParams(
                                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                            )
                        })
                    })

                    // Divider between steps
                    if (!isLast) {
                        stepsColumn.addView(android.view.View(activity).apply {
                            setBackgroundColor(
                                if (isDark) "#333333".toColorInt() else "#E0E0E0".toColorInt()
                            )
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, 1
                            ).apply { marginStart = 84 }
                        })
                    }
                }

                scrollView.addView(stepsColumn)
                addView(scrollView)
            }

            // Stop button
            addView(Button(activity).apply {
                text = "Stop Navigation"
                isAllCaps = false
                setTextColor(android.graphics.Color.WHITE)
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 24f
                    setColor("#DC2626".toColorInt())
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 24 }
                setOnClickListener { stopNavigation() }
            })
        }

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
            bottomMargin = 180
        }
        container.addView(navigationPanel, params)
    }

    fun navigateFromSpace(origin: Space, destination: Space, accessible: Boolean = false) {
        dismissInfoPanel()
        useAccessible = accessible
        val options = GetDirectionsOptions(accessible = accessible)

        mapView.mapData.getDirections(
            NavigationTarget.SpaceTarget(origin),
            NavigationTarget.SpaceTarget(destination),
            options
        ) { result ->
            result.onSuccess { directions ->
                if (directions != null) {
                    currentDirections = directions
                    activeDestination = destination
                    isNavigating = true
                    mapView.navigation.draw(directions, navOptions) { drawResult ->
                        drawResult.onSuccess {
                            addInstructionMarkers(directions, destination.name)
                            activity.runOnUiThread {
                                showNavigationPanel(destination, directions)
                            }
                        }
                    }
                }
            }
        }
    }
    fun showRouteOnly(
        origin: Space,
        destination: Space,
        accessible: Boolean = false
    ) {
        dismissInfoPanel()
        dismissNavigationPanel()
        dismissPlanPanel()

        val options = GetDirectionsOptions(accessible = accessible)

        mapView.mapData.getDirections(
            NavigationTarget.SpaceTarget(origin),
            NavigationTarget.SpaceTarget(destination),
            options
        ) { result ->

            result.onSuccess { directions ->
                if (directions == null) {
                    activity.runOnUiThread {
                        android.widget.Toast.makeText(activity, "No route found", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    return@onSuccess
                }

                activity.runOnUiThread {
                    mapView.navigation.clear()
                    mapView.paths.removeAll()

                    // Switch to origin's floor
                    val originFloorId = origin.floor
                    if (originFloorId != null) {
                        val targetFloor = activity.floorManager.allFloors.find { it.id == originFloorId }
                        if (targetFloor != null) {
                            activity.floorManager.switchToFloor(targetFloor)
                            activity.ui.highlightFloor(targetFloor)
                        }
                    }

                    mapView.paths.add(
                        directions.coordinates,
                        AddPathOptions(
                            color = "#4b90e2",
                            displayArrowsOnPath = true
                        )
                    )
                }
            }

            result.onFailure {
                activity.runOnUiThread {
                    android.widget.Toast.makeText(activity, "Could not find route", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private fun dismissNavigationPanel() {
        navigationPanel?.let { container.removeView(it) }
        navigationPanel = null
    }

    fun stopNavigation() {
        mapView.navigation.clear()
        mapView.markers.removeAll()
        mapView.paths.removeAll()
        isNavigating = false
        dismissNavigationPanel()
        dismissPlanPanel()
        activeDestination = null
        currentDirections = null
    }

    fun startLiveNavigation(destination: Space) {
        activeDestination = destination
        isNavigating = true
    }



    fun updateNavigationPath(userLat: Double, userLon: Double, floorId: String) {
        val dest = activeDestination ?: return
        if (!isNavigating) return
        val directions = currentDirections ?: return

        val coordinate = mapView.createCoordinate(
            userLat,
            userLon,
            floorId
        )

        directions.coordinates.firstOrNull()?.let { firstCoordinate ->
            mapView.navigation.highlightPathSection(
                from = firstCoordinate,
                to = coordinate,
                options = PathSectionHighlightOptions(
                    animationDuration = 0,
                    color = "#9d9d9d",
                    widthMultiplier = 1.1
                )
            )
        }
    }
    private fun addInstructionMarkers(directions: Directions, destinationName: String) {
        val instructions = directions.instructions

        for (i in instructions.indices) {
            val instruction = instructions[i]
            val nextInstruction = if (i < instructions.size - 1) instructions[i + 1] else null
            val isLast = i == instructions.size - 1
            val distance = nextInstruction?.distance?.toInt() ?: 0

            // Skip non-last instructions with no distance
            if (!isLast && distance <= 0) continue

            val markerText = formatInstruction(instruction, nextInstruction, isLast, destinationName)

            val bgColor = if (isLast) "#358320" else "#002F6C"

            val markerTemplate = """
            <div style="
                background: $bgColor;
                color: white;
                padding: 4px 10px;
                border-radius: 16px;
                font-family: -apple-system, sans-serif;
                font-size: 10px;
                white-space: nowrap;
                box-shadow: 0 2px 8px rgba(0,0,0,0.2);
            ">
                <span>${i + 1}. $markerText</span>
            </div>
        """.trimIndent()

            mapView.markers.add(
                instruction.coordinate,
                markerTemplate,
                AddMarkerOptions(
                    rank = AddMarkerOptions.Rank.Tier(CollisionRankingTier.ALWAYS_VISIBLE),
                ),
            ) { }
        }
    }
    private fun formatInstruction(
        instruction: com.mappedin.models.DirectionInstruction,
        nextInstruction: com.mappedin.models.DirectionInstruction?,
        isLast: Boolean,
        destinationName: String
    ): String {
        if (isLast) return "Arrive at $destinationName"

        val action = instruction.action.type?.toString() ?: ""
        val bearing = instruction.action.bearing?.toString() ?: ""
        val connectionType = instruction.action.connectionType?.toString() ?: ""
        val direction = instruction.action.direction?.toString() ?: ""
        val distance = nextInstruction?.distance?.toInt() ?: 0

        val actionText = when {
            // Connections with specific type
            action.equals("TAKE_CONNECTION", ignoreCase = true) && connectionType.contains("ELEVATOR", ignoreCase = true) ->
                "Take the elevator ${direction.lowercase()}"
            action.equals("TAKE_CONNECTION", ignoreCase = true) && connectionType.contains("STAIRS", ignoreCase = true) ->
                "Take the stairs ${direction.lowercase()}"
            action.equals("TAKE_CONNECTION", ignoreCase = true) ->
                "Take the connection ${direction.lowercase()}"
            action.equals("EXIT_CONNECTION", ignoreCase = true) && connectionType.contains("ELEVATOR", ignoreCase = true) ->
                "Exit the elevator"
            action.equals("EXIT_CONNECTION", ignoreCase = true) && connectionType.contains("STAIRS", ignoreCase = true) ->
                "Exit the stairs"
            action.equals("EXIT_CONNECTION", ignoreCase = true) ->
                "Exit and continue"

            // Turns
            action.equals("TURN", ignoreCase = true) -> when {
                bearing.equals("LEFT", ignoreCase = true) -> "Turn left"
                bearing.equals("RIGHT", ignoreCase = true) -> "Turn right"
                bearing.equals("SLIGHT_LEFT", ignoreCase = true) -> "Turn slight left"
                bearing.equals("SLIGHT_RIGHT", ignoreCase = true) -> "Turn slight right"
                bearing.equals("BEAR_LEFT", ignoreCase = true) -> "Bear left"
                bearing.equals("BEAR_RIGHT", ignoreCase = true) -> "Bear right"
                else -> "Turn $bearing".lowercase().replaceFirstChar { it.uppercase() }
            }

            // Departure
            action.equals("DEPARTURE", ignoreCase = true) -> "Depart"

            // Straight
            action.equals("STRAIGHT", ignoreCase = true) -> "Continue straight"

            // Fallback
            else -> action.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
        }

        return if (distance > 0) "$actionText and go $distance meters" else actionText
    }

/*if (shouldReroute(userLat, userLon, floorId) && !isRerouting) {
            rerouteToDestination(dest, userLat, userLon, floorId)
        }
        
 */

    private fun rerouteToDestination(destination: Space, userLat: Double, userLon: Double, floorId: String) {
        if (isRerouting) return
        isRerouting = true

        val origin = NavigationTarget.CoordinateTarget(
            Coordinate(userLat, userLon, floorId)
        )

        val options = GetDirectionsOptions(accessible = useAccessible)
        mapView.mapData.getDirections(origin, NavigationTarget.SpaceTarget(destination)) { result ->
            isRerouting = false

            result.onSuccess { directions ->
                if (directions != null) {
                    currentDirections = directions
                    activeFloorId = floorId
                    mapView.navigation.draw(directions) { }
                }
            }
        }
    }
    fun isInfoPanelShowing(): Boolean {
        // return whether your info panel view is visible
        return infoPanel?.visibility == View.VISIBLE
    }

}



