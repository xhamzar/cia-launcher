package com.cia.launcher

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var clockView: TextView
    private lateinit var dateView: TextView
    private lateinit var wallpaperView: ImageView
    private var taskbarJob: Job? = null
    private var wallpaperJob: Job? = null
    private var wallpaperBitmap: Bitmap? = null
    /** URI string currently displayed (null = default gradient). Guards redundant reloads. */
    private var loadedWallpaperUri: String? = null
    private var wallpaperLoadedOnce = false

    /** System image picker — no storage permission needed (picker grants URI access). */
    private val pickWallpaper =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                    // Provider doesn't support persistable permissions; temp grant still works
                    // for this session.
                }
                WallpaperPrefs.setWallpaperUri(this, uri.toString())
                loadedWallpaperUri = null // force reload
                loadWallpaper()
                Toast.makeText(this, R.string.wallpaper_set, Toast.LENGTH_SHORT).show()
            }
        }

    /** Taskbar slots the user can customize (btn_start is fixed: always opens Start Menu). */
    private data class TaskbarSlot(
        val btnId: Int,
        val prefKey: String,
        val defaultLabel: String,
        val defaultPkgs: List<String>,
        val defaultLabelKeys: List<String>
    )

    private val slots = listOf(
        TaskbarSlot(
            R.id.btn_files, "files", "File Saya",
            listOf(
                "com.google.android.documentsui",
                "com.samsung.android.myfiles",
                "com.android.documentsui"
            ),
            listOf("filesaya", "files", "myfiles", "filemanager", "pengelola")
        ),
        TaskbarSlot(
            R.id.btn_chrome, "chrome", "Chrome",
            listOf("com.android.chrome"),
            listOf("chrome")
        ),
        TaskbarSlot(
            R.id.btn_camera, "camera", "Kamera",
            listOf(
                "com.sec.android.app.camera",
                "com.android.camera",
                "com.google.android.GoogleCamera",
                "org.lineageos.snap"
            ),
            listOf("kamera", "camera")
        )
    )

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 5000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw under the status bar (transparent), keep nav bar normal.
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        window.statusBarColor = Color.TRANSPARENT
        setContentView(R.layout.activity_main)

        clockView = findViewById(R.id.clock)
        dateView = findViewById(R.id.date)
        wallpaperView = findViewById(R.id.wallpaper)
        updateClock()

        // Long-press on empty home-screen area → wallpaper menu.
        // (Taskbar buttons consume their own long-presses, so no conflict.)
        findViewById<View>(R.id.home_root).setOnLongClickListener {
            showWallpaperMenu()
            true
        }
        loadWallpaper()

        findViewById<ImageButton>(R.id.btn_start).setOnClickListener {
            StartMenuSheet().show(supportFragmentManager, "start_menu")
        }

        setupSwipeUpToMenu()
        setupTaskbarIcons()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(tick)
        tick.run()
        // Re-resolve in case apps were installed/uninstalled while away.
        // Snapshot is invalidated so the change is picked up; the query runs
        // on Dispatchers.IO so there is zero main-thread jank.
        AppRepository.invalidate()
        setupTaskbarIcons()
        loadWallpaper()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
        taskbarJob?.cancel()
    }

    override fun onDestroy() {
        wallpaperJob?.cancel()
        wallpaperBitmap?.recycle()
        wallpaperBitmap = null
        super.onDestroy()
    }

    @Deprecated("Back is disabled on the home screen")
    override fun onBackPressed() {
        // Launcher home: ignore back press.
    }

    /**
     * Bind the 3 app taskbar buttons to REAL installed apps:
     * real icon from PackageManager, tap launches the app.
     * A user-customized slot (long-press to change) takes precedence;
     * when the custom app is gone it falls back to the default.
     * Buttons whose app is not installed are hidden — never a wrong icon.
     *
     * Resolution runs on Dispatchers.IO (icon loading is ~50-100ms per app);
     * the home screen itself renders instantly.
     */
    /**
     * Swipe up anywhere on the home screen (above the taskbar) -> open the
     * start menu. The listener never consumes touches, so taps and the
     * long-press wallpaper menu keep working untouched.
     */
    private fun setupSwipeUpToMenu() {
        val homeRoot = findViewById<View>(R.id.home_root)
        val taskbar = findViewById<View>(R.id.taskbar)
        val vc = ViewConfiguration.get(this)
        val density = resources.displayMetrics.density
        val minDistance = 100 * density
        val minVelocity = vc.scaledMinimumFlingVelocity * 4
        val detector = GestureDetector(this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    val start = e1 ?: return false
                    val dy = e2.y - start.y
                    val dx = e2.x - start.x
                    if (dy < -minDistance && abs(dy) > abs(dx) &&
                        velocityY < -minVelocity && start.y < taskbar.top
                    ) {
                        StartMenuSheet().show(supportFragmentManager, "start_menu")
                        return true
                    }
                    return false
                }
            })
        homeRoot.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            false
        }
    }

    private fun setupTaskbarIcons() {
        taskbarJob?.cancel()
        taskbarJob = lifecycleScope.launch {
            val pm = packageManager
            val hidden = HiddenAppsPrefs.getHidden(this@MainActivity)
            val apps = withContext(Dispatchers.IO) {
                slots.map { slot ->
                    val customPkg = TaskbarPrefs.getCustomPackage(this@MainActivity, slot.prefKey)
                        ?.takeIf { it !in hidden }
                    val app = customPkg?.let { AppRepository.resolveCustom(pm, it) }
                        ?: AppRepository.resolveSlot(
                            pm, slot.defaultLabel, slot.defaultPkgs, slot.defaultLabelKeys
                        )?.takeIf { it.packageName !in hidden }
                    slot to app
                }
            }
            apps.forEach { (slot, app) -> bindTaskbarButton(slot, app) }
        }
    }

    private fun bindTaskbarButton(slot: TaskbarSlot, app: PinnedApp?) {
        val btn = findViewById<ImageButton>(slot.btnId)
        if (app == null) {
            btn.visibility = View.GONE
            return
        }
        btn.visibility = View.VISIBLE
        btn.setImageDrawable(app.icon)
        btn.contentDescription = app.label
        btn.setOnClickListener { launchOrToast(app.launchIntent, app.packageName) }
        // Long-press: minimal customization menu (change / reset to default).
        btn.setOnLongClickListener {
            showSlotMenu(slot)
            true
        }
    }

    /** Small menu for a taskbar slot: change the app, or reset to default. */
    private fun showSlotMenu(slot: TaskbarSlot) {
        val hasCustom = TaskbarPrefs.getCustomPackage(this, slot.prefKey) != null
        val items = mutableListOf(getString(R.string.taskbar_change))
        if (hasCustom) items.add(getString(R.string.taskbar_reset))
        AlertDialog.Builder(this, R.style.Theme_CiaLauncher_Dialog)
            .setItems(items.toTypedArray()) { _, which ->
                if (which == 0) {
                    showAppPicker(slot)
                } else {
                    TaskbarPrefs.clearCustomPackage(this, slot.prefKey)
                    setupTaskbarIcons()
                }
            }
            .show()
    }

    /** Searchable picker: tapping an app assigns it to the slot. */
    private fun showAppPicker(slot: TaskbarSlot) {
        val dialog = AppPickerDialog()
        dialog.onAppPicked = { packageName ->
            TaskbarPrefs.setCustomPackage(this, slot.prefKey, packageName)
            setupTaskbarIcons()
        }
        dialog.show(supportFragmentManager, "app_picker")
    }

    /** Long-press menu on empty home-screen area: change or reset wallpaper. */
    private fun showWallpaperMenu() {
        val hasCustom = WallpaperPrefs.getWallpaperUri(this) != null
        val items = mutableListOf(getString(R.string.wallpaper_change))
        if (hasCustom) items.add(getString(R.string.wallpaper_reset))
        AlertDialog.Builder(this, R.style.Theme_CiaLauncher_Dialog)
            .setItems(items.toTypedArray()) { _, which ->
                if (which == 0) {
                    pickWallpaper.launch("image/*")
                } else {
                    WallpaperPrefs.clearWallpaperUri(this)
                    loadedWallpaperUri = null
                    wallpaperBitmap?.recycle()
                    wallpaperBitmap = null
                    wallpaperView.setImageResource(R.drawable.wallpaper)
                    Toast.makeText(this, R.string.wallpaper_reset_done, Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    /**
     * Loads the custom wallpaper (downsampled to screen size on a background
     * thread) or the default gradient. Skips work when nothing changed.
     */
    private fun loadWallpaper() {
        val uriStr = WallpaperPrefs.getWallpaperUri(this)
        if (wallpaperLoadedOnce && uriStr == loadedWallpaperUri) return
        wallpaperLoadedOnce = true
        loadedWallpaperUri = uriStr
        wallpaperJob?.cancel()
        if (uriStr == null) {
            wallpaperBitmap?.recycle()
            wallpaperBitmap = null
            wallpaperView.setImageResource(R.drawable.wallpaper)
            return
        }
        wallpaperJob = lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) { decodeWallpaper(uriStr) }
            if (bmp != null) {
                wallpaperBitmap?.recycle()
                wallpaperBitmap = bmp
                wallpaperView.setImageDrawable(BitmapDrawable(resources, bmp))
            } else {
                // URI invalid/revoked → fall back to default and forget it.
                WallpaperPrefs.clearWallpaperUri(this@MainActivity)
                loadedWallpaperUri = null
                wallpaperView.setImageResource(R.drawable.wallpaper)
            }
        }
    }

    /** Decodes the wallpaper URI downsampled to roughly screen size to avoid OOM. */
    private fun decodeWallpaper(uriStr: String): Bitmap? {
        return try {
            val uri = Uri.parse(uriStr)
            val metrics = resources.displayMetrics
            val targetW = metrics.widthPixels.coerceAtLeast(1)
            val targetH = metrics.heightPixels.coerceAtLeast(1)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return null
            var sample = 1
            while (w / (sample * 2) >= targetW && h / (sample * 2) >= targetH) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun updateClock() {
        val id = Locale("in", "ID")
        val now = Date()
        clockView.text = SimpleDateFormat("HH:mm", id).format(now)
        dateView.text = SimpleDateFormat("EEE, d MMMM", id).format(now)
    }

    private fun launchOrToast(intent: Intent?, packageName: String? = null) {
        if (intent != null) {
            try {
                RecentAppsPrefs.record(this, packageName)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (_: Exception) {
                toastNotFound()
            }
        } else {
            toastNotFound()
        }
    }

    private fun toastNotFound() {
        Toast.makeText(this, R.string.app_not_found, Toast.LENGTH_SHORT).show()
    }
}
