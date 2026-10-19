package com.cia.launcher

import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class StartMenuSheet : BottomSheetDialogFragment() {

    private lateinit var pinnedAdapter: PinnedAdapter
    private lateinit var suggestedList: LinearLayout
    private lateinit var suggestedEmpty: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_start_menu, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // UI shows INSTANTLY — the grid starts empty and fills in when the
        // (slow) PackageManager work finishes on a background thread.
        val grid = view.findViewById<RecyclerView>(R.id.pinned_grid)
        grid.layoutManager = GridLayoutManager(requireContext(), 4)
        grid.setHasFixedSize(true)
        pinnedAdapter = PinnedAdapter(
            emptyList(),
            onClick = { app -> launchApp(app) },
            onLongClick = { app -> showAppMenu(app) }
        )
        grid.adapter = pinnedAdapter

        view.findViewById<View>(R.id.btn_all_pinned).setOnClickListener {
            openDrawer(focusSearch = false)
        }
        view.findViewById<View>(R.id.search_fake).setOnClickListener {
            openDrawer(focusSearch = true)
        }
        view.findViewById<View>(R.id.btn_more_suggested).setOnClickListener {
            openDrawer(focusSearch = false)
        }

        suggestedList = view.findViewById(R.id.suggested_list)
        suggestedEmpty = view.findViewById(R.id.suggested_empty)

        setupSwipeUpToDrawer(view)
        loadContent()
    }

    override fun onResume() {
        super.onResume()
        // Refresh in case pins/hidden set changed while the drawer was open.
        loadContent()
    }

    /** (Re)load pinned grid + suggested rows off the main thread. */
    private fun loadContent() {
        val view = view ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = requireContext()
            val pm = ctx.packageManager
            val hidden = HiddenAppsPrefs.getHidden(ctx)

            // First run: compute defaults from installed apps and persist.
            var pinnedPkgs = PinnedPrefs.getPinned(ctx)
            if (pinnedPkgs == null) {
                pinnedPkgs = withContext(Dispatchers.IO) {
                    AppRepository.defaultPinnedPkgs(pm)
                }
                PinnedPrefs.setPinned(ctx, pinnedPkgs)
            }

            val pinned = withContext(Dispatchers.IO) {
                AppRepository.pinnedApps(pm, pinnedPkgs, hidden)
            }
            if (isAdded) pinnedAdapter.submitList(pinned)

            val recent = withContext(Dispatchers.IO) {
                AppRepository.recentApps(ctx, pm, hidden)
            }
            if (!isAdded) return@launch
            suggestedList.removeAllViews()
            if (recent.isEmpty()) {
                suggestedEmpty.visibility = View.VISIBLE
            } else {
                suggestedEmpty.visibility = View.GONE
                for (s in recent) {
                    addSuggested(suggestedList, s.name, s.time, s.app)
                }
            }
        }
    }

    /** Long-press on a pinned app: offer to unpin it. */
    /**
     * Long-press menu on a pinned app: app info (opens system Settings),
     * unpin from Start, and uninstall (non-system apps only).
     */
    private fun showAppMenu(app: PinnedApp) {
        val pkg = app.packageName ?: return
        val ctx = requireContext()
        val items = mutableListOf<String>()
        items.add(getString(R.string.app_info))
        items.add(getString(R.string.unpin_app))
        if (isUninstallable(ctx, pkg)) items.add(getString(R.string.uninstall_app))
        AlertDialog.Builder(ctx, R.style.Theme_CiaLauncher_Dialog)
            .setItems(items.toTypedArray()) { _, which ->
                when (items[which]) {
                    getString(R.string.app_info) -> openAppInfo(ctx, pkg)
                    getString(R.string.unpin_app) -> {
                        PinnedPrefs.removePin(ctx, pkg)
                        loadContent()
                        Toast.makeText(
                            ctx,
                            getString(R.string.app_unpinned, app.label),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    getString(R.string.uninstall_app) ->
                        confirmUninstall(ctx, pkg, app.label)
                }
            }
            .show()
    }

    private fun openDrawer(focusSearch: Boolean) {
        val intent = Intent(requireContext(), AppDrawerActivity::class.java)
        intent.putExtra(AppDrawerActivity.EXTRA_FOCUS_SEARCH, focusSearch)
        startActivity(intent)
        // Drawer rises from the bottom like a sheet; the activity underneath stays put.
        requireActivity().overridePendingTransition(R.anim.slide_in_up, R.anim.stay)
        dismiss()
    }

    /**
     * Swipe up while the start menu is open -> jump straight to the full app
     * drawer. Only fires when the menu content cannot scroll any further
     * down, so normal scrolling of the menu is never hijacked. Attached to
     * both the scroll container and the pinned grid (which doesn't scroll
     * itself) so the gesture works from anywhere in the menu.
     */
    private fun setupSwipeUpToDrawer(root: View) {
        val scrollView = root as? NestedScrollView ?: return
        val vc = ViewConfiguration.get(requireContext())
        val density = resources.displayMetrics.density
        val minDistance = 100 * density
        val minVelocity = vc.scaledMinimumFlingVelocity * 4
        val detector = GestureDetector(requireContext(),
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
                        velocityY < -minVelocity && !scrollView.canScrollVertically(1)
                    ) {
                        openDrawer(focusSearch = false)
                        return true
                    }
                    return false
                }
            })
        val touchListener = View.OnTouchListener { _, event ->
            detector.onTouchEvent(event)
            false // never consume: menu scrolling & taps keep working
        }
        scrollView.setOnTouchListener(touchListener)
        root.findViewById<View>(R.id.pinned_grid)?.setOnTouchListener(touchListener)
    }

    /**
     * Adds a "Disarankan" row backed by a real recently-used app.
     * Tapping launches the app and records it as recent.
     */
    private fun addSuggested(
        parent: LinearLayout,
        name: String,
        time: String,
        app: PinnedApp
    ) {
        val row = layoutInflater.inflate(R.layout.item_suggested, parent, false)
        row.findViewById<TextView>(R.id.suggested_name).text = name
        row.findViewById<TextView>(R.id.suggested_time).text = time
        row.findViewById<ImageView>(R.id.suggested_icon).setImageDrawable(app.icon)
        row.isClickable = true
        row.isFocusable = true
        row.setOnClickListener {
            launchApp(app)
        }
        parent.addView(row)
    }

    private fun launchApp(app: PinnedApp) {
        val intent = app.launchIntent
        if (intent == null) {
            toastNotFound()
            return
        }
        try {
            RecentAppsPrefs.record(requireContext(), app.packageName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            dismiss()
        } catch (_: Exception) {
            toastNotFound()
        }
    }

    private fun toastNotFound() {
        Toast.makeText(requireContext(), R.string.app_not_found, Toast.LENGTH_SHORT).show()
    }

    override fun onStart() {
        super.onStart()
        // Solid dark sheet (matches system UI) — standard dim.
        dialog?.window?.let { w ->
            w.setDimAmount(0.5f)
        }
        dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let {
            it.layoutParams.height = (resources.displayMetrics.heightPixels * 0.88).toInt()
            it.requestLayout()
            BottomSheetBehavior.from(it).state = BottomSheetBehavior.STATE_EXPANDED
        }
    }
}
