package com.cia.launcher

import android.content.Context
import org.json.JSONArray

/**
 * Persists the user's pinned apps as an ordered list of package names.
 * Null (never set) = first run; the caller should compute defaults from
 * the installed apps and save them via [setPinned].
 */
object PinnedPrefs {

    private const val PREFS = "pinned_prefs"
    private const val KEY = "pinned_pkgs_json"

    fun getPinned(context: Context): List<String>? {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return null
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { i -> arr.getString(i) }
        } catch (_: Exception) {
            null
        }
    }

    fun setPinned(context: Context, pkgs: List<String>) {
        val arr = JSONArray()
        for (p in pkgs) arr.put(p)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, arr.toString())
            .apply()
    }

    fun addPin(context: Context, packageName: String) {
        val current = getPinned(context)?.toMutableList() ?: mutableListOf()
        if (packageName !in current) {
            current.add(packageName)
            setPinned(context, current)
        }
    }

    fun removePin(context: Context, packageName: String) {
        val current = getPinned(context)?.toMutableList() ?: return
        if (current.remove(packageName)) {
            setPinned(context, current)
        }
    }

    fun isPinned(context: Context, packageName: String): Boolean =
        getPinned(context)?.contains(packageName) == true
}
