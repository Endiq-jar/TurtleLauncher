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

package com.endiq.turtlelauncher.filemanager.config

import com.tencent.mmkv.MMKV

private const val KEY_SHOW_HIDDEN = "show_hidden"
private const val KEY_SORT_FIELD = "sort_field"
private const val KEY_SORT_ASC = "sort_ascending"
private const val KEY_FOLDER_FIRST = "folder_first"
private const val KEY_TRASH_SORT_FIELD = "trash_sort_field"
private const val KEY_TRASH_SORT_ASC = "trash_sort_ascending"
private const val KEY_TRASH_FOLDER_FIRST = "trash_folder_first"
private const val KEY_EDITOR_WORDWRAP = "editor_wordwrap"
private const val KEY_EDITOR_COMPLETION = "editor_completion"
private const val KEY_EDITOR_LINE_NUMBER = "editor_line_number"
private const val KEY_EDITOR_HIGHLIGHT_LINE = "editor_highlight_line"
private const val KEY_EDITOR_NON_PRINTABLE = "editor_non_printable"
private const val KEY_EDITOR_FONT_SIZE = "editor_font_size"
private const val KEY_EDITOR_SEARCH_MATCH_CASE = "editor_search_match_case"
private const val KEY_EDITOR_SEARCH_WHOLE_WORD = "editor_search_whole_word"
private const val KEY_EDITOR_SEARCH_REGEX = "editor_search_regex"

/** File manager configuration store */
object FmConfig {
    private const val MMKV_ID = "turtle_file_manager"

    private fun mmkv(): MMKV = MMKV.mmkvWithID(MMKV_ID, MMKV.SINGLE_PROCESS_MODE)

    /** Sets whether hidden files are shown */
    fun setShowHidden(value: Boolean) {
        mmkv().putBoolean(KEY_SHOW_HIDDEN, value)
    }

    /** Whether hidden files are shown */
    fun showHidden(): Boolean = mmkv().decodeBool(KEY_SHOW_HIDDEN, true)

    /** Sets the sort field of the main list */
    fun setSortField(value: String) {
        mmkv().putString(KEY_SORT_FIELD, value)
    }

    /** Main list sort field */
    fun sortField(): String = mmkv().decodeString(KEY_SORT_FIELD) ?: SortField.NAME.name

    /** Sets whether the main list sorts ascending */
    fun setSortAscending(value: Boolean) {
        mmkv().putBoolean(KEY_SORT_ASC, value)
    }

    /** Whether the main list sorts ascending */
    fun sortAscending(): Boolean = mmkv().decodeBool(KEY_SORT_ASC, true)

    /** Sets whether the main list puts directories first */
    fun setFolderFirst(value: Boolean) {
        mmkv().putBoolean(KEY_FOLDER_FIRST, value)
    }

    /** Whether the main list puts directories first */
    fun folderFirst(): Boolean = mmkv().decodeBool(KEY_FOLDER_FIRST, true)

    /** Sets the sort field of the trash list */
    fun setTrashSortField(value: String) {
        mmkv().putString(KEY_TRASH_SORT_FIELD, value)
    }

    /** Trash list sort field */
    fun trashSortField(): String = mmkv().decodeString(KEY_TRASH_SORT_FIELD) ?: TrashSortField.DELETED.name

    /** Sets whether the trash list sorts ascending */
    fun setTrashSortAscending(value: Boolean) {
        mmkv().putBoolean(KEY_TRASH_SORT_ASC, value)
    }

    /** Whether the trash list sorts ascending */
    fun trashSortAscending(): Boolean = mmkv().decodeBool(KEY_TRASH_SORT_ASC, false)

    /** Sets whether the trash list puts directories first */
    fun setTrashFolderFirst(value: Boolean) {
        mmkv().putBoolean(KEY_TRASH_FOLDER_FIRST, value)
    }

    /** Whether the trash list puts directories first */
    fun trashFolderFirst(): Boolean = mmkv().decodeBool(KEY_TRASH_FOLDER_FIRST, true)

    /** Sets editor line wrapping */
    fun setEditorWordwrap(value: Boolean) {
        mmkv().putBoolean(KEY_EDITOR_WORDWRAP, value)
    }

    /** Editor line wrapping */
    fun editorWordwrap(): Boolean = mmkv().decodeBool(KEY_EDITOR_WORDWRAP, true)

    /** Sets the editor code-completion toggle */
    fun setEditorCompletionEnabled(value: Boolean) {
        mmkv().putBoolean(KEY_EDITOR_COMPLETION, value)
    }

    /** Editor code-completion toggle */
    fun editorCompletionEnabled(): Boolean = mmkv().decodeBool(KEY_EDITOR_COMPLETION, true)

    /** Sets whether the editor shows line numbers */
    fun setEditorLineNumber(value: Boolean) {
        mmkv().putBoolean(KEY_EDITOR_LINE_NUMBER, value)
    }

    /** Whether the editor shows line numbers */
    fun editorLineNumber(): Boolean = mmkv().decodeBool(KEY_EDITOR_LINE_NUMBER, true)

    /** Sets editor current-line highlight */
    fun setEditorHighlightLine(value: Boolean) {
        mmkv().putBoolean(KEY_EDITOR_HIGHLIGHT_LINE, value)
    }

    /** Editor current-line highlight */
    fun editorHighlightLine(): Boolean = mmkv().decodeBool(KEY_EDITOR_HIGHLIGHT_LINE, true)

    /** Sets whether the editor shows invisible characters */
    fun setEditorNonPrintable(value: Boolean) {
        mmkv().putBoolean(KEY_EDITOR_NON_PRINTABLE, value)
    }

    /** The editor shows invisible characters */
    fun editorNonPrintable(): Boolean = mmkv().decodeBool(KEY_EDITOR_NON_PRINTABLE, false)

    /** Sets the editor font size (px; 0 means unset, use the default) */
    fun setEditorFontSize(value: Float) {
        mmkv().putFloat(KEY_EDITOR_FONT_SIZE, value)
    }

    /** Editor font size (px; 0 means unset, use the default) */
    fun editorFontSize(): Float = mmkv().decodeFloat(KEY_EDITOR_FONT_SIZE, 0f)

    /** Sets case-sensitive search */
    fun setEditorSearchMatchCase(value: Boolean) {
        mmkv().putBoolean(KEY_EDITOR_SEARCH_MATCH_CASE, value)
    }

    /** Case-sensitive search */
    fun editorSearchMatchCase(): Boolean = mmkv().decodeBool(KEY_EDITOR_SEARCH_MATCH_CASE, false)

    /** Sets whole-word search */
    fun setEditorSearchWholeWord(value: Boolean) {
        mmkv().putBoolean(KEY_EDITOR_SEARCH_WHOLE_WORD, value)
    }

    /** Whole-word search */
    fun editorSearchWholeWord(): Boolean = mmkv().decodeBool(KEY_EDITOR_SEARCH_WHOLE_WORD, false)

    /** Sets regex search */
    fun setEditorSearchRegex(value: Boolean) {
        mmkv().putBoolean(KEY_EDITOR_SEARCH_REGEX, value)
    }

    /** Regex search */
    fun editorSearchRegex(): Boolean = mmkv().decodeBool(KEY_EDITOR_SEARCH_REGEX, false)

    enum class SortField { NAME, SIZE, MODIFIED }

    enum class TrashSortField { NAME, DELETED }
}