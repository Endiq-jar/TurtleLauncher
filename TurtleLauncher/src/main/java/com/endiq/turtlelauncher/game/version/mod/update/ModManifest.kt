package com.endiq.turtlelauncher.game.version.mod.update

import com.endiq.turtlelauncher.game.download.assets.platform.PlatformVersion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Manifest needed for updating mods
 * @param new the fetched new mod version info
 */
data class ModManifest(
    val data: ModData,
    val new: PlatformVersion,
)

/**
 * Returns all new mod version info in the manifest
 */
fun List<ModManifest>.allNews() = map { it.new }

fun List<ModManifest>.toSelectableList() = map { manifest ->
    SelectableModManifest(
        data = manifest.data,
        new = manifest.new,
    )
}

/**
 * An update mod manifest that can record selection state
 * @see ModManifest
 * @property selected whether this entry is selected
 */
class SelectableModManifest(
    val data: ModData,
    val new: PlatformVersion,
) {
    private val _selected = MutableStateFlow(true)
    val selected = _selected.asStateFlow()

    fun updateSelected(value: Boolean) {
        _selected.update { value }
    }

    fun selected() = _selected.value
}

fun List<SelectableModManifest>.toFinalList() = mapNotNull { manifest ->
    if (manifest.selected()) {
        ModManifest(
            data = manifest.data,
            new = manifest.new,
        )
    } else null
}