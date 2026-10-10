package com.endiq.turtlelauncher.feature.download

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.endiq.turtlelauncher.R

/**
 * One card per Minecraft version series (for example "1.21" or "26.2"), plus the fixed
 * Beta/Alpha buckets. ListAdapter performs diffs away from the UI thread and reuses existing
 * holders instead of replacing the whole adapter on each search keystroke.
 */
class SeriesCardAdapter(
    private val onCardClick: (CardEntry) -> Unit
) : ListAdapter<SeriesCardAdapter.CardEntry, SeriesCardAdapter.ViewHolder>(DIFF_CALLBACK) {

    data class CardEntry(
        val label: String,
        val versionCount: Int,
        val iconRes: Int,
        val isLatest: Boolean,
        val versions: List<net.endiq.launcher.JMinecraftVersionList.Version>
    )

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.series_icon)
        val label: TextView = view.findViewById(R.id.series_label)
        val count: TextView = view.findViewById(R.id.series_count)
        val latestBadge: TextView = view.findViewById(R.id.series_latest_badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_version_series_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val card = getItem(position)
        holder.icon.setImageResource(card.iconRes)
        holder.label.text = card.label
        holder.count.text = holder.itemView.resources.getQuantityString(
            R.plurals.version_series_count, card.versionCount, card.versionCount
        )
        holder.latestBadge.visibility = if (card.isLatest) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener { onCardClick(card) }
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<CardEntry>() {
            override fun areItemsTheSame(oldItem: CardEntry, newItem: CardEntry): Boolean =
                oldItem.label == newItem.label

            override fun areContentsTheSame(oldItem: CardEntry, newItem: CardEntry): Boolean =
                oldItem.versionCount == newItem.versionCount &&
                    oldItem.iconRes == newItem.iconRes &&
                    oldItem.isLatest == newItem.isLatest &&
                    oldItem.versions.map { it.id } == newItem.versions.map { it.id }
        }
    }
}
