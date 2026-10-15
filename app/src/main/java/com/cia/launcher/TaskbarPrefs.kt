package com.cia.launcher

import android.content.Context

/**
 * Persists taskbar slot customizations. Key -> package name of the chosen app.
 * No entry = use the default resolution.
 */
object TaskbarPrefs {

    private const val PREFS = "taskbar_prefs"
    private const val PREFIX = "taskbar_custom_"

    fun getCustomPackage(context: Context, slotKey: String): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PREFIX + slotKey, null)

    fun setCustomPackage(context: Context, slotKey: String, packageName: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(PREFIX + slotKey, packageName)
            .apply()
    }

    fun clearCustomPackage(context: Context, slotKey: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(PREFIX + slotKey)
            .apply()
    }
}
