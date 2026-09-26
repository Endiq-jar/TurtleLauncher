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
import androidx.compose.ui.graphics.Brush
import com.endiq.colorpicker.ColorPickerController
import com.endiq.colorpicker.getGradientColorAtPosition

/**
 * Alpha bar picker
 * @param controller the [ColorPickerController] instance used to control and react to alpha changes
 * @param onChangeFinished callback after the bar finishes being dragged
 */
@Composable
fun AlphaBarPicker(
    controller: ColorPickerController,
    modifier: Modifier = Modifier,
    onChangeFinished: () -> Unit = {}
) {
    var pressOffset by remember { mutableStateOf(Offset.Zero) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    var heightPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(controller.alpha, widthPx) {
        if (widthPx > 0f) {
            pressOffset = Offset(alphaToX(controller.alpha, widthPx), heightPx / 2f)
        }
    }

    //Current color
    val currentColor by controller.color

    //Transparent -> current color
    val colors by remember(currentColor) {
        derivedStateOf {
            listOf(
                currentColor.copy(alpha = 0f),
                currentColor.copy(alpha = 1f)
            )
        }
    }

    Canvas(
        modifier = modifier
            .progressBar(
                widthPx = widthPx,
                heightPx = heightPx,
                onOffsetChanged = { offset ->
                    pressOffset = offset
                    controller.setAlpha(xToAlpha(offset.x, widthPx))
                },
                onSizeChanged = { size ->
                    widthPx = size.width.toFloat()
                    heightPx = size.height.toFloat()
                },
                onChangeFinished = onChangeFinished
            )
    ) {
        transparentCheckerBackground(
            width = size.width,
            height = size.height
        )

        drawRect(
            brush = Brush.horizontalGradient(colors = colors),
            size = size
        )

        val indicatorColor = colors.getGradientColorAtPosition(
            x = pressOffset.x,
            widthPx = widthPx
        )

        drawVerticalIndicator(
            currentColor = indicatorColor,
            xPos = pressOffset.x,
            height = size.height
        )
    }
}

private fun alphaToX(alpha: Float, widthPx: Float) =
    (alpha * widthPx).coerceIn(0f, widthPx)

private fun xToAlpha(x: Float, widthPx: Float) =
    (x / widthPx).coerceIn(0f, 1f)