package com.example.uttylermaps

import android.app.AlertDialog
import android.content.Context
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import com.mappedin.MapView
import com.mappedin.models.Directions
import com.mappedin.models.NavigationTarget
import com.mappedin.models.Space

//handles the navigation dialog and drawing directions on the map
class NavigationManager(
    private val context: Context,
    private val mapView: MapView
) {
    //save previous choices so they show up next time
    var startSpace: Space? = null
    var endSpace: Space? = null

    //start navigation window
    fun showNavigationDialog(allSpaces: List<Space>) {
        val roomNames = allSpaces
            .map { it.name }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }
        //start room
        val startInput = AutoCompleteTextView(context).apply {
            hint = "Start room"
            setAdapter(
                ArrayAdapter(
                    context,
                    android.R.layout.simple_dropdown_item_1line,
                    roomNames
                )
            )
        }
        //destination
        val endInput = AutoCompleteTextView(context).apply {
            hint = "Destination room"
            setAdapter(
                ArrayAdapter(
                    context,
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

        AlertDialog.Builder(context)
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
                    Log.e("Navigation", "Could not find selected spaces")
                    return@setPositiveButton
                }

                if (selectedStart == selectedEnd) {
                    Log.e("Navigation", "Start and destination are the same")
                    return@setPositiveButton
                }

                startSpace = selectedStart
                endSpace = selectedEnd

                getAndDrawDirections(selectedStart, selectedEnd)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getAndDrawDirections(start: Space, end: Space) {
        mapView.paths.removeAll()

        mapView.mapData.getDirections(
            NavigationTarget.SpaceTarget(start),
            NavigationTarget.SpaceTarget(end),
            // GetDirectionsOptions(accessible = true),
        ) { result ->
            result.onSuccess { directions ->
                if (directions != null) {

                    mapView.navigation.draw(directions) { drawResult ->
                        drawResult.onSuccess {
                            showTurnByTurnDialog(directions)
                        }
                        drawResult.onFailure {
                            Log.e("Navigation", "Failed to draw navigation", it)
                        }
                    }
                }
            }

            result.onFailure {
                Log.e("Navigation", "Failed to get directions", it)
            }
        }
    }

    private fun showTurnByTurnDialog(directions: Directions) {
        val steps = buildInstructionText(directions)

        AlertDialog.Builder(context)
            .setTitle("Turn-by-Turn Directions")
            .setMessage(steps.joinToString("\n\n"))
            .setPositiveButton("OK", null)
            .show()
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
}