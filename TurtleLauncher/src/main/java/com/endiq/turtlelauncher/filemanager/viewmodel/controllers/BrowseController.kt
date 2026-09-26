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

import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.filemanager.config.FmConfig
import com.endiq.turtlelauncher.filemanager.events.FileManagerEvent
import com.endiq.turtlelauncher.filemanager.events.FileManagerEventBus
import com.endiq.turtlelauncher.filemanager.logic.AccessScope
import com.endiq.turtlelauncher.filemanager.logic.FileManagerLogic
import com.endiq.turtlelauncher.filemanager.logic.FmResult
import com.endiq.turtlelauncher.filemanager.logic.entry.FmEntry
import com.endiq.turtlelauncher.filemanager.logic.entry.FmListResult
import com.endiq.turtlelauncher.filemanager.os.FmLog
import com.endiq.turtlelauncher.filemanager.viewmodel.FmSnackbar
import com.endiq.turtlelauncher.filemanager.viewmodel.FmStateStore
import com.endiq.turtlelauncher.filemanager.viewmodel.RawList
import com.endiq.turtlelauncher.filemanager.viewmodel.SortConfig
import com.endiq.turtlelauncher.filemanager.viewmodel.applyVisibility
import com.endiq.turtlelauncher.filemanager.viewmodel.entryPathKey
import com.endiq.turtlelauncher.filemanager.viewmodel.persist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "FmBrowse"

/** Retry interval while the browse task is blocked by another task */
private const val BUSY_RETRY_DELAY_MS = 150L

/**
 * Browsing / navigation controller, managing directory list refreshes and navigation history
 * and centrally manages the lifecycle of the single directory-refresh coroutine task
 */
class BrowseController(
    private val logic: FileManagerLogic,
    private val scope: AccessScope,
    private val store: FmStateStore,
    private val coroutineScope: CoroutineScope
) {
    private val refreshLock = Any()

    private var refreshJob: Job? = null

    /** Latest pending refresh target: intermediate targets are coalesced away during fast navigation (access only under [refreshLock]) */
    private var pendingTarget: Path? = null

    /**
     * Requests a directory refresh: coalesced into the target pipeline; the newest target is always processed.
     * @param target target directory; null refreshes the current path
     */
    fun refreshDir(target: Path? = null) {
        val t = target ?: store.history.currentPath
        synchronized(refreshLock) {
            pendingTarget = t
            store.updateState {
                it.copy(currentDir = t, refreshing = true)
            }
            if (refreshJob?.isActive == true) return
            refreshJob = coroutineScope.launch(Dispatchers.IO) { refreshPipeline() }
        }
    }

    /** After an operation error, refreshes the current directory so the list reflects the real disk state */
    fun refreshCurrentDir() = refreshDir(store.history.currentPath)

    /** Target refresh pipeline: processes the accumulated latest targets in order. */
    private suspend fun refreshPipeline() {
        while (true) {
            val target = takePendingTarget()
            if (target == null) {
                val shouldExit = synchronized(refreshLock) {
                    if (pendingTarget == null) {
                        refreshJob = null
                        true
                    } else {
                        false
                    }
                }
                if (shouldExit) {
                    store.updateState {
                        it.copy(refreshing = false)
                    }
                    return
                }
                continue
            }
            try {
                refreshOnce(target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                FmLog.warn(TAG, "refresh pipeline error for $target", e)
                store.emitSnackbar(FmSnackbar(store.stringResolver(R.string.fm_error_browse_failed)))
            }
        }
    }

    /** Atomically takes the latest pending refresh target */
    private fun takePendingTarget(): Path? = synchronized(refreshLock) {
        val t = pendingTarget
        pendingTarget = null
        t
    }

    private suspend fun refreshOnce(target: Path) {
        FmLog.info(TAG, "refreshList target=$target")
        // Wait and retry while the task is busy; animation is driven by the UI from content state, the pipeline only handles data
        val result = browseAwaitingMutex(target) ?: return

        if (target != store.history.currentPath) {
            FmLog.info(TAG, "browse result stale, drop: $target")
            return
        }

        when (result) {
            is FmResult.Ok -> {
                // Re-validate before applying: the user may have navigated away meanwhile; stale results are never written
                if (result.value.currentDir == store.history.currentPath) {
                    FmLog.info(TAG, "browse ok: entries=${result.value.entries.size}, current=${result.value.currentDir}")
                    updateList(result.value)
                } else {
                    FmLog.info(TAG, "browse result stale at apply, drop: ${result.value.currentDir}")
                }
            }
            is FmResult.Failed -> {
                FmLog.warn(TAG, "browse failed", result.error)
                store.emitSnackbar(FmSnackbar(result.error.message ?: store.stringResolver(R.string.fm_error_browse_failed)))
                // On navigation failure, restore showing the last successful directory,
                // so the path bar doesn't sit on a dead path with content/path mismatch
                val lastGood = store.stateValue().rawList?.currentDir
                if (lastGood != null && target != lastGood) {
                    FmLog.info(TAG, "browse failed for navigation target, revert to: $lastGood")
                    store.updateState { it.copy(currentDir = lastGood) }
                }
            }
            FmResult.Cancelled -> {
                FmLog.warn(TAG, "browse cancelled")
            }
            FmResult.Rejected -> {
                FmLog.warn(TAG, "browse rejected (busy)")
                store.emitSnackbar(FmSnackbar(store.stringResolver(R.string.fm_task_busy)))
            }
        }
    }

    private suspend fun browseAwaitingMutex(target: Path): FmResult<FmListResult>? {
        var result = logic.browse(target)
        while (result is FmResult.Rejected && currentCoroutineContext().isActive) {
            if (target != store.history.currentPath) {
                FmLog.info(TAG, "browse abandoned (target changed): $target")
                return null
            }
            delay(BUSY_RETRY_DELAY_MS.milliseconds)
            result = logic.browse(target)
        }
        return result
    }

    /** Navigates to a directory (recorded in history) */
    fun navigateTo(path: Path) {
        val safe = logic.validateTarget(path) ?: run {
            store.emitSnackbar(FmSnackbar(store.stringResolver(R.string.fm_error_invalid_target)))
            return
        }
        store.clearSelectionAndExitMulti()
        store.history.navigate(safe)
        syncNavState()
        refreshDir(safe)
    }

    /** Enters a directory from a tapped entry */
    fun enterDirectory(entry: FmEntry) {
        if (!entry.isDirectory) return
        navigateTo(entry.path)
    }

    /**
     * Goes back
     * @return true when it moved
     */
    fun back(): Boolean {
        val p = store.history.back() ?: return false
        store.clearSelectionAndExitMulti()
        syncNavState()
        refreshDir(p)
        return true
    }

    /**
     * Goes forward
     * @return true when it moved
     */
    fun forward(): Boolean {
        val p = store.history.forward() ?: return false
        store.clearSelectionAndExitMulti()
        syncNavState()
        refreshDir(p)
        return true
    }

    /**
     * Goes up one directory level
     * @return true when it moved
     */
    fun goParent(): Boolean {
        val parent = store.history.currentPath.parent ?: return false
        if (!scope.isUnder(parent)) return false
        navigateTo(parent)
        return true
    }

    /**
     * Jumps to a directory
     * The target is limited to the accessible scope; out-of-scope or nonexistent targets are refused with a notice
     */
    fun jumpTo(targetInput: String) {
        val candidate = Paths.get(targetInput).normalize().toAbsolutePath()
        val safe = logic.validateTarget(candidate)
        if (safe == null) {
            store.emitError(store.stringResolver(R.string.fm_error_out_of_scope))
            return
        }
        navigateTo(safe)
    }

    /**
     * Submits a directory jump
     * Returns false when validation fails
     * @return whether the jump was initiated
     */
    fun submitJump(targetInput: String): Boolean {
        val candidate = try {
            Paths.get(targetInput).normalize().toAbsolutePath()
        } catch (_: Exception) {
            store.emitError(store.stringResolver(R.string.fm_jump_invalid))
            return false
        }
        if (logic.validateTarget(candidate) == null) {
            store.emitError(store.stringResolver(R.string.fm_jump_invalid))
            return false
        }
        jumpTo(candidate.toString())
        return true
    }

    /** Selects a search result: jumps to its parent directory and highlights the entry */
    fun navigateToSearchHit(hitPath: Path) {
        val target = hitPath.parent ?: store.history.currentPath
        val safe = logic.validateTarget(target) ?: return
        store.clearSelectionAndExitMulti()
        store.history.navigate(safe)
        syncNavState()
        store.updateState { it.copy(locateHighlightPath = hitPath) }
        store.dismissDialog()
        refreshDir(safe)
    }

    /** Dispatches a filesystem change event and refreshes the current directory */
    fun notifyFileChanged(event: FileManagerEvent) {
        FileManagerEventBus.dispatch(event)
        refreshCurrentDir()
    }

    /**
     * Cleans up navigation history after a directory is deleted
     */
    fun pruneHistory(deleted: Path) {
        store.history.pruneDeleted(deleted)
        syncNavState()
    }

    /** Applies the sort config and recomputes the visible list */
    fun setSortConfig(config: SortConfig) {
        config.persist()
        val newVisible = applyVisibility(store.stateValue().rawList?.entries ?: emptyList(), config, store.stateValue().showHidden)
        store.updateState { it.copy(sortConfig = config, visibleEntries = newVisible) }
        recountVisible(newVisible)
        reconcileSelectionWith(newVisible)
    }

    /** Toggles "show hidden files" and recomputes the visible list */
    fun toggleHidden() {
        val show = !store.stateValue().showHidden
        FmConfig.setShowHidden(show)
        val newVisible = applyVisibility(store.stateValue().rawList?.entries ?: emptyList(), store.stateValue().sortConfig, show)
        store.updateState { it.copy(showHidden = show, visibleEntries = newVisible) }
        recountVisible(newVisible)
        reconcileSelectionWith(newVisible)
    }

    private fun syncNavState() {
        store.updateState {
            it.copy(
                canNavigateBack = store.history.canBack,
                canNavigateForward = store.history.canForward
            )
        }
    }

    private fun updateList(result: FmListResult) {
        val raw = RawList.of(result)
        val visible = applyVisibility(raw.entries, store.stateValue().sortConfig, store.stateValue().showHidden)
        // Keep selected entries that still exist (compared by path string, avoiding Path-instance hashCode differences)
        val present = visible.mapTo(mutableSetOf()) { entryPathKey(it) }
        val newSelection = store.selection.intersect(present)
        store.setSelection(newSelection, newSelection.isNotEmpty())
        store.updateState {
            it.copy(
                rawList = raw,
                visibleEntries = visible
            )
        }
        recountVisible(visible)
    }

    private fun recountVisible(visible: List<FmEntry>) {
        val folders = visible.count { it.isDirectory }
        val files = visible.count { it.isFile }
        store.updateState {
            it.copy(folderCount = folders, fileCount = files)
        }
    }

    private fun reconcileSelectionWith(visible: List<FmEntry>) {
        val present = visible.mapTo(mutableSetOf()) { entryPathKey(it) }
        if (store.selection.any { it !in present }) {
            val newSelection = store.selection.intersect(present)
            store.setSelection(newSelection, newSelection.isNotEmpty())
        }
    }
}
