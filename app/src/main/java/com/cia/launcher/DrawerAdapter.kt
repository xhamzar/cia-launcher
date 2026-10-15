package com.cia.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

sealed class DrawerItem {
    data class Section(val letter: Char) : DrawerItem()
    data class App(val entry: AppEntry, val showSubtitle: Boolean = false) : DrawerItem()
    data class Suggestion(val text: String, val sub: String, val query: String) : DrawerItem()
}

class DrawerAdapter(
    private val onAppClick: (AppEntry) -> Unit,
    private val onAppLongClick: (AppEntry) -> Unit = {},
    private val onSuggestionClick: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<DrawerItem> = emptyList()

    fun submit(newItems: List<DrawerItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    /** Adapter position of the section header for a letter, or null. */
    fun sectionPositionFor(letter: Char): Int? {
        items.forEachIndexed { i, it ->
            if (it is DrawerItem.Section && it.letter == letter) return i
        }
        return null
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is DrawerItem.Section -> 0
        is DrawerItem.App -> 1
        is DrawerItem.Suggestion -> 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> SectionHolder(inf.inflate(R.layout.item_drawer_section, parent, false))
            2 -> SuggestionHolder(inf.inflate(R.layout.item_drawer_row, parent, false))
            else -> AppHolder(inf.inflate(R.layout.item_drawer_row, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val it = items[position]) {
            is DrawerItem.Section -> (holder as SectionHolder).bind(it.letter)
            is DrawerItem.App -> (holder as AppHolder).bind(
                it.entry, it.showSubtitle, onAppClick, onAppLongClick
            )
            is DrawerItem.Suggestion ->
                (holder as SuggestionHolder).bind(it, onSuggestionClick)
        }
    }

    override fun getItemCount(): Int = items.size

    class SectionHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val letter: TextView = v.findViewById(R.id.section_letter)
        fun bind(c: Char) {
            letter.text = c.toString()
        }
    }

    class AppHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val icon: ImageView = v.findViewById(R.id.app_icon)
        private val name: TextView = v.findViewById(R.id.app_name)
        private val subtitle: TextView = v.findViewById(R.id.app_subtitle)
        fun bind(
            entry: AppEntry,
            showSubtitle: Boolean,
            onClick: (AppEntry) -> Unit,
            onLongClick: (AppEntry) -> Unit = {}
        ) {
            icon.setImageDrawable(entry.icon)
            name.text = entry.label
            subtitle.visibility = if (showSubtitle) View.VISIBLE else View.GONE
            itemView.setOnClickListener { onClick(entry) }
            itemView.setOnLongClickListener {
                onLongClick(entry)
                true
            }
        }
    }

    class SuggestionHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val icon: ImageView = v.findViewById(R.id.app_icon)
        private val name: TextView = v.findViewById(R.id.app_name)
        private val subtitle: TextView = v.findViewById(R.id.app_subtitle)
        fun bind(s: DrawerItem.Suggestion, onClick: (String) -> Unit) {
            icon.setImageResource(R.drawable.ic_search)
            name.text = s.text
            subtitle.text = s.sub
            subtitle.visibility = View.VISIBLE
            itemView.setOnClickListener { onClick(s.query) }
        }
    }
}
