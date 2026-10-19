package com.endiq.turtlelauncher.feature.turtle

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.endiq.turtlelauncher.ui.miuix.MiuixTheme
import java.util.LinkedList

/**
 * Miuix-styled sparkline view for the "RAM Usage Graph" HUD module.
 */
class RamGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val maxSamples = 40
    private val samples = LinkedList<Float>() // 0f..1f, used/max ratio

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MiuixTheme.colors(context).primary
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2.5f * resources.displayMetrics.density
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val p = MiuixTheme.colors(context).primary
        color = Color.argb(56, Color.red(p), Color.green(p), Color.blue(p))
        style = Paint.Style.FILL
    }

    /** [usedMb]/[maxMb] — pass maxMb <= 0 to clear the graph. */
    fun pushSample(usedMb: Long, maxMb: Long) {
        val ratio = if (maxMb > 0) (usedMb.toFloat() / maxMb.toFloat()).coerceIn(0f, 1f) else 0f
        samples.addLast(ratio)
        while (samples.size > maxSamples) samples.removeFirst()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (samples.size < 2 || width == 0 || height == 0) return

        val stepX = width.toFloat() / (maxSamples - 1).coerceAtLeast(1)
        val startIndex = maxSamples - samples.size

        val line = Path()
        val fill = Path()
        samples.forEachIndexed { i, ratio ->
            val x = (startIndex + i) * stepX
            val y = height - (ratio * height)
            if (i == 0) {
                line.moveTo(x, y)
                fill.moveTo(x, height.toFloat())
                fill.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        val lastX = (startIndex + samples.size - 1) * stepX
        fill.lineTo(lastX, height.toFloat())
        fill.close()

        canvas.drawPath(fill, fillPaint)
        canvas.drawPath(line, linePaint)
    }
}
