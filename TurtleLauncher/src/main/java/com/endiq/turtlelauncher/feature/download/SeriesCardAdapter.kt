package com.endiq.turtlelauncher.feature.download

import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.feature.download.utils.SeriesBannerResolver
import com.endiq.turtlelauncher.feature.download.utils.VersionSeriesCodenames
import com.endiq.turtlelauncher.utils.anim.AnimatedLogo


/**
 * One card per Minecraft version series (e.g. "1.21", "26.2"), plus the two fixed
 * Beta/Alpha era cards. Tapping a card hands its full version list to
 * [com.endiq.turtlelauncher.ui.fragment.VersionSeriesDetailFragment].
 */
class SeriesCardAdapter(
    private val cards: List<CardEntry>,
    private val onCardClick: (CardEntry) -> Unit
) : RecyclerView.Adapter<SeriesCardAdapter.ViewHolder>() {

    /** A card is either a numbered series or one of the two fixed era buckets. */
    data class CardEntry(
        val label: String,
        val versionCount: Int,
        val iconRes: Int,
        val isLatest: Boolean,
        val versions: List<net.endiq.launcher.JMinecraftVersionList.Version>
    )

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val banner: com.google.android.material.imageview.ShapeableImageView = view.findViewById(R.id.series_banner)
        val label: android.widget.TextView = view.findViewById(R.id.series_label)
        val codename: android.widget.TextView = view.findViewById(R.id.series_codename)
        val count: android.widget.TextView = view.findViewById(R.id.series_count)
        val latestBadge: android.widget.TextView = view.findViewById(R.id.series_latest_badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_version_series_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val card = cards[position]

        val art = SeriesBannerResolver.resolveArt(holder.itemView.context, card)
        holder.banner.setImageDrawable(art.drawable)
        // The watermarked series icon (grass block on release series, classic
        // cobblestone/grass on the Beta/Alpha cards) animates only while the
        // card is hovered, pressed or focused - see AnimatedLogo.
        val layers = art.drawable as? LayerDrawable
        if (layers != null && art.iconLayerIndex >= 0 && AnimatedLogo.isAnimatedLogo(card.iconRes)) {
            AnimatedLogo.bindWatermark(holder.banner, layers, art.iconLayerIndex, card.iconRes)
        } else {
            AnimatedLogo.clear(holder.banner)
        }
        holder.label.text = card.label

        val codename = VersionSeriesCodenames.of(card.label)
        holder.codename.visibility = if (codename != null) View.VISIBLE else View.GONE
        holder.codename.text = codename ?: ""

        holder.count.text = holder.itemView.resources.getQuantityString(
            R.plurals.version_series_count, card.versionCount, card.versionCount
        )
        holder.latestBadge.visibility = if (card.isLatest) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener { onCardClick(card) }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        AnimatedLogo.clear(holder.banner)
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = cards.size
}
