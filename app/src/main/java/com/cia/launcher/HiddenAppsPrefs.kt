package com.cia.launcher

import android.content.Context

/**
 * Persists the set of hidden app package names.
 * Basic hiding only — not encryption; a determined person can still find
 * the apps via Android Settings. Don't overpromise in UI text.
 */
object HiddenAppsPrefs {

    private const val PREFS = "hidden_apps_prefs"
    private const val KEY = "hidden_packages"

    /** Copy of the hidden package names (never the live prefs set). */
    fun getHidden(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet())
            ?.toSet()
            ?: emptySet()

    fun isHidden(context: Context, packageName: String): Boolean =
        getHidden(context).contains(packageName)

    fun hide(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val updated = getHidden(context).toMutableSet()
        updated.add(packageName)
        prefs.edit().putStringSet(KEY, updated).apply()
    }

    fun unhide(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val updated = getHidden(context).toMutableSet()
        updated.remove(packageName)
        prefs.edit().putStringSet(KEY, updated).apply()
    }
}
