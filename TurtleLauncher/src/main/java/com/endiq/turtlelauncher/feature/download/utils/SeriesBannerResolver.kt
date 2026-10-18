package com.endiq.turtlelauncher.feature.download.utils

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.feature.download.SeriesCardAdapter

/**
 * Resolves the banner art behind a version-series card.
 *
 * Drop a real promo image in as res/drawable/banner_<slug>.(webp|png|jpg) - e.g.
 * "banner_1_21.webp" for the "1.21" series - and it's used automatically; that's the
 * place to put real Mojang/Minecraft artwork if you have rights to bundle it (not
 * something generated here). With no such asset bundled, falls back to a generated
 * per-series background: a gradient, a generic blocky skyline silhouette tinted a
 * darker shade of the same colour, and the existing series icon watermarked in the
 * middle - so the grid still reads as a real background image rather than a flat tile.
 */
object SeriesBannerResolver {

    private val PALETTE = intArrayOf(
        0xFF2D6A4F.toInt(), 0xFF3A6EA5.toInt(), 0xFF6A4C93.toInt(),
        0xFFB5651D.toInt(), 0xFF1D3557.toInt(), 0xFF2A9D8F.toInt(),
        0xFF8E4162.toInt(), 0xFF457B9D.toInt()
    )

    /** Banner art plus where the series-icon watermark sits inside it (-1: no watermark). */
    class BannerArt(val drawable: Drawable, val iconLayerIndex: Int)

    fun resolve(context: Context, card: SeriesCardAdapter.CardEntry): Drawable =
        resolveArt(context, card).drawable

    fun resolveArt(context: Context, card: SeriesCardAdapter.CardEntry): BannerArt {
        val slug = card.label.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
        val resId = context.resources.getIdentifier("banner_$slug", "drawable", context.packageName)
        if (resId != 0) {
            ContextCompat.getDrawable(context, resId)?.let { return BannerArt(it, -1) }
        }
        return fallbackArt(context, card)
    }

    private fun fallbackArt(context: Context, card: SeriesCardAdapter.CardEntry): BannerArt {
        val base = PALETTE[Math.floorMod(card.label.hashCode(), PALETTE.size)]
        val density = context.resources.displayMetrics.density

        val gradient = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(darken(base, 0.10f), darken(base, 0.55f))
        )

        val skyline = ContextCompat.getDrawable(context, R.drawable.bg_terrain_skyline)?.mutate()
        val icon = ContextCompat.getDrawable(context, card.iconRes)?.mutate()

        val layers = mutableListOf<Drawable>(gradient)
        if (skyline != null) {
            DrawableCompat.setTint(skyline, darken(base, 0.35f))
            DrawableCompat.setTintMode(skyline, android.graphics.PorterDuff.Mode.SRC_IN)
            skyline.alpha = 150
            layers.add(skyline)
        }
        if (icon != null) {
            icon.alpha = 40
            layers.add(icon)
        }
        if (layers.size == 1) return BannerArt(gradient, -1)

        val layered = LayerDrawable(layers.toTypedArray())
        var index = 1
        var iconLayerIndex = -1
        if (skyline != null) {
            layered.setLayerGravity(index, Gravity.BOTTOM)
            layered.setLayerHeight(index, (56 * density).toInt())
            index++
        }
        if (icon != null) {
            iconLayerIndex = index
            val insetPx = (28 * density).toInt()
            layered.setLayerInset(index, insetPx, insetPx, insetPx, insetPx)
        }
        return BannerArt(layered, iconLayerIndex)
    }

    private fun darken(color: Int, factor: Float): Int {
        val a = (color ushr 24) and 0xFF
        val r = (((color ushr 16) and 0xFF) * (1 - factor)).toInt()
        val g = (((color ushr 8) and 0xFF) * (1 - factor)).toInt()
        val b = ((color and 0xFF) * (1 - factor)).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
