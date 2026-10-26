package com.endiq.turtlelauncher.ui.fragment

import com.endiq.turtlelauncher.feature.download.enums.Classify

/**
 * The single source of truth for the Download screen's tab ↔ page mapping.
 *
 * `fragment_download.xml` lays out one `DslTabLayout` child per entry of
 * [entries], in this exact order, and `DownloadFragment`'s `ViewPager2` creates
 * one fragment per position from the same list. Before this existed the two were
 * written independently, and they had drifted: the layout put *Shader Pack* in
 * the fourth slot and *World* in the fifth, while [Classify] numbers World as 3
 * and Shader Pack as 4. Tapping "Download World" therefore opened page 4 (the
 * shader page), and tapping "Download Shader Pack" opened page 3 (the world
 * page) - which on Modrinth has no backing project type and errors out, so both
 * tabs looked broken.
 *
 * `tests/check_download_tabs.py` reads the layout and fails if the tab order
 * ever drifts from this list again.
 */
object DownloadPages {

    enum class Page(val classify: Classify, val tabId: String) {
        MODS(Classify.MOD, "mod_image"),
        MODPACKS(Classify.MODPACK, "modpack_image"),
        RESOURCE_PACKS(Classify.RESOURCE_PACK, "resource_pack_image"),
        WORLDS(Classify.WORLD, "world_image"),
        SHADER_PACKS(Classify.SHADER_PACK, "shader_pack_image")
    }

    /** Tab order, left to right, and ViewPager page order. */
    @JvmField
    val entries: List<Page> = Page.entries

    /** The tab/page index for a classify value - the inverse of [classifyAt]. */
    fun indexOf(classify: Classify): Int = entries.indexOfFirst { it.classify == classify }

    /** The classify shown at a tab/page [index]; null when out of range. */
    fun classifyAt(index: Int): Classify? = entries.getOrNull(index)?.classify

    /**
     * Modrinth exposes no world/archive project type (see
     * `ModrinthHelper.searchWorld`), so the World tab can only ever offer
     * "install from a local .zip". Keep that knowledge next to the mapping so
     * the UI can explain itself instead of showing a generic search failure.
     */
    fun supportsRemoteSearch(classify: Classify): Boolean = classify != Classify.WORLD
}
