package com.teamrocket.uttylermaps

import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.search.SearchView as MaterialSearchView
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.setPadding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.search.SearchBar
import com.mappedin.MapView
import com.mappedin.models.Floor
import com.mappedin.models.Space

class UIBuilder(
    private val activity: MapActivity,
    private val isDark: Boolean,
    private val mapView: MapView
) {

    lateinit var loadingIndicator: ProgressBar
    lateinit var container: FrameLayout
    lateinit var startNavButton: FloatingActionButton
    lateinit var myLocationButton: FloatingActionButton
    lateinit var searchOverlay: LinearLayout
    lateinit var searchResults: ListView
    // Change field type:
    lateinit var searchAdapter: SearchResultAdapter

    lateinit var materialSearchBar: SearchBar
    lateinit var materialSearchView: MaterialSearchView
    lateinit var bottomNav: BottomNavigationView
    var filteredRooms: MutableList<String> = mutableListOf()
    var allSpaces: List<Space> = emptyList()
    val searchHistory by lazy{ SearchHistory(activity)}

    private lateinit var floorChipGroup: LinearLayout
    private var floorChips = mutableMapOf<String, TextView>()


    // ── Initial layout (before map loads) ──

    fun buildInitialLayout(): FrameLayout {
        container = FrameLayout(activity)
        container.setPadding(0, 0, 0, 0)

        container.addView(
            mapView.view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // Colored overlay to hide the white map
        val loadingOverlay = View(activity).apply {
            setBackgroundColor(if (isDark) "#1F1F1F".toColorInt() else "#F5F5F5".toColorInt())
            tag = "loadingOverlay"
        }
        container.addView(loadingOverlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        loadingIndicator = ProgressBar(activity)
        container.addView(
            loadingIndicator,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        )

        return container
    }

    // ── Controls (call after map is ready) ──

    fun buildControls(
        onLocationClick: () -> Unit,
        onNavClick: () -> Unit,
        onSettingsClick: () -> Unit,
        onSearchSubmit: (String) -> Unit,
        onSearchItemClick: (String) -> Unit
    ) {
        buildLocationButton(onLocationClick)
        buildNavButton(onNavClick)
        buildBottomNav(onSettingsClick)
        buildSearchOverlay(onSearchSubmit, onSearchItemClick)
        setupInsets()
    }

    // ── Location button ──

    private fun buildLocationButton(onClick: () -> Unit) {
        myLocationButton = FloatingActionButton(activity).apply {
            setImageResource(android.R.drawable.ic_menu_mylocation)
            backgroundTintList = ColorStateList.valueOf("#16A34A".toColorInt())
            setOnClickListener { onClick() }
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

    fun setLocationButtonColor(color: String) {
        myLocationButton.backgroundTintList = ColorStateList.valueOf(color.toColorInt())
    }

    // ── Navigation button ──

    private fun buildNavButton(onClick: () -> Unit) {
        startNavButton = FloatingActionButton(activity).apply {
            size = FloatingActionButton.SIZE_NORMAL
            setImageResource(R.drawable.directions)
            backgroundTintList = ColorStateList.valueOf("#002F6C".toColorInt())
            setOnClickListener { onClick() }
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

    // ── Floor switcher ──

    fun buildFloorSwitcher(floors: List<Floor>, onFloorSwitch: (Floor) -> Unit) {
        if (::floorChipGroup.isInitialized) {
            container.removeView(floorChipGroup)
        }
        floorChips.clear()

        floorChipGroup = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            elevation = 8f
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 24f
                setColor(if (isDark) "#2A2A2A".toColorInt() else "#FFFFFF".toColorInt())
                setStroke(1, if (isDark) "#444444".toColorInt() else "#CCCCCC".toColorInt())
            }
            setPadding(8, 12, 8, 12)
        }

        val sorted = floors.sortedByDescending { getFloorLevel(it) }

        for (floor in sorted) {
            val chip = TextView(activity).apply {
                text = getFloorLabel(floor)
                textSize = 18f
                gravity = Gravity.CENTER
                setPadding(32, 24, 32, 24)
                minWidth = 72
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 16f
                }
                setOnClickListener {
                    onFloorSwitch(floor)
                    highlightFloor(floor)
                }
            }
            floorChips[floor.id] = chip
            floorChipGroup.addView(chip)
        }

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            marginEnd = 16
        }
        container.addView(floorChipGroup, params)

        floors.firstOrNull()?.let { highlightFloor(it) }
    }

    fun highlightFloor(activeFloor: Floor) {
        for ((id, chip) in floorChips) {
            val isActive = id == activeFloor.id
            val bg = chip.background as android.graphics.drawable.GradientDrawable

            if (isActive) {
                bg.setColor("#002F6C".toColorInt())
                chip.setTextColor(android.graphics.Color.WHITE)
                chip.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                bg.setColor(android.graphics.Color.TRANSPARENT)
                chip.setTextColor(
                    if (isDark) "#CCCCCC".toColorInt() else "#333333".toColorInt()
                )
                chip.setTypeface(null, android.graphics.Typeface.NORMAL)
            }
        }
    }

    private fun getFloorLabel(floor: Floor): String {
        return when {
            floor.name.contains("First", ignoreCase = true) || floor.name.startsWith("1") -> "1"
            floor.name.contains("Second", ignoreCase = true) || floor.name.startsWith("2") -> "2"
            floor.name.contains("Third", ignoreCase = true) || floor.name.startsWith("3") -> "3"
            else -> floor.name.take(2)
        }
    }

    private fun getFloorLevel(floor: Floor): Int {
        return when {
            floor.name.contains("First", ignoreCase = true) || floor.name.startsWith("1") -> 1
            floor.name.contains("Second", ignoreCase = true) || floor.name.startsWith("2") -> 2
            floor.name.contains("Third", ignoreCase = true) || floor.name.startsWith("3") -> 3
            else -> 0
        }
    }

    // ── Bottom navigation ──

    private fun buildBottomNav(onSettingsClick: () -> Unit) {
        bottomNav = BottomNavigationView(activity).apply {

            inflateMenu(R.menu.navigation_bar)

            itemActiveIndicatorColor = ColorStateList.valueOf(
                activity.getColor(R.color.uttblue)
            )

            minimumHeight = 0
            setPadding(0,0,0,0)
            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_map -> true
                    R.id.nav_favorites -> {
                        android.widget.Toast.makeText(activity, "Coming Soon!", android.widget.Toast.LENGTH_SHORT).show()
                        false
                    }
                    R.id.nav_settings -> {
                        onSettingsClick()
                        false
                    }
                    else -> false
                }
            }
        }
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT

        ).apply {
            gravity = Gravity.BOTTOM
        }
        container.addView(bottomNav, params)
    }

    // ── Search overlay ──

    private fun buildSearchOverlay(
        onSearchSubmit: (String) -> Unit,
        onSearchItemClick: (String) -> Unit
    ) {
        // — Create results list FIRST —
        filteredRooms = mutableListOf()
        searchAdapter = SearchResultAdapter(activity, filteredRooms, isDark, searchHistory)
        searchResults = ListView(activity).apply {
            visibility = View.GONE
            divider = null
            dividerHeight = 0
            adapter = searchAdapter
        }

        // — Search Bar (the collapsed pill) —
        materialSearchBar = SearchBar(activity).apply {
            hint = "Search for rooms, labs..."
            id = View.generateViewId()
        }

        // — Search View (the expanded overlay) —
        materialSearchView = MaterialSearchView(activity).apply {
            setupWithSearchBar(materialSearchBar)
            hint = "Search for rooms, labs..."

            editText.setOnEditorActionListener { _, _, _ ->
                val query = text.toString()
                if (query.isNotBlank()) {
                    onSearchSubmit(query)
                    hide()
                }
                true
            }

            editText.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val q = s?.toString()?.trim()?.lowercase() ?: ""
                    filteredRooms.clear()
                    if (q.isNotBlank()) {
                        filteredRooms.addAll(
                            allSpaces.map { it.name }
                                .filter { it.isNotBlank() && it.lowercase().contains(q) }
                                .distinct()
                                .sorted()
                        )
                    } else {
                        // Show history when text is cleared
                        filteredRooms.addAll(searchHistory.getHistory())
                    }
                    searchAdapter.notifyDataSetChanged()
                    searchResults.visibility =
                        if (filteredRooms.isEmpty()) View.GONE else View.VISIBLE
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })

            addTransitionListener { _, _, newState ->
                when (newState) {
                    MaterialSearchView.TransitionState.SHOWN -> {
                        searchOverlay.visibility = View.GONE
                        if (editText.text.isNullOrEmpty()) {
                            filteredRooms.clear()
                            filteredRooms.addAll(searchHistory.getHistory())
                            searchAdapter.notifyDataSetChanged()
                            searchResults.visibility = if (filteredRooms.isEmpty()) View.GONE else View.VISIBLE
                        }
                    }
                    MaterialSearchView.TransitionState.SHOWING -> {
                        searchOverlay.visibility = View.GONE
                    }
                    MaterialSearchView.TransitionState.HIDDEN -> {
                        searchOverlay.visibility = View.VISIBLE
                        searchResults.visibility = View.GONE
                    }
                    else -> {}
                }
            }

            // Add results list into the SearchView body
            addView(searchResults)
        }

        searchResults.setOnItemClickListener { _, _, position, _ ->
            val selected = filteredRooms[position]
            onSearchItemClick(selected)
            materialSearchView.hide()
            materialSearchBar.setText(selected)
        }

        // Add SearchView (full-screen overlay)
        container.addView(materialSearchView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // Wrap SearchBar + chips at the top
        searchOverlay = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipToPadding = false
            clipChildren = false
            addView(materialSearchBar, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(buildCategoryChips(), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = -12  // pull chips closer to search bar
            })
        }

        container.addView(searchOverlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.TOP })
    }

    // ── Quick filter buttons ──
    private fun buildCategoryChips(): android.widget.HorizontalScrollView {
        return android.widget.HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = true
            setPadding(50, 4, 50, 4)
            clipToPadding = false

            val chipGroup = ChipGroup(activity).apply {
                isSingleLine = true
                chipSpacingHorizontal = 12
                //setPadding(24, 0, 24, 0)
            }

            val categories = listOf(
                Triple("Restrooms", R.drawable.restroom, "restroom"),
                Triple("Classrooms", R.drawable.classroom, "classroom"),
                Triple("Labs", R.drawable.lab, "lab"),
                Triple("Offices", R.drawable.office, "office"),
                Triple("Study Rooms", R.drawable.studyroom, "conference"),
                Triple("Food", R.drawable.restaurant, "restaurant"),
            )

            for ((label, iconRes, keyword) in categories) {
                chipGroup.addView(Chip(activity).apply {
                    setChipIconResource(iconRes)
                    text = label
                    isChipIconVisible = true
                    isCheckable = false
                    chipBackgroundColor = ColorStateList.valueOf(
                        if (isDark) "#303134".toColorInt() else "#FFFFFF".toColorInt()
                    )
                    chipStrokeWidth = 0f
                    setTextColor(if (isDark) "#E8EAED".toColorInt() else "#202124".toColorInt())
                    chipIconTint = ColorStateList.valueOf(
                        if (isDark) "#9AA0A6".toColorInt() else "#5F6368".toColorInt()
                    )

                    // Size
                    chipMinHeight = 36f.dpToPx()
                    chipIconSize = 24f.dpToPx()
                    ensureAccessibleTouchTarget(0)

                    // Pill shape
                    shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                        .setAllCornerSizes(24f.dpToPx())
                        .build()

                    elevation = if (isDark) 2f else 4f
                    // Padding
                    chipStartPadding = 12f.dpToPx()
                    chipEndPadding = 12f.dpToPx()
                    iconStartPadding = 4f.dpToPx()
                    textStartPadding = 4f.dpToPx()

                    text = label
                    setOnClickListener {
                        filterRoomsByKeyword(keyword)
                    }
                })
            }

            addView(chipGroup)
        }
    }

    private fun Float.dpToPx(): Float {
        return this * activity.resources.displayMetrics.density
    }
    private fun buildChip(label: String, iconRes: Int): LinearLayout {
        val chipBg = if (isDark) "#2A2A2A".toColorInt() else "#F1F3F4".toColorInt()
        val chipText = if (isDark) "#E8EAED".toColorInt() else "#202124".toColorInt()
        val chipStroke = if (isDark) "#5F6368".toColorInt() else "#DADCE0".toColorInt()

        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(32, 16, 36, 20)
            elevation = 0f

            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 40f
                setColor(chipBg)
                setStroke(2, chipStroke)
            }

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 30
            }

            addView(android.widget.ImageView(activity).apply {
                setImageResource(iconRes)
                setColorFilter(chipText)
                layoutParams = LinearLayout.LayoutParams(56, 56).apply {
                    marginEnd = 10
                }
            })

            addView(TextView(activity).apply {
                text = label
                textSize = 18f
                setTextColor(chipText)
            })

            setOnClickListener {
                filterRoomsByKeyword(label.lowercase().removeSuffix("s"))
            }
        }
    }

    private fun buildQuickFilterButtons(): LinearLayout {
        val buttonBg = if (isDark) "#2A2A2A".toColorInt() else "#EEEEEE".toColorInt()
        val buttonText = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK

        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(1)

            addView(Button(activity).apply {
                text = "Restrooms"
                setBackgroundColor(buttonBg)
                setTextColor(buttonText)
                setOnClickListener { filterRoomsByKeyword("restroom") }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 12 }
            })

            addView(Button(activity).apply {
                text = "Offices"
                setBackgroundColor(buttonBg)
                setTextColor(buttonText)
                setOnClickListener { filterRoomsByKeyword("office") }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 12 }
            })

            addView(Button(activity).apply {
                text = "Labs"
                setBackgroundColor(buttonBg)
                setTextColor(buttonText)
                setOnClickListener { filterRoomsByKeyword("lab") }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 12 }
            })
            addView(Button(activity).apply {
                text = "Study"
                setBackgroundColor(buttonBg)
                setTextColor(buttonText)
                setOnClickListener { filterRoomsByKeyword("conference") }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 12 }
            })
        }
    }
    private var activeFilter: String? = null

    private val categoryMap = mapOf(
        "restroom" to listOf("cat_598c9293e4e54a3d", "cat_9ca8623837d4867f", "cat_fa9910b45005fb0b"),
        "office" to listOf("cat_9caae6c50fb0045f"),
        "lab" to listOf("cat_7b16b0f151765a1f"),
        "conference" to listOf("cat_922d4ca5a71fbcb7"),
        "classroom" to listOf("cat_45e221e1206031ea"),
        "restaurant" to listOf("cat_cdcf20533032e7af", "cat_c42a46873f548188"),
        "computers" to listOf("cat_726989eb1bc82b34"),
    )
    private val filterDisplayNames = mapOf(
        "restroom" to "Restrooms",
        "office" to "Offices",
        "lab" to "Labs",
        "conference" to "Study Rooms",
        "classroom" to "Classrooms",
        "restaurant" to "Food & Drink",
        "computers" to "Computers"
    )
    private fun filterRoomsByKeyword(keyword: String) {
        val displayName = filterDisplayNames[keyword] ?: keyword.replaceFirstChar { it.uppercase() }
        materialSearchBar.setText(displayName)
        activeFilter = keyword

        materialSearchBar.setNavigationIcon(com.google.android.material.R.drawable.ic_clear_black_24)
        materialSearchBar.setNavigationOnClickListener { clearFilter() }

        // Fade all spaces
        allSpaces.forEach { space ->
            mapView.updateState(space, com.mappedin.models.GeometryUpdateState(
                color = "initial",
                interactive = false
            ))
        }

        // Get matching category IDs
        val categoryIds = categoryMap[keyword] ?: emptyList()

        // Get all profile IDs that belong to those categories
        mapView.mapData.getByType<com.mappedin.models.LocationCategory>(com.mappedin.models.MapDataType.LOCATION_CATEGORY) { result ->
            result.onSuccess { categories ->
                val matchingProfileIds = categories
                    .filter { it.id in categoryIds }
                    .flatMap { it.locationProfiles }
                    .toSet()

                // Find spaces whose profiles match
                val matchingSpaces = allSpaces.filter { space ->
                    space.locationProfiles.any { profile -> profile in matchingProfileIds }
                }

                activity.runOnUiThread {
                    matchingSpaces.forEach { space ->
                        mapView.updateState(space, com.mappedin.models.GeometryUpdateState(
                            color = "#FF8200",
                            opacity = 0.6,
                            interactive = true
                        ))
                    }

                    mapView.labels.removeAll()

                    matchingSpaces.forEach { space ->
                        mapView.labels.add(
                            target = space,
                            text = space.name,
                            options = com.mappedin.models.AddLabelOptions(
                                labelAppearance = com.mappedin.models.LabelAppearance(),
                                interactive = true
                            )
                        )
                    }

                    if (matchingSpaces.isNotEmpty()) {
                        val targets = matchingSpaces.map {
                            com.mappedin.models.FocusTarget.SpaceTarget(it)
                        }
                        mapView.camera.focusOn(
                            targets,
                            com.mappedin.models.FocusOnOptions(
                                animationDuration = 3000,
                                easing = com.mappedin.models.EasingFunction.EASE_IN_OUT
                            )
                        )
                    }
                }
            }
        }
    }
    //private var activeFilter: String? = null
    private var clearFilterButton: android.widget.ImageView? = null
    private fun showClearFilterButton() {
        clearFilterButton?.let { materialSearchBar.removeView(it) }

        clearFilterButton = android.widget.ImageView(activity).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(if (isDark) "#9AA0A6".toColorInt() else "#5F6368".toColorInt())
            setPadding(16, 16, 16, 16)
            setOnClickListener { clearFilter() }
        }

        // Add X to the end of the search bar
        materialSearchBar.addView(clearFilterButton, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            marginEnd = 16
        })
    }


    fun clearFilter() {
        if (activeFilter == null) return
        activeFilter = null
        materialSearchBar.setText("")

        // Restore default search icon
        materialSearchBar.setNavigationIcon(com.google.android.material.R.drawable.ic_search_black_24)
        materialSearchBar.setNavigationOnClickListener(null)

        // Restore all spaces
        allSpaces.forEach { space ->
            mapView.updateState(space, com.mappedin.models.GeometryUpdateState(
                color = "initial",
                opacity = 1.0,
                interactive = true
            ))
        }

        activity.reAddAllLabels()
    }
    fun isFilterActive(): Boolean = activeFilter != null

    // ── Helpers ──

    fun dismissSearch() {
        if (::materialSearchView.isInitialized && materialSearchView.isShowing) {
            materialSearchView.hide()
        }
    }

    // ── Window insets ──

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(container) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())

            bottomNav.post {
                val bottomNavHeight = bottomNav.height

                (startNavButton.layoutParams as FrameLayout.LayoutParams).apply {
                    bottomMargin = bottomNavHeight + 30
                    marginEnd = 30
                    startNavButton.layoutParams = this
                }

                (myLocationButton.layoutParams as FrameLayout.LayoutParams).apply {
                    bottomMargin = bottomNavHeight + 30
                    marginStart = 30
                    myLocationButton.layoutParams = this
                }

                (mapView.view.layoutParams as FrameLayout.LayoutParams).apply {
                    bottomMargin = bottomNavHeight
                    mapView.view.layoutParams = this
                }
            }

            searchOverlay.setPadding(24, statusBar.top, 24, 0)


            insets
        }
        container.requestApplyInsets()
    }
}