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
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.filemanager.events.FileManagerEvent
import com.endiq.turtlelauncher.filemanager.events.FileManagerEventBus
import com.endiq.turtlelauncher.filemanager.logic.FileManagerLogic
import com.endiq.turtlelauncher.filemanager.logic.FilenameValidator
import com.endiq.turtlelauncher.filemanager.logic.FmResult
import com.endiq.turtlelauncher.filemanager.logic.compress.CompressFormat
import com.endiq.turtlelauncher.filemanager.logic.compress.CompressOptions
import com.endiq.turtlelauncher.filemanager.logic.entry.FmEntry
import com.endiq.turtlelauncher.filemanager.logic.ops.ConflictResolution
import com.endiq.turtlelauncher.filemanager.logic.ops.FilePermissions
import com.endiq.turtlelauncher.filemanager.os.FmLog
import com.endiq.turtlelauncher.filemanager.viewmodel.DialogIntent
import com.endiq.turtlelauncher.filemanager.viewmodel.FmSnackbar
import com.endiq.turtlelauncher.filemanager.viewmodel.FmStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private const val TAG = "FmCompress"

/** Compression controller */
class CompressController(
    private val context: Context,
    private val logic: FileManagerLogic,
    private val store: FmStateStore,
    private val browse: BrowseController,
    private val coroutineScope: CoroutineScope
) {
    /** Stashed pending compression request */
    private var pendingCompress: PendingCompress? = null
    /** Stashed target of a compression conflict flow */
    private var pendingCompressOutputTarget: OutputTarget? = null

    fun bulkCompress() {
        store.dismissDialog()
        val paths = store.selectedEntries().map { it.path }
        if (paths.isEmpty()) return
        val defaultName = defaultCompressName(paths)
        store.updateState {
            it.copy(
                dialogIntent = DialogIntent.CompressSetup(
                    defaultName = defaultName,
                    sources = paths
                )
            )
        }
    }

    /** Starts compression for a single directory / file */
    fun compressEntry(entry: FmEntry) {
        store.dismissDialog()
        val defaultName = defaultCompressName(listOf(entry.path))
        store.updateState {
            it.copy(
                dialogIntent = DialogIntent.CompressSetup(
                    defaultName = defaultName,
                    sources = listOf(entry.path)
                )
            )
        }
    }

    /** Compression settings confirmation */
    fun onCompressSetupConfirmed(
        name: String,
        sources: List<Path>,
        options: CompressOptions
    ) {
        if (name.isBlank()) return
        // The archive name also goes through filename validation; the reason is shown when invalid
        val err = FilenameValidator.verify(name)
        if (err != null) {
            store.emitError(store.filenameErrorText(err))
            return
        }
        // If the user typed the same suffix as the chosen format, avoid appending it twice (foo.zip + .zip)
        val cleanName = stripCompressSuffix(name, options.format)
        pendingCompress = PendingCompress(cleanName, sources, options)
        store.updateState {
            it.copy(dialogIntent = DialogIntent.CompressOutputChoice)
        }
    }

    /** Creates the archive in the current directory */
    fun onCompressOutputChoiceCurrent() {
        val pending = pendingCompress ?: run {
            store.dismissDialog()
            return
        }

        val target = OutputTarget.Local(store.history.currentPath)
        val fileName = pending.name + pending.options.format.suffix
        coroutineScope.launch(Dispatchers.IO) {
            if (existsInTarget(target, fileName)) {
                stageConflict(target, fileName)
            } else {
                executeCompress(target, fileName, overwrite = false)
            }
        }
    }

    /** Picks the output directory via SAF */
    fun onCompressOutputChoiceSaf() {
        store.updateState {
            it.copy(dialogIntent = DialogIntent.CompressOutputPick)
        }
    }

    /** SAF output directory selection cancelled; clears the stash and ends the flow. */
    fun onCompressOutputPickedCancelled() {
        pendingCompress = null
        store.dismissDialog()
    }

    /** SAF output directory selected: pops the conflict dialog on a same-named file, otherwise executes the compression directly. */
    fun onCompressOutputPicked(treeUri: Uri) {
        val pending = pendingCompress ?: run {
            store.dismissDialog()
            return
        }

        val fileName = pending.name + pending.options.format.suffix
        coroutineScope.launch(Dispatchers.IO) {
            if (findChildDocument(context, treeUri, fileName) != null) {
                pendingCompressOutputTarget = OutputTarget.Saf(treeUri)
                store.updateState {
                    it.copy(dialogIntent = DialogIntent.CompressConflict(fileName))
                }
            } else {
                executeCompress(OutputTarget.Saf(treeUri), fileName, overwrite = false)
            }
        }
    }

    /** Compression conflict decision: executes the compression per SKIP/OVERWRITE/KEEP_BOTH. */
    fun resolveCompressConflict(resolution: ConflictResolution) {
        val target = pendingCompressOutputTarget ?: run {
            store.dismissDialog()
            return
        }

        val pending = pendingCompress ?: run {
            store.dismissDialog()
            return
        }

        store.dismissDialog()
        when (resolution) {
            ConflictResolution.SKIP -> {
                pendingCompress = null
                pendingCompressOutputTarget = null
            }
            ConflictResolution.OVERWRITE -> coroutineScope.launch(Dispatchers.IO) {
                executeCompress(
                    target = target,
                    fileName = pending.name + pending.options.format.suffix,
                    overwrite = true
                )
            }
            ConflictResolution.KEEP_BOTH -> coroutineScope.launch(Dispatchers.IO) {
                executeCompress(
                    target = target,
                    fileName = pending.name + pending.options.format.suffix,
                    overwrite = false,
                    keepBoth = true
                )
            }
        }
    }

    private suspend fun executeCompress(
        target: OutputTarget,
        fileName: String,
        overwrite: Boolean,
        keepBoth: Boolean = false
    ) {
        val pending = pendingCompress ?: return
        store.dismissDialog()
        // Temp file creation and half-done cleanup are handled by the logic layer TempWorkspace
        val tempFile = logic.tempWorkspace.compressTempFile(pending.options.format.extension)
        try {
            when (val r = logic.compress(pending.sources, tempFile, pending.options)) {
                is FmResult.Ok -> {
                    val targetName = if (keepBoth) {
                        nextKeepBothName(target, fileName)
                    } else fileName

                    when (target) {
                        is OutputTarget.Local -> {
                            val dest = target.dir.resolve(targetName)
                            if (overwrite) runCatching { Files.deleteIfExists(dest) }
                            withContext(Dispatchers.IO) {
                                Files.move(tempFile, dest, StandardCopyOption.REPLACE_EXISTING)
                                // Cross-filesystem moves can drop permissions; ensure 664 after landing
                                FilePermissions.apply(dest)
                            }
                            browse.notifyFileChanged(FileManagerEvent(FileManagerEvent.Type.ARCHIVE, listOf(target.dir.toString())))
                        }
                        is OutputTarget.Saf -> {
                            copyTempToSaf(
                                treeUri = target.treeUri,
                                file = tempFile.toFile(),
                                name = targetName,
                                mimeType = pending.options.format.mimeType,
                                overwrite = overwrite
                            )
                            FileManagerEventBus.dispatch(FileManagerEvent(FileManagerEvent.Type.ARCHIVE, listOf(target.treeUri.toString())))
                        }
                        else -> { /* SafDir doesn't apply to the compression flow */ }
                    }
                }
                is FmResult.Failed -> {
                    store.emitSnackbar(FmSnackbar(store.fileOpErrorText(r.error, R.string.fm_error_compress_failed)))
                    browse.refreshCurrentDir()
                }
                FmResult.Rejected -> {
                    store.emitSnackbar(FmSnackbar(store.stringResolver(R.string.fm_task_busy)))
                }
                FmResult.Cancelled -> {}
            }
        } catch (e: Exception) {
            FmLog.warn(TAG, "Compress failed", e)
            store.emitSnackbar(FmSnackbar(store.fileOpErrorText(e, R.string.fm_error_compress_failed)))
            browse.refreshCurrentDir()
        } finally {
            // Cleanup of half-done output (including cancel/failure) is handled by the logic layer
            logic.tempWorkspace.delete(tempFile)
            pendingCompress = null
            pendingCompressOutputTarget = null
        }
    }

    private fun stripCompressSuffix(name: String, format: CompressFormat): String {
        val lower = name.lowercase()
        if (lower.endsWith(format.suffix)) {
            return name.substring(0, name.length - format.suffix.length)
        }
        return name
    }

    private fun stageConflict(target: OutputTarget, fileName: String) {
        pendingCompressOutputTarget = target
        store.updateState {
            it.copy(dialogIntent = DialogIntent.CompressConflict(fileName))
        }
    }

    private fun existsInTarget(target: OutputTarget, fileName: String): Boolean {
        return when (target) {
            is OutputTarget.Local -> Files.exists(target.dir.resolve(fileName), LinkOption.NOFOLLOW_LINKS)
            is OutputTarget.Saf -> findChildDocument(context, target.treeUri, fileName) != null
            is OutputTarget.SafDir -> findChildDocument(context, target.treeUri, fileName) != null
        }
    }

    private fun nextKeepBothName(target: OutputTarget, name: String): String {
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 1
        while (true) {
            val candidate = "$base ($n)$ext"
            if (!existsInTarget(target, candidate)) return candidate
            n++
        }
    }

    private suspend fun copyTempToSaf(
        treeUri: Uri,
        file: File,
        name: String,
        mimeType: String,
        overwrite: Boolean
    ) = withContext(Dispatchers.IO) {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        if (overwrite) {
            val existing = findChildDocument(context, treeUri, name)
            if (existing != null) {
                runCatching {
                    DocumentsContract.deleteDocument(context.contentResolver, existing)
                }
            }
        }
        val newDoc = DocumentsContract.createDocument(context.contentResolver, parent, mimeType, name)
            ?: throw IllegalStateException("Failed to create document")
        val out = context.contentResolver.openOutputStream(newDoc, "wt")
            ?: throw IllegalStateException("Failed to open output stream")
        out.use { output ->
            file.inputStream().use { input -> input.copyTo(output) }
        }
    }

    private fun defaultCompressName(sources: List<Path>): String {
        if (sources.size == 1) {
            val src = sources[0]
            if (Files.isDirectory(src)) return src.fileName?.toString() ?: "archive"
            val name = src.fileName?.toString() ?: "archive"
            val dot = name.lastIndexOf('.')
            return if (dot > 0) name.substring(0, dot) else name
        }
        return store.history.currentPath.fileName?.toString() ?: "archive"
    }

    private data class PendingCompress(
        val name: String,
        val sources: List<Path>,
        val options: CompressOptions
    )
}
