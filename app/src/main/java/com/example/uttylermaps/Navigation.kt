package com.example.uttylermaps

import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.test.espresso.base.Default
import com.mappedin.MapView
import com.mappedin.models.Coordinate
import com.mappedin.models.Directions
import com.mappedin.models.NavigationTarget
import com.mappedin.models.PathSectionHighlightOptions
import com.mappedin.models.Space
import com.mappedin.views.Navigation
import org.json.JSONObject
import com.mappedin.models.NavigationOptions
import com.mappedin.models.AddPathOptions

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

    private var currentDirections: Directions? = null
    private var isRerouting = false
    private var activeFloorId: String? = null
    var isNavigating = false
        private set

    private val bgColor get() = if (isDark) "#1E1E1E".toColorInt() else android.graphics.Color.WHITE
    private val textColor get() = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK

    private val navOptions = NavigationOptions(
        pathOptions = AddPathOptions(
            color = "#4b90e2",
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

    // Start navigation from user's location to a space
    fun navigateTo(destination: Space, userLat: Double, userLon: Double, floorId: String) {
        dismissInfoPanel()

        val origin = NavigationTarget.CoordinateTarget(
            Coordinate(userLat, userLon, floorId)
        )

        mapView.mapData.getDirections(origin, NavigationTarget.SpaceTarget(destination)) { result ->
            result.onSuccess { directions ->
                if (directions != null) {
                    currentDirections = directions
                    activeDestination = destination
                    activeFloorId = floorId
                    isNavigating = true

                    mapView.navigation.draw(directions, navOptions) { drawResult ->
                        drawResult.onSuccess {
                            activity.runOnUiThread {
                                showNavigationPanel(destination, directions.distance)
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
        // Include ALL spaces on this floor, even unnamed ones
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

        mapView.mapData.getDirections(
            NavigationTarget.SpaceTarget(nearestSpace),
            NavigationTarget.SpaceTarget(destination)
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



        mapView.navigation.draw(directions, navOptions) { drawResult ->
            drawResult.onSuccess {
                activeDestination = destination
                isNavigating = true
                activity.runOnUiThread {
                    showNavigationPanel(destination, directions.distance)
                }
            }
            drawResult.onFailure {
                Log.e("Navigation", "Failed to draw", it)
            }
        }
    }

    private fun showNavigationPanel(destination: Space, distance: Double) {
        dismissNavigationPanel()

        val distanceText = if (distance < 1000) "${distance.toInt()}m"
        else "${"%.1f".format(distance / 1000)}km"

        navigationPanel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 36, 48, 36)
            elevation = 24f
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadii = floatArrayOf(32f, 32f, 32f, 32f, 0f, 0f, 0f, 0f)
                setColor(bgColor)
            }

            addView(TextView(activity).apply {
                text = "Navigating to ${destination.name}"
                setTextColor(this@NavigationManager.textColor)
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })

            addView(TextView(activity).apply {
                text = distanceText
                setTextColor(if (isDark) "#AAAAAA".toColorInt() else "#666666".toColorInt())
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 8 }
            })

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
            bottomMargin = 180  // above the bottom nav bar
        }
        container.addView(navigationPanel, params)
    }

    private fun dismissNavigationPanel() {
        navigationPanel?.let { container.removeView(it) }
        navigationPanel = null
    }

    fun stopNavigation() {
        mapView.navigation.clear()
        mapView.paths.removeAll()
        isNavigating = false
        dismissNavigationPanel()
        activeDestination = null
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

    // Keep your existing showNavigationDialog if you still need it
    fun showNavigationDialog(allSpaces: List<Space>) {
        // your existing dialog code
    }
}



