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

package com.endiq.turtlelauncher.filemanager.events

import android.os.Bundle
import android.os.Message
import android.os.Messenger
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

/**
 * File change event of the file manager
 * @param type the event type
 * @param changedDirs absolute paths of the directories that changed
 * @param id unique event ID
 */
@Parcelize
data class FileManagerEvent(
    val type: Type,
    val changedDirs: List<String>,
    val id: String = UUID.randomUUID().toString()
) : Parcelable {
    enum class Type {
        /** Created file/folder */
        CREATE,
        /** Entry renamed */
        RENAME,
        /** Deleted (moved to trash) */
        DELETE,
        /** Copy/paste */
        COPY_PASTE,
        /** Compress */
        ARCHIVE,
        /** Extract */
        EXTRACT,
        /** SAF import finished */
        IMPORT,
        /** Restored from trash */
        TRASH_RESTORE,
        /** Permanently deleted from trash */
        TRASH_PURGE,
        /** Trash emptied */
        TRASH_CLEAR
    }

    companion object {
        const val KEY_EVENT = "fm_event"

        /**
         * Wraps the event into a [Message] sendable via Messenger
         */
        fun toMessage(event: FileManagerEvent): Message = Message.obtain().apply {
            what = MSG_EVENT
            data = Bundle().apply {
                putParcelable(KEY_EVENT, event)
            }
        }

        /**
         * Parses an event from a [Message]; illegal messages return null
         */
        fun fromMessage(message: Message): FileManagerEvent? {
            if (message.what != MSG_EVENT) return null
            val data = message.data ?: return null
            data.classLoader = FileManagerEvent::class.java.classLoader
            return data.getParcelable(KEY_EVENT)
        }

        const val MSG_REGISTER_CLIENT = 1
        const val MSG_UNREGISTER_CLIENT = 2
        const val MSG_EVENT = 100
    }
}

fun Messenger.sendEvent(event: FileManagerEvent) {
    runCatching {
        send(FileManagerEvent.toMessage(event))
    }
}
