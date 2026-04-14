package com.teamrocket.uttylermaps

import android.app.Activity
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity

class SearchActivity : AppCompatActivity() {

    private lateinit var searchInput: EditText
    private lateinit var resultsList: ListView
    private lateinit var adapter: ArrayAdapter<String>

    private var allRooms: List<String> = emptyList()
    private var filteredRooms: MutableList<String> = mutableListOf()

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
            android.R.layout.simple_list_item_1,
            filteredRooms
        )
        resultsList.adapter = adapter

        resultsList.setOnItemClickListener { _, _, position, _ ->
            val selectedRoom = filteredRooms[position]
            intent.putExtra("selected_room", selectedRoom)
            setResult(Activity.RESULT_OK, intent)
            finish()
        }

        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterRooms(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        root.addView(searchInput)
        root.addView(quickButtonsRow)
        root.addView(resultsList)

        setContentView(root)
    }

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