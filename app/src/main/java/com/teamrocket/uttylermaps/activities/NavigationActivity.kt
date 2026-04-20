package com.teamrocket.uttylermaps.activities

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.teamrocket.uttylermaps.R
import com.teamrocket.uttylermaps.data.SearchHistory

/**
 * Activity for selecting origin and destination rooms before starting navigation.
 *
 * Presents two input fields (origin and destination) with a searchable room list. The user
 * can type to filter rooms, tap a result to select it, and swap origin/destination with the
 * swap button. The origin field defaults to "Your location" (meaning the user's live
 * IndoorAtlas position), but can be changed to a specific room for space-to-space routing.
 *
 * Recent search history is displayed when inputs are focused but empty. Results are returned
 * to [com.teamrocket.uttylermaps.MapActivity] via [android.app.Activity.RESULT_OK] with `origin_room` and `dest_room` extras.
 *
 * @see com.teamrocket.uttylermaps.MapActivity.navLauncher which launches this activity and processes the result
 * @see SearchHistory for recent search persistence
 */
class NavigationActivity : AppCompatActivity() {

    /** The selected origin room name, or `null` to use the user's current location. */
    private var selectedOrigin: String? = null
    /** The selected destination room name, or `null` if not yet chosen. */
    private var selectedDest: String? = null
    /** Tracks whether the user is currently editing the origin field (`true`) or destination (`false`). */
    private var editingOrigin = false
    /** Whether the app is currently in dark mode. */
    private var isDark = false

    private lateinit var originInput: EditText
    private lateinit var destInput: EditText
    private lateinit var resultsList: ListView
    private lateinit var startButton: Button
    private lateinit var searchHistory: SearchHistory

    private val filteredNames = mutableListOf<String>()
    private lateinit var adapter: BaseAdapter
    private var roomNames: List<String> = emptyList()
    /** Guard flag to prevent the text watcher from firing during a programmatic swap. */
    private var isSwapping = false

    /**
     * Called when the activity is first created.
     *
     * Reads the list of room names and optional pre-filled destination from the launching
     * intent, initializes the search history and list adapter, builds the UI layout, and
     * focuses the destination input field.
     *
     * @param savedInstanceState the previously saved instance state, if any
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isDark =
            (resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES

        roomNames = intent.getStringArrayListExtra("room_names") ?: emptyList()
        val prefillDest = intent.getStringExtra("prefill_dest")

        adapter = object : BaseAdapter() {
            override fun getCount() = filteredNames.size
            override fun getItem(position: Int) = filteredNames[position]
            override fun getItemId(position: Int) = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val textColor = if (isDark) "#E8EAED".toColorInt() else "#202124".toColorInt()
                val iconColor = if (isDark) "#9AA0A6".toColorInt() else "#5F6368".toColorInt()
                val name = filteredNames[position]
                val isHistory = searchHistory.getHistory().contains(name)

                return LinearLayout(this@NavigationActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(36, 28, 36, 28)

                    if (isHistory) {
                        addView(ImageView(this@NavigationActivity).apply {
                            setImageResource(R.drawable.history)
                            setColorFilter(iconColor)
                            layoutParams = LinearLayout.LayoutParams(48, 48).apply {
                                marginEnd = 24
                            }
                        })
                    }

                    addView(TextView(this@NavigationActivity).apply {
                        text = name
                        textSize = 16f
                        setTextColor(textColor)
                    })
                }
            }
        }
        searchHistory = SearchHistory(this)

        setContentView(buildLayout())

        if (!prefillDest.isNullOrBlank()) {
            selectedDest = prefillDest
            destInput.setText(prefillDest)
            updateStartButton()
        }

        destInput.requestFocus()
    }

    //layout adapted from https://www.geeksforgeeks.org/android/linearlayout-and-its-important-attributes-with-examples-in-android/
    /**
     * Constructs the entire activity layout programmatically.
     *
     * Builds the back button, origin and destination input fields, swap button, divider,
     * start/show-route button, and the search results list. Attaches text watchers to
     * both input fields for real-time filtering, and sets up item click listeners to
     * populate the selected room.
     *
     * @return the root [LinearLayout] to be set as the content view
     */
    private fun buildLayout(): LinearLayout {
        val bgColor = if (isDark) "#1E1E1E".toColorInt() else "#FFFFFF".toColorInt()
        val inputBg = if (isDark) "#303134".toColorInt() else "#F1F3F4".toColorInt()
        val inputText = if (isDark) "#E8EAED".toColorInt() else "#202124".toColorInt()
        val inputHint = if (isDark) "#9AA0A6".toColorInt() else "#5F6368".toColorInt()
        val textColor = if (isDark) "#E8EAED".toColorInt() else "#202124".toColorInt()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(bgColor)
        root.setPadding(24, 16, 24, 24)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(24, statusBars.top + 12, 24, 24)
            insets
        }

        val topRow = LinearLayout(this)
        topRow.orientation = LinearLayout.HORIZONTAL
        topRow.gravity = Gravity.CENTER_VERTICAL

        val backButton = Button(this).apply {
            text = "←"
            textSize = 28f
            setTextColor(textColor)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(12, 12, 12, 12)
            setOnClickListener { finish() }
        }
        topRow.addView(
            backButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val inputsColumn = LinearLayout(this)
        inputsColumn.orientation = LinearLayout.VERTICAL
        inputsColumn.layoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            marginStart = 12
            marginEnd = 12
        }

        originInput = EditText(this).apply {
            hint = "Your location"
            setText("Your location")
            textSize = 17f
            setTextColor(inputText)
            setHintTextColor(inputHint)
            isSingleLine = true
            background = GradientDrawable().apply {
                cornerRadius = 26f
                setColor(inputBg)
            }
            setPadding(36, 26, 36, 26)
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    editingOrigin = true
                    if (text.toString() == "Your location") {
                        setText("")
                    }
                } else {
                    if (text.toString().isBlank() && selectedOrigin == null) {
                        setText("Your location")
                    }
                }
            }
        }
        inputsColumn.addView(
            originInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val spacer = View(this)
        inputsColumn.addView(
            spacer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                18
            )
        )

        destInput = EditText(this).apply {
            hint = "Choose destination"
            textSize = 17f
            setTextColor(inputText)
            setHintTextColor(inputHint)
            isSingleLine = true
            background = GradientDrawable().apply {
                cornerRadius = 26f
                setColor(inputBg)
            }
            setPadding(36, 26, 36, 26)
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    editingOrigin = false
                    if (text.toString().isBlank()) {
                        filteredNames.clear()
                        filteredNames.addAll(searchHistory.getHistory())
                        adapter.notifyDataSetChanged()
                        resultsList.visibility = if (filteredNames.isEmpty()) View.GONE else View.VISIBLE
                    }
                }
            }
        }
        inputsColumn.addView(
            destInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        topRow.addView(inputsColumn)

        val swapButton = Button(this).apply {
            text = "⇅"
            textSize = 26f
            setTextColor(textColor)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(16, 16, 16, 16)
            setOnClickListener { swapFields() }
        }
        topRow.addView(
            swapButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            topRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        var divider = View(this).apply {
            setBackgroundColor(if (isDark) "#3C4043".toColorInt() else "#E0E0E0".toColorInt())
        }
        root.addView(
            divider,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                2
            ).apply {
                topMargin = 20
                bottomMargin = 20
            }
        )
        startButton = Button(this).apply {
            text = "Start Navigation"
            isAllCaps = false
            textSize = 17f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply {
                cornerRadius = 28f
                setColor("#2563EB".toColorInt())
            }
            setPadding(0, 30, 0, 30)
            isEnabled = false
            alpha = 0.6f
            setOnClickListener {
                if (selectedDest == null) return@setOnClickListener
                if (selectedOrigin == selectedDest) {
                    Toast.makeText(this@NavigationActivity, "Cannot find route between the same rooms", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val result = Intent().apply {
                    putExtra("origin_room", selectedOrigin)
                    putExtra("dest_room", selectedDest)
                }
                setResult(RESULT_OK, result)
                finish()
            }
        }
        root.addView(
            startButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 20
                bottomMargin = 12
            }
        )

        resultsList = ListView(this).apply {
            visibility = View.VISIBLE
            dividerHeight = 0
            setBackgroundColor(bgColor)
            adapter = this@NavigationActivity.adapter
        }
        root.addView(
            resultsList,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )




        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isSwapping) return
                val q = s?.toString()?.trim()?.lowercase() ?: ""

                filteredNames.clear()
                if (q.isNotBlank() && q != "your location") {
                    filteredNames.addAll(
                        roomNames.filter { it.lowercase().contains(q) }.take(10)
                    )
                } else {
                    filteredNames.addAll(searchHistory.getHistory())
                }

                adapter.notifyDataSetChanged()
                resultsList.visibility = View.VISIBLE  // always visible
            }
        }

        originInput.addTextChangedListener(watcher)
        destInput.addTextChangedListener(watcher)

        resultsList.setOnItemClickListener { _, _, position, _ ->
            val picked = filteredNames[position]
            searchHistory.addSearch(picked)

            if (editingOrigin) {
                selectedOrigin = picked
                originInput.setText(picked)
                originInput.clearFocus()
                destInput.requestFocus()
            } else {
                selectedDest = picked
                destInput.setText(picked)
                destInput.clearFocus()
            }

            // Show history again instead of hiding
            filteredNames.clear()
            filteredNames.addAll(searchHistory.getHistory())
            adapter.notifyDataSetChanged()
            resultsList.visibility = View.VISIBLE
            updateStartButton()
        }

        return root
    }

    /**
     * Swaps the origin and destination field values and their backing selections.
     *
     * Sets the [isSwapping] guard to prevent the text watcher from reacting to
     * programmatic text changes during the swap. If the new origin would be blank,
     * it defaults back to "Your location".
     */
    private fun swapFields() {
        isSwapping = true

        val oldOrigin = selectedOrigin
        val oldDest = selectedDest

        selectedOrigin = oldDest
        selectedDest = oldOrigin

        val originText = originInput.text.toString()
        val destText = destInput.text.toString()

        originInput.setText(
            if (destText.isBlank()) "Your location" else destText
        )
        destInput.setText(
            if (originText == "Your location") "" else originText
        )
        if (selectedOrigin == null) {
            originInput.setText("Your location")
        }

        updateStartButton()

        isSwapping = false
    }

    /**
     * Updates the start button's enabled state and label based on the current selections.
     *
     * The button is enabled only when a destination is selected. Its text changes to
     * "Show Route" when a specific origin room is selected, or "Start Navigation" when
     * using the user's current location as the origin.
     */
    private fun updateStartButton() {
        val ready = !selectedDest.isNullOrBlank()
        startButton.isEnabled = ready
        startButton.alpha = if (ready) 1f else 0.6f
        startButton.text = if (selectedOrigin != null) "Show Route" else "Start Navigation"
    }
}