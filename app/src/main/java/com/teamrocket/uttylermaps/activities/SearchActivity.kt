package com.teamrocket.uttylermaps.activities

import android.R
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity

/**
 * Simple search activity for finding rooms by name or category.
 *
 * Provides a text input for free-text search and quick-filter buttons for common
 * categories (Restrooms, Offices, Classrooms). The filtered results are displayed
 * in a [android.widget.ListView]; tapping a result returns the selected room name to the calling
 * activity via [android.app.Activity.RESULT_OK] with a `selected_room` extra.
 *
 * This activity receives the full list of room names via the `room_names` intent extra.
 *
 * @see NavigationActivity for the primary navigation entry point used in the app
 */
class SearchActivity : AppCompatActivity() {

    private lateinit var searchInput: EditText
    private lateinit var resultsList: ListView
    private lateinit var adapter: ArrayAdapter<String>

    /** The complete list of room names received from the launching intent. */
    private var allRooms: List<String> = emptyList()
    /** The currently filtered subset of rooms displayed in the results list. */
    private var filteredRooms: MutableList<String> = mutableListOf()

    /**
     * Called when the activity is first created.
     *
     * Reads the room name list from the intent, builds the UI with a search input,
     * quick-filter buttons, and a results list. Attaches a text watcher for real-time
     * filtering and an item click listener to return the selected room.
     *
     * @param savedInstanceState the previously saved instance state, if any
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        allRooms = intent.getStringArrayListExtra("room_names") ?: arrayListOf()
        filteredRooms = allRooms.toMutableList()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        searchInput = EditText(this).apply {
            hint = "Search rooms, offices, restrooms..."
        }

        val quickButtonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val restroomButton = Button(this).apply {
            text = "Restrooms"
            setOnClickListener { filterRooms("restroom") }
        }

        val officeButton = Button(this).apply {
            text = "Offices"
            setOnClickListener { filterRooms("office") }
        }

        val classroomButton = Button(this).apply {
            text = "Classrooms"
            setOnClickListener { filterClassrooms() }
        }

        quickButtonsRow.addView(restroomButton)
        quickButtonsRow.addView(officeButton)
        quickButtonsRow.addView(classroomButton)

        resultsList = ListView(this)
        adapter = ArrayAdapter(
            this,
            R.layout.simple_list_item_1,
            filteredRooms
        )
        resultsList.adapter = adapter

        resultsList.setOnItemClickListener { _, _, position, _ ->
            val selectedRoom = filteredRooms[position]
            intent.putExtra("selected_room", selectedRoom)
            setResult(RESULT_OK, intent)
            finish()
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterRooms(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        root.addView(searchInput)
        root.addView(quickButtonsRow)
        root.addView(resultsList)

        setContentView(root)
    }

    /**
     * Filters the room list by a text query using case-insensitive substring matching.
     *
     * If the query is blank, all rooms are shown. Updates the adapter to refresh the list.
     *
     * @param query the search string to filter rooms by
     */
    private fun filterRooms(query: String) {
        val q = query.trim().lowercase()

        filteredRooms.clear()
        if (q.isBlank()) {
            filteredRooms.addAll(allRooms)
        } else {
            filteredRooms.addAll(
                allRooms.filter { it.lowercase().contains(q) }
            )
        }
        adapter.notifyDataSetChanged()
    }

    /**
     * Filters the room list to show only rooms that contain digits in their name.
     *
     * This serves as a heuristic for identifying classrooms, which typically have
     * numeric room numbers (e.g., "RBN 2020", "HPR 115").
     */
    private fun filterClassrooms() {
        filteredRooms.clear()
        filteredRooms.addAll(
            allRooms.filter { room ->
                room.any { it.isDigit() }
            }
        )
        adapter.notifyDataSetChanged()
    }
}