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

import com.endiq.turtlelauncher.filemanager.logic.entry.FmEntry
import com.endiq.turtlelauncher.filemanager.logic.entry.FmListResult
import com.endiq.turtlelauncher.filemanager.logic.ops.ConflictResolution
import com.endiq.turtlelauncher.filemanager.logic.ops.DirStats
import com.endiq.turtlelauncher.filemanager.logic.ops.PasteRequest
import com.endiq.turtlelauncher.filemanager.logic.task.TaskProgress
import com.endiq.turtlelauncher.filemanager.logic.task.TaskState
import com.endiq.turtlelauncher.filemanager.logic.trash.TrashItem
import com.endiq.turtlelauncher.ui.code_editor.EditorState
import java.nio.file.Path

/** 目录属性扫描状态 */
data class DirScanUiState(
    val running: Boolean,
    val stats: DirStats?
)

/** 文本编辑器状态 */
data class EditorUiState(
    /** 正在编辑的文件路径 */
    val path: Path? = null,
    /** 文件内容状态（加载中 / 加载完成） */
    val state: EditorState = EditorState.Loading,
    /** 文件是否可写，不可写时以只读方式打开 */
    val writable: Boolean = true,
    /** Whether unsaved changes exist */
    val dirty: Boolean = false,
    /** 是否正在保存 */
    val saving: Boolean = false,
    /** 是否请求显示退出确认弹窗 */
    val exitConfirm: Boolean = false,
    /** 打开 / 加载失败的错误提示 */
    val error: String? = null
)

/** 搜索状态 */
data class SearchUiState(
    /** 是否正在搜索 */
    val running: Boolean = false,
    /** 当前正在扫描的目录 */
    val currentDir: Path? = null,
    /** Search results列表 */
    val hits: List<SearchHitView> = emptyList(),
    /** 最近一次搜索的关键词 */
    val lastKeyword: String = ""
)

/** 搜索命中结果视图 */
data class SearchHitView(
    val path: Path,
    val name: String,
    val isDirectory: Boolean,
    val size: Long
)

/** 文件管理器状态集合 */
data class FileManagerUiState(
    val currentDir: Path? = null,
    /** 刷新流水线是否运行中 */
    val refreshing: Boolean = false,
    val visibleEntries: List<FmEntry> = emptyList(),
    val rawList: RawList? = null,
    val folderCount: Int = 0,
    val fileCount: Int = 0,
    val selection: Set<String> = emptySet(),
    val multiSelect: Boolean = false,
    val clipboard: FmClipboard? = null,
    val sortConfig: SortConfig = SortConfig(),
    val showHidden: Boolean = false,
    val trashSortConfig: TrashSortConfig = TrashSortConfig(),
    val taskState: TaskState = TaskState.Idle,
    val taskProgress: TaskProgress? = null,
    val trashView: TrashViewState = TrashViewState.Idle,
    val snackbar: FmSnackbar? = null,
    val locateHighlightPath: Path? = null,
    val canNavigateBack: Boolean = false,
    val canNavigateForward: Boolean = false,
    /** 当前打开的Dialog intents；为空表示无对话框 */
    val dialogIntent: DialogIntent? = null
) {
    val canBack: Boolean get() = rawList?.let { it.currentDir != it.rootDir } ?: false
}

/** 原始浏览结果 */
data class RawList(
    val currentDir: Path,
    val rootDir: Path,
    val ancestors: List<Path>,
    val entries: List<FmEntry>,
    val hasSubdirectory: Boolean,
    val writable: Boolean
) {
    companion object {
        fun of(result: FmListResult): RawList = RawList(
            currentDir = result.currentDir,
            rootDir = result.rootDir,
            ancestors = result.ancestors,
            entries = result.entries,
            hasSubdirectory = result.hasSubdirectory,
            writable = result.writable
        )
    }
}

/** 回收站视图 */
sealed interface TrashViewState {
    data object Idle : TrashViewState
    /** 回收站已打开 */
    data class Opened(
        val rawItems: List<TrashItem>,
        val trashListView: TrashListView
    ) : TrashViewState
}

data class TrashItemView(
    val uuid: String,
    val name: String,
    val deletedAt: Long,
    val isFolder: Boolean,
    val size: Long,
    val corrupted: Boolean
)

/** Trash internal loading view */
data class TrashListView(
    val items: List<TrashItemView> = emptyList(),
    val totalSize: Long = 0L,
    val total: Int = 0,
    val loading: Boolean = false,
    val selection: Set<String> = emptySet(),
    val multiSelect: Boolean = false
)

/** Snackbar message */
data class FmSnackbar(
    val text: String,
    val long: Boolean = true
)

/** Dialog intents */
sealed interface DialogIntent {
    /** Search settings dialog */
    data object Search : DialogIntent
    /** Search task dialog */
    data object SearchTask : DialogIntent
    /** Search result list dialog */
    data object SearchResult : DialogIntent

    /**
     * Compression settings dialog
     * @param defaultName default archive name (without suffix)
     * @param sources absolute paths of the entries to compress
     */
    data class CompressSetup(
        val defaultName: String,
        val sources: List<Path>
    ) : DialogIntent
    /** Compression output location selection */
    data object CompressOutputChoice : DialogIntent
    /** Marker selecting the compression output location: pick the output directory via SAF. */
    data object CompressOutputPick : DialogIntent
    /** Conflict dialog when the output directory already has a same-named archive */
    data class CompressConflict(
        val fileName: String
    ) : DialogIntent

    /**
     * Extraction settings dialog
     * @param archivePath absolute path of the archive
     * @param archiveName archive file name
     */
    data class ExtractSetup(
        val archivePath: Path,
        val archiveName: String
    ) : DialogIntent
    /** Extraction output location selection */
    data object ExtractOutputChoice : DialogIntent
    /** Marker selecting the extraction output location: pick the output directory via SAF */
    data object ExtractOutputPick : DialogIntent
    /** Conflict dialog when the extraction target already has same-named top-level content */
    data class ExtractConflict(
        val name: String
    ) : DialogIntent
    /** Archive password input dialog */
    data class ExtractPassword(
        val errorText: String? = null
    ) : DialogIntent

    /** Multi-select files via SAF */
    data object ImportFiles : DialogIntent
    /** Pick a directory via SAF */
    data object ImportDir : DialogIntent

    /** Paste conflict flow */
    data class PasteConflict(
        val request: PasteRequest.ResolveRequest,
        val decidedResolutions: List<ConflictResolution> = emptyList(),
        val currentIndex: Int = 0
    ) : DialogIntent

    /** Restore-from-trash conflict flow */
    data class TrashRestoreConflict(
        val trashItems: List<TrashItem>,
        val conflictItems: List<Pair<TrashItem, Int>>,
        val resolutions: Map<String, ConflictResolution> = emptyMap(),
        val pendingIndex: Int = 0
    ) : DialogIntent
}