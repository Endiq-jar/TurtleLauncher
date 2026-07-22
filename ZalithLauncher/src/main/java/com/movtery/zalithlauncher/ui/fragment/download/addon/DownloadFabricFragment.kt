package com.movtery.zalithlauncher.ui.fragment.download.addon

import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.feature.mod.modloader.FabricLikeUtils

class DownloadFabricFragment : DownloadFabricLikeFragment(FabricLikeUtils.FABRIC_UTILS, R.drawable.ic_fabric) {
    companion object {
        const val TAG: String = "DownloadFabricFragment"
    }
}