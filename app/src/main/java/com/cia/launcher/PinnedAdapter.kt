package com.cia.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PinnedAdapter(
    items: List<PinnedApp>,
    private val onClick: (PinnedApp) -> Unit,
    private val onLongClick: (PinnedApp) -> Unit = {}
) : RecyclerView.Adapter<PinnedAdapter.Holder>() {

    private val data = items.toMutableList()

    /** Replace contents (used when icons finish loading off the main thread). */
    fun submitList(newItems: List<PinnedApp>) {
        data.clear()
        data.addAll(newItems)
        notifyDataSetChanged()
    }

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.pinned_icon)
        val label: TextView = v.findViewById(R.id.pinned_label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pinned_app, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val app = data[position]
        holder.icon.setImageDrawable(app.icon)
        holder.label.text = app.label
        holder.itemView.setOnClickListener { onClick(app) }
        holder.itemView.setOnLongClickListener {
            onLongClick(app)
            true
        }
    }

    override fun getItemCount(): Int = data.size
}
