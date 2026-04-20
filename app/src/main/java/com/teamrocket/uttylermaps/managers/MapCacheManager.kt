package com.teamrocket.uttylermaps

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages local caching of the Mappedin MVF (Mappedin Venue Format) file.
 *
 * Downloads the MVF bundle directly from Mappedin's REST API on first load, saves it
 * to the app's internal storage, and reuses it on subsequent launches for fast loading.
 * Credentials must still be passed to [com.mappedin.MapView.hydrateMapDataFromURL] so
 * that navigation, outdoor view, and authenticated features continue to work while
 * using the cached file.
 *
 * Flow on first launch:
 * 1. POST to Mappedin's token endpoint with API key/secret → receive access token
 * 2. GET the MVF signed URL from the venue endpoint using the token
 * 3. Download the zip bundle and save it to [Context.getFilesDir]
 * 4. Load the map via [com.mappedin.MapView.hydrateMapDataFromURL] with the cache URL
 *
 * Flow on subsequent launches:
 * 1. Check if a fresh cache file exists
 * 2. Skip network calls and load directly from disk
 *
 * Adapted from Mappedin's official CacheMVFDemoActivity sample.
 *
 * @param context the Android context used to access internal storage
 */
class MapCacheManager(private val context: Context) {

    companion object {
        private const val TAG = "MapCache"
        private const val CACHE_FILE_PREFIX = "cached-mvf-"
        private const val TOKEN_URL = "https://app.mappedin.com/api/v1/api-key/token"
        private const val MVF_VERSION = "3.0.0"

        /** Maximum age of the cached map before a fresh copy is fetched. */
        private const val MAX_CACHE_AGE_MS = 7 * 24 * 60 * 60 * 1000L  // 7 days
    }

    /**
     * Returns the cache file for the given map ID.
     */
    private fun getCacheFile(mapId: String): File =
        File(context.filesDir, "$CACHE_FILE_PREFIX$mapId.zip")

    /**
     * Returns true if a cached MVF file exists for this map ID and is fresher
     * than [MAX_CACHE_AGE_MS].
     */
    fun hasFreshCache(mapId: String): Boolean {
        val file = getCacheFile(mapId)
        if (!file.exists()) return false
        val age = System.currentTimeMillis() - file.lastModified()
        val fresh = age < MAX_CACHE_AGE_MS
        Log.d(TAG, "Cache age for $mapId: ${age / 1000 / 60} min, fresh: $fresh")
        return fresh
    }

    /**
     * Returns the cache filename (not the full URL) for passing to
     * [com.mappedin.MapView.getCacheUrl].
     *
     * @param mapId the Mappedin map identifier
     * @return the filename, e.g., "cached-mvf-abc123.zip"
     */
    fun getCacheFilename(mapId: String): String = "$CACHE_FILE_PREFIX$mapId.zip"

    /**
     * Downloads the MVF bundle from Mappedin's REST API and saves it to the cache.
     *
     * Must be called from a background thread (use a coroutine with
     * [kotlinx.coroutines.Dispatchers.IO]).
     *
     * @param key the Mappedin API key
     * @param secret the Mappedin API secret
     * @param mapId the Mappedin map identifier to download
     * @return true if the download and save succeeded, false otherwise
     */
    fun downloadAndCache(key: String, secret: String, mapId: String): Boolean {
        return try {
            Log.d(TAG, "Requesting access token")
            val accessToken = getAccessToken(key, secret)

            Log.d(TAG, "Fetching MVF download URL")
            val mvfUrl = getMvfUrl(accessToken, mapId)

            Log.d(TAG, "Downloading MVF bundle")
            val bytes = URL(mvfUrl).openStream().use { it.readBytes() }

            val file = getCacheFile(mapId)
            file.writeBytes(bytes)
            Log.d(TAG, "Cached MVF to ${file.absolutePath} (${bytes.size / 1024} KB)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download/cache MVF: ${e.message}", e)
            getCacheFile(mapId).takeIf { it.exists() }?.delete()
            false
        }
    }

    /**
     * Posts to Mappedin's token endpoint with API credentials to retrieve a bearer token.
     *
     * @param key the Mappedin API key
     * @param secret the Mappedin API secret
     * @return the access token string
     */
    private fun getAccessToken(key: String, secret: String): String {
        val connection = URL(TOKEN_URL).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        val body = JSONObject().apply {
            put("key", key)
            put("secret", secret)
        }

        connection.outputStream.use { it.write(body.toString().toByteArray()) }
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        return JSONObject(response).getString("access_token")
    }

    /**
     * Retrieves the temporary signed URL for downloading the MVF bundle of a given map.
     *
     * @param accessToken the bearer token from [getAccessToken]
     * @param mapId the Mappedin map identifier
     * @return the signed URL pointing to the MVF zip file
     */
    private fun getMvfUrl(accessToken: String, mapId: String): String {
        val url = URL("https://app.mappedin.com/api/venue/$mapId/mvf?version=$MVF_VERSION")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $accessToken")

        val response = connection.inputStream.bufferedReader().use { it.readText() }
        return JSONObject(response).getString("url")
    }

    /**
     * Deletes the cached MVF file for a given map ID.
     */
    fun clearCache(mapId: String) {
        val file = getCacheFile(mapId)
        if (file.exists()) {
            file.delete()
            Log.d(TAG, "Cleared cache for $mapId")
        }
    }

    /** Returns the size of the cached file in bytes, or 0 if no cache exists. */
    fun cacheSize(mapId: String): Long {
        val file = getCacheFile(mapId)
        return if (file.exists()) file.length() else 0L
    }
}