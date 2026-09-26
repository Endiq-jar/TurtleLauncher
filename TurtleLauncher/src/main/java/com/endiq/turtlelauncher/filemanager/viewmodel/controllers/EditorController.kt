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
import com.endiq.turtlelauncher.filemanager.logic.editor.MAX_EDIT_SIZE
import com.endiq.turtlelauncher.filemanager.os.FmLog
import com.endiq.turtlelauncher.filemanager.viewmodel.EditorUiState
import com.endiq.turtlelauncher.filemanager.viewmodel.FmSnackbar
import com.endiq.turtlelauncher.filemanager.viewmodel.FmStateStore
import com.endiq.turtlelauncher.ui.code_editor.EditorState
import io.github.rosemoe.sora.text.Content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Text editor controller
 */
class EditorController(
    private val store: FmStateStore,
    private val browse: BrowseController,
    private val coroutineScope: CoroutineScope
) {
    /** Encoding of the current file; saved back in the original encoding */
    private var charset: Charset = StandardCharsets.UTF_8

    /** In-progress save task */
    private var saveJob: Job? = null

    /** Opens a file and loads its content asynchronously */
    fun open(path: Path) {
        store.updateEditorUi { EditorUiState(path = path) }
        coroutineScope.launch(Dispatchers.IO) {
            val result = runCatching { loadFile(path) }
            result.onSuccess { content ->
                store.updateEditorUi {
                    it.copy(
                        state = EditorState.Success(content),
                        writable = Files.isWritable(path),
                        error = null
                    )
                }
            }.onFailure { e ->
                if (e is CancellationException) return@launch
                FmLog.error(TAG, "Open editor file failed: ${path}", e)
                store.updateEditorUi {
                    it.copy(error = store.fileOpErrorText(e, R.string.fm_editor_open_failed))
                }
            }
        }
    }

    /** Content modified (for the editor event callback) */
    fun onTextChanged() {
        store.updateEditorUi { it.copy(dirty = true) }
    }

    /**
     * Saves the current content to the file
     */
    fun save(onDone: (Boolean) -> Unit = {}) {
        if (store.editorUiValue().saving) return
        val state = store.editorUiValue()
        val path = state.path ?: return
        val content = (state.state as? EditorState.Success)?.content ?: return
        store.updateEditorUi { it.copy(saving = true) }
        saveJob = coroutineScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    runCatching {
                        Files.write(path, content.toString().toByteArray(charset))
                    }.isSuccess
                }
                // If the user cancelled after the write completed (Files.write is blocking, cannot be interrupted),
                // keep the unsaved state: don't update dirty, don't report a result
                ensureActive()
                if (!success) {
                    FmLog.error(TAG, "Save editor file failed: $path")
                }
                store.updateEditorUi { it.copy(dirty = false) }
                if (success) {
                    store.emitSnackbar(FmSnackbar(store.stringResolver(R.string.generic_saved), long = false))
                    // File size / mtime changed; refresh the list display
                    browse.refreshDir()
                } else {
                    store.emitSnackbar(FmSnackbar(store.stringResolver(R.string.fm_editor_save_failed)))
                }
                onDone(success)
            } catch (e: CancellationException) {
                // User cancelled the save: don't update dirty, don't report a result
                onDone(false)
                throw e
            } finally {
                store.updateEditorUi { it.copy(saving = false) }
            }
        }
    }

    /**
     * Cancels an in-progress save
     */
    fun cancelSave() {
        saveJob?.cancel()
        saveJob = null
        store.updateEditorUi { it.copy(saving = false) }
    }

    /** Requests the exit confirmation dialog */
    fun requestExitConfirm() {
        store.updateEditorUi { it.copy(exitConfirm = true) }
    }

    /** Cancels the exit confirmation dialog */
    fun cancelExitConfirm() {
        store.updateEditorUi { it.copy(exitConfirm = false) }
    }

    /** Whether unsaved changes exist (for the system back-key check) */
    fun hasDirty(): Boolean = store.editorUiValue().dirty

    private fun loadFile(path: Path): Content {
        val size = Files.size(path)
        if (size > MAX_EDIT_SIZE || !hasEnoughMemoryFor(size)) {
            throw EditorFileTooLargeException(
                store.stringResolver(
                    if (size > MAX_EDIT_SIZE) R.string.fm_editor_file_too_large
                    else R.string.fm_editor_out_of_memory
                )
            )
        }
        val bytes = Files.readAllBytes(path)
        val text = decode(bytes)
        return Content(text)
    }

    /**
     * Checks whether the remaining heap can hold the file's peak load; refuses to load when short
     */
    private fun hasEnoughMemoryFor(fileSize: Long): Boolean {
        val runtime = Runtime.getRuntime()
        val available = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
        val estimated = fileSize * MEMORY_MULTIPLIER + MEMORY_OVERHEAD
        return estimated <= available * MEMORY_SAFE_RATIO
    }

    /**
     * Decodes file content: prefers BOM detection, otherwise strict UTF-8,
     * falling back to GBK on decode failure (compatible with legacy-encoded Chinese text files).
     */
    private fun decode(bytes: ByteArray): String {
        val bom = detectBom(bytes)
        val contentBytes = bytes.copyOfRange(bom.length, bytes.size)
        if (bom.charset != null) {
            charset = bom.charset
            return String(contentBytes, bom.charset)
        }
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            charset = StandardCharsets.UTF_8
            decoder.decode(ByteBuffer.wrap(contentBytes)).toString()
        } catch (e: CharacterCodingException) {
            charset = Charset.forName("GBK")
            String(contentBytes, charset)
        }
    }

    private fun detectBom(bytes: ByteArray): BomResult = when {
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
            BomResult(3, StandardCharsets.UTF_8)

        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
            BomResult(2, StandardCharsets.UTF_16LE)

        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
            BomResult(2, StandardCharsets.UTF_16BE)

        else -> BomResult(0, null)
    }

    private data class BomResult(
        val length: Int,
        val charset: Charset?
    )

    private class EditorFileTooLargeException(message: String) : Exception(message)

    companion object {
        private const val TAG = "EditorController"
        /** Memory amplification factor when loading a text file (byte array + UTF-16 string + editor line structures) */
        private const val MEMORY_MULTIPLIER: Long = 6
        /** Fixed memory overhead during loading (syntax analysis and other allocations) */
        private const val MEMORY_OVERHEAD: Long = 16L * 1024 * 1024
        /** Allowed fraction of the available heap, leaving headroom for the rest of the app */
        private const val MEMORY_SAFE_RATIO = 0.75
    }
}
