package com.example.c_moto_android.com.example.c_moto_android

import android.content.Context
import androidx.preference.PreferenceManager

class PreferencesManager(context: Context) {
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    var isDarkModeEnabled: Boolean
        get() = sharedPreferences.getBoolean("theme", false)
        set(value) = sharedPreferences.edit().putBoolean("theme", value).apply()
}
