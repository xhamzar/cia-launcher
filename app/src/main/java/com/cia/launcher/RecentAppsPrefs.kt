package com.cia.launcher

import android.content.Context
import org.json.JSONArray

/**
 * Tracks recently launched apps for the "Disarankan" (Recommended) section.
 * Stores up to [MAX] package names with timestamps, most-recent-first,
 * deduplicated. The launcher itself is never recorded.
 */
object RecentAppsPrefs {

    private const val PREFS = "recent_apps_prefs"
    private const val KEY = "recent_apps_json"
    private const val MAX = 20

    private data class Entry(val packageName: String, val timestamp: Long)

    /** Record a launch. Call from every launch point in the launcher. */
    fun record(context: Context, packageName: String?) {
        if (packageName.isNullOrEmpty()) return
        if (packageName == context.packageName) return // never record ourselves
        val entries = getEntries(context).toMutableList()
        entries.removeAll { it.packageName == packageName }
        entries.add(0, Entry(packageName, System.currentTimeMillis()))
        while (entries.size > MAX) entries.removeAt(entries.size - 1)
        save(context, entries)
    }

    /** Most-recent-first list of (packageName, timestamp). */
    fun getRecent(context: Context): List<Pair<String, Long>> =
        getEntries(context).map { it.packageName to it.timestamp }

    private fun getEntries(context: Context): List<Entry> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Entry(o.getString("pkg"), o.getLong("ts"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(context: Context, entries: List<Entry>) {
        val arr = JSONArray()
        for (e in entries) {
            val o = org.json.JSONObject()
            o.put("pkg", e.packageName)
            o.put("ts", e.timestamp)
            arr.put(o)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, arr.toString())
            .apply()
    }

    /** Indonesian relative time: "baru saja", "5 menit lalu", "3 jam lalu", "2 hari lalu". */
    fun relativeTime(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val minutes = diff / 60_000
        if (minutes < 1) return "baru saja"
        if (minutes < 60) return "$minutes menit lalu"
        val hours = minutes / 60
        if (hours < 24) return "$hours jam lalu"
        val days = hours / 24
        if (days < 7) return "$days hari lalu"
        val weeks = days / 7
        return "$weeks minggu lalu"
    }
}
