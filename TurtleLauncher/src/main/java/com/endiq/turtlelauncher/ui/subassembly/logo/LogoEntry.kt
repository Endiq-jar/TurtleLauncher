package com.endiq.turtlelauncher.ui.subassembly.logo

import java.io.File

/**
 * One row in the logo picker. [file] is null for the built-in default logo
 * (rendered from R.drawable.ic_turtle_full); otherwise it's a file under
 * PathManager.DIR_CUSTOM_LOGO (bundled-pack or user-imported).
 */
data class LogoEntry(
    val file: File?,
    val displayName: String
) {
    /** What's persisted to AllSettings.selectedLogo - empty string for the default. */
    val settingValue: String get() = file?.name ?: ""
}
