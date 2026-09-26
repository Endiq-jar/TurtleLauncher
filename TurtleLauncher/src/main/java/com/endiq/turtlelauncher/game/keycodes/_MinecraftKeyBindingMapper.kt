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

package com.endiq.turtlelauncher.game.keycodes

import com.endiq.inputmap.keycodes.Lwjgl2Keycode
import com.endiq.inputmap.keycodes.MinecraftKeyBindingMapper
import com.endiq.turtlelauncher.game.launch.MCOptions

/**
 * Maps a string key to its keycode
 * @return the keycode when mapped, `null` otherwise
 */
fun mapToKeycode(bindingKey: String?, defaultValue: String): Int? {
    val binding = bindingKey?.let { MCOptions.get(it) } ?: defaultValue

    return if (binding.startsWith("key.")) {
        //New MC keybind mappings
        MinecraftKeyBindingMapper.getGlfwKeycode(binding)?.toInt()
    } else {
        binding.toIntOrNull()?.let { lwjgl2Code ->
            //Old MC versions stored LWJGL2 key values directly
            //Convert old LWJGL2 keycodes to GLFW
            Lwjgl2Keycode.lwjgl2ToGlfw(lwjgl2Code)
        }
    }
}

/**
 * Maps a string key to its control layout event identifier
 * @return the identifier when mapped, `null` otherwise
 */
fun mapToControlEvent(bindingKey: String?, defaultValue: String): String? {
    val binding = bindingKey?.let { MCOptions.get(it) } ?: defaultValue

    return if (binding.startsWith("key.")) {
        MinecraftKeyBindingMapper.getControlEvent(binding)
    } else {
        binding.toIntOrNull()?.let { lwjgl2Code ->
            //Old MC versions stored LWJGL2 key values directly
            //Convert old LWJGL2 keycodes to control event identifiers
            Lwjgl2Keycode.lwjgl2ToControlEvent(lwjgl2Code)
        }
    }
}