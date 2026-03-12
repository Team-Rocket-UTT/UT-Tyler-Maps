package com.example.uttylermaps

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import com.mappedin.models.Space
import androidx.appcompat.app.AppCompatActivity
import com.mappedin.MapView
import com.mappedin.models.AddLabelOptions
import com.mappedin.models.Doors
import com.mappedin.models.DoorsUpdateState
import com.mappedin.models.Floor
import com.mappedin.models.FloorStack
import com.mappedin.models.GeometryUpdateState
import com.mappedin.models.UpdateState
import com.mappedin.models.GetMapDataWithCredentialsOptions
import com.mappedin.models.LabelAppearance
import com.mappedin.models.MapDataType
import com.mappedin.models.Show3DMapOptions

//from https://developer.mappedin.com/android-sdk
class MapActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var loadingIndicator: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Display a Map"

        // Create a FrameLayout to hold both the map view and loading indicator
        val container = FrameLayout(this)

        mapView = MapView(this)
        container.addView(
            mapView.view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        // Add loading indicator
        loadingIndicator = ProgressBar(this)
        val loadingParams =
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        loadingParams.gravity = Gravity.CENTER
        container.addView(loadingIndicator, loadingParams)

        setContentView(container)

        // See Trial API key Terms and Conditions
        // https://developer.mappedin.com/docs/demo-keys-and-maps
        val options =
            GetMapDataWithCredentialsOptions(
                key = "mik_WHm7lPemUXoBeBY0j5482076a",
                secret = "mis_qGm14reCYjwNXATtwlqz4Zk29t48YRYpEHkrS2RzVdU94251086",
                mapId = "696db8c80f54a6000bdca0ad",
            )

        // Load the map data.
        mapView.getMapData(options) { result ->
            result
                .onSuccess {
                    Log.d("Mappedin", "getMapData success")
                    // Display the map.
                    mapView.show3dMap(Show3DMapOptions()) { r ->
                        r.onSuccess {
                            runOnUiThread {
                                //Map is laoded and ready
                                loadingIndicator.visibility = android.view.View.GONE
                            }
                            onMapReady(mapView)
                        }
                        r.onFailure {
                            //error showing map
                            runOnUiThread {
                                loadingIndicator.visibility = android.view.View.GONE
                            }
                            Log.e("Mappedin", "show3dMap error: $it")
                        }
                    }
                }.onFailure {// error loading map
                    runOnUiThread {
                        loadingIndicator.visibility = android.view.View.GONE
                    }
                    Log.e("Mappedin", "getMapData error: $it")
                }
        }
    }


    // Place your code to be called when the map is ready here.
    private fun onMapReady(mapView: MapView) {

        //make doors visible
        mapView.updateState(
            Doors.INTERIOR,
            DoorsUpdateState(
                visible = true,
                color = "brown",
                topColor = "brown",
                opacity = 0.5
            ),

            )

        //add labels to areas
        mapView.mapData.getByType<Space>(MapDataType.SPACE) { result ->
            result.onSuccess { spaces ->
                spaces.forEach { space ->
                    mapView.updateState(
                        space,
                        GeometryUpdateState(
                            interactive = true
                        )
                    ) { result ->
                        result.onFailure {
                            Log.e("Mappedin", "Failed to update space", it)
                        }
                    }
                }
            }
        }


        //add labels
        mapView.mapData.getByType<Space>(MapDataType.SPACE) { result ->
            result.onSuccess { spaces ->
                for (space in spaces) {
                    if (space.name.isNotEmpty()) {
                        val color = "blue"
                        val appearance =
                            LabelAppearance(
                                color = color,
                                //icon = space.images.firstOrNull()?.url ?: svgIcon,
                            )
                        mapView.labels.add(
                            target = space,
                            text = space.name,
                            options = AddLabelOptions(labelAppearance = appearance, interactive = true),
                        )
                    }
                }
            }
        }
    }
}

/*
        // Get all floor stacks
        mapView.mapData.getByType<FloorStack>(MapDataType.FLOOR_STACK) { result ->
            result.onSuccess { stacks ->
                floorStacks = stacks?.sortedBy { it.name } ?: emptyList()

                // Get all floors
                mapView.mapData.getByType<Floor>(MapDataType.FLOOR) { floorsResult ->
                    floorsResult.onSuccess { floors ->
                        allFloors = floors ?: emptyList()
                        Log.d("MappedinDemo", "Floors: $floors")
                    }
                }
            }
        }

   // }
   */