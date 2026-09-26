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

package com.endiq.turtlelauncher.ui.screens.content.home.version

import com.google.gson.annotations.SerializedName
import com.endiq.turtlelauncher.game.path.GamePathManager
import com.endiq.turtlelauncher.game.version.installed.Version

/**
 * Game directory holding the card's version
 */
sealed interface VersionCardDir {
    /** Resolves to the actual game directory path */
    fun resolveGameHome(): String

    /** Launcher default game directory */
    data object Default : VersionCardDir {
        override fun resolveGameHome(): String = GamePathManager.getDefaultPath()
    }

    /** User custom game directories */
    data class Custom(val path: String) : VersionCardDir {
        override fun resolveGameHome(): String = path
    }

    companion object {
        /** Derives the directory type from the actual game directory path */
        fun fromGameHome(gameHome: String): VersionCardDir =
            if (gameHome == GamePathManager.getDefaultPath()) Default else Custom(gameHome)
    }
}

/**
 * Persistent record of a version card
 */
data class VersionCardRecord(
    @SerializedName("cardId")
    val cardId: String,
    @SerializedName("versionName")
    val versionName: String,
    @SerializedName("dir")
    val dir: VersionCardDir
)

/** Version card availability state */
sealed interface VersionCardStatus {
    /** First check not finished yet */
    data object Loading : VersionCardStatus
    /** Version available */
    data class Available(val version: Version) : VersionCardStatus
    /** Game directory accessible, but the version is gone (deleted or folder corrupted) */
    data object Deleted : VersionCardStatus
    /** Path inaccessible: no storage permission, or the game directory is gone */
    data object Inaccessible : VersionCardStatus
}

/** Full state of a version card */
data class VersionCardState(
    val record: VersionCardRecord,
    val status: VersionCardStatus
)
