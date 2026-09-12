package com.endiq.zalithlauncher.ui.fragment.download.addon

import com.endiq.zalithlauncher.R
import com.endiq.zalithlauncher.feature.mod.modloader.FabricLikeUtils

class DownloadFabricFragment : DownloadFabricLikeFragment(FabricLikeUtils.FABRIC_UTILS, R.drawable.ic_fabric) {
    companion object {
        const val TAG: String = "DownloadFabricFragment"
    }
}