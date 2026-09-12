package com.endiq.zalithlauncher.feature.download.enums

import com.endiq.zalithlauncher.feature.download.platform.AbstractPlatformHelper
import com.endiq.zalithlauncher.feature.download.platform.modrinth.ModrinthHelper

enum class Platform(val pName: String, val helper: AbstractPlatformHelper) {
    MODRINTH("Modrinth", ModrinthHelper())
}
