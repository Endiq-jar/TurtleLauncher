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

import android.content.Context
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.filemanager.events.FileManagerEvent
import com.endiq.turtlelauncher.filemanager.logic.FileManagerLogic
import com.endiq.turtlelauncher.filemanager.logic.FilenameValidator
import com.endiq.turtlelauncher.filemanager.logic.FmResult
import com.endiq.turtlelauncher.filemanager.logic.entry.FmEntry
import com.endiq.turtlelauncher.filemanager.os.FmLog
import com.endiq.turtlelauncher.filemanager.viewmodel.FmSnackbar
import com.endiq.turtlelauncher.filemanager.viewmodel.FmStateStore
import com.endiq.turtlelauncher.filemanager.viewmodel.entryPathKey
import com.endiq.turtlelauncher.utils.file.shareFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.LinkOption

private const val TAG = "FmDelete"

/** Delete / rename / create / share controller */
class EntryController(
    private val context: Context,
    private val logic: FileManagerLogic,
    private val store: FmStateStore,
    private val browse: BrowseController,
    private val coroutineScope: CoroutineScope
) {
    fun showShare(entry: FmEntry) {
        store.dismissDialog()
        // Sharing applies to single files; directories get no share entry, this is the guard
        if (!entry.isFile) return
        coroutineScope.launch(Dispatchers.IO) {
            val file = entry.path.toFile()
            if (!file.exists()) {
                store.emitSnackbar(FmSnackbar(store.stringResolver(R.string.fm_error_share_failed)))
                browse.refreshCurrentDir()
                return@launch
            }
            runCatching {
                shareFile(context, file)
            }.onFailure {
                FmLog.warn(TAG, "Share failed: ${entry.path}", it)
                store.emitSnackbar(FmSnackbar(store.stringResolver(R.string.fm_error_share_failed)))
                browse.refreshCurrentDir()
            }
        }
    }

    /** Submits creation: validates the filename and same-directory duplicates, then executes */
    fun submitCreate(name: String, isFolder: Boolean, onDone: (Boolean) -> Unit) {
        if (name.isBlank()) {
            onDone(false)
            return
        }

        // Validates a filename
        val err = FilenameValidator.verify(name)
        if (err != null) {
            store.emitError(store.filenameErrorText(err))
            return
        }

        // Check for a same-named entry already in the current directory
        val conflict = store.stateValue().visibleEntries.any {
            it.name.equals(name, ignoreCase = false)
        }
        if (conflict) {
            store.emitError(store.stringResolver(R.string.fm_name_conflict))
            return
        }
        if (isFolder) {
            createFolder(name, onDone)
        } else {
            createFile(name, onDone)
        }
        store.dismissDialog()
    }

    /** Submits a rename */
    fun submitRename(entry: FmEntry, newName: String, onSuccess: () -> Unit) {
        rename(entry, newName) {
            store.dismissDialog()
            onSuccess()
        }
    }

    /**
     * Runs inline validation of the rename candidate
     * @return the error description or null
     */
    fun validateRename(entry: FmEntry, newName: String): String? {
        if (newName.isBlank()) return store.stringResolver(R.string.generic_cannot_empty)
        val err = FilenameValidator.verify(newName)
        if (err != null) return store.filenameErrorText(err)
        // Whether another entry in the same directory has that name (excluding itself)
        val sibling = store.history.currentPath.resolve(newName).normalize().toAbsolutePath()
        if (sibling != entry.path && Files.exists(sibling, LinkOption.NOFOLLOW_LINKS)) {
            return store.stringResolver(R.string.fm_name_conflict)
        }
        return null
    }

    fun rename(entry: FmEntry, newName: String, onSuccess: () -> Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val newPath = logic.rename(entry.path, newName)
                browse.notifyFileChanged(
                    FileManagerEvent(
                        FileManagerEvent.Type.RENAME,
                        listOfNotNull(entry.path.parent?.toString())
                    )
                )
                store.updateState {
                    it.copy(locateHighlightPath = newPath)
                }
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                FmLog.warn(TAG, "rename failed", e)
                browse.refreshCurrentDir()
                withContext(Dispatchers.Main) {
                    store.emitError(store.operationErrorText(e, R.string.fm_error_rename_failed))
                }
            }
        }
    }

    fun createFolder(name: String, onDone: (Boolean) -> Unit) {
        createEntry(name, isFolder = true, onDone)
    }

    fun createFile(name: String, onDone: (Boolean) -> Unit) {
        createEntry(name, isFolder = false, onDone)
    }

    private fun createEntry(name: String, isFolder: Boolean, onDone: (Boolean) -> Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            val target = store.history.currentPath
            try {
                val created = if (isFolder) {
                    logic.createFolder(target, name)
                } else {
                    logic.createFile(target, name)
                }
                browse.notifyFileChanged(
                    FileManagerEvent(
                        FileManagerEvent.Type.CREATE,
                        listOf(target.toString())
                    )
                )
                store.updateState {
                    it.copy(locateHighlightPath = created)
                }
                withContext(Dispatchers.Main) {
                    onDone(true)
                }
            } catch (e: Exception) {
                FmLog.warn(TAG, "createEntry failed", e)
                browse.refreshCurrentDir()
                withContext(Dispatchers.Main) {
                    store.emitError(store.operationErrorText(e, R.string.fm_error_create_failed))
                    onDone(false)
                }
            }
        }
    }

    fun deleteSelected(toTrash: Boolean) {
        val entries = store.selectedEntries()
        val targets = entries.map { it.path }
        if (targets.isEmpty()) return
        // Single-entry delete confirmation entered execution; clear the stashed marker
        store.stagedSingleDeleteKey = null
        val dirTargets = entries.filter { it.isDirectory }.map { it.path }.toSet()
        coroutineScope.launch(Dispatchers.IO) {
            when (val r = logic.delete(targets, toTrash)) {
                is FmResult.Ok -> {
                    r.value.results
                        .filter { it.success && it.path in dirTargets }
                        .forEach { browse.pruneHistory(it.path) }
                    browse.notifyFileChanged(
                        FileManagerEvent(
                            FileManagerEvent.Type.DELETE,
                            targets.mapNotNull { it.parent?.toString() }.distinct()
                        )
                    )
                }
                is FmResult.Failed -> {
                    store.emitSnackbar(FmSnackbar(r.error.message ?: store.stringResolver(R.string.fm_error_delete_failed)))
                    browse.refreshCurrentDir()
                }
                FmResult.Rejected -> store.emitSnackbar(FmSnackbar(store.stringResolver(R.string.fm_task_busy)))
                FmResult.Cancelled -> {}
            }
        }
    }

    /** Temporarily selects a single entry to start delete confirmation */
    fun stageSingleDelete(entry: FmEntry) {
        val key = entryPathKey(entry)
        store.stagedSingleDeleteKey = key
        store.selection += key
    }

    /** Cancels single-entry delete confirmation */
    fun cancelStagedDelete() {
        store.stagedSingleDeleteKey?.let { store.selection -= it }
        store.stagedSingleDeleteKey = null
    }
}
