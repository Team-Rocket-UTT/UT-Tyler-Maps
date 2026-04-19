package com.teamrocket.uttylermaps

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.indooratlas.android.sdk.IALocation
import com.indooratlas.android.sdk.IALocationListener
import com.indooratlas.android.sdk.IALocationManager
import com.indooratlas.android.sdk.IALocationRequest

/**
 * Standalone activity for testing and debugging IndoorAtlas indoor positioning.
 *
 * This activity initializes the IndoorAtlas SDK, requests the necessary runtime permissions
 * (location and Bluetooth), and logs location updates to Logcat. It is separate from the
 * main [MapActivity] and serves as a development tool to verify that IndoorAtlas is
 * returning valid floor-level positions before integrating them into the map.
 *
 * Location updates are throttled to at most once per second via [lastUpdateTime].
 *
 * Implements [IALocationListener] to receive position callbacks from the IndoorAtlas SDK.
 *
 * @see MapActivity.onLocationChanged for the production location handler
 */
class IALocationManager : AppCompatActivity(), IALocationListener {

    /** The IndoorAtlas location manager instance used to request and receive position updates. */
    private lateinit var iaLocationManager: IALocationManager

    /** Timestamp of the last processed location update, used for throttling. */
    private var lastUpdateTime = 0L

    /**
     * Activity result launcher that requests all required runtime permissions.
     *
     * If all permissions are granted, [startIndoorAtlas] is called to begin location updates.
     * If any permission is denied, an error is logged.
     */
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val granted = results.values.all { it }
            if (granted) {
                startIndoorAtlas()
            } else {
                Log.e("IndoorAtlas", "Required permissions were denied")
            }
        }

    /**
     * Called when the activity is first created.
     *
     * Initializes the [IALocationManager] and checks for required permissions. If all
     * permissions are already granted, location updates begin immediately; otherwise,
     * the permission request dialog is shown via [permissionLauncher].
     *
     * @param savedInstanceState the previously saved instance state, if any
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        iaLocationManager = IALocationManager.create(this)

        if (hasAllPermissions()) {
            startIndoorAtlas()
        } else {
            permissionLauncher.launch(requiredPermissions())
        }
    }

    /**
     * Called when the activity resumes.
     *
     * Re-registers for IndoorAtlas location updates if permissions have been granted.
     */
    override fun onResume() {
        super.onResume()
        if (hasAllPermissions()) {
            iaLocationManager.requestLocationUpdates(
                IALocationRequest.create(),
                this
            )
        }
    }

    /**
     * Called when the activity pauses.
     *
     * Removes IndoorAtlas location update listeners to conserve battery.
     */
    override fun onPause() {
        iaLocationManager.removeLocationUpdates(this)
        super.onPause()
    }

    /**
     * Called when the activity is destroyed.
     *
     * Releases all IndoorAtlas SDK resources.
     */
    override fun onDestroy() {
        iaLocationManager.destroy()
        super.onDestroy()
    }

    /**
     * Begins requesting location updates from the IndoorAtlas SDK.
     *
     * Uses default [IALocationRequest] settings for update interval and accuracy.
     */
    private fun startIndoorAtlas() {
        iaLocationManager.requestLocationUpdates(
            IALocationRequest.create(),
            this
        )
    }

    /**
     * Checks whether all required runtime permissions have been granted.
     *
     * @return `true` if all permissions in [requiredPermissions] are granted, `false` otherwise
     */
    private fun hasAllPermissions(): Boolean {
        return requiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Returns the list of runtime permissions required for IndoorAtlas positioning.
     *
     * Includes fine and coarse location on all API levels. On Android 12+ (API 31),
     * Bluetooth scan and connect permissions are added. On Android 13+ (API 33),
     * the nearby Wi-Fi devices permission is included.
     *
     * @return an array of permission strings
     */
    private fun requiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(android.Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        return permissions.toTypedArray()
    }

    /**
     * Called by IndoorAtlas when a new indoor location fix is available.
     *
     * Throttles updates to once per second. Logs the latitude, longitude, floor level,
     * and accuracy of each accepted update to Logcat under the "IndoorAtlas" tag.
     *
     * @param location the new [IALocation] from IndoorAtlas
     */
    override fun onLocationChanged(location: IALocation) {
        val now = System.currentTimeMillis()

        if (now - lastUpdateTime > 1000) {
            lastUpdateTime = now

            val lat = location.latitude
            val lon = location.longitude
            val floor = location.floorLevel
            val accuracy = location.accuracy

            Log.d("IndoorAtlas", "lat=$lat lon=$lon floor=$floor acc=$accuracy")

        }

    }

    /**
     * Called when the IndoorAtlas positioning service status changes.
     *
     * Logs the provider name, status code, and any extra information to Logcat.
     *
     * @param provider the name of the location provider
     * @param status the new status code
     * @param extras optional extra information about the status change
     */
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        Log.d("IndoorAtlas", "provider=$provider status=$status extras=$extras")
    }
}