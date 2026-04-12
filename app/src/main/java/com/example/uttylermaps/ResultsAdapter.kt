package com.example.uttylermaps

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.toColorInt

class SearchResultAdapter(
    private val context: Context,
    private val items: MutableList<String>,
    private val isDark: Boolean,
    private val searchHistory: SearchHistory? = null
) : BaseAdapter() {

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val row = buildRow(items[position])
        val divider = row.getChildAt(1)
        divider.visibility = if (position < items.size - 1) View.VISIBLE else View.GONE
        return row
    }

    private fun buildRow(name: String): LinearLayout {
        val textColor = if (isDark) "#E8EAED".toColorInt() else "#202124".toColorInt()
        val iconColor = if (isDark) "#9AA0A6".toColorInt() else "#5F6368".toColorInt()
        val dividerColor = if (isDark) "#3C4043".toColorInt() else "#E8EAED".toColorInt()
        val isHistory = searchHistory?.getHistory()?.contains(name) == true

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL

            val content = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(32, 28, 32, 28)

                if (isHistory) {
                    addView(ImageView(context).apply {
                        setImageResource(R.drawable.history)
                        setColorFilter(iconColor)
                        layoutParams = LinearLayout.LayoutParams(44, 44).apply {
                            marginEnd = 20
                        }
                    })
                }

                addView(TextView(context).apply {
                    text = name
                    textSize = 15f
                    setTextColor(textColor)
                    maxLines = 1
                    layoutParams = LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                    )
                })
            }
            addView(content)

            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1
                ).apply {
                    marginStart = 32
                    marginEnd = 32
                }
                setBackgroundColor(dividerColor)
            })
        }
    }
}