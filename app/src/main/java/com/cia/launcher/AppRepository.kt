package com.cia.launcher

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.LruCache

object AppRepository {

    const val TAB_ALL = 0
    const val TAB_SOCIAL = 1
    const val TAB_TOOLS = 2
    const val TAB_GAMES = 3

    private val SOCIAL_KEYS = listOf(
        "whatsapp", "telegram", "instagram", "facebook", "twitter",
        "tiktok", "musical.ly", "messenger", "line.", "viber", "wechat",
        "discord", "snapchat", "reddit", "threads", "pinterest"
    )
    private val GAME_KEYS = listOf(
        "game", "mobile.legends", "tencent", "freefire", "genshin", "mihoyo",
        "minecraft", "roblox", "callofduty", "supercell", "gameloft",
        "pubg", "brawlstars", "clash", "ea.", ".games."
    )
    private val TOOL_KEYS = listOf(
        "settings", "calculator", "deskclock", "calendar", "camera",
        "documentsui", "myfiles", "filemanager", "contacts", "dialer",
        "gmail", "chrome", "vending", "maps", "keep", "drive",
        "clock", "soundrecorder", "compass"
    )

    /**
     * One installed launchable app, captured in a SINGLE PackageManager pass.
     * The old code called queryIntentActivities + loadLabel + loadIcon once per
     * pinned slot (12+ full passes on the main thread) — that was the lag.
     */
    private data class SnapApp(
        val label: String,
        val normLabel: String,
        val packageName: String,
        val resolveInfo: ResolveInfo,
        val launchIntent: Intent?
    )

    private data class Snapshot(
        val apps: List<SnapApp>,
        val byPackage: Map<String, SnapApp>,
        val capturedAt: Long
    )

    private const val SNAPSHOT_TTL_MS = 60_000L
    private var snapshot: Snapshot? = null

    /** Drawable cache keyed by package name — loadIcon is the slowest PM call.
     *  Kept small (32) to bound RAM: icons are re-decoded cheaply on miss. */
    private val iconCache = LruCache<String, Drawable>(32)

    /** Drop the snapshot so the next call re-queries (e.g. after install/uninstall). */
    @Synchronized
    fun invalidate() {
        snapshot = null
        // iconCache is kept: icons rarely change and missing pkgs just won't resolve.
    }

    private fun getSnapshot(pm: PackageManager, force: Boolean = false): Snapshot {
        val now = SystemClock.uptimeMillis()
        snapshot?.let { if (!force && now - it.capturedAt < SNAPSHOT_TTL_MS) return it }
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = try {
            pm.queryIntentActivities(main, 0).mapNotNull { ri ->
                try {
                    val label = ri.loadLabel(pm)?.toString()?.trim().orEmpty()
                    if (label.isEmpty()) return@mapNotNull null
                    val pkg = ri.activityInfo.packageName
                    SnapApp(
                        label = label,
                        normLabel = normalize(label),
                        packageName = pkg,
                        resolveInfo = ri,
                        launchIntent = pm.getLaunchIntentForPackage(pkg)
                    )
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
        return Snapshot(list, list.associateBy { it.packageName }, now).also { snapshot = it }
    }

    private fun iconFor(pm: PackageManager, app: SnapApp): Drawable {
        synchronized(iconCache) {
            iconCache.get(app.packageName)?.let { return it }
        }
        // Only the matched app's icon is ever loaded — never all of them.
        // Decoded as RGB_565 (halves bitmap RAM vs ARGB_8888); on the dark
        // launcher theme the missing alpha channel is invisible.
        val d = try {
            app.resolveInfo.loadIcon(pm).toRgb565()
        } catch (_: Exception) {
            app.resolveInfo.loadIcon(pm)
        }
        synchronized(iconCache) { iconCache.put(app.packageName, d) }
        return d
    }

    /**
     * Convert a drawable to an RGB_565 bitmap drawable (~50% RAM of ARGB_8888).
     * Falls back to the original drawable on any error. Transparent areas
     * become black, which blends into the dark launcher background.
     */
    private fun Drawable.toRgb565(): Drawable {
        // Fast path: already a 565 bitmap (or immutable shareable).
        (this as? android.graphics.drawable.BitmapDrawable)?.bitmap?.let { src ->
            if (src.config == android.graphics.Bitmap.Config.RGB_565 || src.isRecycled) return this
            try {
                src.copy(android.graphics.Bitmap.Config.RGB_565, false)?.let { copy ->
                    return android.graphics.drawable.BitmapDrawable(
                        android.content.res.Resources.getSystem(), copy
                    )
                }
            } catch (_: Exception) { /* fall through to rasterize */ }
            return this
        }
        // Rasterize vector/adaptive icons at intrinsic size (capped — launcher
        // icons are displayed small, no need for full-res adaptive layers).
        val w = (if (intrinsicWidth > 0) intrinsicWidth else 96).coerceAtMost(192)
        val h = (if (intrinsicHeight > 0) intrinsicHeight else 96).coerceAtMost(192)
        val bmp = android.graphics.Bitmap.createBitmap(
            w, h, android.graphics.Bitmap.Config.RGB_565
        )
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(android.graphics.Color.BLACK)
        setBounds(0, 0, w, h)
        draw(canvas)
        return android.graphics.drawable.BitmapDrawable(
            android.content.res.Resources.getSystem(), bmp
        )
    }

    private fun toPinned(pm: PackageManager, designLabel: String, app: SnapApp): PinnedApp =
        PinnedApp(designLabel, app.packageName, iconFor(pm, app), app.launchIntent)

    private fun normalize(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9]"), "")

    /**
     * Resolve an app by exact package names using the shared snapshot.
     * Returns null when none is installed — callers must HIDE the slot.
     */
    fun resolveApp(pm: PackageManager, designLabel: String, vararg pkgs: String): PinnedApp? {
        val snap = getSnapshot(pm)
        for (pkg in pkgs) {
            val app = snap.byPackage[pkg] ?: continue
            if (app.launchIntent == null) continue
            return toPinned(pm, designLabel, app)
        }
        return null
    }

    /**
     * Resolve an app by matching installed app labels (for ROMs where the
     * package name differs, e.g. crDroid/AOSP cameras and galleries).
     * Single snapshot pass — icon is loaded ONLY for the matched app.
     */
    fun resolveAppByLabel(
        pm: PackageManager,
        designLabel: String,
        vararg labelKeys: String
    ): PinnedApp? {
        val keys = labelKeys.map { normalize(it) }
        if (keys.isEmpty()) return null
        val snap = getSnapshot(pm)
        // pass 1: exact normalized match
        for (key in keys) {
            snap.apps.firstOrNull { it.normLabel == key && it.launchIntent != null }
                ?.let { return toPinned(pm, designLabel, it) }
        }
        // pass 2: starts-with match (either direction)
        for (key in keys) {
            snap.apps.firstOrNull {
                it.launchIntent != null &&
                    (it.normLabel.startsWith(key) || key.startsWith(it.normLabel))
            }?.let { return toPinned(pm, designLabel, it) }
        }
        // pass 3: contains match
        for (key in keys) {
            snap.apps.firstOrNull { it.launchIntent != null && it.normLabel.contains(key) }
                ?.let { return toPinned(pm, designLabel, it) }
        }
        return null
    }

    /** Resolve by packages first, then by label. Null when not installed. */
    fun resolveSlot(
        pm: PackageManager,
        designLabel: String,
        pkgs: List<String>,
        labelKeys: List<String>
    ): PinnedApp? =
        resolveApp(pm, designLabel, *pkgs.toTypedArray())
            ?: resolveAppByLabel(pm, designLabel, *labelKeys.toTypedArray())

    /**
     * Resolve an exact installed package name, using the app's REAL label.
     * Used for user-customized taskbar slots. Null when not installed.
     */
    fun resolveCustom(pm: PackageManager, packageName: String): PinnedApp? {
        val snap = getSnapshot(pm)
        val app = snap.byPackage[packageName] ?: return null
        if (app.launchIntent == null) return null
        return toPinned(pm, app.label, app)
    }

    private data class PinnedSlot(
        val label: String,
        val pkgs: List<String>,
        val labelKeys: List<String>
    )

    /**
     * The 12 default pinned slots from the design, used on first run.
     * Only slots whose app is actually installed are returned — missing apps
     * are hidden, never placeholders.
     */
    fun defaultPinnedPkgs(pm: PackageManager): List<String> {
        val slots = listOf(
            PinnedSlot("Chrome", listOf("com.android.chrome"), listOf("chrome")),
            PinnedSlot("YouTube", listOf("com.google.android.youtube"), listOf("youtube")),
            PinnedSlot(
                "WhatsApp",
                listOf("com.whatsapp", "com.whatsapp.w4b"),
                listOf("whatsapp")
            ),
            PinnedSlot("Instagram", listOf("com.instagram.android"), listOf("instagram")),
            PinnedSlot(
                "File Saya",
                listOf(
                    "com.google.android.documentsui",
                    "com.samsung.android.myfiles",
                    "com.android.documentsui"
                ),
                listOf("filesaya", "files", "myfiles", "filemanager", "pengelola")
            ),
            PinnedSlot(
                "Foto",
                listOf("com.google.android.apps.photos", "com.samsung.android.gallery3d"),
                listOf("foto", "photos", "gallery", "galeri")
            ),
            PinnedSlot("Play Store", listOf("com.android.vending"), listOf("playstore")),
            PinnedSlot("Spotify", listOf("com.spotify.music"), listOf("spotify")),
            PinnedSlot(
                "Kalender",
                listOf("com.google.android.calendar", "com.samsung.android.calendar"),
                listOf("kalender", "calendar")
            ),
            PinnedSlot(
                "Kamera",
                listOf(
                    "com.sec.android.app.camera",
                    "com.android.camera",
                    "com.google.android.GoogleCamera",
                    "org.lineageos.snap"
                ),
                listOf("kamera", "camera")
            ),
            PinnedSlot("Gmail", listOf("com.google.android.gm"), listOf("gmail")),
            PinnedSlot(
                "Pengaturan",
                listOf("com.android.settings"),
                listOf("pengaturan", "settings", "setelan")
            )
        )
        return slots.mapNotNull { s ->
            resolveSlot(pm, s.label, s.pkgs, s.labelKeys)?.packageName
        }
    }

    /**
     * Resolve the user's pinned package list to [PinnedApp]s, using each
     * app's REAL label. Missing/uninstalled/hidden apps are skipped —
     * the grid handles any count gracefully.
     */
    fun pinnedApps(
        pm: PackageManager,
        pinnedPkgs: List<String>,
        hidden: Set<String> = emptySet()
    ): List<PinnedApp> =
        pinnedPkgs.mapNotNull { pkg ->
            if (pkg in hidden) null
            else resolveCustom(pm, pkg)
        }

    /**
     * "Disarankan" rows backed by REAL recently-launched apps
     * (tracked via [RecentAppsPrefs]). Up to [limit] entries, most recent
     * first, with Indonesian relative-time subtitles. Empty when no history.
     */
    fun recentApps(
        context: android.content.Context,
        pm: PackageManager,
        hidden: Set<String> = emptySet(),
        limit: Int = 6
    ): List<SuggestedApp> {
        val recent = RecentAppsPrefs.getRecent(context)
        val out = mutableListOf<SuggestedApp>()
        for ((pkg, ts) in recent) {
            if (out.size >= limit) break
            if (pkg in hidden) continue
            val app = resolveCustom(pm, pkg) ?: continue
            out.add(SuggestedApp(app.label, RecentAppsPrefs.relativeTime(ts), app))
        }
        return out
    }

    /**
     * Load every launchable app, sorted A-Z. Icons come from the LruCache.
     * Hidden apps (privacy) are excluded when [hidden] is given.
     */
    fun loadAll(
        pm: PackageManager,
        hidden: Set<String> = emptySet()
    ): List<AppEntry> {
        val snap = getSnapshot(pm)
        return snap.apps
            .filter { it.packageName !in hidden }
            .map { s ->
            AppEntry(
                label = s.label,
                packageName = s.packageName,
                icon = iconFor(pm, s),
                launchIntent = s.launchIntent
            )
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    /** Which tab an app belongs to (social / tools / games). */
    fun categoryOf(entry: AppEntry): Int {
        val pkg = entry.packageName.lowercase()
        if (SOCIAL_KEYS.any { pkg.contains(it) }) return TAB_SOCIAL
        if (GAME_KEYS.any { pkg.contains(it) }) return TAB_GAMES
        if (TOOL_KEYS.any { pkg.contains(it) }) return TAB_TOOLS
        return TAB_ALL
    }

    fun launchIntentFor(pm: PackageManager, vararg pkgs: String): Intent? {
        for (pkg in pkgs) {
            try {
                pm.getLaunchIntentForPackage(pkg)?.let { return it }
            } catch (_: Exception) {
            }
        }
        return null
    }
}
