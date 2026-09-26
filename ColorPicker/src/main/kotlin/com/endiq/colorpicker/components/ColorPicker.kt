package com.endiq.colorpicker.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.endiq.colorpicker.ColorPickerController

/**
 * Square color selection panel for picking saturation and brightness
 * @param controller the [ColorPickerController] instance used to control and react to color changes
 * @param onChangeFinished callback after dragging ends
 */
@Composable
fun ColorSquarePicker(
    controller: ColorPickerController,
    modifier: Modifier = Modifier,
    onChangeFinished: () -> Unit = {}
) {
    var pressOffset by remember { mutableStateOf(Offset.Zero) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    var heightPx by remember { mutableFloatStateOf(0f) }

    val hue by remember(controller.hue) {
        derivedStateOf { controller.hue }
    }

    val currentColor by controller.color

    LaunchedEffect(controller.saturation, controller.value, widthPx, heightPx) {
        if (widthPx > 0f && heightPx > 0f) {
            pressOffset = satValToOffset(controller.saturation, controller.value, widthPx, heightPx)
        }
    }

    Canvas(
        modifier = modifier
            .squarePicker(
                widthPx = widthPx,
                heightPx = heightPx,
                onOffsetChanged = { offset ->
                    pressOffset = offset
                    val (s, v) = pointToSatVal(offset.x, offset.y, widthPx, heightPx)
                    controller.setSaturation(s)
                    controller.setValue(v)
                },
                onSizeChanged = { size ->
                    widthPx = size.width.toFloat()
                    heightPx = size.height.toFloat()
                },
                onChangeFinished = onChangeFinished
            )
    ) {
        //Saturation-brightness palette
        val hueColor = Color.hsv(hue, 1f, 1f)

        //Horizontal saturation gradient: white -> current hue
        val satBrush = Brush.horizontalGradient(
            0f to Color.White,
            1f to hueColor
        )

        //Vertical brightness gradient: transparent -> black
        val valBrush = Brush.verticalGradient(
            0f to Color.Transparent,
            1f to Color.Black
        )

        //Saturation
        drawRect(
            brush = satBrush,
            size = size
        )

        //Brightness overlay
        drawRect(
            brush = valBrush,
            size = size,
            blendMode = BlendMode.Multiply
        )

        drawCenterIndicator(
            currentColor = currentColor,
            pressOffset = pressOffset
        )
    }
}

/**
 * Converts touch coordinates into saturation and brightness
 */
private fun pointToSatVal(x: Float, y: Float, widthPx: Float, heightPx: Float): Pair<Float, Float> {
    val sat = (x / widthPx).coerceIn(0f, 1f)
    val value = (1f - y / heightPx).coerceIn(0f, 1f)
    return sat to value
}

/**
 * Converts saturation and brightness into coordinates
 */
private fun satValToOffset(s: Float, v: Float, widthPx: Float, heightPx: Float): Offset {
    val x = (s * widthPx).coerceIn(0f, widthPx)
    val y = ((1f - v) * heightPx).coerceIn(0f, heightPx)
    return Offset(x, y)
}