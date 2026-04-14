package com.teamrocket.uttylermaps

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt

class NavigationActivity : AppCompatActivity() {

    private var selectedOrigin: String? = null   // null means current user location
    private var selectedDest: String? = null
    private var editingOrigin = false
    private var isDark = false

    private lateinit var originInput: EditText
    private lateinit var destInput: EditText
    private lateinit var resultsList: ListView
    private lateinit var startButton: Button
    private lateinit var searchHistory: SearchHistory

    private val filteredNames = mutableListOf<String>()
    private lateinit var adapter: android.widget.BaseAdapter
    private var roomNames: List<String> = emptyList()
    private var isSwapping = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isDark =
            (resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES

        roomNames = intent.getStringArrayListExtra("room_names") ?: emptyList()
        val prefillDest = intent.getStringExtra("prefill_dest")

        adapter = object : android.widget.BaseAdapter() {
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
                        addView(android.widget.ImageView(this@NavigationActivity).apply {
                            setImageResource(R.drawable.history)
                            setColorFilter(iconColor)
                            layoutParams = LinearLayout.LayoutParams(48, 48).apply {
                                marginEnd = 24
                            }
                        })
                    }

                    addView(android.widget.TextView(this@NavigationActivity).apply {
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
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val statusBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())
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
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
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
            background = android.graphics.drawable.GradientDrawable().apply {
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
            background = android.graphics.drawable.GradientDrawable().apply {
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
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
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
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 28f
                setColor("#2563EB".toColorInt())
            }
            setPadding(0, 30, 0, 30)
            isEnabled = false
            alpha = 0.6f
            setOnClickListener {
                if (selectedDest == null) return@setOnClickListener
                if (selectedOrigin == selectedDest) {
                    android.widget.Toast.makeText(this@NavigationActivity, "Cannot find route between the same rooms", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val result = Intent().apply {
                    putExtra("origin_room", selectedOrigin)
                    putExtra("dest_room", selectedDest)
                }
                setResult(Activity.RESULT_OK, result)
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




        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {}

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

    private fun updateStartButton() {
        val ready = !selectedDest.isNullOrBlank()
        startButton.isEnabled = ready
        startButton.alpha = if (ready) 1f else 0.6f
        startButton.text = if (selectedOrigin != null) "Show Route" else "Start Navigation"
    }
}