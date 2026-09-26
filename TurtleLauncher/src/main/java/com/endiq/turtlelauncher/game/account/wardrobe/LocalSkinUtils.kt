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

package com.endiq.turtlelauncher.game.account.wardrobe

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.alpha
import com.endiq.turtlelauncher.utils.image.isColorMatch
import com.endiq.turtlelauncher.utils.image.recycleIfLarge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun legacyStrFill(str: String, code: Char, length: Int): String {
    return if (str.length > length) {
        str.take(length)
    } else {
        str.padEnd(length, code).drop(str.length) + str
    }
}

private fun getLocalUuid(name: String): String {
    val lenHex = name.length.toString(16)
    val lengthPart = legacyStrFill(lenHex, '0', 16)

    val hashCode = name.hashCode().toLong() and 0xFFFFFFFFL
    val hashHex = hashCode.toString(16)
    val hashPart = legacyStrFill(hashHex, '0', 16) //ensure at most 16 chars

    return buildString(34) {
        append(lengthPart.take(12))
        append('3')
        append(lengthPart.substring(13, 16))
        append('9')
        append(hashPart.take(15))
    }
}

/**
 * Generates a profileId from the skin model type
 */
fun getLocalUUIDWithSkinModel(userName: String, skinModelType: SkinModelType): String {
    val baseUuid = getLocalUuid(userName)
    if (skinModelType == SkinModelType.NONE) return baseUuid

    val prefix = baseUuid.take(27)
    val a = baseUuid[7].digitToInt(16)
    val b = baseUuid[15].digitToInt(16)
    val c = baseUuid[23].digitToInt(16)

    var suffix = baseUuid.substring(27).toLong(16)
    val maxSuffix = 0xFFFFFL

    repeat(maxSuffix.toInt() + 1) {
        val currentD = (suffix and 0xFL).toInt()
        if ((a xor b xor c xor currentD) % 2 == skinModelType.targetParity) {
            return prefix + suffix.toString(16).padStart(5, '0').uppercase()
        }
        suffix = if (suffix == maxSuffix) 0L else suffix + 1
    }

    return prefix + suffix.toString(16).padStart(5, '0').uppercase()
}

/**
 * Validates the skin's pixel size; Minecraft only supports 64x64 or 64x32 skins
 */
suspend fun validateSkinFile(skinFile: File): Boolean {
    return withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(skinFile.absolutePath, options)
        options.isDualLayerSkin() || options.isClassicSkin()
    }
}

/**
 * Whether it's a double-layer skin: 64x64
 */
fun BitmapFactory.Options.isDualLayerSkin(): Boolean {
    return outWidth == 64 && outHeight == 64
}

/**
 * Whether it's a classic (single-layer) skin: the early type where both arms / both legs share one texture
 * 64x32
 */
fun BitmapFactory.Options.isClassicSkin(): Boolean {
    return outWidth == 64 && outHeight == 32
}

/**
 * Checks whether the skin uses the slim (Alex) model
 */
suspend fun File.isSlimModel(): Boolean = withContext(Dispatchers.IO) {
    val options = BitmapFactory.Options()
    val bitmap = BitmapFactory.decodeFile(absolutePath, options) ?: return@withContext false
    try {
        if (options.isClassicSkin()) {
            //Legacy single-layer skins don't support slim arms
            false
        } else {
            val rightHand = bitmap.isTransparent(50..51, 16..19)
            val rightArm = bitmap.isTransparent(54..55, 20..31)

            val leftHand = bitmap.isTransparent(42..43, 48..51)
            val leftArm = bitmap.isTransparent(46..47, 52..63)

            rightHand && rightArm && leftHand && leftArm
        }
    } catch (_: Exception) {
        false
    } finally {
        bitmap.recycleIfLarge()
    }
}

private fun Bitmap.isTransparent(xRange: IntRange, yRange: IntRange): Boolean {
    return isColorMatch(
        xRange = xRange,
        yRange = yRange,
        predicate = { color, _, _ ->
            color.alpha == 0
        },
        requireAll = true
    )
}
