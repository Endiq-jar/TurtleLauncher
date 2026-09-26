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
import com.endiq.turtlelauncher.filemanager.events.FileManagerEvent
import com.endiq.turtlelauncher.filemanager.logic.FileManagerLogic
import com.endiq.turtlelauncher.filemanager.logic.FmResult
import com.endiq.turtlelauncher.filemanager.logic.ops.ConflictResolution
import com.endiq.turtlelauncher.filemanager.logic.ops.PasteMode
import com.endiq.turtlelauncher.filemanager.logic.ops.PasteRequest
import com.endiq.turtlelauncher.filemanager.logic.ops.PasteSummary
import com.endiq.turtlelauncher.filemanager.viewmodel.DialogIntent
import com.endiq.turtlelauncher.filemanager.viewmodel.FmSnackbar
import com.endiq.turtlelauncher.filemanager.viewmodel.FmStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.file.Path

/** Paste controller */
class PasteController(
    private val logic: FileManagerLogic,
    private val store: FmStateStore,
    private val browse: BrowseController,
    private val coroutineScope: CoroutineScope
) {
    /** Triggers the paste flow */
    fun requestPaste() {
        val cb = store.clipboard ?: return
        val target = store.history.currentPath
        val mode = if (cb.isCut) PasteMode.MOVE else PasteMode.COPY
        coroutineScope.launch(Dispatchers.IO) {
            val request = runCatching {
                logic.buildPasteRequest(cb.sources, target, mode)
            }.getOrElse {
                store.emitSnackbar(FmSnackbar(it.message ?: store.stringResolver(R.string.fm_error_paste_invalid)))
                browse.refreshCurrentDir()
                return@launch
            }
            when (request) {
                is PasteRequest.Ready -> {
                    executePasteInternal(request.sources, request.targetDir, request.mode,
                        resolutions = request.sources.map { ConflictResolution.SKIP }
                    ) { /* The result is already reported via snackbar */ }
                }
                is PasteRequest.ResolveRequest -> {
                    // Initial index points at the first non-null conflict (null = no conflict, no decision needed)
                    val firstConflict = request.conflicts.indexOfFirst { it != null }
                    if (firstConflict < 0) {
                        executePasteInternal(request.sources, request.targetDir, request.mode,
                            resolutions = request.sources.map { ConflictResolution.SKIP }
                        ) { }
                        return@launch
                    }
                    store.updateState {
                        it.copy(
                            dialogIntent = DialogIntent.PasteConflict(
                                request = request,
                                decidedResolutions = emptyList(),
                                currentIndex = firstConflict
                            )
                        )
                    }
                }
            }
        }
    }

    /** Paste conflict decision */
    fun resolvePasteConflict(resolution: ConflictResolution) {
        val cur = store.stateValue().dialogIntent as? DialogIntent.PasteConflict ?: return
        val req = cur.request
        val total = req.conflicts.size
        // Write the current resolution into the entry slot tied to currentIndex, then find the next conflict index
        val updated = cur.decidedResolutions.toMutableList()
        while (updated.size < total) updated += ConflictResolution.SKIP
        updated[cur.currentIndex] = resolution
        // Find the next non-null conflict index
        var next = cur.currentIndex + 1
        while (next < total && req.conflicts[next] == null) next++
        if (next < total) {
            store.updateState {
                it.copy(dialogIntent = DialogIntent.PasteConflict(req, updated, next))
            }
        } else {
            // All decisions made
            store.dismissDialog()
            coroutineScope.launch(Dispatchers.IO) {
                // For import-triggered conflict flows, clean the temp directory after pasting (async; cannot clean in finally)
                val tempDir = store.pendingImportTempDir
                if (tempDir != null) {
                    executeImportPaste(req.sources, req.targetDir, req.mode, updated, tempDir)
                } else {
                    executePasteInternal(req.sources, req.targetDir, req.mode, updated) { }
                }
            }
        }
    }

    /** Executes the paste. */
    suspend fun executePasteInternal(
        sources: List<Path>,
        targetDir: Path,
        mode: PasteMode,
        resolutions: List<ConflictResolution>,
        onResult: suspend (PasteSummary?) -> Unit
    ) {
        when (val r = logic.executePaste(sources, targetDir, mode, resolutions)) {
            is FmResult.Ok -> {
                // The clipboard is kept when the task is cancelled and cleared once it finishes
                store.setClipboard(null)
                browse.notifyFileChanged(
                    FileManagerEvent(
                        FileManagerEvent.Type.COPY_PASTE,
                        listOf(targetDir.toString())
                    )
                )
                onResult(r.value)
            }
            is FmResult.Failed -> {
                store.emitSnackbar(FmSnackbar(r.error.message ?: store.stringResolver(R.string.fm_error_paste_failed)))
                browse.refreshCurrentDir()
                onResult(null)
            }
            FmResult.Cancelled -> {
                // The clipboard is kept on cancellation so the user can paste again
                onResult(null)
            }
            FmResult.Rejected -> {
                store.emitSnackbar(FmSnackbar(store.stringResolver(R.string.fm_task_busy)))
                onResult(null)
            }
        }
    }

    /** Executes an import-triggered paste */
    suspend fun executeImportPaste(
        sources: List<Path>,
        targetDir: Path,
        mode: PasteMode,
        resolutions: List<ConflictResolution>,
        tempDir: Path
    ) {
        executePasteInternal(sources, targetDir, mode, resolutions) {
            finishImportPaste(targetDir.toString(), tempDir)
        }
    }

    private suspend fun finishImportPaste(targetPath: String, tempDir: Path) {
        logic.tempWorkspace.delete(tempDir)
        store.pendingImportTempDir = null
        browse.notifyFileChanged(
            FileManagerEvent(FileManagerEvent.Type.IMPORT, listOf(targetPath))
        )
    }
}
