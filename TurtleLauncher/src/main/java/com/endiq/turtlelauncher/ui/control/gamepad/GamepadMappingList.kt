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

package com.endiq.turtlelauncher.ui.control.gamepad

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class GamepadMappingList(
    val name: String,
    val list: MutableList<GamepadMapping>
) : Parcelable {
    /**
     * Gamepad-to-keyboard mapping bindings
     */
    @IgnoredOnParcel
    private val allKeyMappings = mutableMapOf<Int, TargetKeys>()
    @IgnoredOnParcel
    private val allDpadMappings = mutableMapOf<DpadDirection, TargetKeys>()

    /**
     * A data class for recording target keyboard mappings
     */
    data class TargetKeys(
        val inGame: Set<String>,
        val inMenu: Set<String>
    ) {
        fun getKeys(isInGame: Boolean) = if (isInGame) inGame else inMenu
    }

    fun load() {
        allKeyMappings.clear()
        allDpadMappings.clear()

        list.forEach { mapping ->
            addInMappingsMap(mapping)
        }
    }

    private fun addInMappingsMap(mapping: GamepadMapping) {
        val target = TargetKeys(mapping.targetsInGame, mapping.targetsInMenu)
        mapping.dpadDirection?.let {
            allDpadMappings[it] = target
        } ?: run {
            allKeyMappings[mapping.key] = target
        }
    }

    /**
     * Resets the gamepad-to-keyboard mapping bindings
     */
    fun resetMapping(gamepadMap: GamepadMap, inGame: Boolean) =
        applyMapping(gamepadMap, inGame)

    /**
     * Sets the target keyboard mapping for a given gamepad mapping
     */
    fun saveMapping(gamepadMap: GamepadMap, targets: Set<String>, inGame: Boolean) =
        applyMapping(gamepadMap, inGame, customTargets = targets)

    /**
     * Saves or resets gamepad-to-keyboard mapping bindings
     * @param gamepadMap the gamepad mapping object
     * @param inGame whether it's the in-game mapping (true = in-game, false = in-menu)
     * @param customTargets custom target keys; empty resets to defaults
     */
    private fun applyMapping(
        gamepadMap: GamepadMap,
        inGame: Boolean,
        customTargets: Set<String>? = null,
    ) {
        val dpad = gamepadMap.dpadDirection
        val isDpad = dpad != null
        val existing = if (isDpad) allDpadMappings[dpad] else allKeyMappings[gamepadMap.gamepad]

        val (targetsInGame, targetsInMenu) = if (inGame) {
            val newTargets = customTargets ?: gamepadMap.defaultKeysInGame
            newTargets to (existing?.inMenu ?: emptySet())
        } else {
            val newTargets = customTargets ?: gamepadMap.defaultKeysInMenu
            (existing?.inGame ?: emptySet()) to newTargets
        }

        val mapping = GamepadMapping(
            key = gamepadMap.gamepad,
            dpadDirection = dpad,
            targetsInGame = targetsInGame,
            targetsInMenu = targetsInMenu
        )
        addInMappingsMap(mapping)
        list.removeIf { mapping0 ->
            if (isDpad) {
                mapping0.dpadDirection == mapping.dpadDirection
            } else {
                mapping0.dpadDirection == null && mapping0.key == mapping.key
            }
        }
        list.add(mapping)
        save()
    }

    /**
     * Resolves the keyboard mapping for a gamepad key code
     * @return null when not found
     */
    fun findByCode(key: Int, inGame: Boolean) =
        allKeyMappings[key]?.getKeys(inGame)

    /**
     * Resolves the keyboard mapping for a gamepad d-pad key
     * @return null when not found
     */
    fun findByDpad(dir: DpadDirection, inGame: Boolean) =
        allDpadMappings[dir]?.getKeys(inGame)

    /**
     * Resolves the keyboard mapping for a gamepad axis
     * @return null when not found
     */
    fun findByMap(map: GamepadMap, inGame: Boolean) =
        (map.dpadDirection?.let { allDpadMappings[it] } ?: allKeyMappings[map.gamepad])
            ?.getKeys(inGame)

    fun save() {
        keyMappingListMMKV().encode(name, this)
    }
}