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

package com.endiq.turtlelauncher.filemanager.logic.editor

/** Size cap for editable files; larger files are refused as text */
const val MAX_EDIT_SIZE: Long = 20L * 1024 * 1024

/**
 * Common text format extensions (lowercase)
 * Covers documents, markup, config files and common source code formats
 */
private val KNOWN_TEXT_EXTENSIONS = setOf(
    // Plain text / documents
    "txt", "text", "md", "markdown", "log", "rtf",
    // Markup / serialization
    "json", "yaml", "yml", "toml", "xml", "html", "htm", "css",
    // Configuration files
    "ini", "cfg", "conf", "properties", "env", "editorconfig",
    // Common source code
    "js", "mjs", "cjs", "ts", "jsx", "tsx", "kt", "kts", "java",
    "c", "h", "cpp", "hpp", "cc", "cxx", "cs", "py", "sh", "bash",
    "zsh", "bat", "cmd", "ps1", "sql", "gradle", "groovy", "rb",
    "go", "rs", "php", "swift", "scala", "lua", "pl", "r",
    // Data / other
    "csv", "tsv", "diff", "patch", "gitignore", "gitattributes"
)

/**
 * Checks whether a file name matches a known common text format
 */
fun isKnownTextFile(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext.isNotEmpty() && ext in KNOWN_TEXT_EXTENSIONS
}
