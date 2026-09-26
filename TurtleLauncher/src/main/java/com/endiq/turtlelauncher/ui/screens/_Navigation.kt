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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.scene.Scene
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.utils.animation.BounceEasing
import com.endiq.turtlelauncher.utils.animation.JellyBounce
import com.endiq.turtlelauncher.utils.animation.OvershootEasing
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
        val duration = (getAnimateSpeed() / 5) * 2
        val floatTween: FiniteAnimationSpec<Float> = tween(durationMillis = duration)
        val bounce: FiniteAnimationSpec<Float> = tween(durationMillis = duration, easing = BounceEasing)
        val jelly: FiniteAnimationSpec<Float> = tween(durationMillis = duration, easing = JellyBounce)
        val overshoot: FiniteAnimationSpec<Float> = tween(durationMillis = duration, easing = OvershootEasing)
        val springy: FiniteAnimationSpec<Float> = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
        val offsetTween: FiniteAnimationSpec<IntOffset> = tween(durationMillis = duration)
        val offsetBounce: FiniteAnimationSpec<IntOffset> = tween(durationMillis = duration, easing = BounceEasing)

        fun fadePair(): ContentTransform =
            ContentTransform(fadeIn(floatTween), fadeOut(floatTween))

        when (type) {
            TransitionAnimationType.CLOSE -> {
                { ContentTransform(fadeIn(snap()), fadeOut(snap())) }
            }
            TransitionAnimationType.JELLY_BOUNCE -> {
                {
                    ContentTransform(
                        scaleIn(jelly, initialScale = 0.85f) + fadeIn(jelly),
                        scaleOut(jelly, targetScale = 1.05f) + fadeOut(jelly)
                    )
                }
            }
            TransitionAnimationType.BOUNCE -> {
                {
                    ContentTransform(
                        scaleIn(bounce, initialScale = 0.85f) + fadeIn(bounce),
                        scaleOut(bounce, targetScale = 1.05f) + fadeOut(bounce)
                    )
                }
            }
            TransitionAnimationType.SLICE_IN -> {
                {
                    ContentTransform(
                        slideInVertically(offsetBounce) { it / 6 } + fadeIn(floatTween),
                        slideOutVertically(offsetTween) { -it / 6 } + fadeOut(floatTween)
                    )
                }
            }
            TransitionAnimationType.FADE -> {
                { fadePair() }
            }
            TransitionAnimationType.SLIDE_UP -> {
                {
                    ContentTransform(
                        slideInVertically(offsetTween) { it / 4 } + fadeIn(floatTween),
                        slideOutVertically(offsetTween) { -it / 4 } + fadeOut(floatTween)
                    )
                }
            }
            TransitionAnimationType.SLIDE_DOWN -> {
                {
                    ContentTransform(
                        slideInVertically(offsetTween) { -it / 4 } + fadeIn(floatTween),
                        slideOutVertically(offsetTween) { it / 4 } + fadeOut(floatTween)
                    )
                }
            }
            TransitionAnimationType.SLIDE_LEFT -> {
                {
                    ContentTransform(
                        slideInHorizontally(offsetTween) { it / 4 } + fadeIn(floatTween),
                        slideOutHorizontally(offsetTween) { -it / 4 } + fadeOut(floatTween)
                    )
                }
            }
            TransitionAnimationType.SLIDE_RIGHT -> {
                {
                    ContentTransform(
                        slideInHorizontally(offsetTween) { -it / 4 } + fadeIn(floatTween),
                        slideOutHorizontally(offsetTween) { it / 4 } + fadeOut(floatTween)
                    )
                }
            }
            TransitionAnimationType.FADE_SLIDE_UP -> {
                {
                    ContentTransform(
                        slideInVertically(offsetTween) { it / 8 } + fadeIn(floatTween),
                        slideOutVertically(offsetTween) { -it / 8 } + fadeOut(floatTween)
                    )
                }
            }
            TransitionAnimationType.FADE_SLIDE_DOWN -> {
                {
                    ContentTransform(
                        slideInVertically(offsetTween) { -it / 8 } + fadeIn(floatTween),
                        slideOutVertically(offsetTween) { it / 8 } + fadeOut(floatTween)
                    )
                }
            }
            TransitionAnimationType.FADE_SLIDE_LEFT -> {
                {
                    ContentTransform(
                        slideInHorizontally(offsetTween) { it / 8 } + fadeIn(floatTween),
                        slideOutHorizontally(offsetTween) { -it / 8 } + fadeOut(floatTween)
                    )
                }
            }
            TransitionAnimationType.FADE_SLIDE_RIGHT -> {
                {
                    ContentTransform(
                        slideInHorizontally(offsetTween) { -it / 8 } + fadeIn(floatTween),
                        slideOutHorizontally(offsetTween) { it / 8 } + fadeOut(floatTween)
                    )
                }
            }
            TransitionAnimationType.SCALE -> {
                {
                    ContentTransform(
                        scaleIn(floatTween, initialScale = 0.9f),
                        scaleOut(floatTween, targetScale = 0.9f)
                    )
                }
            }
            TransitionAnimationType.ZOOM_IN -> {
                {
                    ContentTransform(
                        scaleIn(floatTween, initialScale = 0.7f) + fadeIn(floatTween),
                        scaleOut(floatTween, targetScale = 1.3f) + fadeOut(floatTween)
                    )
                }
            }
            TransitionAnimationType.ZOOM_OUT -> {
                {
                    ContentTransform(
                        scaleIn(floatTween, initialScale = 1.3f) + fadeIn(floatTween),
                        scaleOut(floatTween, targetScale = 0.7f) + fadeOut(floatTween)
                    )
                }
            }
            TransitionAnimationType.EXPAND_CENTER -> {
                {
                    ContentTransform(
                        expandIn(tween(durationMillis = duration)) + scaleIn(floatTween, initialScale = 0.9f) + fadeIn(floatTween),
                        shrinkOut(tween(durationMillis = duration)) + fadeOut(floatTween)
                    )
                }
            }
            TransitionAnimationType.WIPE_VERTICAL -> {
                {
                    ContentTransform(
                        expandVertically(tween(durationMillis = duration)) + fadeIn(floatTween),
                        shrinkVertically(tween(durationMillis = duration)) + fadeOut(floatTween)
                    )
                }
            }
            TransitionAnimationType.WIPE_HORIZONTAL -> {
                {
                    ContentTransform(
                        expandHorizontally(tween(durationMillis = duration)) + fadeIn(floatTween),
                        shrinkHorizontally(tween(durationMillis = duration)) + fadeOut(floatTween)
                    )
                }
            }
            TransitionAnimationType.OVERSHOOT -> {
                {
                    ContentTransform(
                        scaleIn(overshoot, initialScale = 0.85f) + fadeIn(floatTween),
                        scaleOut(floatTween, targetScale = 1.05f) + fadeOut(floatTween)
                    )
                }
            }
            TransitionAnimationType.REVEAL -> {
                {
                    ContentTransform(
                        expandIn(tween(durationMillis = duration)) + fadeIn(floatTween),
                        fadeOut(floatTween)
                    )
                }
            }
            TransitionAnimationType.ZOOM_DISSOLVE -> {
                {
                    ContentTransform(
                        scaleIn(springy, initialScale = 0.8f) + fadeIn(springy),
                        scaleOut(springy, targetScale = 1.2f) + fadeOut(springy)
                    )
                }
            }
        }
    }
}