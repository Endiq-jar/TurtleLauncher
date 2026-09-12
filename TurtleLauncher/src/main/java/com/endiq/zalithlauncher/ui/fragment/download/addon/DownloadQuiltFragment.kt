package com.endiq.zalithlauncher.ui.fragment.download.addon

import com.endiq.zalithlauncher.R
import com.endiq.zalithlauncher.feature.mod.modloader.FabricLikeUtils

class DownloadQuiltFragment : DownloadFabricLikeFragment(FabricLikeUtils.QUILT_UTILS, R.drawable.ic_quilt) {
    companion object {
        const val TAG: String = "DownloadQuiltFragment"
    }
}