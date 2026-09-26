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
import com.endiq.turtlelauncher.filemanager.logic.FileManagerLogic
import com.endiq.turtlelauncher.filemanager.logic.SearchResult
import com.endiq.turtlelauncher.filemanager.logic.task.RunResult
import com.endiq.turtlelauncher.filemanager.logic.task.TaskKind
import com.endiq.turtlelauncher.filemanager.logic.task.TaskManager
import com.endiq.turtlelauncher.filemanager.viewmodel.DialogIntent
import com.endiq.turtlelauncher.filemanager.viewmodel.FmSnackbar
import com.endiq.turtlelauncher.filemanager.viewmodel.FmStateStore
import com.endiq.turtlelauncher.filemanager.viewmodel.SearchHitView
import com.endiq.turtlelauncher.filemanager.viewmodel.SearchUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Search controller */
class SearchController(
    private val logic: FileManagerLogic,
    private val taskManager: TaskManager,
    private val store: FmStateStore,
    private val coroutineScope: CoroutineScope
) {
    /**
     * Opens the search entry
     * Shows the result list directly when this session already has results; otherwise the search settings
     */
    fun showSearchDialog() {
        store.dismissDialog()
        val hasResult = store.searchUiValue().lastKeyword.isNotBlank()
        store.updateState {
            it.copy(dialogIntent = if (hasResult) DialogIntent.SearchResult else DialogIntent.Search)
        }
    }

    /**
     * Submits a search
     * After non-empty validation, starts the search task and shows the results on completion
     */
    fun submitSearch(keyword: String, caseSensitive: Boolean) {
        if (keyword.isBlank()) return
        val startDir = store.history.currentPath
        store.setSearchUi(
            SearchUiState(
                running = true,
                currentDir = startDir,
                hits = emptyList(),
                lastKeyword = keyword
            )
        )
        store.updateState {
            it.copy(dialogIntent = DialogIntent.SearchTask)
        }

        coroutineScope.launch(Dispatchers.IO) {
            val result = taskManager.run(TaskKind.SEARCH) {
                logic.search(startDir, keyword, caseSensitive) { dir ->
                    report(currentName = dir.toString())
                    store.updateSearchUi { it.copy(currentDir = dir) }
                }
            }
            when (result) {
                is RunResult.Ok -> {
                    when (val search = result.value) {
                        is SearchResult.Ok -> {
                            // Hidden-file filtering is done by the data-control layer
                            val showHidden = FmConfig.showHidden()
                            val hits = search.hits
                                .filterNot { !showHidden && it.hidden }
                                .map { SearchHitView(it.path, it.name, it.isDirectory, it.size) }

                            store.setSearchUi(SearchUiState(running = false, currentDir = null, hits = hits, lastKeyword = keyword))
                            store.updateState { it.copy(dialogIntent = DialogIntent.SearchResult) }
                        }
                        is SearchResult.Failed -> {
                            store.setSearchUi(SearchUiState(running = false, currentDir = null, lastKeyword = keyword))
                            store.emitSnackbar(FmSnackbar(store.stringResolver(R.string.fm_error_search_failed)))
                            store.updateState { it.copy(dialogIntent = DialogIntent.SearchResult) }
                        }
                    }
                }
                is RunResult.Failed -> {
                    store.setSearchUi(SearchUiState(running = false, currentDir = null, lastKeyword = keyword))
                    store.emitSnackbar(FmSnackbar(store.stringResolver(R.string.fm_error_search_failed)))
                    store.updateState { it.copy(dialogIntent = DialogIntent.SearchResult) }
                }
                RunResult.Cancelled -> {
                    store.updateSearchUi { it.copy(running = false, currentDir = null) }
                    store.updateState { it.copy(dialogIntent = DialogIntent.SearchResult) }
                }
                is RunResult.Rejected -> {
                    store.setSearchUi(SearchUiState(running = false, currentDir = null, lastKeyword = keyword))
                    store.emitSnackbar(FmSnackbar(store.stringResolver(R.string.fm_task_busy)))
                    store.updateState { it.copy(dialogIntent = DialogIntent.SearchResult) }
                }
            }
        }
    }

    /** Clears the search results and returns to the search settings */
    fun clearSearch() {
        store.setSearchUi(SearchUiState())
        store.updateState {
            it.copy(dialogIntent = DialogIntent.Search)
        }
    }

    /** Returns from the results list to the search settings (start a new search) */
    fun backToSearchSetup() {
        store.updateState {
            it.copy(dialogIntent = DialogIntent.Search)
        }
    }
}
