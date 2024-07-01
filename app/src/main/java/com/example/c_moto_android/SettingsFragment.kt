package com.example.c_moto_android

import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.example.c_moto_android.R
import com.example.c_moto_android.PreferencesManager

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val themePreference = findPreference<SwitchPreferenceCompat>(getString(R.string.theme))
        themePreference?.setOnPreferenceChangeListener { _, newValue ->
            val isDarkModeEnabled = newValue as Boolean
            val preferencesManager = PreferencesManager(requireContext())
            preferencesManager.isDarkModeEnabled = isDarkModeEnabled
            applyTheme(isDarkModeEnabled)
            true
        }
    }

    private fun applyTheme(isDarkModeEnabled: Boolean) {
        if (isDarkModeEnabled) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
        // Restart activity to apply theme changes
        requireActivity().recreate()
    }
}
