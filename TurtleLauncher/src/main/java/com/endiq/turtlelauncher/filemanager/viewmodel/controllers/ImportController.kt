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
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.filemanager.logic.FileManagerLogic
import com.endiq.turtlelauncher.filemanager.logic.ops.ConflictResolution
import com.endiq.turtlelauncher.filemanager.logic.ops.FilePermissions
import com.endiq.turtlelauncher.filemanager.logic.ops.PasteMode
import com.endiq.turtlelauncher.filemanager.logic.ops.PasteRequest
import com.endiq.turtlelauncher.filemanager.logic.task.RunResult
import com.endiq.turtlelauncher.filemanager.logic.task.TaskKind
import com.endiq.turtlelauncher.filemanager.logic.task.TaskManager
import com.endiq.turtlelauncher.filemanager.os.FmLog
import com.endiq.turtlelauncher.filemanager.viewmodel.DialogIntent
import com.endiq.turtlelauncher.filemanager.viewmodel.FmSnackbar
import com.endiq.turtlelauncher.filemanager.viewmodel.FmStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

private const val TAG = "FmImport"

/** SAF import controller */
class ImportController(
    private val context: Context,
    private val logic: FileManagerLogic,
    private val taskManager: TaskManager,
    private val store: FmStateStore,
    private val paste: PasteController,
    private val browse: BrowseController,
    private val coroutineScope: CoroutineScope
) {
    /**
     * SAF selected files to import (multi-select)
     * Downloads to a temp directory, then runs the paste flow
     */
    fun onImportFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        importFromUris(uris, multiFile = true)
    }

    /**
     * SAF selected a full directory to import
     * Enumerates the directory tree and downloads, then runs the paste flow
     */
    fun onImportDir(treeUri: Uri) {
        importFromUris(listOf(treeUri), multiFile = false)
    }

    private fun importFromUris(uris: List<Uri>, multiFile: Boolean) {
        coroutineScope.launch(Dispatchers.IO) {
            doImport(uris, multiFile)
        }
    }

    private suspend fun doImport(uris: List<Uri>, multiFile: Boolean) {
        // Temp directory creation and half-done cleanup are handled by the logic layer TempWorkspace
        val tempDir = logic.tempWorkspace.importTempDir()
        try {
            // Download to the temp directory
            val downloadResult: RunResult<List<Path>> = taskManager.run(TaskKind.IMPORT) {
                downloadUris(uris, tempDir, multiFile) { d, t -> report(completed = d, total = t) }
            }
            val sources = when (downloadResult) {
                is RunResult.Ok -> downloadResult.value
                is RunResult.Failed -> throw downloadResult.error
                RunResult.Cancelled -> {
                    logic.tempWorkspace.delete(tempDir)
                    store.dismissDialog()
                    return
                }
                is RunResult.Rejected -> {
                    store.emitSnackbar(FmSnackbar(store.stringResolver(R.string.fm_task_busy)))
                    logic.tempWorkspace.delete(tempDir)
                    store.dismissDialog()
                    return
                }
            }
            if (sources.isEmpty()) {
                store.emitSnackbar(FmSnackbar(store.stringResolver(R.string.fm_import_empty)))
                logic.tempWorkspace.delete(tempDir)
                store.dismissDialog()
                return
            }

            // Builds a paste request, reusing conflict decisions and execution
            // Close the import intent before pasting so the SAF picker isn't re-launched mid-import
            store.dismissDialog()
            val target = store.history.currentPath
            when (val req = logic.buildPasteRequest(sources, target, PasteMode.COPY)) {
                is PasteRequest.Ready -> {
                    paste.executeImportPaste(req.sources, req.targetDir, req.mode,
                        req.sources.map { ConflictResolution.SKIP }, tempDir)
                }
                is PasteRequest.ResolveRequest -> {
                    // Conflict flow defers the paste execution
                    // Stash the temp directory for post-completion cleanup
                    val firstConflict = req.conflicts.indexOfFirst { it != null }
                    if (firstConflict < 0) {
                        paste.executeImportPaste(req.sources, req.targetDir, req.mode,
                            req.sources.map { ConflictResolution.SKIP }, tempDir)
                        return
                    }
                    store.pendingImportTempDir = tempDir
                    store.updateState {
                        it.copy(
                            dialogIntent = DialogIntent.PasteConflict(
                                request = req,
                                decidedResolutions = emptyList(),
                                currentIndex = firstConflict
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            FmLog.error(TAG, "Import failed: ${e.message}", e)
            logic.tempWorkspace.delete(tempDir)
            val detail = e.message?.takeIf { it.isNotBlank() }
            store.emitSnackbar(FmSnackbar(detail ?: store.stringResolver(R.string.import_modpack_failed_title)))
            store.dismissDialog()
            browse.refreshCurrentDir()
        }
    }

    private suspend fun downloadUris(
        uris: List<Uri>,
        tempDir: Path,
        multiFile: Boolean,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): List<Path> = withContext(Dispatchers.IO) {
        if (multiFile) {
            val total = uris.size
            val result = mutableListOf<Path>()
            var done = 0
            for (uri in uris) {
                val name = displayNameOf(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: continue
                val dest = tempDir.resolve(name)
                if (downloadUri(uri, dest)) result.add(dest)
                done++
                onProgress(done, total)
            }
            return@withContext result
        }
        val treeUri = uris.firstOrNull() ?: return@withContext emptyList()
        // Create a subdirectory named after the source directory, preserving the top-level structure
        val dirName = treeDisplayNameOf(treeUri) ?: "imported"
        val rootDest = tempDir.resolve(dirName)
        Files.createDirectories(rootDest)
        // Recursively walk the directory tree with DocumentFile
        val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
        if (treeDoc == null) {
            FmLog.warn(TAG, "Import dir: DocumentFile.fromTreeUri returned null for $treeUri")
            return@withContext emptyList()
        }
        // Count the total files first (for progress)
        val total = countFiles(treeDoc)
        FmLog.info(TAG, "Import dir: '$dirName' total files=$total")
        if (total == 0) {
            FmLog.warn(TAG, "Import dir: no files counted under $treeUri")
        }
        val counter = Counter()
        importDocTree(treeDoc, rootDest, counter, total, onProgress)
        FmLog.info(TAG, "Import dir: downloaded ${counter.value}/$total files to $rootDest")

        listOf(rootDest)
    }

    private fun countFiles(dir: DocumentFile): Int {
        val children = runCatching { dir.listFiles() }.getOrElse { e ->
            FmLog.warn(TAG, "Import dir: countFiles listFiles() failed for ${dir.uri}", e)
            null
        } ?: return 0
        var count = 0
        for (child in children) {
            count += if (child.isDirectory) countFiles(child) else 1
        }
        return count
    }

    private fun importDocTree(
        dir: DocumentFile,
        destDir: Path,
        counter: Counter,
        total: Int,
        onProgress: (Int, Int) -> Unit
    ) {
        val children = runCatching { dir.listFiles() }.getOrElse { e ->
            FmLog.warn(TAG, "Import dir: listFiles() failed for ${dir.uri}", e)
            null
        }
        if (children == null) return
        for (child in children) {
            runCatching {
                val name = child.name ?: return@runCatching
                val dest = destDir.resolve(name)
                if (child.isDirectory) {
                    Files.createDirectories(dest)
                    importDocTree(child, dest, counter, total, onProgress)
                } else {
                    if (downloadUri(child.uri, dest)) {
                        counter.increment()
                        onProgress(counter.value, total)
                    }
                }
            }.onFailure { e ->
                FmLog.warn(TAG, "Import dir: failed on child ${child.uri}", e)
            }
        }
    }

    private fun treeDisplayNameOf(treeUri: Uri): String? {
        return runCatching {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            displayNameOf(documentUri)
        }.getOrElse { e ->
            FmLog.warn(TAG, "Import dir: failed to resolve tree display name for $treeUri", e)
            null
        }
    }

    private fun downloadUri(uri: Uri, dest: Path): Boolean {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                Files.createDirectories(dest.parent)
                Files.newOutputStream(dest).use { output -> input.copyTo(output) }
                FilePermissions.apply(dest)
            } ?: run {
                FmLog.warn(TAG, "Import: openInputStream returned null for $uri")
                return false
            }
            true
        }.getOrElse { e ->
            FmLog.warn(TAG, "Import: failed to download URI $uri -> $dest", e)
            false
        }
    }

    private fun displayNameOf(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    private class Counter {
        var value = 0
        fun increment(): Int = ++value
    }
}
