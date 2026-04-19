package com.teamrocket.uttylermaps

/**
 * Manages a persistent list of recent room search queries using SharedPreferences.
 *
 * Stores up to [MAX_HISTORY] recent searches as a `||`-delimited string in the
 * `search_history` SharedPreferences file. New searches are added to the top of the
 * list, and duplicates are automatically removed to keep entries unique.
 *
 * Used by [UIBuilder] and [NavigationActivity] to display recent searches when the
 * search input is focused but empty.
 *
 * @param context the Android context used to access SharedPreferences
 */
class SearchHistory(context: android.content.Context) {
    private val prefs = context.getSharedPreferences("search_history", android.content.Context.MODE_PRIVATE)

    /** Maximum number of recent searches to retain. */
    private val MAX_HISTORY = 15

    /** SharedPreferences key under which the search history string is stored. */
    private val KEY = "recent_searches"

    /**
     * Retrieves the list of recent search queries, ordered from most recent to oldest.
     *
     * @return a list of search strings, or an empty list if no history exists
     */
    fun getHistory(): List<String> {
        val raw = prefs.getString(KEY, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split("||")
    }

    /**
     * Adds a search query to the top of the history list.
     *
     * If the query already exists in the history, it is moved to the top rather than
     * duplicated. If the history exceeds [MAX_HISTORY] entries after insertion, the
     * oldest entry is removed. Blank queries are ignored.
     *
     * @param query the search string to add
     */
    fun addSearch(query: String) {
        if (query.isBlank()) return
        val history = getHistory().toMutableList()
        history.remove(query) // remove duplicate
        history.add(0, query) // add to top
        if (history.size > MAX_HISTORY) history.removeAt(history.lastIndex)
        prefs.edit().putString(KEY, history.joinToString("||")).apply()
    }

    /**
     * Clears all saved search history.
     */
    fun clear() {
        prefs.edit().remove(KEY).apply()
    }
}