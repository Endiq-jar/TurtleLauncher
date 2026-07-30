package com.movtery.zalithlauncher.ui.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import com.movtery.anim.AnimPlayer
import com.movtery.anim.animations.Animations
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.databinding.FragmentVersionBinding
import com.movtery.zalithlauncher.event.sticky.MinecraftVersionValueEvent
import com.movtery.zalithlauncher.feature.download.SeriesCardAdapter
import com.movtery.zalithlauncher.feature.download.utils.VersionSeriesUtils
import com.movtery.zalithlauncher.utils.ZHTools
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * TurtleLauncher: version-card redesign. This used to host a TabLayout switching between
 * single-type (Release/Snapshot/Beta/Alpha) lists; it's now a grid of series cards (1.21,
 * 1.20, ... plus fixed Beta/Alpha cards), each opening [VersionSeriesDetailFragment] for
 * that one series' categorized version list. See [VersionSeriesUtils] for how a version id
 * gets mapped to its series.
 */
class VersionSelectorFragment : FragmentWithAnim(R.layout.fragment_version) {
    companion object {
        const val TAG: String = "FileSelectorFragment"
    }

    private lateinit var binding: FragmentVersionBinding
    private var allCards: List<SeriesCardAdapter.CardEntry> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentVersionBinding.inflate(layoutInflater)
        return binding.root
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.apply {
            allCards = buildCards()
            seriesGrid.layoutManager = GridLayoutManager(requireContext(), 3)
            renderCards(allCards)

            searchVersion.doAfterTextChanged { text -> applyFilter(text?.toString()) }

            returnButton.setOnClickListener { ZHTools.onBackPressed(requireActivity()) }
        }
    }

    private fun applyFilter(query: String?) {
        val trimmed = query?.trim().orEmpty()
        renderCards(
            if (trimmed.isEmpty()) allCards
            else allCards.filter { it.label.contains(trimmed, ignoreCase = true) }
        )
    }

    /**
     * The manifest this screen's cards are built from (see [VersionSeriesUtils.fetchAllVersions])
     * is fetched asynchronously by AsyncVersionList and can still be downloading - or mid a
     * forced re-download after a stale/corrupt cache - at the moment this screen opens. Without
     * this subscription, [buildCards] at onViewCreated only ever saw whatever was already in the
     * sticky event at that instant, so a newly released Minecraft version (or the very first
     * successful manifest fetch on a fresh install) would never appear here until something else
     * happened to recreate the fragment. `sticky = true` also delivers whatever event is already
     * posted immediately on register, so this one subscription covers both the "still loading"
     * and "already loaded" cases.
     */
    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onVersionListUpdated(event: MinecraftVersionValueEvent) {
        if (!isAdded || view == null) return
        allCards = buildCards()
        applyFilter(binding.searchVersion.text?.toString())
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    private fun renderCards(cards: List<SeriesCardAdapter.CardEntry>) {
        binding.seriesGrid.adapter = SeriesCardAdapter(cards) { card ->
            val bundle = Bundle()
            bundle.putString(VersionSeriesDetailFragment.BUNDLE_SERIES_LABEL, card.label)
            ZHTools.swapFragmentWithAnim(this, VersionSeriesDetailFragment::class.java, VersionSeriesDetailFragment.TAG, bundle)
        }
    }

    private fun buildCards(): List<SeriesCardAdapter.CardEntry> {
        val grouped = VersionSeriesUtils.group(VersionSeriesUtils.fetchAllVersions())
        val newestSeriesLabel = grouped.seriesCards.firstOrNull()?.seriesLabel

        val cards = mutableListOf<SeriesCardAdapter.CardEntry>()
        grouped.seriesCards.forEach { series ->
            cards.add(
                SeriesCardAdapter.CardEntry(
                    label = series.seriesLabel,
                    versionCount = series.versions.size,
                    iconRes = R.drawable.ic_minecraft,
                    isLatest = series.seriesLabel == newestSeriesLabel,
                    versions = series.versions
                )
            )
        }
        if (grouped.betaVersions.isNotEmpty()) {
            cards.add(
                SeriesCardAdapter.CardEntry(
                    label = getString(R.string.version_beta),
                    versionCount = grouped.betaVersions.size,
                    iconRes = R.drawable.ic_old_cobblestone,
                    isLatest = false,
                    versions = grouped.betaVersions
                )
            )
        }
        if (grouped.alphaVersions.isNotEmpty()) {
            cards.add(
                SeriesCardAdapter.CardEntry(
                    label = getString(R.string.version_alpha),
                    versionCount = grouped.alphaVersions.size,
                    iconRes = R.drawable.ic_old_grass_block,
                    isLatest = false,
                    versions = grouped.alphaVersions
                )
            )
        }
        return cards
    }

    override fun slideIn(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.versionLayout, Animations.BounceInDown))
            .apply(AnimPlayer.Entry(binding.operateLayout, Animations.BounceInLeft))
    }

    override fun slideOut(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.versionLayout, Animations.FadeOutUp))
            .apply(AnimPlayer.Entry(binding.operateLayout, Animations.FadeOutRight))
    }
}
