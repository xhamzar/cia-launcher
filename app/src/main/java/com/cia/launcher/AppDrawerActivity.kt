package com.cia.launcher

import android.app.SearchManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppDrawerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FOCUS_SEARCH = "focus_search"
        const val TAB_COUNT = 4 // Semua, Sosial, Alat, Game
    }

    private lateinit var searchInput: EditText
    private lateinit var clearBtn: ImageView
    private lateinit var appList: RecyclerView
    private lateinit var alphaIndex: LinearLayout
    private lateinit var hiddenBtn: ImageView
    private lateinit var adapter: DrawerAdapter

    private var allApps: List<AppEntry> = emptyList()
    private var currentTab = AppRepository.TAB_ALL
    private var query = ""

    private val tabViews = mutableListOf<TextView>()
    private val alphaLetters = listOf('#') + ('A'..'Z')

    /** Refresh daftar aplikasi setiap ada aplikasi yang dihapus. */
    private val uninstallReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_PACKAGE_REMOVED) reloadApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        window.statusBarColor = Color.TRANSPARENT
        setContentView(R.layout.activity_app_drawer)

        searchInput = findViewById(R.id.search_input)
        clearBtn = findViewById(R.id.btn_clear_search)
        appList = findViewById(R.id.app_list)
        alphaIndex = findViewById(R.id.alpha_index)
        hiddenBtn = findViewById(R.id.btn_hidden_apps)
        hiddenBtn.setOnClickListener { showHiddenApps() }

        // Swipe gestures are handled by DrawerRootLayout (whole screen):
        //  - horizontal swipe anywhere -> switch category tab
        //  - pull DOWN while the list is at the very top -> close drawer
        // Taps, vertical scrolling and the A-Z strip are never stolen.
        val drawerRoot: DrawerRootLayout = findViewById(R.id.drawer_root)
        val density = resources.displayMetrics.density
        val baseTopPad = (12 * density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(drawerRoot) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, sb + baseTopPad, v.paddingRight, v.paddingBottom)
            insets
        }
        drawerRoot.exclusionView = alphaIndex
        drawerRoot.callbacks = object : DrawerRootLayout.Callbacks {
            override fun isAtTop(): Boolean = !appList.canScrollVertically(-1)
            // Pull-down gesture: DrawerRootLayout already slid the content
            // off-screen, so finish with NO system animation — seamless.
            override fun onPullDownClose() {
                finish()
                overridePendingTransition(0, 0)
            }
            override fun onSwipeTabLeft() = selectTab((currentTab + 1) % TAB_COUNT)
            override fun onSwipeTabRight() =
                selectTab((currentTab + TAB_COUNT - 1) % TAB_COUNT)
        }

        adapter = DrawerAdapter(
            onAppClick = { launchApp(it) },
            onAppLongClick = { showHideMenu(it) },
            onSuggestionClick = { webSearch(it) }
        )
        appList.layoutManager = LinearLayoutManager(this)
        appList.adapter = adapter

        tabViews.addAll(
            listOf(
                findViewById(R.id.tab_all),
                findViewById(R.id.tab_social),
                findViewById(R.id.tab_tools),
                findViewById(R.id.tab_games)
            )
        )
        tabViews.forEachIndexed { i, tv -> tv.setOnClickListener { selectTab(i) } }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty()
                clearBtn.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
                applyFilter()
            }
        })
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH && query.isNotBlank()) {
                webSearch(query.trim())
                true
            } else false
        }
        clearBtn.setOnClickListener { searchInput.text.clear() }

        if (intent.getBooleanExtra(EXTRA_FOCUS_SEARCH, false)) {
            searchInput.post {
                searchInput.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        buildAlphaIndex(emptySet())

        // App list loads on Dispatchers.IO; the shared snapshot + icon cache
        // in AppRepository make repeat opens near-instant.
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) { AppRepository.loadAll(packageManager) }
            allApps = apps
            applyFilter()
        }
    }

    override fun onResume() {
        super.onResume()
        findViewById<DrawerRootLayout>(R.id.drawer_root).resetGestureState()
        updateHiddenButton()
        // Reload penuh: daftar bisa berubah saat drawer tidak aktif
        // (mis. habis uninstall — broadcast PACKAGE_REMOVED terlewat
        // kalau receiver sedang unregister).
        if (::adapter.isInitialized) reloadApps()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(Intent.ACTION_PACKAGE_REMOVED).apply {
            addDataScheme("package")
        }
        registerReceiver(uninstallReceiver, filter)
        registerReceiver(
            uninstallResultReceiver,
            IntentFilter("$packageName.UNINSTALL_RESULT"),
            Context.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        try {
            unregisterReceiver(uninstallReceiver)
        } catch (_: Exception) {
        }
        try {
            unregisterReceiver(uninstallResultReceiver)
        } catch (_: Exception) {
        }
        super.onStop()
    }

    /** Muat ulang daftar aplikasi dari PackageManager (mis. setelah uninstall). */
    private fun reloadApps() {
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) { AppRepository.loadAll(packageManager) }
            allApps = apps
            if (::adapter.isInitialized) applyFilter()
        }
    }

    @Deprecated("Back closes the drawer with a slide-down animation")
    override fun onBackPressed() {
        finish()
        overridePendingTransition(R.anim.stay, R.anim.slide_out_down)
    }

    override fun onDestroy() {
        // Release drawer resources so GC can reclaim icon bitmaps and the
        // app list when the drawer closes (RAM optimization).
        try {
            if (::adapter.isInitialized) adapter.submit(emptyList())
        } catch (_: Exception) {
        }
        allApps = emptyList()
        super.onDestroy()
    }

    private fun selectTab(tab: Int) {
        currentTab = tab
        tabViews.forEachIndexed { i, tv ->
            tv.background = ContextCompat.getDrawable(
                this,
                if (i == tab) R.drawable.bg_pill_selected else R.drawable.bg_pill
            )
        }
        applyFilter()
    }

    private fun applyFilter() {
        val hidden = HiddenAppsPrefs.getHidden(this)
        var list = allApps.filter { it.packageName !in hidden }
        if (currentTab != AppRepository.TAB_ALL) {
            list = list.filter { AppRepository.categoryOf(it) == currentTab }
        }
        val q = query.trim()
        if (q.isEmpty()) {
            adapter.submit(buildSections(list))
            alphaIndex.visibility = View.VISIBLE
            buildAlphaIndex(list.map { sectionLetterOf(it.label) }.toSet())
        } else {
            val lq = q.lowercase()
            val matches = list.filter { it.label.lowercase().contains(lq) }
            val items = mutableListOf<DrawerItem>()
            matches.forEach { items.add(DrawerItem.App(it, showSubtitle = true)) }
            items.add(DrawerItem.Suggestion("$q - Pencarian Google", "Pencarian", q))
            items.add(DrawerItem.Suggestion("$q download", "Pencarian", "$q download"))
            items.add(DrawerItem.Suggestion("$q apk", "Pencarian", "$q apk"))
            adapter.submit(items)
            alphaIndex.visibility = View.GONE
        }
    }

    private fun sectionLetterOf(label: String): Char {
        val c = label.firstOrNull()?.uppercaseChar() ?: '#'
        return if (c.isLetter()) c else '#'
    }

    private fun buildSections(list: List<AppEntry>): List<DrawerItem> {
        val out = mutableListOf<DrawerItem>()
        var last = ' '
        for (e in list) {
            val letter = sectionLetterOf(e.label)
            if (letter != last) {
                out.add(DrawerItem.Section(letter))
                last = letter
            }
            out.add(DrawerItem.App(e))
        }
        return out
    }

    private fun buildAlphaIndex(present: Set<Char>) {
        alphaIndex.removeAllViews()
        val accent = ContextCompat.getColor(this, R.color.accent_blue)
        val dim = Color.parseColor("#3A4356")
        for (c in alphaLetters) {
            val tv = TextView(this).apply {
                text = c.toString()
                textSize = 11f
                setTextColor(if (present.contains(c)) accent else dim)
                gravity = Gravity.CENTER
                setPadding(0, 3, 0, 3)
            }
            alphaIndex.addView(tv)
        }
        alphaIndex.setOnTouchListener { v, ev ->
            if (ev.action == MotionEvent.ACTION_DOWN || ev.action == MotionEvent.ACTION_MOVE) {
                val idx = (ev.y / v.height * alphaLetters.size).toInt()
                    .coerceIn(0, alphaLetters.size - 1)
                adapter.sectionPositionFor(alphaLetters[idx])?.let {
                    appList.scrollToPosition(it)
                }
            }
            true
        }
    }

    /**
     * Long-press menu on a drawer app: pin to Start, or hide it from the
     * launcher. The launcher itself can never be hidden (otherwise there'd
     * be no way back to this screen to unhide it).
     */
    private fun showHideMenu(entry: AppEntry) {
        val items = mutableListOf<String>()
        items.add(getString(R.string.app_info))
        val pinned = PinnedPrefs.isPinned(this, entry.packageName)
        items.add(
            getString(
                if (pinned) R.string.unpin_app else R.string.pin_app
            )
        )
        val canHide = entry.packageName != packageName
        if (canHide) items.add(getString(R.string.hide_app))
        if (isUninstallable(this, entry.packageName)) items.add(getString(R.string.uninstall_app))
        AlertDialog.Builder(this, R.style.Theme_CiaLauncher_Dialog)
            .setItems(items.toTypedArray()) { _, which ->
                val picked = items[which]
                when {
                    picked == getString(R.string.app_info) -> openAppInfo(this, entry.packageName)
                    picked == getString(R.string.uninstall_app) ->
                        confirmUninstall(this, entry.packageName, entry.label)
                    picked == getString(R.string.pin_app) -> {
                        PinnedPrefs.addPin(this, entry.packageName)
                        Toast.makeText(
                            this,
                            getString(R.string.app_pinned, entry.label),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    picked == getString(R.string.unpin_app) -> {
                        PinnedPrefs.removePin(this, entry.packageName)
                        Toast.makeText(
                            this,
                            getString(R.string.app_unpinned, entry.label),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    else -> {
                        HiddenAppsPrefs.hide(this, entry.packageName)
                        applyFilter()
                        updateHiddenButton()
                        Toast.makeText(
                            this,
                            getString(R.string.app_hidden, entry.label),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .show()
    }

    /** Hasil uninstall via PackageInstaller API milik launcher sendiri. */
    private val uninstallResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE
            ) ?: PackageInstaller.STATUS_FAILURE
            val pkg = intent?.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    // Sistem minta konfirmasi user — buka dialognya.
                    val confirm = intent?.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    if (confirm != null) startActivity(confirm)
                }
                PackageInstaller.STATUS_SUCCESS -> {
                    Toast.makeText(
                        this@AppDrawerActivity,
                        "Terhapus.",
                        Toast.LENGTH_SHORT
                    ).show()
                    reloadApps()
                }
                else -> {
                    val msg = intent?.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    Toast.makeText(
                        this@AppDrawerActivity,
                        "Gagal menghapus${if (msg != null) ": $msg" else ""}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /** Eye button: dialog listing hidden apps; tapping one unhides it. */
    private fun showHiddenApps() {
        val dialog = HiddenAppsDialog()
        dialog.onChanged = {
            applyFilter()
            updateHiddenButton()
        }
        dialog.show(supportFragmentManager, "hidden_apps")
    }

    /** The eye button only appears when there is something hidden. */
    private fun updateHiddenButton() {
        hiddenBtn.visibility =
            if (HiddenAppsPrefs.getHidden(this).isEmpty()) View.GONE else View.VISIBLE
    }

    private fun launchApp(entry: AppEntry) {        try {
            val intent = entry.launchIntent
                ?: packageManager.getLaunchIntentForPackage(entry.packageName)
            if (intent != null) {
                RecentAppsPrefs.record(this, entry.packageName)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else {
                toastNotFound()
            }
        } catch (_: Exception) {
            toastNotFound()
        }
    }

    private fun webSearch(q: String) {
        try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH)
            intent.putExtra(SearchManager.QUERY, q)
            startActivity(intent)
        } catch (_: Exception) {
            try {
                val view = Intent(
                    Intent.ACTION_VIEW,
                    android.net.Uri.parse(
                        "https://www.google.com/search?q=" +
                            android.net.Uri.encode(q)
                    )
                )
                startActivity(view)
            } catch (_: Exception) {
                toastNotFound()
            }
        }
    }

    private fun toastNotFound() {
        Toast.makeText(this, R.string.app_not_found, Toast.LENGTH_SHORT).show()
    }
}
