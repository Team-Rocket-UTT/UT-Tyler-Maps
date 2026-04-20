package com.teamrocket.uttylermaps.ui

import android.R
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.search.SearchBar
import com.google.android.material.search.SearchView
import com.mappedin.MapView
import com.mappedin.models.AddLabelOptions
import com.mappedin.models.EasingFunction
import com.mappedin.models.Floor
import com.mappedin.models.FocusOnOptions
import com.mappedin.models.FocusTarget
import com.mappedin.models.GeometryUpdateState
import com.mappedin.models.LabelAppearance
import com.mappedin.models.LocationCategory
import com.mappedin.models.MapDataType
import com.mappedin.models.Space
import com.teamrocket.uttylermaps.MapActivity
import com.teamrocket.uttylermaps.data.SearchHistory
import kotlin.collections.iterator

/**
 * Builds and manages all UI elements for the [com.teamrocket.uttylermaps.MapActivity] programmatically.
 *
 * This class constructs the full view hierarchy without XML layouts, including the map container,
 * loading overlay, search bar with Material SearchView, category filter chips, floor switcher,
 * floating action buttons (location and navigation), and the bottom navigation bar. It also
 * handles category-based room filtering by highlighting matching spaces on the map and
 * dimming non-matching ones.
 *
 * All UI elements are theme-aware, adapting colors and styling based on [isDark].
 *
 * @property activity the parent [com.teamrocket.uttylermaps.MapActivity] for context and access to shared state
 * @property isDark whether the app is in dark mode
 * @property mapView the [com.mappedin.MapView] whose view is embedded in the layout
 * @see com.teamrocket.uttylermaps.MapActivity.onMapReady where [buildControls] is called after the map loads
 */
class UIBuilder(
    private val activity: MapActivity,
    private val isDark: Boolean,
    private val mapView: MapView
) {

    /** Progress spinner displayed while the map is loading. */
    lateinit var loadingIndicator: ProgressBar

    /** Root [android.widget.FrameLayout] that contains the map view and all overlay UI elements. */
    lateinit var container: FrameLayout

    /** Floating action button that opens the [com.teamrocket.uttylermaps.activities.NavigationActivity]. */
    lateinit var startNavButton: FloatingActionButton

    /** Floating action button that toggles camera follow mode on the user's location. */
    lateinit var myLocationButton: FloatingActionButton

    /** Container for the search bar and category chips at the top of the screen. */
    lateinit var searchOverlay: LinearLayout

    /** ListView that displays filtered room search results. */
    lateinit var searchResults: ListView

    /** Adapter backing the [searchResults] ListView. */
    lateinit var searchAdapter: SearchResultAdapter

    /** Material Design search bar (collapsed pill). */
    lateinit var materialSearchBar: SearchBar

    /** Material Design search view (expanded full-screen overlay). */
    lateinit var materialSearchView: SearchView

    /** Bottom navigation bar with Map, Favorites, and Settings tabs. */
    lateinit var bottomNav: BottomNavigationView

    /** Mutable list of room names currently matching the search query. */
    var filteredRooms: MutableList<String> = mutableListOf()

    /** All spaces available on the map, set by [MapActivity] after map data loads. */
    var allSpaces: List<Space> = emptyList()

    /** Manages recent search history stored in SharedPreferences. */
    val searchHistory by lazy{ SearchHistory(activity) }

    private lateinit var floorChipGroup: LinearLayout
    private var floorChips = mutableMapOf<String, TextView>()


    /**
     * Creates the initial layout shown while the map is loading.
     *
     * Builds a [FrameLayout] containing the [MapView], a colored overlay to hide the
     * unloaded map, and a centered progress spinner. The overlay and spinner are removed
     * in [MapActivity.onMapReady] once the map finishes loading.
     *
     * @return the root [FrameLayout] to be set as the activity's content view
     */
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

    /**
     * Builds all interactive control elements after the map has finished loading.
     *
     * Creates the location button, navigation button, bottom navigation bar, search
     * overlay (search bar + category chips), and configures window inset handling.
     *
     * @param onLocationClick callback for when the location FAB is tapped
     * @param onNavClick callback for when the navigation FAB is tapped
     * @param onSettingsClick callback for when the Settings tab is selected
     * @param onSearchSubmit callback for when a search query is submitted
     * @param onSearchItemClick callback for when a search result item is tapped
     */
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

    /**
     * Creates and adds the "my location" floating action button to the bottom-left of the screen.
     *
     * @param onClick callback invoked when the button is tapped
     */
    private fun buildLocationButton(onClick: () -> Unit) {
        myLocationButton = FloatingActionButton(activity).apply {
            setImageResource(R.drawable.ic_menu_mylocation)
            backgroundTintList = ColorStateList.valueOf("#657085".toColorInt())
            imageTintList = ColorStateList.valueOf(Color.WHITE)
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

    /**
     * Updates the background tint of the location FAB to indicate follow mode state.
     *
     * @param color hex color string (e.g., `"#2563EB"` for active, `"#657085"` for inactive)
     */
    fun setLocationButtonColor(color: String) {
        myLocationButton.backgroundTintList = ColorStateList.valueOf(color.toColorInt())
    }

    /**
     * Creates and adds the navigation floating action button to the bottom-right of the screen.
     *
     * @param onClick callback invoked when the button is tapped
     */
    private fun buildNavButton(onClick: () -> Unit) {
        startNavButton = FloatingActionButton(activity).apply {
            size = FloatingActionButton.SIZE_NORMAL
            setImageResource(com.teamrocket.uttylermaps.R.drawable.directions)
            backgroundTintList = ColorStateList.valueOf("#002F6C".toColorInt())
            imageTintList = ColorStateList.valueOf(Color.WHITE)
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

    /**
     * Builds and displays the floor switcher widget on the right edge of the screen.
     *
     * Creates a vertical column of labeled chips (e.g., "1", "2") for each building floor,
     * sorted in descending order (highest floor on top). Tapping a chip triggers the
     * [onFloorSwitch] callback and visually highlights the selected floor.
     *
     * @param floors the list of [com.mappedin.models.Floor] objects to display
     * @param onFloorSwitch callback invoked with the selected [com.mappedin.models.Floor] when a chip is tapped
     */
    fun buildFloorSwitcher(floors: List<Floor>, onFloorSwitch: (Floor) -> Unit) {
        if (::floorChipGroup.isInitialized) {
            container.removeView(floorChipGroup)
        }
        floorChips.clear()

        floorChipGroup = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            elevation = 8f
            background = GradientDrawable().apply {
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
                background = GradientDrawable().apply {
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

    /**
     * Updates the floor switcher UI to highlight the active floor chip.
     *
     * The active chip receives a navy blue background with white bold text; all other
     * chips are reset to transparent backgrounds with default text styling.
     *
     * @param activeFloor the [Floor] currently being displayed on the map
     */
    fun highlightFloor(activeFloor: Floor) {
        for ((id, chip) in floorChips) {
            val isActive = id == activeFloor.id
            val bg = chip.background as GradientDrawable

            if (isActive) {
                bg.setColor("#002F6C".toColorInt())
                chip.setTextColor(Color.WHITE)
                chip.setTypeface(null, Typeface.BOLD)
            } else {
                bg.setColor(Color.TRANSPARENT)
                chip.setTextColor(
                    if (isDark) "#CCCCCC".toColorInt() else "#333333".toColorInt()
                )
                chip.setTypeface(null, Typeface.NORMAL)
            }
        }
    }

    /**
     * Converts a floor name to a short label for the floor switcher (e.g., "First Floor" → "1").
     *
     * @param floor the [Floor] to generate a label for
     * @return a short string label (typically a single digit)
     */
    private fun getFloorLabel(floor: Floor): String {
        return when {
            floor.name.contains("First", ignoreCase = true) || floor.name.startsWith("1") -> "1"
            floor.name.contains("Second", ignoreCase = true) || floor.name.startsWith("2") -> "2"
            floor.name.contains("Third", ignoreCase = true) || floor.name.startsWith("3") -> "3"
            else -> floor.name.take(2)
        }
    }

    /**
     * Converts a floor name to a numeric level for sorting purposes.
     *
     * @param floor the [Floor] to determine the level of
     * @return an integer level (1, 2, 3, or 0 for unknown floors)
     */
    private fun getFloorLevel(floor: Floor): Int {
        return when {
            floor.name.contains("First", ignoreCase = true) || floor.name.startsWith("1") -> 1
            floor.name.contains("Second", ignoreCase = true) || floor.name.startsWith("2") -> 2
            floor.name.contains("Third", ignoreCase = true) || floor.name.startsWith("3") -> 3
            else -> 0
        }
    }

    /**
     * Creates and adds the bottom navigation bar with Map, Favorites, and Settings tabs.
     *
     * The Map tab is selected by default. The Favorites tab shows a "Coming Soon" toast.
     * The Settings tab invokes the [onSettingsClick] callback to open [com.teamrocket.uttylermaps.activities.SettingsActivity].
     *
     * @param onSettingsClick callback invoked when the Settings tab is selected
     */
    private fun buildBottomNav(onSettingsClick: () -> Unit) {
        bottomNav = BottomNavigationView(activity).apply {

            inflateMenu(com.teamrocket.uttylermaps.R.menu.navigation_bar)

            itemActiveIndicatorColor = ColorStateList.valueOf(
                activity.getColor(com.teamrocket.uttylermaps.R.color.uttblue)
            )

            minimumHeight = 0
            setPadding(0,0,0,0)
            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    com.teamrocket.uttylermaps.R.id.nav_map -> true
                    com.teamrocket.uttylermaps.R.id.nav_favorites -> {
                        Toast.makeText(activity, "Coming Soon!", Toast.LENGTH_SHORT).show()
                        false
                    }
                    com.teamrocket.uttylermaps.R.id.nav_settings -> {
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

    /**
     * Builds the search overlay containing the Material SearchBar, SearchView, and category chips.
     *
     * The SearchBar is the collapsed pill at the top of the screen. When tapped, it expands
     * into a full-screen SearchView with a text input, filtered results list, and recent
     * search history. Category chips below the search bar provide quick-filter shortcuts.
     *
     * @param onSearchSubmit callback invoked when the user submits a search query
     * @param onSearchItemClick callback invoked when the user taps a search result
     */
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
        materialSearchView = SearchView(activity).apply {
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

            editText.addTextChangedListener(object : TextWatcher {
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
                override fun afterTextChanged(s: Editable?) {}
            })

            addTransitionListener { _, _, newState ->
                when (newState) {
                    SearchView.TransitionState.SHOWN -> {
                        searchOverlay.visibility = View.GONE
                        if (editText.text.isNullOrEmpty()) {
                            filteredRooms.clear()
                            filteredRooms.addAll(searchHistory.getHistory())
                            searchAdapter.notifyDataSetChanged()
                            searchResults.visibility = if (filteredRooms.isEmpty()) View.GONE else View.VISIBLE
                        }
                    }
                    SearchView.TransitionState.SHOWING -> {
                        searchOverlay.visibility = View.GONE
                    }
                    SearchView.TransitionState.HIDDEN -> {
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

    /**
     * Builds a horizontally scrollable row of Material Design [com.google.android.material.chip.Chip] elements for quick
     * category filtering (Restrooms, Classrooms, Labs, Offices, Study Rooms, Food).
     *
     * Each chip has an icon and triggers [filterRoomsByKeyword] when tapped.
     *
     * @return a [HorizontalScrollView][android.widget.HorizontalScrollView] containing the chip group
     */
    private fun buildCategoryChips(): HorizontalScrollView {
        return HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = true
            setPadding(50, 4, 50, 4)
            clipToPadding = false

            val chipGroup = ChipGroup(activity).apply {
                isSingleLine = true
                chipSpacingHorizontal = 12
            }

            val categories = listOf(
                Triple("Restrooms", com.teamrocket.uttylermaps.R.drawable.restroom, "restroom"),
                Triple("Classrooms", com.teamrocket.uttylermaps.R.drawable.classroom, "classroom"),
                Triple("Labs", com.teamrocket.uttylermaps.R.drawable.lab, "lab"),
                Triple("Offices", com.teamrocket.uttylermaps.R.drawable.office, "office"),
                Triple("Study Rooms", com.teamrocket.uttylermaps.R.drawable.studyroom, "conference"),
                Triple("Food", com.teamrocket.uttylermaps.R.drawable.restaurant, "restaurant"),
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

    /**
     * Converts a dp (density-independent pixel) value to actual pixels.
     *
     * @return the equivalent pixel value based on the device's screen density
     */
    private fun Float.dpToPx(): Float {
        return this * activity.resources.displayMetrics.density
    }

    private var activeFilter: String? = null

    /**
     * Maps category keywords to their Mappedin location category IDs.
     *
     * Used by [filterRoomsByKeyword] to find spaces belonging to a specific category.
     */
    private val categoryMap = mapOf(
        "restroom" to listOf("cat_598c9293e4e54a3d", "cat_9ca8623837d4867f", "cat_fa9910b45005fb0b"),
        "office" to listOf("cat_9caae6c50fb0045f"),
        "lab" to listOf("cat_7b16b0f151765a1f"),
        "conference" to listOf("cat_922d4ca5a71fbcb7"),
        "classroom" to listOf("cat_45e221e1206031ea"),
        "restaurant" to listOf("cat_cdcf20533032e7af", "cat_c42a46873f548188"),
        "computers" to listOf("cat_726989eb1bc82b34"),
    )

    /**
     * Maps category keywords to their user-friendly display names shown in the search bar.
     */
    private val filterDisplayNames = mapOf(
        "restroom" to "Restrooms",
        "office" to "Offices",
        "lab" to "Labs",
        "conference" to "Study Rooms",
        "classroom" to "Classrooms",
        "restaurant" to "Food & Drink",
        "computers" to "Computers"
    )

    /**
     * Filters and highlights spaces on the map that match a given category keyword.
     *
     * Fades all spaces to their default state, then queries the Mappedin location categories
     * to find spaces matching the given keyword. Matching spaces are highlighted in orange
     * (`#FF8200`) and their labels are re-added. The camera is animated to focus on all
     * matching spaces. The search bar text is updated to show the active filter name.
     *
     * @param keyword the category keyword to filter by (e.g., "restroom", "lab", "office")
     */
    private fun filterRoomsByKeyword(keyword: String) {
        val displayName = filterDisplayNames[keyword] ?: keyword.replaceFirstChar { it.uppercase() }
        materialSearchBar.setText(displayName)
        activeFilter = keyword

        materialSearchBar.setNavigationIcon(com.google.android.material.R.drawable.ic_clear_black_24)
        materialSearchBar.setNavigationOnClickListener { clearFilter() }

        // fade all spaces
        allSpaces.forEach { space ->
            mapView.updateState(space, GeometryUpdateState(
                color = "initial",
                interactive = false
            )
            )
        }

        // get matching category ids
        val categoryIds = categoryMap[keyword] ?: emptyList()

        // get all profile ids that belong to those categories
        mapView.mapData.getByType<LocationCategory>(MapDataType.LOCATION_CATEGORY) { result ->
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
                        mapView.updateState(space, GeometryUpdateState(
                            color = "#FF8200",
                            opacity = 0.6,
                            interactive = true
                        )
                        )
                    }

                    mapView.labels.removeAll()

                    matchingSpaces.forEach { space ->
                        mapView.labels.add(
                            target = space,
                            text = space.name,
                            options = AddLabelOptions(
                                labelAppearance = LabelAppearance(),
                                interactive = true
                            )
                        )
                    }

                    if (matchingSpaces.isNotEmpty()) {
                        val targets = matchingSpaces.map {
                            FocusTarget.SpaceTarget(it)
                        }
                        mapView.camera.focusOn(
                            targets,
                            FocusOnOptions(
                                animationDuration = 3000,
                                easing = EasingFunction.EASE_IN_OUT
                            )
                        )
                    }
                }
            }
        }
    }

    private var clearFilterButton: ImageView? = null

    /**
     * Adds a clear (X) button to the search bar to allow dismissing the active filter.
     */
    private fun showClearFilterButton() {
        clearFilterButton?.let { materialSearchBar.removeView(it) }

        clearFilterButton = ImageView(activity).apply {
            setImageResource(R.drawable.ic_menu_close_clear_cancel)
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

    /**
     * Clears the active category filter and restores all spaces to their default state.
     *
     * Resets all space geometries to initial colors and full opacity, restores the search
     * bar icon and text, and re-adds all labels to the map via [MapActivity.reAddAllLabels].
     */
    fun clearFilter() {
        if (activeFilter == null) return
        activeFilter = null
        materialSearchBar.setText("")

        // Restore default search icon
        materialSearchBar.setNavigationIcon(com.google.android.material.R.drawable.ic_search_black_24)
        materialSearchBar.setNavigationOnClickListener(null)

        // Restore all spaces
        allSpaces.forEach { space ->
            mapView.updateState(space, GeometryUpdateState(
                color = "initial",
                opacity = 1.0,
                interactive = true
            )
            )
        }

        activity.reAddAllLabels()
    }

    /**
     * Returns whether a category filter is currently active.
     *
     * @return `true` if a filter is applied, `false` otherwise
     */
    fun isFilterActive(): Boolean = activeFilter != null

    /**
     * Hides the Material SearchView if it is currently expanded.
     */
    fun dismissSearch() {
        if (::materialSearchView.isInitialized && materialSearchView.isShowing) {
            materialSearchView.hide()
        }
    }

    /**
     * Configures window inset handling for edge-to-edge display.
     *
     * Adjusts the position of the FABs, map view, and search overlay to account for
     * the system status bar and the bottom navigation bar height, ensuring no UI elements
     * are hidden behind system chrome.
     */
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