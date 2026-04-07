package com.example.uttylermaps

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.indooratlas.android.sdk.IALocation
import com.indooratlas.android.sdk.IALocationListener
import com.indooratlas.android.sdk.IALocationManager
import com.indooratlas.android.sdk.IALocationRequest

class IndoorAtlasActivity : AppCompatActivity(), IALocationListener {

    private lateinit var iaLocationManager: IALocationManager

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val granted = results.values.all { it }
            if (granted) {
                startIndoorAtlas()
            } else {
                Log.e("IndoorAtlas", "Required permissions were denied")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        iaLocationManager = IALocationManager.create(this)

        if (hasAllPermissions()) {
            startIndoorAtlas()
        } else {
            permissionLauncher.launch(requiredPermissions())
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasAllPermissions()) {
            iaLocationManager.requestLocationUpdates(
                IALocationRequest.create(),
                this
            )
        }
    }

    override fun onPause() {
        iaLocationManager.removeLocationUpdates(this)
        super.onPause()
    }

    override fun onDestroy() {
        iaLocationManager.destroy()
        super.onDestroy()
    }

    private fun startIndoorAtlas() {
        iaLocationManager.requestLocationUpdates(
            IALocationRequest.create(),
            this
        )
    }

    private fun hasAllPermissions(): Boolean {
        return requiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onLocationChanged(location: IALocation) {
        val lat = location.latitude
        val lon = location.longitude
        val floor = location.floorLevel
        val accuracy = location.accuracy

        Log.d("IndoorAtlas", "lat=$lat lon=$lon floor=$floor acc=$accuracy")
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        Log.d("IndoorAtlas", "provider=$provider status=$status extras=$extras")
    }
}