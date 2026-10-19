package com.endiq.turtlelauncher.feature.version.pin

import com.endiq.turtlelauncher.feature.version.Version
import com.endiq.turtlelauncher.feature.version.VersionsManager

/**
 * Pin/unpin/reorder for the version list, independent of (and layered on top of) the favorite
 * groups in FavoritesVersionUtils. Pinned versions always sort first, in the order stored here;
 * everything else keeps VersionsManager's existing sort.
 */
object PinnedVersionUtils {
    private inline fun modifyPinned(action: (MutableList<String>) -> Unit) {
        VersionsManager.currentGameInfo.apply {
            action(pinnedVersions)
            saveCurrentInfo()
        }
    }

    fun isPinned(versionName: String): Boolean =
        VersionsManager.currentGameInfo.pinnedVersions.contains(versionName)

    /** Pins to the bottom of the pinned section if not already pinned; no-op if already pinned. */
    fun pin(versionName: String) = modifyPinned { list ->
        if (!list.contains(versionName)) list.add(versionName)
    }

    fun unpin(versionName: String) = modifyPinned { list ->
        list.remove(versionName)
    }

    fun togglePin(versionName: String) {
        if (isPinned(versionName)) unpin(versionName) else pin(versionName)
    }

    /** Called when a version is renamed elsewhere, so a pin survives the rename. */
    fun renameVersion(oldName: String, newName: String) = modifyPinned { list ->
        val index = list.indexOf(oldName)
        if (index >= 0) list[index] = newName
    }

    /** @return the stored pin order, filtered to versions that still exist. */
    fun getPinnedOrder(): List<String> =
        VersionsManager.currentGameInfo.pinnedVersions.filter { VersionsManager.isVersionExists(it) }

    /** Persists a full new ordering of the pinned versions (e.g. after a drag-to-reorder). */
    fun reorderPinned(newOrder: List<String>) = modifyPinned { list ->
        list.clear()
        list.addAll(newOrder)
    }

    /**
     * Pinned versions first (in pin order), then every other version in whatever order it was
     * already in (VersionsManager's own sort). Safe to call with any subset of versions (e.g. a
     * favorites-folder-filtered list) - pins outside that subset are simply skipped.
     */
    fun applyPinnedOrder(versions: List<Version>): List<Version> {
        val pinnedNames = getPinnedOrder()
        if (pinnedNames.isEmpty()) return versions

        val byName = versions.associateBy { it.getVersionName() }
        val pinnedVersions = pinnedNames.mapNotNull { byName[it] }
        val pinnedSet = pinnedVersions.map { it.getVersionName() }.toSet()
        val rest = versions.filterNot { it.getVersionName() in pinnedSet }
        return pinnedVersions + rest
    }
}
