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

package com.endiq.turtlelauncher.utils.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import com.endiq.turtlelauncher.utils.logging.Logger
import java.io.File

private const val TAG = "ImageUtils"

/**
 * Converts a [Drawable] into a [Bitmap]
 * If the Drawable already is a [BitmapDrawable] with a non-null Bitmap, return it directly
 * Otherwise render onto a new Bitmap
 */
fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable && this.bitmap != null) {
        return this.bitmap
    }

    val width = if (intrinsicWidth > 0) intrinsicWidth else 1
    val height = if (intrinsicHeight > 0) intrinsicHeight else 1

    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)

    return bitmap
}

/**
 * Iterates the given Bitmap region, evaluating a predicate
 *
 * @param xRange the X coordinate range
 * @param yRange the Y coordinate range
 * @param predicate predicate taking (color, x, y)
 * @param requireAll whether every pixel must satisfy the predicate; false means any hit returns true
 * @return whether the condition holds
 */
inline fun Bitmap.isColorMatch(
    xRange: IntRange,
    yRange: IntRange,
    predicate: (color: Int, x: Int, y: Int) -> Boolean,
    requireAll: Boolean = false
): Boolean {
    val width = this.width
    val height = this.height

    for (x in xRange) {
        if (x !in 0 until width) continue
        for (y in yRange) {
            if (y !in 0 until height) continue
            val match = predicate(this[x, y], x, y)
            if (requireAll) {
                if (!match) return false
            } else {
                if (match) return true
            }
        }
    }
    return requireAll
}

/**
 * Recycles the Bitmap when larger than the threshold
 */
fun Bitmap?.recycleIfLarge(thresholdBytes: Int = 8 * 1024 * 1024) {
    this ?: return

    if (!isRecycled && byteCount >= thresholdBytes) {
        recycle()
    }
}

/**
 * Tries telling whether a file is an image
 */
fun File.isImageFile(): Boolean {
    if (!this.exists()) return false

    return try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(this.absolutePath, options)
        options.outWidth > 0 && options.outHeight > 0
    } catch (e: Exception) {
        Logger.warning(TAG,
            "An exception occurred while trying to determine if ${this.absolutePath} is an image.",
            e
        )
        false
    }
}