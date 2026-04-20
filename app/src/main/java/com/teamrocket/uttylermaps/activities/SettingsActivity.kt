package com.teamrocket.uttylermaps.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.teamrocket.uttylermaps.R

/**
 * Activity that hosts the application settings screen using AndroidX Preferences.
 *
 * Displays user-configurable options via [SettingsFragment], including theme selection
 * (light, dark, or system default) and a toggle for safety annotation visibility.
 * Settings changes take effect immediately for theming and are picked up by [com.teamrocket.uttylermaps.MapActivity]
 * on resume for annotation visibility.
 *
 * Uses the XML layout `settings_activity` with a fragment container, and loads preferences
 * from `root_preferences.xml`.
 *
 * @see com.teamrocket.uttylermaps.MapActivity.onResume where annotation preference changes are applied
 * @see com.teamrocket.uttylermaps.MapActivity.onCreate where theme preference is read on startup
 */
class SettingsActivity : AppCompatActivity() {

    /**
     * Called when the activity is first created.
     *
     * Sets the content view and loads the [SettingsFragment] into the fragment container
     * if this is a fresh launch (not a configuration change). Enables the back button
     * in the action bar for navigation.
     *
     * @param savedInstanceState the previously saved instance state, if any
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    /**
     * Fragment that displays the app's preference screen.
     *
     * Loads preferences from `root_preferences.xml` and attaches change listeners to:
     * - **Theme preference**: applies the selected night mode immediately via
     *   [androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode]
     * - **Safety annotations toggle**: the preference value is persisted and read by
     *   [com.teamrocket.uttylermaps.MapActivity.onResume] to show or hide annotation markers on the map
     */
    class SettingsFragment : PreferenceFragmentCompat() {

        /**
         * Called to initialize the preference hierarchy from the XML resource.
         *
         * Sets up change listeners for the theme and annotation preferences. Theme
         * changes are applied immediately; annotation changes are deferred to the
         * next [com.teamrocket.uttylermaps.MapActivity.onResume] call.
         *
         * @param savedInstanceState the previously saved instance state, if any
         * @param rootKey the key of the preference root to display, or `null` for the full hierarchy
         */
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            val themePref = findPreference<ListPreference>("theme_preference")

            themePref?.setOnPreferenceChangeListener { _, newValue ->

                when (newValue) {
                    "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                }

                true

            }

            val annotationPref = findPreference<SwitchPreferenceCompat>("show_safety_annotations")
            annotationPref?.setOnPreferenceChangeListener { _, newValue ->
                // The map will reload the setting next time MapActivity resumes
                true
            }

        }
    }
}