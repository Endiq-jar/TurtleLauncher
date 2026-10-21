package com.endiq.turtlelauncher.ui.subassembly.logo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.ui.view.AnimTextView
import com.endiq.turtlelauncher.ui.view.AnimatedIconView

/**
 * Backs the logo picker's list (Discord "App Icon" settings style): a thumbnail,
 * a name, and a checkmark on whichever row is currently selected. Selecting a row
 * applies it immediately via [onSelect] - there's no separate confirm step and no
 * per-row rename/share/delete menu.
 */
class LogoEntryAdapter(
    private val items: MutableList<LogoEntry>,
    private val thumbnailCornerRadiusPx: Int,
    private var selectedValue: String,
    private val onSelect: (LogoEntry) -> Unit
) : RecyclerView.Adapter<LogoEntryAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_logo_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun replaceAll(newItems: List<LogoEntry>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    /** Called after AllSettings.selectedLogo has already been persisted by the caller. */
    private fun applySelection(entry: LogoEntry) {
        if (entry.settingValue == selectedValue) return
        val previousValue = selectedValue
        selectedValue = entry.settingValue
        items.forEachIndexed { index, item ->
            if (item.settingValue == previousValue || item.settingValue == selectedValue) {
                notifyItemChanged(index)
            }
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumb: ImageView = itemView.findViewById(R.id.logo_thumb)
        private val name: AnimTextView = itemView.findViewById(R.id.logo_name)
        private val check: AnimatedIconView = itemView.findViewById(R.id.logo_selected_check)

        fun bind(entry: LogoEntry) {
            name.text = entry.displayName
            val isSelected = entry.settingValue == selectedValue
            check.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
            itemView.setBackgroundResource(
                if (isSelected) R.drawable.background_item_selected else R.drawable.background_item_unselected
            )

            val glide = Glide.with(thumb)
            if (entry.file != null) {
                glide.load(entry.file).transform(RoundedCorners(thumbnailCornerRadiusPx)).into(thumb)
            } else {
                glide.load(R.drawable.ic_turtle_full).transform(RoundedCorners(thumbnailCornerRadiusPx)).into(thumb)
            }

            itemView.setOnClickListener {
                onSelect(entry)
                applySelection(entry)
            }
        }
    }
}
