package com.cia.launcher

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Simple searchable app picker used to customize a home-screen taskbar slot.
 * Shows every installed app (icon + name); tapping one returns its package name.
 */
class AppPickerDialog : DialogFragment() {

    var onAppPicked: ((packageName: String) -> Unit)? = null

    private lateinit var adapter: PickerAdapter
    private var allApps: List<AppEntry> = emptyList()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_app_picker, null)
        val list = view.findViewById<RecyclerView>(R.id.picker_list)
        val search = view.findViewById<EditText>(R.id.picker_search)

        adapter = PickerAdapter { entry ->
            onAppPicked?.invoke(entry.packageName)
            dismiss()
        }
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filter(s?.toString().orEmpty())
            }
        })

        // Snapshot + icon cache make this cheap after the first load.
        // Hidden apps are excluded so they don't leak through the picker.
        lifecycleScope.launch {
            val hidden = HiddenAppsPrefs.getHidden(requireContext())
            val apps = withContext(Dispatchers.IO) {
                AppRepository.loadAll(requireContext().packageManager, hidden)
            }
            allApps = apps
            adapter.submit(apps)
        }

        return AlertDialog.Builder(requireContext(), R.style.Theme_CiaLauncher_Dialog)
            .setView(view)
            .create()
    }

    private fun filter(query: String) {
        val q = query.trim().lowercase()
        adapter.submit(
            if (q.isEmpty()) allApps
            else allApps.filter { it.label.lowercase().contains(q) }
        )
    }

    private class PickerAdapter(
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
