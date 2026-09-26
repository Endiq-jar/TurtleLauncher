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

package com.endiq.turtlelauncher.ui.control.mouse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.gif.GifDecoder
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.bridge.CursorShape
import com.endiq.turtlelauncher.bridge.TLBridgeStates
import com.endiq.turtlelauncher.path.PathManager
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.setting.enums.MouseControlMode
import com.endiq.turtlelauncher.utils.device.PhysicalMouseChecker
import com.endiq.turtlelauncher.utils.file.child
import com.endiq.turtlelauncher.utils.file.ifExists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Default (arrow) cursor image file
 */
val arrowPointerFile: File = PathManager.DIR_MOUSE_POINTER.child("default_pointer.image")

/**
 * Hand cursor image file
 */
val linkPointerFile: File = PathManager.DIR_MOUSE_POINTER.child("link_pointer.image")

/**
 * Input cursor image file
 */
val iBeamPointerFile: File = PathManager.DIR_MOUSE_POINTER.child("ibeam_pointer.image")

/**
 * Crosshair cursor image file
 */
val crossHairPointerFile: File = PathManager.DIR_MOUSE_POINTER.child("crosshair_pointer.image")

/**
 * Vertical-resize cursor icon file
 */
val resizeNSPointerFile: File = PathManager.DIR_MOUSE_POINTER.child("resize_NS_pointer.image")

/**
 * Horizontal-resize cursor icon file
 */
val resizeEWPointerFile: File = PathManager.DIR_MOUSE_POINTER.child("resize_EW_pointer.image")

/**
 * All-directions cursor icon file
 */
val resizeAllPointerFile: File = PathManager.DIR_MOUSE_POINTER.child("resize_ALL_pointer.image")

/**
 * Not-allowed cursor icon file
 */
val notAllowedPointerFile: File = PathManager.DIR_MOUSE_POINTER.child("not_allowed_pointer.image")

/**
 * Virtual pointer simulation layer
 * @param controlMode               Control mode: SLIDE (slide control), CLICK (click control)
 * @param enableMouseClick          Whether virtual mouse click actions are enabled (slide control only)
 * @param longPressTimeoutMillis    long-press trigger detection timeout
 * @param requestPointerCapture     whether pointer capture is used
 * @param hideMouseInClickMode      whether the pointer hides in click-control mode
 * @param lastMousePosition         last virtual mouse position
 * @param onTap                     tap callback; argument is the touch point's absolute coordinates in the control
 * @param onLongPress               long-press start callback
 * @param onLongPressEnd            long-press end callback
 * @param onPointerMove             pointer move callback; takes the pointer position in SLIDE mode and the finger position in CLICK mode
 * @param onMouseScroll             physical mouse wheel scrolling
 * @param onMouseButton             physical mouse button feedback
 * @param isMoveOnlyPointer         whether the parent marked the pointer as move-only
 * @param onOccupiedPointer         occupy-pointer callback
 * @param onReleasePointer          release-pointer callback
 * @param mouseSize                 pointer size
 * @param cursorSensitivity         pointer sensitivity (slide mode only)
 */
@Composable
fun VirtualPointerLayout(
    modifier: Modifier = Modifier,
    controlMode: MouseControlMode = AllSettings.mouseControlMode.state,
    enableMouseClick: Boolean = AllSettings.enableMouseClick.state,
    longPressTimeoutMillis: Long = AllSettings.mouseLongPressDelay.state.toLong(),
    requestPointerCapture: Boolean = !AllSettings.physicalMouseMode.state,
    hideMouseInClickMode: Boolean = AllSettings.hideMouse.state,
    lastMousePosition: Offset? = null,
    onTap: (Offset) -> Unit = {},
    onLongPress: () -> Unit = {},
    onLongPressEnd: () -> Unit = {},
    onPointerMove: (Offset) -> Unit = {},
    onMouseScroll: (Offset) -> Unit = {},
    onMouseButton: (button: Int, pressed: Boolean) -> Unit = { _, _ -> },
    isMoveOnlyPointer: (PointerId) -> Boolean = { false },
    onOccupiedPointer: (PointerId) -> Unit = {},
    onReleasePointer: (PointerId) -> Unit = {},
    mouseSize: Dp = AllSettings.mouseSize.state.dp,
    cursorSensitivity: Int = AllSettings.cursorSensitivity.state,
    requestFocusKey: Any? = null
) {
    val speedFactor = cursorSensitivity / 100f

    val windowSize = LocalWindowInfo.current.containerSize
    val screenWidth: Float = windowSize.width.toFloat()
    val screenHeight: Float = windowSize.height.toFloat()

    val capturePointer = requestPointerCapture && !rememberTextInputActive()

    var showMousePointer by remember {
        mutableStateOf(requestPointerCapture)
    }
    fun updateMousePointer(show: Boolean) {
        showMousePointer = show
    }
    LaunchedEffect(hideMouseInClickMode) {
        updateMousePointer(
            show = when {
                //Physical mouse connected: is it captured control mode?
                PhysicalMouseChecker.physicalMouseConnected -> requestPointerCapture
                //Click-control mode: decided by the hide-virtual-mouse setting
                controlMode == MouseControlMode.CLICK -> !hideMouseInClickMode
                //Slide control always shows it
                else -> controlMode == MouseControlMode.SLIDE
            }
        )
    }

    var pointerPosition by remember {
        val pos = lastMousePosition?.takeIf {
            //With a physical mouse in use, fall back to the last virtual mouse position
            //Otherwise default the mouse to the screen center
            !showMousePointer
        } ?: Offset(screenWidth / 2f, screenHeight / 2f)
        onPointerMove(pos)
        mutableStateOf(pos)
    }

    Box(modifier = modifier) {
        val cursorShape by TLBridgeStates.cursorShape.collectAsStateWithLifecycle()

        if (showMousePointer) {
            MousePointer(
                modifier = Modifier.mouseFixedPosition(
                    mouseSize = mouseSize,
                    cursorShape = cursorShape,
                    pointerPosition = pointerPosition
                ),
                cursorShape = cursorShape,
                mouseSize = mouseSize,
                mouseFile = getMouseFile(cursorShape).ifExists(),
            )
        }

        TouchpadLayout(
            modifier = Modifier.fillMaxSize(),
            controlMode = controlMode,
            enableMouseClick = enableMouseClick,
            longPressTimeoutMillis = longPressTimeoutMillis,
            requestPointerCapture = capturePointer,
            pointerIcon = cursorShape.composeIcon,
            onTap = { fingerPos ->
                onTap(
                    if (controlMode == MouseControlMode.CLICK) {
                        updateMousePointer(!hideMouseInClickMode)
                        //The current finger's absolute coordinates
                        pointerPosition = fingerPos
                        fingerPos
                    } else {
                        pointerPosition
                    }
                )
            },
            onLongPress = onLongPress,
            onLongPressEnd = onLongPressEnd,
            onPointerMove = { offset, isMoveOnly ->
                pointerPosition = if (isMoveOnly || controlMode == MouseControlMode.SLIDE) {
                    updateMousePointer(true)
                    Offset(
                        x = (pointerPosition.x + offset.x * speedFactor).coerceIn(0f, screenWidth),
                        y = (pointerPosition.y + offset.y * speedFactor).coerceIn(0f, screenHeight)
                    )
                } else {
                    updateMousePointer(!hideMouseInClickMode)
                    //The current finger's absolute coordinates
                    offset
                }
                onPointerMove(pointerPosition)
            },
            onMouseMove = { offset ->
                if (capturePointer) {
                    updateMousePointer(true)
                    pointerPosition = Offset(
                        x = (pointerPosition.x + offset.x * speedFactor).coerceIn(0f, screenWidth),
                        y = (pointerPosition.y + offset.y * speedFactor).coerceIn(0f, screenHeight)
                    )
                    onPointerMove(pointerPosition)
                } else {
                    //Not pointer-capture mode
                    updateMousePointer(false)
                    pointerPosition = offset
                    onPointerMove(pointerPosition)
                }
            },
            onMouseScroll = onMouseScroll,
            onMouseButton = onMouseButton,
            isMoveOnlyPointer = isMoveOnlyPointer,
            onOccupiedPointer = onOccupiedPointer,
            onReleasePointer = onReleasePointer,
            inputChange = arrayOf<Any>(speedFactor, controlMode),
            requestFocusKey = requestFocusKey
        )
    }
}

/**
 * Virtual mouse position decoration; computes the right pointer position from size,
 * cursor shape, actual pointer position and the launcher's hotspot setting
 */
@Composable
fun Modifier.mouseFixedPosition(
    mouseSize: Dp,
    cursorShape: CursorShape,
    pointerPosition: Offset
): Modifier {
    val sizePx = with(LocalDensity.current) { mouseSize.toPx() }
    val hotspotUnit = remember(
        cursorShape
    ) {
        when (cursorShape) {
            CursorShape.Arrow -> AllSettings.arrowMouseHotspot
            CursorShape.IBeam -> AllSettings.iBeamMouseHotspot
            CursorShape.Hand -> AllSettings.linkMouseHotspot
            CursorShape.CrossHair -> AllSettings.crossHairMouseHotspot
            CursorShape.ResizeNS -> AllSettings.resizeNSMouseHotspot
            CursorShape.ResizeEW -> AllSettings.resizeEWMouseHotspot
            CursorShape.ResizeAll -> AllSettings.resizeAllMouseHotspot
            CursorShape.NotAllowed -> AllSettings.notAllowedMouseHotspot
        }
    }
    val hotspot = hotspotUnit.state
    val x = pointerPosition.x - sizePx * (hotspot.xPercent.toFloat() / 100f)
    val y = pointerPosition.y - sizePx * (hotspot.yPercent.toFloat() / 100f)

    return this.absoluteOffset(
        x = with(LocalDensity.current) { x.toDp() },
        y = with(LocalDensity.current) { y.toDp() }
    )
}

/**
 * Returns the matching mouse image per cursor shape
 */
@Composable
fun getMouseFile(
    cursorShape: CursorShape
): File {
    return remember(cursorShape) {
        when (cursorShape) {
            CursorShape.Arrow -> arrowPointerFile
            CursorShape.IBeam -> iBeamPointerFile
            CursorShape.Hand -> linkPointerFile
            CursorShape.CrossHair -> crossHairPointerFile
            CursorShape.ResizeNS -> resizeNSPointerFile
            CursorShape.ResizeEW -> resizeEWPointerFile
            CursorShape.ResizeAll -> resizeAllPointerFile
            CursorShape.NotAllowed -> notAllowedPointerFile
        }
    }
}

/**
 * Renders the virtual mouse pointer on screen
 */
@Composable
fun MousePointer(
    modifier: Modifier = Modifier,
    mouseSize: Dp = AllSettings.mouseSize.state.dp,
    cursorShape: CursorShape = CursorShape.Arrow,
    mouseFile: File?,
    centerIcon: Boolean = false,
    triggerRefresh: Any? = null,
    crossfade: Boolean = false
) {
    val context = LocalContext.current
    val loader = remember(triggerRefresh, crossfade, mouseSize) {
        ImageLoader.Builder(context)
            .components {
                add(GifDecoder.Factory())
                add(SvgDecoder.Factory())
            }
            .crossfade(crossfade)
            .build()
    }

    val fileExists by produceState(initialValue = false, triggerRefresh, mouseFile) {
        value = withContext(Dispatchers.IO) { mouseFile?.exists() == true }
    }
    val defaultRes = when (cursorShape) {
        CursorShape.Arrow -> R.drawable.img_mouse_pointer_arrow
        CursorShape.IBeam -> R.drawable.img_mouse_pointer_ibeam
        CursorShape.Hand -> R.drawable.img_mouse_pointer_link
        CursorShape.CrossHair -> R.drawable.img_mouse_pointer_crosshair
        CursorShape.ResizeNS -> R.drawable.img_mouse_pointer_resize_ns
        CursorShape.ResizeEW -> R.drawable.img_mouse_pointer_resize_ew
        CursorShape.ResizeAll -> R.drawable.img_mouse_pointer_resize_move
        CursorShape.NotAllowed -> R.drawable.img_mouse_pointer_not_allowed
    }

    val model = remember(fileExists, triggerRefresh, cursorShape) {
        if (fileExists && mouseFile != null) mouseFile else defaultRes
    }

    val imageAlignment = if (centerIcon) Alignment.Center else Alignment.TopStart

    AsyncImage(
        model = model,
        imageLoader = loader,
        contentDescription = null,
        alignment = imageAlignment,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(mouseSize)
    )
}