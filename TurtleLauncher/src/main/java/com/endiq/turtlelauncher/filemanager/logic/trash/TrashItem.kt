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

package com.endiq.turtlelauncher.filemanager.logic.trash

import org.json.JSONObject
import java.nio.file.Path

/**
 * Trash entry metadata
 * @param originalPath original absolute path of the deleted entry
 * @param deletedAt deletion time (epoch millis)
 * @param name name of the deleted entry
 * @param isFolder whether the entry is a directory
 * @param corrupted corruption marker
 */
data class TrashMeta(
    val originalPath: String,
    val deletedAt: Long,
    val name: String,
    val isFolder: Boolean,
    val corrupted: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("originalPath", originalPath)
        put("deletedAt", deletedAt)
        put("name", name)
        put("isFolder", isFolder)
        put("corrupted", corrupted)
    }

    companion object {
        fun fromJson(text: String): TrashMeta? = runCatching {
            val obj = JSONObject(text)
            TrashMeta(
                originalPath = obj.getString("originalPath"),
                deletedAt = obj.getLong("deletedAt"),
                name = obj.getString("name"),
                isFolder = obj.getBoolean("isFolder"),
                corrupted = obj.optBoolean("corrupted", false)
            )
        }.getOrNull()
    }
}

/**
 * Trash list item
 * @param contentDir path of the stored content inside the trash
 * @param size total size of the trashed content (bytes)
 */
data class TrashItem(
    val uuid: String,
    val meta: TrashMeta,
    val contentDir: Path,
    val size: Long
) {
    val isFolder: Boolean get() = meta.isFolder
    val name: String get() = meta.name
    val deletedAt: Long get() = meta.deletedAt
}