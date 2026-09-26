/*
 * Turtle Launcher
 * Copyright (C) 2025 Endiq and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.endiq.turtlelauncher.filemanager.viewmodel.controllers

import com.endiq.turtlelauncher.filemanager.logic.entry.FmEntry
import com.endiq.turtlelauncher.filemanager.viewmodel.FmStateStore
import com.endiq.turtlelauncher.filemanager.viewmodel.entryPathKey

/** Main list selection / multi-select controller */
class SelectionController(private val store: FmStateStore) {
    /**
     * Enters multi-select and selects the entry
     */
    fun enterMultiSelectWith(entry: FmEntry) {
        val key = entryPathKey(entry)
        store.rangeAnchorKey = key
        store.setSelection(store.selection + key, true)
    }

    /**
     * Tapping an entry in multi-select mode toggles its selection
     */
    fun toggleSelection(entry: FmEntry) {
        val key = entryPathKey(entry)
        val newSelection = if (key in store.selection) {
            store.selection - key
        } else {
            store.selection + key
        }
        if (newSelection.isEmpty()) {
            store.rangeAnchorKey = null
            store.setSelection(newSelection, false)
        } else {
            store.setSelection(newSelection, true)
        }
    }

    /**
     * Swipe chain-selection
     * With the anchor and swiped entry as bounds, selects the unselected entries between them
     */
    fun swipeRangeSelect(entry: FmEntry) {
        val list = store.stateValue().visibleEntries
        val swipeKey = entryPathKey(entry)
        val swipeIndex = list.indexOfFirst { entryPathKey(it) == swipeKey }
        if (swipeIndex < 0) return

        if (!store.stateValue().multiSelect) {
            enterMultiSelectWith(entry)
            return
        }

        val anchorIndex = store.rangeAnchorKey?.let { a ->
            list.indexOfFirst { entryPathKey(it) == a }
        } ?: -1
        if (anchorIndex < 0) {
            // No anchor (box selection ended / anchor invalidated)
            // Take the current swiped entry as the new anchor; a fresh box selection begins
            store.rangeAnchorKey = swipeKey
            store.setSelection(store.selection + swipeKey, true)
            return
        }

        val from = minOf(anchorIndex, swipeIndex)
        val to = maxOf(anchorIndex, swipeIndex)
        val newSelection = store.selection + list.subList(from, to + 1).map { entryPathKey(it) }
        // Box selection done; clear the anchor so the next swipe starts a fresh one
        store.rangeAnchorKey = null
        store.setSelection(newSelection, true)
    }

    fun selectAll() {
        store.rangeAnchorKey = null
        store.setSelection(store.stateValue().visibleEntries.map { entryPathKey(it) }.toSet(), true)
    }

    fun clearSelection() {
        store.rangeAnchorKey = null
        store.setSelection(emptySet(), false)
    }
}
