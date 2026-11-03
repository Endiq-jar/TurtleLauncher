package com.endiq.turtlelauncher.feature.download.enums

import com.endiq.turtlelauncher.feature.download.platform.AbstractPlatformHelper
import com.endiq.turtlelauncher.feature.download.platform.curseforge.CurseForgeHelper
import com.endiq.turtlelauncher.feature.download.platform.modrinth.ModrinthHelper

/**
 * Download sources offered in the Download screen's platform spinner.
 *
 * Both platforms stay available - CurseForge is an addition, not a replacement. Two tabs
 * are platform-limited on purpose:
 *  - Modrinth has no world/archive downloads (pre-existing), and
 *  - CurseForge has no Modrinth-style "shader" project type for every category.
 * Each platform throws [com.endiq.turtlelauncher.feature.download.platform.PlatformNotSupportedException]
 * with a human-readable reason for those, and the screen shows that reason verbatim.
 */
enum class Platform(val pName: String, val helper: AbstractPlatformHelper) {
    MODRINTH("Modrinth", ModrinthHelper()),
    CURSEFORGE("CurseForge", CurseForgeHelper())
}
