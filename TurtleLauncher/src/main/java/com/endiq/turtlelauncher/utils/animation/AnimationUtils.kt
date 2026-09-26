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

package com.endiq.turtlelauncher.utils.animation

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.endiq.turtlelauncher.setting.AllSettings

/**
 * Gets the animation duration
 */
fun getAnimateSpeed(): Int = calculateAnimationTime(
    AllSettings.launcherAnimateSpeed.state,
    1500,
    0.1f
)

/**
 * Gets delayMillis adjusted by the animation speed factor
 */
fun getAdjustedDelayMillis(baseDelayMillis: Int): Int {
    if (baseDelayMillis == 0) return 0
    val adjustedAnimationTime = calculateAnimationTime(
        AllSettings.launcherAnimateSpeed.state,
        baseDelayMillis
    )
    return adjustedAnimationTime
}

/**
 * Whether page transition animations are disabled
 */
fun isSwapAnimateClosed() = AllSettings.launcherSwapAnimateType.state == TransitionAnimationType.CLOSE

fun <E> getAnimateTween(
    delayMillis: Int = 0
): FiniteAnimationSpec<E> = getAnimateTween(
    durationMillis = getAnimateSpeed(),
    delayMillis = delayMillis
)

fun <E> getAnimateTween(
    durationMillis: Int,
    delayMillis: Int = 0
): FiniteAnimationSpec<E> = tween(
    durationMillis = durationMillis,
    delayMillis = delayMillis
)

fun <E> getAnimateTweenBounce(
    delayMillis: Int = 0
): FiniteAnimationSpec<E> = getAnimateTweenBounce(
    durationMillis = getAnimateSpeed(),
    delayMillis = delayMillis
)

fun <E> getAnimateTweenBounce(
    durationMillis: Int,
    delayMillis: Int = 0
): FiniteAnimationSpec<E> = tween(
    durationMillis = durationMillis,
    delayMillis = delayMillis,
    easing = BounceEasing
)

fun <E> getAnimateTweenJellyBounce(
    delayMillis: Int = 0
): FiniteAnimationSpec<E> = getAnimateTweenJellyBounce(
    durationMillis = getAnimateSpeed(),
    delayMillis = delayMillis
)

fun <E> getAnimateTweenJellyBounce(
    durationMillis: Int,
    delayMillis: Int = 0
): FiniteAnimationSpec<E> = tween(
    durationMillis = durationMillis,
    delayMillis = delayMillis,
    easing = JellyBounce
)

/**
 * Gets the page transition animation
 */
fun <E> getSwapAnimateTween(
    swapIn: Boolean,
    delayMillis: Int = 0
): FiniteAnimationSpec<E> {
    val adjustedDelayMillis = getAdjustedDelayMillis(delayMillis)
    return if (swapIn) {
        when (AllSettings.launcherSwapAnimateType.state) {
            TransitionAnimationType.CLOSE -> snap()
            TransitionAnimationType.BOUNCE,
            TransitionAnimationType.OVERSHOOT -> getAnimateTweenBounce(adjustedDelayMillis)
            TransitionAnimationType.JELLY_BOUNCE,
            TransitionAnimationType.ZOOM_DISSOLVE -> getAnimateTweenJellyBounce(adjustedDelayMillis)
            else -> getAnimateTween(adjustedDelayMillis)
        }
    } else {
        getAnimateTween(adjustedDelayMillis)
    }
}

/**
 * Computes the animation magnitude (targetValue)
 * Base 5: magnitude 5 maps to targetValue itself
 *
 * Magnitude 0: targetValue * 0.5
 * Magnitude 5: targetValue * 1.0
 * Magnitude 10: targetValue * 1.5
 */
fun getTargetValueByAmplitude(
    targetValue: Dp,
    amplitude: Int = 5
): Dp {
    val safeAmplitude = amplitude.coerceIn(0, 10)

    val minScale = 0.5f
    val maxScale = 1.5f
    val baseAmplitude = 5

    val scale = if (safeAmplitude == baseAmplitude) {
        1.0f
    } else if (safeAmplitude < baseAmplitude) {
        minScale + (safeAmplitude.toFloat() / baseAmplitude) * (1.0f - minScale)
    } else {
        1.0f + ((safeAmplitude - baseAmplitude).toFloat() / (10 - baseAmplitude)) * (maxScale - 1.0f)
    }

    return (targetValue.value * scale).dp
}

@Composable
fun swapAnimateDpAsState(
    targetValue: Dp,
    swapIn: Boolean,
    amplitude: Int = AllSettings.launcherAnimateExtent.state,
    isHorizontal: Boolean = false,
    delayMillis: Int = 0
): State<Dp> {
    return if (!isSwapAnimateClosed()) {
        swapAnimateDpAsState(
            targetValue = targetValue,
            swapIn = swapIn,
            amplitude = amplitude,
            isHorizontal = isHorizontal,
            animationSpec = getSwapAnimateTween(swapIn, delayMillis = delayMillis)
        )
    } else {
        rememberUpdatedState(newValue = 0.dp)
    }
}

@Composable
fun swapAnimateDpAsState(
    targetValue: Dp,
    swapIn: Boolean,
    amplitude: Int = AllSettings.launcherAnimateExtent.state,
    isHorizontal: Boolean = false,
    animationSpec: AnimationSpec<Dp>
): State<Dp> {
    val value = if (swapIn) 0.dp
    else {
        getTargetValueByAmplitude(
            if (isHorizontal) targetValue / 2
            else targetValue,
            amplitude
        )
    }
    return animateDpAsState(
        targetValue = value,
        animationSpec = animationSpec
    )
}

/**
 * Computes the time adjusted by the speed multiplier (ms)
 * @param minFactor the multiplier applied at maximum speed (0.25 = quarter time)
 * @return the speed-adjusted time
 */
fun calculateAnimationTime(speed: Int, baseTime: Int, minFactor: Float = 0.25f): Int {
    val factor = 1f - (speed / 10f) * (1f - minFactor)
    return (baseTime * factor).toInt()
}