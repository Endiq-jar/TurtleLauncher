package com.endiq.turtlelauncher.ui.miuix

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import kotlin.math.roundToInt

/**
 * Programmatic vector drawables ported directly from `top.yukonga.miuix.kmp.icon.basic.*`:
 * - `MiuixIcons.Basic.ArrowRight` (10x16dp)
 * - `MiuixIcons.Basic.ArrowUpDown` (10x16dp)
 * - `MiuixIcons.Basic.Check` (20x20dp, viewport 56x56)
 */
object MiuixIconsDrawables {

    @JvmStatic
    fun arrowRight(
        context: Context,
        @ColorInt tintColor: Int = MiuixTheme.colors(context).onSurfaceVariantActions
    ): Drawable = MiuixVectorDrawable(
        widthPx = MiuixTheme.dpToPx(context, 10f).roundToInt(),
        heightPx = MiuixTheme.dpToPx(context, 16f).roundToInt(),
        viewportWidth = 10f,
        viewportHeight = 16f,
        tintColor = tintColor,
        basePath = buildArrowRightPath()
    )

    @JvmStatic
    fun arrowUpDown(
        context: Context,
        @ColorInt tintColor: Int = MiuixTheme.colors(context).onSurfaceVariantActions
    ): Drawable = MiuixVectorDrawable(
        widthPx = MiuixTheme.dpToPx(context, 10f).roundToInt(),
        heightPx = MiuixTheme.dpToPx(context, 16f).roundToInt(),
        viewportWidth = 10f,
        viewportHeight = 16f,
        tintColor = tintColor,
        basePath = buildArrowUpDownPath()
    )

    @JvmStatic
    fun check(
        context: Context,
        @ColorInt tintColor: Int = MiuixTheme.colors(context).primary,
        sizeDp: Float = 20f
    ): Drawable {
        val sizePx = MiuixTheme.dpToPx(context, sizeDp).roundToInt()
        return MiuixVectorDrawable(
            widthPx = sizePx,
            heightPx = sizePx,
            viewportWidth = 56f,
            viewportHeight = 56f,
            tintColor = tintColor,
            basePath = buildCheckPath()
        )
    }

    private fun buildArrowRightPath(): Path = Path().apply {
        fillType = Path.FillType.EVEN_ODD
        moveTo(1.65f, 1.469f)
        cubicTo(1.929f, 1.19f, 2.381f, 1.19f, 2.66f, 1.469f)
        lineTo(8.721f, 7.53f)
        cubicTo(9.0f, 7.809f, 9.0f, 8.261f, 8.721f, 8.54f)
        lineTo(2.66f, 14.601f)
        cubicTo(2.381f, 14.88f, 1.929f, 14.88f, 1.65f, 14.601f)
        cubicTo(1.371f, 14.322f, 1.371f, 13.87f, 1.65f, 13.591f)
        lineTo(7.205f, 8.035f)
        lineTo(1.65f, 2.479f)
        cubicTo(1.371f, 2.2f, 1.371f, 1.748f, 1.65f, 1.469f)
        close()
    }

    private fun buildArrowUpDownPath(): Path = Path().apply {
        fillType = Path.FillType.EVEN_ODD
        moveTo(2.397f, 4.7384f)
        lineTo(4.5688f, 2.5665f)
        lineTo(5.0075f, 2.1278f)
        lineTo(5.4266f, 2.5469f)
        lineTo(7.5985f, 4.7187f)
        lineTo(8.531f, 5.6512f)
        cubicTo(8.8282f, 5.9485f, 9.3102f, 5.9485f, 9.6075f, 5.6512f)
        cubicTo(9.9047f, 5.354f, 9.9047f, 4.872f, 9.6075f, 4.5747f)
        lineTo(8.675f, 3.6423f)
        lineTo(6.5031f, 1.4704f)
        lineTo(5.5706f, 0.5379f)
        cubicTo(5.3595f, 0.3267f, 5.0551f, 0.2656f, 4.7899f, 0.3544f)
        cubicTo(4.6561f, 0.3855f, 4.5291f, 0.4532f, 4.4248f, 0.5575f)
        lineTo(3.4924f, 1.49f)
        lineTo(1.3205f, 3.6619f)
        lineTo(0.388f, 4.5943f)
        cubicTo(0.0907f, 4.8916f, 0.0907f, 5.3736f, 0.388f, 5.6708f)
        cubicTo(0.6853f, 5.9681f, 1.1672f, 5.9681f, 1.4645f, 5.6708f)
        lineTo(2.397f, 4.7384f)
        close()

        moveTo(2.397f, 11.257f)
        lineTo(4.5688f, 13.4289f)
        lineTo(5.0075f, 13.8675f)
        lineTo(5.4266f, 13.4485f)
        lineTo(7.5985f, 11.2766f)
        lineTo(8.531f, 10.3441f)
        cubicTo(8.8282f, 10.0468f, 9.3102f, 10.0468f, 9.6075f, 10.3441f)
        cubicTo(9.9047f, 10.6414f, 9.9047f, 11.1233f, 9.6075f, 11.4206f)
        lineTo(8.675f, 12.3531f)
        lineTo(6.5031f, 14.525f)
        lineTo(5.5706f, 15.4574f)
        cubicTo(5.3594f, 15.6686f, 5.0551f, 15.7298f, 4.7899f, 15.6409f)
        cubicTo(4.6561f, 15.6098f, 4.5291f, 15.5421f, 4.4248f, 15.4378f)
        lineTo(3.4924f, 14.5053f)
        lineTo(1.3205f, 12.3335f)
        lineTo(0.388f, 11.401f)
        cubicTo(0.0907f, 11.1037f, 0.0907f, 10.6217f, 0.388f, 10.3245f)
        cubicTo(0.6853f, 10.0272f, 1.1672f, 10.0272f, 1.4645f, 10.3245f)
        lineTo(2.397f, 11.257f)
        close()
    }

    private fun buildCheckPath(): Path = Path().apply {
        fillType = Path.FillType.EVEN_ODD
        moveTo(46.8171f, 18.1514f)
        cubicTo(48.0496f, 16.6624f, 47.8417f, 14.4561f, 46.3527f, 13.2235f)
        cubicTo(44.8636f, 11.991f, 42.6573f, 12.1989f, 41.4247f, 13.6879f)
        lineTo(22.9535f, 36.0031f)
        lineTo(13.4007f, 26.4502f)
        cubicTo(12.0338f, 25.0833f, 9.8177f, 25.0833f, 8.4509f, 26.4502f)
        cubicTo(7.0841f, 27.817f, 7.0841f, 30.0331f, 8.4509f, 31.3999f)
        lineTo(20.7077f, 43.6567f)
        cubicTo(21.7243f, 44.6733f, 23.2108f, 44.9338f, 24.4682f, 44.4381f)
        cubicTo(25.0159f, 44.2302f, 25.5189f, 43.8818f, 25.9192f, 43.3982f)
        lineTo(46.8171f, 18.1514f)
        close()
    }

    private class MiuixVectorDrawable(
        private val widthPx: Int,
        private val heightPx: Int,
        private val viewportWidth: Float,
        private val viewportHeight: Float,
        @ColorInt tintColor: Int,
        private val basePath: Path
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = tintColor
        }
        private val scaledPath = Path()
        private val matrix = Matrix()

        override fun getIntrinsicWidth(): Int = widthPx
        override fun getIntrinsicHeight(): Int = heightPx

        override fun onBoundsChange(bounds: Rect) {
            super.onBoundsChange(bounds)
            matrix.reset()
            matrix.setScale(
                bounds.width() / viewportWidth,
                bounds.height() / viewportHeight
            )
            matrix.postTranslate(bounds.left.toFloat(), bounds.top.toFloat())
            basePath.transform(matrix, scaledPath)
        }

        override fun draw(canvas: Canvas) {
            if (!bounds.isEmpty) {
                canvas.drawPath(scaledPath, paint)
            }
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
            invalidateSelf()
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
