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

package com.endiq.turtlelauncher.ui.base

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import com.endiq.turtlelauncher.ui.screens.TitledNavKey

/**
 * Single-level base screen: visibility judged from `currentKey`
 * @param screenKey this screen's key
 * @param currentKey the key currently shown
 * @param useClassEquality whether to use class equality
 */
@Composable
fun BaseScreen(
    screenKey: TitledNavKey,
    currentKey: TitledNavKey?,
    useClassEquality: Boolean = false,
    content: @Composable (isVisible: Boolean) -> Unit,
) {
    val targetVisible = remember(currentKey, screenKey, useClassEquality) {
        isTagVisible(screenKey, currentKey, useClassEquality)
    }

    //Initially invisible, triggering the first false -> true animation
    val visibleState = remember { mutableStateOf(false) }

    //Visible state updates only after composition completes
    LaunchedEffect(targetVisible) {
        visibleState.value = targetVisible
    }

    BaseScreen(
        content = content,
        visible = visibleState.value
    )
}

/**
 * Multi-level base screen: visibility judged per key in the level list
 * @param levels level list, each level holding (key, current key, reference equality flag)
 */
@Composable
fun BaseScreen(
    vararg levels: Triple<TitledNavKey, TitledNavKey?, Boolean>,
    content: @Composable (isVisible: Boolean) -> Unit,
) {
    val targetVisible = remember(levels) {
        levels.all { (tag, currentKey, useReferenceEquality) ->
            isTagVisible(tag, currentKey, useReferenceEquality)
        }
    }

    //Initially invisible, triggering the first false -> true animation
    val visibleState = remember { mutableStateOf(false) }

    //Visible state updates only after composition completes
    LaunchedEffect(targetVisible) {
        visibleState.value = targetVisible
    }

    BaseScreen(
        content = content,
        visible = visibleState.value
    )
}

/**
 * Multi-level base screen: visibility judged per key in the level list
 */
@Composable
fun BaseScreen(
    levels1: List<Pair<Class<out TitledNavKey>, TitledNavKey?>>,
    vararg levels2: Triple<TitledNavKey, TitledNavKey?, Boolean>,
    content: @Composable (isVisible: Boolean) -> Unit,
) {
    val targetVisible = remember(levels1, levels2) {
        val v1 = levels1.all { (key, currentKey) ->
            isTagVisible(key, currentKey)
        }
        val v2 = levels2.all { (key, currentKey, useClassEquality) ->
            isTagVisible(key, currentKey, useClassEquality)
        }
        v1 && v2
    }

    //Initially invisible, triggering the first false -> true animation
    val visibleState = remember { mutableStateOf(false) }

    //Visible state updates only after composition completes
    LaunchedEffect(targetVisible) {
        visibleState.value = targetVisible
    }

    BaseScreen(
        content = content,
        visible = visibleState.value
    )
}

@Composable
private fun BaseScreen(
    content: @Composable (isVisible: Boolean) -> Unit,
    visible: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        content(visible)

        if (!visible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0f)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {}
                    )
            )
        }
    }
}

private fun isTagVisible(key: Class<out TitledNavKey>, current: TitledNavKey?): Boolean {
    return key.isInstance(current)
}

/**
 * @param useClassEquality whether to use class equality
 */
private fun isTagVisible(key: TitledNavKey, current: TitledNavKey?, useClassEquality: Boolean): Boolean {
    return when {
        current == null -> false
        useClassEquality -> key::class == current::class
        else -> key == current
    }
}