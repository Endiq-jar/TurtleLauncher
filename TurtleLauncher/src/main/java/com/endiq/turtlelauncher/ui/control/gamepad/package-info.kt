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

@file:Suppress("unused")

/**
 * Implementation ideas partly based on [G-Mapper for Android](https://github.com/Mathias-Boulay/android_gamepad_remapper/),
 * which ships under GNU Lesser General Public License v3.0 (LGPL-3.0).
 *
 * This project ships under GNU General Public License v3.0 (GPL-3.0).
 *
 * Notes:
 * - The launcher uses Jetpack Compose for its UI and cannot integrate the original library directly,
 *   so its core logic is reimplemented in Kotlin and Compose style.
 * - Some algorithms and structures from the original are simplified and refactored while preserving design intent.
 * - This project neither contains nor links to the original project source code.
 */

package com.endiq.turtlelauncher.ui.control.gamepad
