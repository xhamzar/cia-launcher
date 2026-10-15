package com.cia.launcher

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Minimal dialog listing hidden apps. Tapping one unhides it.
 * Reuses the drawer row layout for a consistent look.
 */
class HiddenAppsDialog : DialogFragment() {

    /** Called after any unhide so the host can refresh its list. */
    var onChanged: (() -> Unit)? = null

    private lateinit var adapter: HiddenAdapter
    private lateinit var emptyView: TextView

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_app_picker, null)
        val list = view.findViewById<RecyclerView>(R.id.picker_list)
        val search = view.findViewById<android.widget.EditText>(R.id.picker_search)
        val title = view.findViewById<TextView>(R.id.picker_title)

        title.text = getString(R.string.hidden_apps_title)
        // No search needed for a short hidden list — hide the field.
        search.visibility = View.GONE

        emptyView = TextView(requireContext()).apply {
            text = getString(R.string.hidden_apps_empty)
            setTextColor(android.graphics.Color.parseColor("#9AA3B5"))
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        adapter = HiddenAdapter { entry ->
            HiddenAppsPrefs.unhide(requireContext(), entry.packageName)
            Toast.makeText(
                requireContext(),
                getString(R.string.app_unhidden, entry.label),
                Toast.LENGTH_SHORT
            ).show()
            onChanged?.invoke()
            loadHidden()
        }
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        loadHidden()

        // Overlay the empty-state text centered over the dialog content.
        val container = android.widget.FrameLayout(requireContext()).apply {
            addView(view)
            addView(emptyView, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            ))
        }

        return AlertDialog.Builder(requireContext(), R.style.Theme_CiaLauncher_Dialog)
            .setView(container)
            .create()
    }

    private fun loadHidden() {
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = requireContext().packageManager
                HiddenAppsPrefs.getHidden(requireContext()).mapNotNull { pkg ->
                    AppRepository.resolveCustom(pm, pkg)?.let { pinned ->
                        AppEntry(
                            label = pinned.label,
                            packageName = pkg,
                            icon = pinned.icon,
                            launchIntent = pinned.launchIntent
                        )
                    }
                }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            }
            adapter.submit(apps)
            emptyView.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private class HiddenAdapter(
        private val onClick: (AppEntry) -> Unit
    ) : RecyclerView.Adapter<DrawerAdapter.AppHolder>() {

        private var items: List<AppEntry> = emptyList()

        fun submit(newItems: List<AppEntry>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): DrawerAdapter.AppHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_drawer_row, parent, false)
            return DrawerAdapter.AppHolder(v)
        }

        override fun onBindViewHolder(holder: DrawerAdapter.AppHolder, position: Int) {
            holder.bind(items[position], false, onClick)
        }

        override fun getItemCount(): Int = items.size
    }
}
