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

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.endiq.turtlelauncher.game.version.installed.Version
import com.endiq.turtlelauncher.game.version.installed.VersionsManager
import com.endiq.turtlelauncher.utils.hasStoragePermission
import com.endiq.turtlelauncher.utils.logging.Logger
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private const val TAG = "VersionCardManager"

/** Persistent data of the version card record */
private data class VersionCardRecordDto(
    @SerializedName("cardId")
    val cardId: String = "",
    @SerializedName("versionName")
    val versionName: String = "",
    @SerializedName("dirType")
    val dirType: String = "",
    @SerializedName("dirPath")
    val dirPath: String = ""
) {
    fun toRecord() = VersionCardRecord(
        cardId = cardId,
        versionName = versionName,
        dir = when (dirType) {
            VersionCardManager.DIR_TYPE_CUSTOM -> VersionCardDir.Custom(dirPath)
            else -> VersionCardDir.Default
        }
    )

    companion object {
        fun fromRecord(record: VersionCardRecord) = VersionCardRecordDto(
            cardId = record.cardId,
            versionName = record.versionName,
            dirType = when (record.dir) {
                is VersionCardDir.Custom -> VersionCardManager.DIR_TYPE_CUSTOM
                VersionCardDir.Default -> VersionCardManager.DIR_TYPE_DEFAULT
            },
            dirPath = (record.dir as? VersionCardDir.Custom)?.path ?: ""
        )
    }
}

/**
 * Version card manager
 */
object VersionCardManager {
    const val DIR_TYPE_DEFAULT = "default"
    const val DIR_TYPE_CUSTOM = "custom"

    private const val KEY_RECORDS = "versionCards"

    private val mmkv: MMKV by lazy { MMKV.mmkvWithID("home_version_cards") }
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _cards = MutableStateFlow<List<VersionCardState>>(emptyList())

    /** All version cards and their current availability states */
    val cards: StateFlow<List<VersionCardState>> = _cards.asStateFlow()

    init {
        _cards.value = loadRecords().map { record ->
            VersionCardState(record, VersionCardStatus.Loading)
        }
        recheck()
        //Sync card availability after the version list refreshes
        VersionsManager.registerListener {
            recheck()
        }
    }

    /** Whether the given version already has a card */
    fun hasCard(versionName: String, gameHome: String): Boolean {
        val dir = VersionCardDir.fromGameHome(gameHome)
        return _cards.value.any { it.record.versionName == versionName && it.record.dir == dir }
    }

    /**
     * Creates a card for the given version, no-op when one already exists
     * @return whether creation succeeded
     */
    fun addCard(version: Version): Boolean {
        val dir = VersionCardDir.fromGameHome(version.getGameHome())
        synchronized(this) {
            if (hasCard(version.getVersionName(), version.getGameHome())) return false
            val record = VersionCardRecord(
                cardId = UUID.randomUUID().toString(),
                versionName = version.getVersionName(),
                dir = dir
            )
            _cards.update { states ->
                states + VersionCardState(record, VersionCardStatus.Available(version))
            }
            saveRecords()
        }
        return true
    }

    /** Remove the given card */
    fun removeCard(cardId: String) {
        synchronized(this) {
            val removed = _cards.value.any { it.record.cardId == cardId }
            if (!removed) return
            _cards.update { states -> states.filterNot { it.record.cardId == cardId } }
            saveRecords()
        }
    }

    /**
     * After a version rename, sync card records so they keep pointing at the renamed version
     */
    fun onVersionRenamed(gameHome: String, oldName: String, newName: String) {
        val dir = VersionCardDir.fromGameHome(gameHome)
        synchronized(this) {
            val changed = _cards.value.any { it.record.versionName == oldName && it.record.dir == dir }
            if (!changed) return
            _cards.update { states ->
                states.map { state ->
                    if (state.record.versionName == oldName && state.record.dir == dir) {
                        state.copy(record = state.record.copy(versionName = newName))
                    } else {
                        state
                    }
                }
            }
            saveRecords()
        }
        recheck()
    }

    /**
     * Re-checks availability of every card
     */
    private fun recheck() {
        scope.launch {
            val current = _cards.value
            if (current.isEmpty()) return@launch
            val updated = current.map { state ->
                state.copy(status = resolveStatus(state.record))
            }
            //No emission without state change, shunning pointless home-grid recompositions
            if (updated != current) _cards.value = updated
        }
    }

    /** Locate and load the version from the record, deriving availability */
    private fun resolveStatus(record: VersionCardRecord): VersionCardStatus {
        val gameHome = record.dir.resolveGameHome()
        if (record.dir is VersionCardDir.Custom && !hasStoragePermission) {
            return VersionCardStatus.Inaccessible
        }
        if (!File(gameHome).exists()) return VersionCardStatus.Inaccessible
        val version = VersionsManager.loadVersion(gameHome, record.versionName)
            ?: return VersionCardStatus.Deleted
        Logger.info(
            TAG,
            "Version card loaded version: ${version.getVersionName()}, " +
                    "Path: (${version.getVersionPath()}), " +
                    "Info: ${version.getVersionInfo()?.getInfoString()}"
        )
        return VersionCardStatus.Available(version)
    }

    private fun loadRecords(): List<VersionCardRecord> {
        val json = mmkv.decodeString(KEY_RECORDS, "").orEmpty()
        if (json.isBlank()) return emptyList()
        return runCatching {
            gson.fromJson(json, Array<VersionCardRecordDto>::class.java)
                .orEmpty()
                .map { it.toRecord() }
                .filter { it.cardId.isNotBlank() && it.versionName.isNotBlank() }
        }.onFailure { e ->
            Logger.error(TAG, "Failed to parse version card records.", e)
        }.getOrDefault(emptyList())
    }

    private fun saveRecords() {
        val json = gson.toJson(_cards.value.map { VersionCardRecordDto.fromRecord(it.record) })
        mmkv.encode(KEY_RECORDS, json)
    }
}
