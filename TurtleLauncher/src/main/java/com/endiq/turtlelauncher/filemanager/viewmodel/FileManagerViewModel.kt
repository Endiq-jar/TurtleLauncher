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

package com.endiq.turtlelauncher.filemanager.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.filemanager.config.FmConfig
import com.endiq.turtlelauncher.filemanager.logic.AccessScope
import com.endiq.turtlelauncher.filemanager.logic.FileManagerLogic
import com.endiq.turtlelauncher.filemanager.logic.compress.CompressOptions
import com.endiq.turtlelauncher.filemanager.logic.entry.FmEntry
import com.endiq.turtlelauncher.filemanager.logic.ops.ConflictResolution
import com.endiq.turtlelauncher.filemanager.logic.task.TaskManager
import com.endiq.turtlelauncher.filemanager.logic.task.TaskState
import com.endiq.turtlelauncher.filemanager.logic.trash.TrashItem
import com.endiq.turtlelauncher.filemanager.os.FmLog
import com.endiq.turtlelauncher.filemanager.viewmodel.controllers.BrowseController
import com.endiq.turtlelauncher.filemanager.viewmodel.controllers.CompressController
import com.endiq.turtlelauncher.filemanager.viewmodel.controllers.DirectoryScanController
import com.endiq.turtlelauncher.filemanager.viewmodel.controllers.EditorController
import com.endiq.turtlelauncher.filemanager.viewmodel.controllers.EntryController
import com.endiq.turtlelauncher.filemanager.viewmodel.controllers.ExtractController
import com.endiq.turtlelauncher.filemanager.viewmodel.controllers.ImportController
import com.endiq.turtlelauncher.filemanager.viewmodel.controllers.PasteController
import com.endiq.turtlelauncher.filemanager.viewmodel.controllers.SearchController
import com.endiq.turtlelauncher.filemanager.viewmodel.controllers.SelectionController
import com.endiq.turtlelauncher.filemanager.viewmodel.controllers.TrashController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.nio.file.Paths
import javax.inject.Inject

/** File manager initialization state */
sealed interface FmInitState {
    /** Initializing (or not started yet) */
    data object Pending : FmInitState
    /** Initialization complete */
    data object Ready : FmInitState
    /** Initialization failed */
    data class Failed(val message: String) : FmInitState
}

/**
 * Facade of the file manager data-control layer: assembles the feature controllers and forwards UI calls.
 * Concrete business logic and coroutine launches are managed by the controllers themselves.
 *
 * Init strategy: the constructor only parses arguments and creates a pure in-memory state container (zero IO, zero coroutines, zero side effects);
 * all filesystem / MMKV / coroutine initialization is gathered in [initialize], called by the UI after the first frame is composed:
 * this avoids early main-thread IO racing first-frame composition (which can cause startup ANR on HyperOS),
 * and lets initialization failures show an error instead of crashing.
 */
@HiltViewModel
class FileManagerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** Absolute path of the accessible scope directory. */
    private val rootPathStr: String = savedStateHandle[KEY_ROOT_PATH]
        ?: throw IllegalStateException("Missing required argument: $KEY_ROOT_PATH")
    /** Optional initial current directory; falls back to the root when invalid. */
    private val currentPathStr: String? = savedStateHandle[KEY_CURRENT_PATH]

    private val store = FmStateStore(context)

    private val _initState = MutableStateFlow<FmInitState>(FmInitState.Pending)
    /** Initialization state */
    val initState: StateFlow<FmInitState> = _initState.asStateFlow()

    private lateinit var taskManager: TaskManager
    private lateinit var scope: AccessScope
    private lateinit var logic: FileManagerLogic
    private lateinit var browseCtr: BrowseController
    private lateinit var selectionCtr: SelectionController
    private lateinit var pasteCtr: PasteController
    private lateinit var compressCtr: CompressController
    private lateinit var extractCtr: ExtractController
    private lateinit var importCtr: ImportController
    private lateinit var searchCtr: SearchController
    private lateinit var directoryScanCtr: DirectoryScanController
    private lateinit var entryCtr: EntryController
    private lateinit var trashCtr: TrashController
    private lateinit var editorCtr: EditorController

    val state: StateFlow<FileManagerUiState> get() = store.state
    val searchUi: StateFlow<SearchUiState> get() = store.searchUi
    val dirScan: StateFlow<DirScanUiState?> get() = store.dirScan
    val editorUi: StateFlow<EditorUiState> get() = store.editorUi
    val errorEvents: SharedFlow<String> get() = store.errorEvents

    fun initialize() {
        if (_initState.value != FmInitState.Pending) return
        runCatching { createCore() }.fold(
            onSuccess = {
                _initState.value = FmInitState.Ready
                observeTasks()
                browseCtr.refreshDir(store.history.currentPath)
            },
            onFailure = { e ->
                FmLog.error(TAG, "Initialize failed: ${e.message}", e)
                _initState.value = FmInitState.Failed(
                    e.message ?: context.getString(R.string.generic_error)
                )
            }
        )
    }

    private fun createCore() {
        taskManager = TaskManager()
        scope = AccessScope(Paths.get(rootPathStr).normalize().toAbsolutePath())
        logic = FileManagerLogic(
            scope = scope,
            trashRoot = context.cacheDir.toPath().resolve(TRASH_SUBDIR).normalize().toAbsolutePath(),
            cacheRoot = context.cacheDir.toPath().normalize().toAbsolutePath(),
            taskManager = taskManager
        )

        browseCtr = BrowseController(logic, scope, store, viewModelScope)
        selectionCtr = SelectionController(store)
        pasteCtr = PasteController(logic, store, browseCtr, viewModelScope)
        compressCtr = CompressController(context, logic, store, browseCtr, viewModelScope)
        extractCtr = ExtractController(context, logic, scope, store, browseCtr, viewModelScope)
        importCtr = ImportController(context, logic, taskManager, store, pasteCtr, browseCtr, viewModelScope)
        searchCtr = SearchController(logic, taskManager, store, viewModelScope)
        directoryScanCtr = DirectoryScanController(store, viewModelScope)
        entryCtr = EntryController(context, logic, store, browseCtr, viewModelScope)
        trashCtr = TrashController(logic, store, browseCtr, viewModelScope)
        editorCtr = EditorController(store, browseCtr, viewModelScope)

        store.history = NavHistory(logic.resolveInitialCurrent(currentPathStr?.let { Paths.get(it) }))
        store.updateState {
            it.copy(
                currentDir = store.history.currentPath,
                sortConfig = SortConfig.load().also { c -> c.persist() },
                showHidden = FmConfig.showHidden(),
                trashSortConfig = TrashSortConfig.load().also { c -> c.persist() },
                taskState = TaskState.Idle,
                trashView = TrashViewState.Idle,
                canNavigateBack = store.history.canBack,
                canNavigateForward = store.history.canForward
            )
        }
    }

    // ---------------- Browsing / navigation ----------------

    fun refresh() {
        browseCtr.refreshDir()
    }
    fun navigateTo(path: Path) = browseCtr.navigateTo(path)
    fun enterDirectory(entry: FmEntry) = browseCtr.enterDirectory(entry)
    fun back(): Boolean = browseCtr.back()
    fun forward(): Boolean = browseCtr.forward()
    fun goParent(): Boolean = browseCtr.goParent()
    fun submitJump(targetInput: String): Boolean = browseCtr.submitJump(targetInput)
    fun navigateToSearchHit(hit: SearchHitView) = browseCtr.navigateToSearchHit(hit.path)
    fun setSortConfig(config: SortConfig) = browseCtr.setSortConfig(config)
    fun toggleHidden() = browseCtr.toggleHidden()

    // ---------------- Selection / multi-select ----------------
    fun toggleSelection(entry: FmEntry) = selectionCtr.toggleSelection(entry)
    fun swipeRangeSelect(entry: FmEntry) = selectionCtr.swipeRangeSelect(entry)
    fun selectAll() = selectionCtr.selectAll()
    fun clearSelection() = selectionCtr.clearSelection()

    /**
     * System back-key handling
     * In multi-select mode, exit multi-select first; otherwise go back
     * @return false while at the root directory
     */
    fun consumeBack(): Boolean {
        if (store.stateValue().multiSelect) {
            store.clearSelectionAndExitMulti()
            return true
        }
        // The system back key goes up one directory level, not back to a previously visited directory
        if (browseCtr.goParent()) return true
        // Root directory
        return false
    }

    // ---------------- Directory property scan ----------------

    fun startDirectoryScan(path: Path) = directoryScanCtr.startDirectoryScan(path)
    fun stopDirectoryScan() = directoryScanCtr.stopDirectoryScan()

    // ---------------- Search ----------------

    fun showSearchDialog() = searchCtr.showSearchDialog()
    fun submitSearch(keyword: String, caseSensitive: Boolean) = searchCtr.submitSearch(keyword, caseSensitive)
    fun clearSearch() = searchCtr.clearSearch()
    fun backToSearchSetup() = searchCtr.backToSearchSetup()

    // ---------------- Clipboard / paste ----------------
    fun copyEntry(entry: FmEntry) {
        store.setClipboard(FmClipboard(listOf(entry.path), false))
    }
    fun cutEntry(entry: FmEntry) {
        store.setClipboard(FmClipboard(listOf(entry.path), true))
    }
    fun requestPaste() = pasteCtr.requestPaste()
    fun resolvePasteConflict(resolution: ConflictResolution) = pasteCtr.resolvePasteConflict(resolution)

    /** After copy/cut registers the clipboard, close the bulk-operation dialog */
    fun bulkCopy() {
        val src = store.selectedEntries().map { it.path }
        if (!src.isEmpty()) {
            store.setClipboard(FmClipboard(src, false))
        }
        store.dismissDialog()
    }
    fun bulkCut() {
        val src = store.selectedEntries().map { it.path }
        if (!src.isEmpty()) {
            store.setClipboard(FmClipboard(src, true))
        }
        store.dismissDialog()
    }

    // ---------------- Compression ----------------

    fun bulkCompress() = compressCtr.bulkCompress()
    fun compressEntry(entry: FmEntry) = compressCtr.compressEntry(entry)
    fun onCompressSetupConfirmed(name: String, sources: List<Path>, options: CompressOptions) =
        compressCtr.onCompressSetupConfirmed(name, sources, options)
    fun onCompressOutputChoiceCurrent() = compressCtr.onCompressOutputChoiceCurrent()
    fun onCompressOutputChoiceSaf() = compressCtr.onCompressOutputChoiceSaf()
    fun onCompressOutputPickedCancelled() = compressCtr.onCompressOutputPickedCancelled()
    fun onCompressOutputPicked(treeUri: Uri) = compressCtr.onCompressOutputPicked(treeUri)
    fun resolveCompressConflict(resolution: ConflictResolution) = compressCtr.resolveCompressConflict(resolution)

    // ---------------- Extraction ----------------

    fun showExtract(entry: FmEntry) = extractCtr.showExtract(entry)
    fun onExtractSetupConfirmed(independentFolder: Boolean) = extractCtr.onExtractSetupConfirmed(independentFolder)
    fun onExtractPasswordConfirmed(password: String) = extractCtr.onExtractPasswordConfirmed(password)
    fun onExtractOutputChoiceCurrent() = extractCtr.onExtractOutputChoiceCurrent()
    fun onExtractOutputChoiceSaf() = extractCtr.onExtractOutputChoiceSaf()
    fun onExtractOutputPicked(treeUri: Uri) = extractCtr.onExtractOutputPicked(treeUri)
    fun onExtractOutputPickedCancelled() = extractCtr.onExtractOutputPickedCancelled()
    fun resolveExtractConflict(resolution: ConflictResolution) = extractCtr.resolveExtractConflict(resolution)

    // ---------------- Import ----------------

    fun showImportFilesDialog() {
        store.dismissDialog()
        store.updateState {
            it.copy(dialogIntent = DialogIntent.ImportFiles)
        }
    }
    fun showImportDirDialog() {
        store.dismissDialog()
        store.updateState {
            it.copy(dialogIntent = DialogIntent.ImportDir)
        }
    }
    fun onImportFiles(uris: List<Uri>) = importCtr.onImportFiles(uris)
    fun onImportDir(treeUri: Uri) = importCtr.onImportDir(treeUri)
    fun onImportCancelled() {
        store.dismissDialog()
    }

    // ---------------- Delete / rename / create / share ----------------

    fun stageSingleDelete(entry: FmEntry) = entryCtr.stageSingleDelete(entry)
    fun cancelStagedDelete() = entryCtr.cancelStagedDelete()
    fun deleteSelected(toTrash: Boolean) = entryCtr.deleteSelected(toTrash)
    fun rename(entry: FmEntry, newName: String, onSuccess: () -> Unit) = entryCtr.rename(entry, newName, onSuccess)
    fun submitCreate(name: String, isFolder: Boolean, onDone: (Boolean) -> Unit) = entryCtr.submitCreate(name, isFolder, onDone)
    fun submitRename(entry: FmEntry, newName: String, onSuccess: () -> Unit) = entryCtr.submitRename(entry, newName, onSuccess)
    fun validateRename(entry: FmEntry, newName: String): String? = entryCtr.validateRename(entry, newName)
    fun showShare(entry: FmEntry) = entryCtr.showShare(entry)

    // ---------------- Trash ----------------

    fun loadTrashList() = trashCtr.loadTrashList()
    fun refreshTrashList() = trashCtr.refreshTrashList()
    fun closeTrash() = trashCtr.closeTrash()
    fun setTrashSortConfig(config: TrashSortConfig) = trashCtr.setTrashSortConfig(config)
    fun trashRestore(items: List<TrashItem>, resolutions: Map<String, ConflictResolution>) = trashCtr.trashRestore(items, resolutions)
    fun beginTrashRestore(items: List<TrashItem>) = trashCtr.beginTrashRestore(items)
    fun resolveTrashRestoreConflict(resolution: ConflictResolution) = trashCtr.resolveTrashRestoreConflict(resolution)
    fun restoreTrashItem(item: TrashItem) = trashCtr.restoreTrashItem(item)
    fun trashRestoreAll() = trashCtr.trashRestoreAll()
    fun purgeTrashItem(item: TrashItem) = trashCtr.purgeTrashItem(item)
    fun trashPurge(items: List<TrashItem>) = trashCtr.trashPurge(items)
    fun trashClear() = trashCtr.trashClear()
    fun toggleTrashSelection(uuid: String) = trashCtr.toggleTrashSelection(uuid)
    fun selectAllTrash() = trashCtr.selectAllTrash()
    fun clearTrashSelection() = trashCtr.clearTrashSelection()
    fun trashRangeSelect(swipeItem: TrashItem) = trashCtr.trashRangeSelect(swipeItem)
    fun selectedTrashItems(): List<TrashItem> = trashCtr.selectedTrashItems()

    // ---------------- Text editor ----------------

    /** Opens a file and loads its content asynchronously */
    fun editorOpen(path: Path) = editorCtr.open(path)
    /** Editor content-change callback */
    fun editorTextChanged() = editorCtr.onTextChanged()
    /** Saves the editor content */
    fun editorSave(onDone: (Boolean) -> Unit = {}) = editorCtr.save(onDone)
    /** Cancels an in-progress save */
    fun editorCancelSave() = editorCtr.cancelSave()
    /** Requests the unsaved-changes exit confirmation dialog */
    fun editorRequestExitConfirm() = editorCtr.requestExitConfirm()
    /** Cancels the exit confirmation dialog */
    fun editorCancelExitConfirm() = editorCtr.cancelExitConfirm()
    /** Whether unsaved changes exist */
    fun editorHasDirty(): Boolean = editorCtr.hasDirty()

    // ---------------- Dialogs / Snackbar ----------------

    fun dismissDialog() = store.dismissDialog()
    fun consumeSnackbar() = store.updateState { it.copy(snackbar = null) }
    fun consumeLocateHighlight() = store.updateState { it.copy(locateHighlightPath = null) }

    // ---------------- Task progress sync ----------------

    /** Subscribes to [TaskManager] task state and progress, syncing them into the state store. */
    fun observeTasks() {
        viewModelScope.launch {
            taskManager.state.collect { st ->
                store.updateState { it.copy(taskState = st) }
            }
        }
        viewModelScope.launch {
            taskManager.progress.collect { p ->
                store.updateState { it.copy(taskProgress = p) }
            }
        }
    }

    /** Cancels the current task */
    fun cancelCurrentTask() {
        taskManager.cancel()
    }

    /** Returns the application context */
    fun appContext(): Context = context

    companion object {
        private const val TAG = "FileManagerViewModel"
        private const val TRASH_SUBDIR = "fileManagerTrash"

        /** [SavedStateHandle] key: absolute path of the accessible scope directory. */
        const val KEY_ROOT_PATH = "fm.rootPath"

        /** [SavedStateHandle] key: optional initial current directory. */
        const val KEY_CURRENT_PATH = "fm.currentPath"
    }
}
