package com.cia.launcher

import android.content.Context

/**
 * Persists the custom home-screen wallpaper URI. No entry = default gradient.
 * The URI comes from the system image picker (ACTION_GET_CONTENT) with a
 * persistable read permission, so it survives restarts.
 */
object WallpaperPrefs {

    private const val PREFS = "wallpaper_prefs"
    private const val KEY_URI = "wallpaper_uri"

    fun getWallpaperUri(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_URI, null)

    fun setWallpaperUri(context: Context, uri: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_URI, uri)
            .apply()
    }

    fun clearWallpaperUri(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_URI)
            .apply()
    }
}
