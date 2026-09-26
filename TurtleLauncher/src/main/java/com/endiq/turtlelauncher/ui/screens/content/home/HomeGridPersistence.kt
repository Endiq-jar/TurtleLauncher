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

package com.endiq.turtlelauncher.ui.screens.content.home

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.endiq.cardgrid.state.GridCard
import com.tencent.mmkv.MMKV

/** Persistent data of a single card */
data class HomeCardSnapshot(
    @SerializedName("id")
    val id: String = "",
    @SerializedName("type")
    val type: String = "",
    @SerializedName("x")
    val x: Int = 0,
    @SerializedName("y")
    val y: Int = 0,
    @SerializedName("width")
    val width: Int = 0,
    @SerializedName("height")
    val height: Int = 0
)

/** Persistent data of the grid layout, [columns] = grid columns at save time */
data class HomeGridSnapshot(
    @SerializedName("columns")
    val columns: Int = 0,
    @SerializedName("cards")
    val cards: List<HomeCardSnapshot> = emptyList()
)

/**
 * Home grid layout persistence:
 * Stores Gson-serialized JSON in a dedicated MMKV instance, separate from launcher settings storage,
 * system cards excluded from persistence.
 */
object HomeGridStore {
    private const val KEY_LAYOUT = "homeCardLayout"

    private val mmkv: MMKV by lazy { MMKV.mmkvWithID("home_grid") }

    private val gson = Gson()

    /** Read the layout snapshot, null when there's no valid data */
    fun load(): HomeGridSnapshot? {
        val json = mmkv.decodeString(KEY_LAYOUT, "") ?: ""
        if (json.isBlank()) return null
        return runCatching {
            gson.fromJson(json, HomeGridSnapshot::class.java)
        }.getOrNull()?.takeIf { it.cards.isNotEmpty() }
    }

    /** Save the layout snapshot (user cards only) */
    fun save(cards: List<GridCard>, columns: Int) {
        val snapshot = HomeGridSnapshot(
            columns = columns,
            cards = cards.map { card ->
                HomeCardSnapshot(
                    id = card.id,
                    type = card.type.typeId,
                    x = card.layout.x,
                    y = card.layout.y,
                    width = card.layout.width,
                    height = card.layout.height
                )
            }
        )
        mmkv.encode(KEY_LAYOUT, gson.toJson(snapshot))
    }
}
