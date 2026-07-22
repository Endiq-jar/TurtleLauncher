package com.movtery.zalithlauncher.ui.fragment.download.addon

import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.feature.mod.modloader.FabricLikeUtils

class DownloadQuiltFragment : DownloadFabricLikeFragment(FabricLikeUtils.QUILT_UTILS, R.drawable.ic_quilt) {
    companion object {
        const val TAG: String = "DownloadQuiltFragment"
    }
}