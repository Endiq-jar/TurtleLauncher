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

package com.endiq.turtlelauncher.ui.screens

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.scene.Scene
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.utils.animation.TransitionAnimationType
import com.endiq.turtlelauncher.utils.animation.getAnimateSpeed
import kotlin.reflect.KClass

/**
 * Back-event handling compatible with nested NavDisplay
 */
fun <E: TitledNavKey> onBack(currentBackStack: NavBackStack<E>) {
    when (val key = currentBackStack.lastOrNull()) {
        //Normal screens: just pop the current stack's top
        is NormalNavKey -> currentBackStack.removeLastOrNull()
        is BackStackNavKey<*> -> {
            if (key.backStack.size <= 1) {
                //A nested screen's stack sits on its last entry
                //The current stack's top can be popped now
                currentBackStack.removeLastOrNull()
            } else {
                //Pop the sub-stack's top screen
                key.backStack.removeLastOrNull()
            }
        }
    }
}

fun <E: TitledNavKey> NavBackStack<E>.navigateOnce(key: E) {
    if (key == lastOrNull()) return //avoid reloading
    clearWith(key)
}

fun <E: TitledNavKey> NavBackStack<E>.navigateTo(screenKey: E, useClassEquality: Boolean = false) {
    val current = lastOrNull()
    if (useClassEquality) {
        if (current != null && screenKey::class == current::class) return //avoid reloading
    } else {
        if (screenKey == current) return //avoid reloading
    }
    add(screenKey)
}

fun <E: TitledNavKey> NavBackStack<E>.removeAndNavigateTo(remove: KClass<*>, screenKey: E, useClassEquality: Boolean = false) {
    removeIf { key ->
        key::class == remove
    }
    navigateTo(screenKey, useClassEquality)
}

fun <E: TitledNavKey> NavBackStack<E>.removeAndNavigateTo(removes: List<KClass<*>>, screenKey: E, useClassEquality: Boolean = false) {
    removeIf { key ->
        key::class in removes
    }
    navigateTo(screenKey, useClassEquality)
}

/**
 * Clears every stack, pushing the given key
 */
fun <E: TitledNavKey> NavBackStack<E>.clearWith(navKey: E) {
    val targetClass = navKey::class.java
    if (none { it::class.java == targetClass }) {
        //Push early, avoiding Nav3 seeing an empty frame
        add(navKey)
    }
    removeIf { it::class.java != targetClass }
}

/**
 * Removes the given key
 */
fun <E: TitledNavKey> NavBackStack<E>.clearKeys(vararg navKeys: E) {
    val classes = navKeys.map { it::class.java }
    removeIf { it::class.java in classes }
}

fun <E: TitledNavKey> NavBackStack<E>.addIfEmpty(navKey: E) {
    if (isEmpty()) {
        add(navKey)
    }
}

@Composable
fun rememberSwapTween(): FiniteAnimationSpec<Float> {
    val speed = AllSettings.launcherAnimateSpeed.state
    return remember(speed) {
        tween(durationMillis = (getAnimateSpeed() / 5) * 2)
    }
}

@Composable
fun <T : Any> rememberTransitionSpec(): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform {
    val type = AllSettings.launcherSwapAnimateType.state
    val speed = AllSettings.launcherAnimateSpeed.state
    return remember(type, speed) {
        val tween: FiniteAnimationSpec<Float> = when (type) {
            TransitionAnimationType.CLOSE -> snap()
            else -> tween(durationMillis = (getAnimateSpeed() / 5) * 2)
        }

        {
            ContentTransform(
                fadeIn(animationSpec = tween),
                fadeOut(animationSpec = tween),
            )
        }
    }
}