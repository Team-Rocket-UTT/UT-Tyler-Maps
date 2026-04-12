package com.example.uttylermaps
class SearchHistory(context: android.content.Context) {
    private val prefs = context.getSharedPreferences("search_history", android.content.Context.MODE_PRIVATE)
    private val MAX_HISTORY = 10
    private val KEY = "recent_searches"

    fun getHistory(): List<String> {
        val raw = prefs.getString(KEY, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split("||")
    }

    fun addSearch(query: String) {
        if (query.isBlank()) return
        val history = getHistory().toMutableList()
        history.remove(query) // remove duplicate
        history.add(0, query) // add to top
        if (history.size > MAX_HISTORY) history.removeAt(history.lastIndex)
        prefs.edit().putString(KEY, history.joinToString("||")).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }
}