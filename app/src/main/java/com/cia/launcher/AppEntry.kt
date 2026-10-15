package com.cia.launcher

import android.content.Intent
import android.graphics.drawable.Drawable

/** A launchable app resolved from PackageManager. */
data class AppEntry(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    val launchIntent: Intent?
)

/** A slot on the Start Menu pinned grid. Icon falls back to generic when missing. */
data class PinnedApp(
    val label: String,
    val packageName: String?,
    val icon: Drawable,
    val launchIntent: Intent?
)

/** A "Disarankan" row: display name + time + the real backing app. */
data class SuggestedApp(
    val name: String,
    val time: String,
    val app: PinnedApp
)
