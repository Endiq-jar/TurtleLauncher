package com.endiq.zalithlauncher.feature.download

import androidx.lifecycle.ViewModel
import com.endiq.zalithlauncher.feature.download.item.InfoItem
import com.endiq.zalithlauncher.feature.download.platform.AbstractPlatformHelper


class InfoViewModel : ViewModel() {
    var platformHelper: AbstractPlatformHelper? = null
    var infoItem: InfoItem? = null
}