package com.movtery.zalithlauncher.feature.turtle

import com.movtery.zalithlauncher.BuildConfig

/**
 * TurtleLauncher: the home screen's Changelog card describes what shipped in the currently
 * installed build, not "is an update available" (that's [com.movtery.zalithlauncher.feature.update.UpdateUtils],
 * a separate async network check). Kept as a short hand-curated summary here rather than
 * fetched over the network, so the card always has something to show even fully offline.
 */
object HomeChangelog {
    @JvmStatic
    val title: String get() = "v${BuildConfig.VERSION_NAME}"

    @JvmStatic
    val summary: String =
        "Built-in screen recorder, LTW & MobileGlues renderers, AI-assisted crash diagnosis, " +
            "and an in-app log viewer with mclo.gs sharing."
}
