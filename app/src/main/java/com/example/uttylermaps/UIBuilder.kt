package com.example.uttylermaps

import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.SearchView
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.setPadding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
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
    lateinit var topSearchView: SearchView
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
            backgroundTintList = ColorStateList.valueOf("#2563EB".toColorInt())
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
                bg.setColor("#2563EB".toColorInt())
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
            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_map -> true
                    R.id.nav_settings -> {
                        onSettingsClick()
                        true
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
        searchOverlay = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
        }

        topSearchView = buildSearchView(onSearchSubmit)
        val searchBarWrapper = FrameLayout(activity).apply {
            setPadding(16, 8, 16, 4)
        }
        searchBarWrapper.addView(topSearchView)
        searchOverlay.addView(searchBarWrapper)

        searchOverlay.addView(buildCategoryChips())

        filteredRooms = mutableListOf()
        searchAdapter = SearchResultAdapter(activity, filteredRooms, isDark, searchHistory)
        searchResults = ListView(activity).apply {
            visibility = View.GONE
            val bg = if (isDark) "#303134".toColorInt() else "#FFFFFF".toColorInt()
            setBackgroundColor(bg)
            elevation = 8f
            divider = null
            dividerHeight = 0
            adapter = searchAdapter

            // Rounded corners on the list container
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 16f
                setColor(bg)
            }
        }

        searchResults.setOnItemClickListener { _, _, position, _ ->
            val selectedRoom = filteredRooms[position]
            onSearchItemClick(selectedRoom)
            searchResults.visibility = View.GONE
            filteredRooms.clear()
            searchAdapter.notifyDataSetChanged()
            topSearchView.setQuery(selectedRoom, false)
            topSearchView.clearFocus()
        }

        searchOverlay.addView(searchResults, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = 16
            marginEnd = 16
        })

        val searchParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP
        }
        container.addView(searchOverlay, searchParams)
    }

    private fun buildSearchView(onSearchSubmit: (String) -> Unit): SearchView {
        val uiBuilder = this

        return SearchView(activity).apply {
            queryHint = "Search for rooms, labs..."
            setIconifiedByDefault(false)

            val bgColor = if (isDark) "#303134".toColorInt() else "#F1F3F4".toColorInt()
            val textColor = if (isDark) "#E8EAED".toColorInt() else "#202124".toColorInt()
            val hintColor = if (isDark) "#9AA0A6".toColorInt() else "#5F6368".toColorInt()
            val iconColor = if (isDark) "#9AA0A6".toColorInt() else "#5F6368".toColorInt()

            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 28f
                setColor(bgColor)
            }
            setPadding(16, 4, 16, 4)
            elevation = 4f

            findViewById<View?>(androidx.appcompat.R.id.search_plate)?.background = null
            findViewById<View?>(androidx.appcompat.R.id.search_bar)?.background = null
            findViewById<View?>(androidx.appcompat.R.id.submit_area)?.background = null

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
                        onSearchSubmit(query)
                        searchResults.visibility = View.GONE
                        clearFocus()
                    }
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    val q = newText?.trim()?.lowercase() ?: ""
                    filteredRooms.clear()

                    if (q.isBlank()) {
                        searchResults.visibility = View.GONE
                    } else {
                        filteredRooms.addAll(
                            uiBuilder.allSpaces.map { it.name }
                                .filter { it.isNotBlank() && it.lowercase().contains(q) }
                                .distinct()
                                .sorted()
                        )
                        searchAdapter.notifyDataSetChanged()
                        searchResults.visibility =
                            if (filteredRooms.isEmpty()) View.GONE else View.VISIBLE
                    }
                    return true
                }
            })

            setOnQueryTextFocusChangeListener { _, hasFocus ->
                if (hasFocus && query.isNullOrEmpty()) {
                    // Show search history
                    filteredRooms.clear()
                    filteredRooms.addAll(searchHistory.getHistory())
                    searchAdapter.notifyDataSetChanged()
                    searchResults.visibility = if (filteredRooms.isEmpty()) View.GONE else View.VISIBLE
                } else if (!hasFocus) {
                    searchResults.visibility = View.GONE
                }
            }
        }
    }


    // ── Quick filter buttons ──
    private fun buildCategoryChips(): android.widget.HorizontalScrollView {
        return android.widget.HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(12, 4, 12, 4)

            val chipRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(4, 0, 4, 0)
            }

            val categories = listOf(
                Pair("Restrooms", R.drawable.restroom),     // your icons
                Pair("Offices", R.drawable.office),
                Pair("Labs", R.drawable.lab),
                //Pair("Classrooms", R.drawable.classroom)
            )

            for ((label, iconRes) in categories) {
                chipRow.addView(buildChip(label, iconRes))
            }

            addView(chipRow)
        }
    }

    private fun buildChip(label: String, iconRes: Int): LinearLayout {
        val chipBg = if (isDark) "#303134".toColorInt() else "#FFFFFF".toColorInt()
        val chipText = if (isDark) "#E8EAED".toColorInt() else "#202124".toColorInt()
        val chipStroke = if (isDark) "#5F6368".toColorInt() else "#DADCE0".toColorInt()

        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(32, 20, 36, 20)
            elevation = 2f

            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 40f
                setColor(chipBg)
                setStroke(2, chipStroke)
            }

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 10
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
        }
    }

    private fun filterRoomsByKeyword(keyword: String) {
        topSearchView.clearFocus()
        filteredRooms.clear()
        filteredRooms.addAll(
            allSpaces.map { it.name }
                .filter { it.isNotBlank() && it.contains(keyword, ignoreCase = true) }
                .distinct()
                .sorted()
        )
        searchAdapter.notifyDataSetChanged()
        searchResults.visibility = if (filteredRooms.isEmpty()) View.GONE else View.VISIBLE
    }

    // ── Helpers ──

    fun dismissSearch() {
        topSearchView.clearFocus()
        searchResults.visibility = View.GONE
        val imm = activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(mapView.view.windowToken, 0)
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

            searchOverlay.setPadding(24, statusBar.top + 16, 24, 0)

            insets
        }
        container.requestApplyInsets()
    }
}