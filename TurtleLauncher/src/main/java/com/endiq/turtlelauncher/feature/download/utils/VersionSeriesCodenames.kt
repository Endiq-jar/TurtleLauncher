package com.endiq.turtlelauncher.feature.download.utils

/**
 * Official Java Edition update names, keyed by series label (e.g. "1.21").
 * Returns null for series with no public codename (the new 26.x drop scheme, Beta, Alpha) -
 * the card simply omits the subtitle line in that case.
 */
object VersionSeriesCodenames {
    private val CODENAMES = mapOf(
        "1.21" to "Tricky Trials",
        "1.20" to "Trails & Tales",
        "1.19" to "The Wild Update",
        "1.18" to "Caves & Cliffs: Part II",
        "1.17" to "Caves & Cliffs: Part I",
        "1.16" to "Nether Update",
        "1.15" to "Buzzy Bees",
        "1.14" to "Village & Pillage",
        "1.13" to "Update Aquatic",
        "1.12" to "World of Color Update",
        "1.11" to "Exploration Update",
        "1.10" to "Frostburn Update",
        "1.9" to "Combat Update",
        "1.8" to "Bountiful Update",
        "1.7" to "The Update that Changed the World",
        "1.6" to "Horse Update",
        "1.5" to "Redstone Update",
        "1.4" to "Pretty Scary Update",
        "1.3" to "The \"Have a Cow, Man\" Update",
        "1.2" to "The Jungle Update",
        "1.1" to "The Adventure Update",
        "1.0" to "Release"
    )

    fun of(seriesLabel: String): String? = CODENAMES[seriesLabel]
}
