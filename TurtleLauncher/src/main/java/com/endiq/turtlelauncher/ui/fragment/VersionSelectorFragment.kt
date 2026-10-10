package com.endiq.turtlelauncher.ui.fragment
import com.endiq.turtlelauncher.utils.anim.TurtleTransitions

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.endiq.anim.AnimPlayer
import com.endiq.anim.animations.Animations
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.databinding.FragmentVersionBinding
import com.endiq.turtlelauncher.event.sticky.MinecraftVersionValueEvent
import com.endiq.turtlelauncher.feature.download.SeriesCardAdapter
import com.endiq.turtlelauncher.feature.download.utils.VersionSeriesUtils
import com.endiq.turtlelauncher.utils.ZHTools
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class VersionSelectorFragment : FragmentWithAnim(R.layout.fragment_version) {
    companion object {
        const val TAG: String = "FileSelectorFragment"
    }

    private var _binding: FragmentVersionBinding? = null
    private val binding: FragmentVersionBinding
        get() = checkNotNull(_binding) { "VersionSelectorFragment view is not available" }

    private var allCards: List<SeriesCardAdapter.CardEntry> = emptyList()
    private lateinit var cardsAdapter: SeriesCardAdapter
    private var viewScope: CoroutineScope? = null
    private var buildCardsJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVersionBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        binding.apply {
            cardsAdapter = SeriesCardAdapter { card ->
                val bundle = Bundle().apply {
                    putString(VersionSeriesDetailFragment.BUNDLE_SERIES_LABEL, card.label)
                }
                ZHTools.swapFragmentWithAnim(
                    this@VersionSelectorFragment,
                    VersionSeriesDetailFragment::class.java,
                    VersionSeriesDetailFragment.TAG,
                    bundle
                )
            }
            seriesGrid.layoutManager = GridLayoutManager(requireContext(), 3)
            seriesGrid.adapter = cardsAdapter

            searchVersion.doAfterTextChanged { text -> applyFilter(text?.toString()) }
            returnButton.setOnClickListener { ZHTools.onBackPressed(requireActivity()) }
        }
        rebuildCardsAsync()
    }

    private fun applyFilter(query: String?) {
        if (_binding == null || !::cardsAdapter.isInitialized) return
        val trimmed = query?.trim().orEmpty()
        val filtered = if (trimmed.isEmpty()) allCards
            else allCards.filter { it.label.contains(trimmed, ignoreCase = true) }
        // AsyncListDiffer does the diff away from the UI thread. Do not recreate the adapter
        // or rerun list-entry animations for every keystroke.
        cardsAdapter.submitList(filtered)
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onVersionListUpdated(event: MinecraftVersionValueEvent) {
        if (!isAdded || _binding == null) return
        rebuildCardsAsync()
    }

    private fun rebuildCardsAsync() {
        val scope = viewScope ?: return
        val currentBinding = _binding ?: return
        buildCardsJob?.cancel()
        // Snapshot the event payload and localized labels on the main thread; grouping/sorting
        // the full Minecraft version list happens on Dispatchers.Default.
        val versions = VersionSeriesUtils.fetchAllVersions()
        val betaLabel = getString(R.string.version_beta)
        val alphaLabel = getString(R.string.version_alpha)
        val searchText = currentBinding.searchVersion.text?.toString()
        buildCardsJob = scope.launch {
            val built = withContext(Dispatchers.Default) {
                buildCards(versions, betaLabel, alphaLabel)
            }
            if (_binding == null) return@launch
            allCards = built
            // Read the live query after the background grouping completes so typing while the
            // job runs doesn't restore an old filter value.
            applyFilter(_binding?.searchVersion?.text?.toString() ?: searchText)
        }
    }

    override fun onDestroyView() {
        buildCardsJob?.cancel()
        buildCardsJob = null
        viewScope?.cancel()
        viewScope = null
        buildCardsJob = null
        allCards = emptyList()
        _binding = null
        super.onDestroyView()
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    private fun buildCards(
        versions: List<net.endiq.launcher.JMinecraftVersionList.Version>,
        betaLabel: String,
        alphaLabel: String
    ): List<SeriesCardAdapter.CardEntry> {
        val grouped = VersionSeriesUtils.group(versions)
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
                    label = betaLabel,
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
                    label = alphaLabel,
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
        val currentBinding = _binding ?: return
        animPlayer.apply(AnimPlayer.Entry(currentBinding.versionLayout, TurtleTransitions.enter()))
            .apply(AnimPlayer.Entry(currentBinding.operateLayout, TurtleTransitions.enter()))
    }

    override fun slideOut(animPlayer: AnimPlayer) {
        val currentBinding = _binding ?: return
        animPlayer.apply(AnimPlayer.Entry(currentBinding.versionLayout, TurtleTransitions.exit()))
            .apply(AnimPlayer.Entry(currentBinding.operateLayout, TurtleTransitions.exit()))
    }
}
