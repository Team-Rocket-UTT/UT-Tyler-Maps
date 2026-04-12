// SearchResultAdapter.kt
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
    private val isDark: Boolean
) : BaseAdapter() {

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val row = (convertView as? LinearLayout) ?: buildRow()

        val content = row.getChildAt(0) as LinearLayout
        val title = content.getChildAt(0) as TextView

        title.text = items[position]

        val divider = row.getChildAt(1)
        divider.visibility = if (position < items.size - 1) View.VISIBLE else View.GONE

        return row
    }

    private fun buildRow(): LinearLayout {
        val textColor = if (isDark) "#E8EAED".toColorInt() else "#202124".toColorInt()
        val dividerColor = if (isDark) "#3C4043".toColorInt() else "#E8EAED".toColorInt()

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL

            val content = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(32, 28, 32, 28)

                addView(TextView(context).apply {
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