package com.movtery.zalithlauncher.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.movtery.anim.AnimPlayer
import com.movtery.anim.animations.Animations
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.databinding.FragmentVersionSeriesDetailBinding
import com.movtery.zalithlauncher.feature.download.SeriesVersionAdapter
import com.movtery.zalithlauncher.feature.download.utils.VersionSeriesUtils
import com.movtery.zalithlauncher.utils.ZHTools
import net.kdt.pojavlaunch.JMinecraftVersionList

/**
 * Per-series drill-down: every version belonging to one series (e.g. everything under
 * "1.21"), grouped into Release/Snapshot section headers - reached by tapping a card in
 * [VersionSelectorFragment]. Beta/Alpha "series" only ever contain one section each, since
 * they were already split off by type before reaching here.
 */
class VersionSeriesDetailFragment : FragmentWithAnim(R.layout.fragment_version_series_detail) {
    companion object {
        const val TAG: String = "VersionSeriesDetailFragment"
        const val BUNDLE_SERIES_LABEL: String = "SERIES_LABEL"
    }

    private lateinit var binding: FragmentVersionSeriesDetailBinding
    private var allRows: List<SeriesVersionAdapter.Row> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentVersionSeriesDetailBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val seriesLabel = arguments?.getString(BUNDLE_SERIES_LABEL) ?: run {
            ZHTools.onBackPressed(requireActivity())
            return
        }

        binding.apply {
            seriesDetailTitle.text = seriesLabel
            versionDetailList.layoutManager = LinearLayoutManager(requireContext())

            allRows = buildRows(seriesLabel)
            render(allRows)

            searchVersionDetail.doAfterTextChanged { text ->
                val query = text?.toString()?.trim().orEmpty()
                render(
                    if (query.isEmpty()) allRows
                    else allRows.filter {
                        it is SeriesVersionAdapter.Row.Header ||
                        (it as SeriesVersionAdapter.Row.VersionRow).version.id.contains(query, ignoreCase = true)
                    }
                )
            }

            returnButtonDetail.setOnClickListener { ZHTools.onBackPressed(requireActivity()) }
        }
    }

    private fun render(rows: List<SeriesVersionAdapter.Row>) {
        binding.versionDetailList.adapter = SeriesVersionAdapter(rows) { versionId ->
            val bundle = Bundle()
            bundle.putString(InstallGameFragment.BUNDLE_MC_VERSION, versionId)
            ZHTools.swapFragmentWithAnim(this, InstallGameFragment::class.java, InstallGameFragment.TAG, bundle)
        }
    }

    /**
     * Beta/Alpha cards land here with every version already of that one type - still run
     * through the same release/snapshot bucketing so the fixed Beta/Alpha "series" get a
     * single matching header instead of a hardcoded one.
     */
    private fun buildRows(seriesLabel: String): List<SeriesVersionAdapter.Row> {
        val allVersions = VersionSeriesUtils.fetchAllVersions()
        val grouped = VersionSeriesUtils.group(allVersions)

        val versions: List<JMinecraftVersionList.Version> = when (seriesLabel) {
            getString(R.string.version_beta) -> grouped.betaVersions
            getString(R.string.version_alpha) -> grouped.alphaVersions
            else -> grouped.seriesCards.find { it.seriesLabel == seriesLabel }?.versions ?: emptyList()
        }

        val releases = versions.filter { it.type == "release" }
        val snapshots = versions.filter { it.type == "snapshot" }
        val betas = versions.filter { it.type == "old_beta" }
        val alphas = versions.filter { it.type == "old_alpha" }

        val rows = mutableListOf<SeriesVersionAdapter.Row>()
        if (releases.isNotEmpty()) {
            rows.add(SeriesVersionAdapter.Row.Header(getString(R.string.generic_release)))
            releases.forEach { rows.add(SeriesVersionAdapter.Row.VersionRow(it, getString(R.string.generic_release))) }
        }
        if (snapshots.isNotEmpty()) {
            rows.add(SeriesVersionAdapter.Row.Header(getString(R.string.version_snapshot)))
            snapshots.forEach { rows.add(SeriesVersionAdapter.Row.VersionRow(it, getString(R.string.version_snapshot))) }
        }
        if (betas.isNotEmpty()) {
            rows.add(SeriesVersionAdapter.Row.Header(getString(R.string.version_beta)))
            betas.forEach { rows.add(SeriesVersionAdapter.Row.VersionRow(it, getString(R.string.version_beta))) }
        }
        if (alphas.isNotEmpty()) {
            rows.add(SeriesVersionAdapter.Row.Header(getString(R.string.version_alpha)))
            alphas.forEach { rows.add(SeriesVersionAdapter.Row.VersionRow(it, getString(R.string.version_alpha))) }
        }
        return rows
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
