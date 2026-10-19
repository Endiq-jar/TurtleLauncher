package com.endiq.turtlelauncher.ui.miuix

import android.graphics.Outline
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import kotlin.math.min

/**
 * Exact port of `top.yukonga.miuix.kmp.squircle.SquirclePath`
 * (https://github.com/compose-miuix-ui/miuix).
 *
 * Uses a single cubic Bezier per corner with extended tangent points (`extension = 1.1f`)
 * and `SQUIRCLE_CONTROL = 0.643f` for G2-continuous superellipse corners matching MIUI/HyperOS.
 */
object MiuixSquircle {
    /** Bezier control point ratio as fraction of tile from corner (closer to 1 = sharper). */
    const val SQUIRCLE_CONTROL = 0.643f

    /** Tangent extension factor beyond nominal corner radius (`tile = r * extension`). */
    const val SQUIRCLE_EXTENSION = 1.1f

    @JvmStatic
    @JvmOverloads
    fun buildPath(
        width: Float,
        height: Float,
        radius: Float,
        extension: Float = SQUIRCLE_EXTENSION,
        outPath: Path = Path()
    ): Path {
        return buildPath(
            left = 0f,
            top = 0f,
            right = width,
            bottom = height,
            topLeft = radius,
            topRight = radius,
            bottomRight = radius,
            bottomLeft = radius,
            extension = extension,
            outPath = outPath
        )
    }

    @JvmStatic
    @JvmOverloads
    fun buildPath(
        bounds: RectF,
        radius: Float,
        extension: Float = SQUIRCLE_EXTENSION,
        outPath: Path = Path()
    ): Path {
        return buildPath(
            left = bounds.left,
            top = bounds.top,
            right = bounds.right,
            bottom = bounds.bottom,
            topLeft = radius,
            topRight = radius,
            bottomRight = radius,
            bottomLeft = radius,
            extension = extension,
            outPath = outPath
        )
    }

    @JvmStatic
    @JvmOverloads
    fun buildPath(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        topLeft: Float,
        topRight: Float,
        bottomRight: Float,
        bottomLeft: Float,
        extension: Float = SQUIRCLE_EXTENSION,
        outPath: Path = Path()
    ): Path {
        outPath.reset()
        val w = (right - left).coerceAtLeast(0f)
        val h = (bottom - top).coerceAtLeast(0f)
        if (w <= 0f || h <= 0f) return outPath

        val maxR = min(w, h) / 2f
        val tl = topLeft.coerceIn(0f, maxR)
        val tr = topRight.coerceIn(0f, maxR)
        val br = bottomRight.coerceIn(0f, maxR)
        val bl = bottomLeft.coerceIn(0f, maxR)

        if (tl == 0f && tr == 0f && br == 0f && bl == 0f) {
            outPath.addRect(left, top, right, bottom, Path.Direction.CW)
            return outPath
        }

        val tlTile = min(tl * extension, maxR)
        val tlHandle = tlTile * (1f - SQUIRCLE_CONTROL)
        val trTile = min(tr * extension, maxR)
        val trHandle = trTile * (1f - SQUIRCLE_CONTROL)
        val brTile = min(br * extension, maxR)
        val brHandle = brTile * (1f - SQUIRCLE_CONTROL)
        val blTile = min(bl * extension, maxR)
        val blHandle = blTile * (1f - SQUIRCLE_CONTROL)

        // Start from top edge after top-left corner
        outPath.moveTo(left + tlTile, top)

        // Top edge -> top-right corner
        outPath.lineTo(right - trTile, top)
        if (tr > 0f) {
            outPath.cubicTo(
                right - trHandle, top,
                right, top + trHandle,
                right, top + trTile
            )
        }

        // Right edge -> bottom-right corner
        outPath.lineTo(right, bottom - brTile)
        if (br > 0f) {
            outPath.cubicTo(
                right, bottom - brHandle,
                right - brHandle, bottom,
                right - brTile, bottom
            )
        }

        // Bottom edge -> bottom-left corner
        outPath.lineTo(left + blTile, bottom)
        if (bl > 0f) {
            outPath.cubicTo(
                left + blHandle, bottom,
                left, bottom - blHandle,
                left, bottom - blTile
            )
        }

        // Left edge -> top-left corner
        outPath.lineTo(left, top + tlTile)
        if (tl > 0f) {
            outPath.cubicTo(
                left, top + tlHandle,
                left + tlHandle, top,
                left + tlTile, top
            )
        }

        outPath.close()
        return outPath
    }

    /**
     * Installs a squircle [ViewOutlineProvider] on [view] for clipping and shadow projection.
     */
    @JvmStatic
    fun applyOutline(view: View, cornerRadiusPx: Float, clipToOutline: Boolean = true) {
        view.outlineProvider = object : ViewOutlineProvider() {
            private val path = Path()
            override fun getOutline(v: View, outline: Outline) {
                val w = v.width
                val h = v.height
                if (w <= 0 || h <= 0) return
                val r = cornerRadiusPx.coerceIn(0f, min(w, h) / 2f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    buildPath(w.toFloat(), h.toFloat(), r, SQUIRCLE_EXTENSION, path)
                    outline.setPath(path)
                } else {
                    outline.setRoundRect(0, 0, w, h, r)
                }
            }
        }
        view.clipToOutline = clipToOutline
    }
}
