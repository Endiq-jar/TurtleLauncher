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
import java.nio.file.Path

/** Output target abstraction */
internal sealed interface OutputTarget {
    /** Local directory (inside the accessible scope) */
    data class Local(val dir: Path) : OutputTarget
    /** SAF tree directory root */
    data class Saf(val treeUri: Uri) : OutputTarget
    /** Named subdirectory under an SAF tree (standalone folder) */
    data class SafDir(val treeUri: Uri, val name: String) : OutputTarget
}

/**
 * Finds a child document by name inside an SAF tree directory
 * @return its document URI when it exists
 */
internal fun findChildDocument(context: Context, treeUri: Uri, name: String): Uri? {
    return runCatching {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val child = DocumentsContract.buildDocumentUriUsingTree(treeUri, "$docId/$name")
        if (DocumentsContract.isDocumentUri(context, child)) {
            //Existence is settled by trying to open it
            context.contentResolver.openInputStream(child)?.close()
            child
        } else null
    }.getOrNull()
}

/** Infers the MIME type from the file extension */
internal fun guessMime(name: String): String {
    val lower = name.lowercase()
    return when {
        lower.endsWith(".zip") -> "application/zip"
        lower.endsWith(".7z") -> "application/x-7z-compressed"
        lower.endsWith(".tar") -> "application/x-tar"
        lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
        lower.endsWith(".png") -> "image/png"
        lower.endsWith(".gif") -> "image/gif"
        lower.endsWith(".json") -> "application/json"
        lower.endsWith(".txt") || lower.endsWith(".log") -> "text/plain"
        lower.endsWith(".xml") -> "text/xml"
        lower.endsWith(".html") || lower.endsWith(".htm") -> "text/html"
        else -> "application/octet-stream"
    }
}
