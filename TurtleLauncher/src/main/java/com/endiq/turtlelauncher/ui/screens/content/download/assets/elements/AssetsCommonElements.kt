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

package com.endiq.turtlelauncher.ui.screens.content.download.assets.elements

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.game.download.assets.platform.Platform
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformClasses
import com.endiq.turtlelauncher.game.download.modpack.platform.PackPlatform
import com.endiq.turtlelauncher.ui.components.ShimmerBox
import com.endiq.turtlelauncher.utils.logging.Logger

private const val TAG = "AssetsCommonElements"

/**
 * Locally installed badge
 */
@Composable
fun InstalledModBadge(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.tertiary,
    checkColor: Color = MaterialTheme.colorScheme.onTertiary,
    size: Dp = 14.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            modifier = Modifier.size(size * 0.7f),
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = checkColor
        )
    }
}

/**
 * Platform badge element: platform logo + name
 */
@Composable
fun PlatformIdentifier(
    modifier: Modifier = Modifier,
    platform: Platform,
    iconSize: Dp = 12.dp,
    color: Color = platform.getBrandColor(),
    contentColor: Color = platform.getBrandContentColor(),
    shape: Shape = MaterialTheme.shapes.large,
    textStyle: TextStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
) {
    BasicIdentifier(
        painter = painterResource(platform.getDrawable()),
        text = platform.displayName,
        modifier = modifier,
        iconSize = iconSize,
        color = color,
        contentColor = contentColor,
        shape = shape,
        textStyle = textStyle,
    )
}

/**
 * Returns the platform's logo
 */
fun Platform.getDrawable() = when (this) {
    Platform.CURSEFORGE -> R.drawable.img_platform_curseforge
    Platform.MODRINTH -> R.drawable.img_platform_modrinth
}

/**
 * Platform brand card background, from the platform logo's main color
 */
fun Platform.getBrandColor(): Color = when (this) {
    Platform.CURSEFORGE -> Color(0xFFF16436)
    Platform.MODRINTH -> Color(0xFF1BD96A)
}

/**
 * Platform brand card content color
 */
fun Platform.getBrandContentColor(): Color = when (this) {
    Platform.CURSEFORGE -> Color.White
    Platform.MODRINTH -> Color(0xFF072314)
}

/**
 * Resource type badge element
 */
@Composable
fun ClassesIdentifier(
    classes: PlatformClasses,
    modifier: Modifier = Modifier,
    iconSize: Dp = 12.dp,
    color: Color = MaterialTheme.colorScheme.tertiary,
    contentColor: Color = MaterialTheme.colorScheme.onTertiary,
    shape: Shape = MaterialTheme.shapes.large,
    textStyle: TextStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
) {
    BasicIdentifier(
        painter = painterResource(classes.getDrawable()),
        text = stringResource(classes.getText()),
        modifier = modifier,
        iconSize = iconSize,
        color = color,
        contentColor = contentColor,
        shape = shape,
        textStyle = textStyle,
    )
}

private fun PlatformClasses.getDrawable() = when (this) {
    PlatformClasses.MOD -> R.drawable.ic_extension_outlined
    PlatformClasses.MOD_PACK -> R.drawable.ic_package_2_outlined
    PlatformClasses.RESOURCE_PACK -> R.drawable.ic_format_paint_outlined
    PlatformClasses.SAVES -> R.drawable.ic_public
    PlatformClasses.SHADERS -> R.drawable.ic_lightbulb
}

private fun PlatformClasses.getText() = when (this) {
    PlatformClasses.MOD -> R.string.download_category_mod
    PlatformClasses.MOD_PACK -> R.string.download_category_modpack
    PlatformClasses.RESOURCE_PACK -> R.string.download_category_resource_pack
    PlatformClasses.SAVES -> R.string.download_category_saves
    PlatformClasses.SHADERS -> R.string.download_category_shaders
}

@Composable
private fun BasicIdentifier(
    painter: Painter,
    text: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 12.dp,
    color: Color = MaterialTheme.colorScheme.tertiary,
    contentColor: Color = MaterialTheme.colorScheme.onTertiary,
    shape: Shape = MaterialTheme.shapes.large,
    textStyle: TextStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
) {
    Surface(
        modifier = modifier,
        color = color,
        contentColor = contentColor,
        shape = shape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                modifier = Modifier.size(iconSize),
                painter = painter,
                contentDescription = text
            )
            Text(
                text = text,
                style = textStyle
            )
        }
    }
}

/**
 * Resource cover icon from the network
 * @param iconUrl Icon URL
 */
@Composable
fun AssetsIcon(
    modifier: Modifier = Modifier,
    size: Dp,
    iconUrl: String? = null,
    colorFilter: ColorFilter? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val pxSize = with(density) { size.roundToPx() }

    val imageRequest = remember(iconUrl, pxSize) {
        iconUrl?.takeIf { it.isNotBlank() }?.let {
            ImageRequest.Builder(context)
                .data(it)
                .size(pxSize) //fixed size
                .listener(
                    onError = { _, result -> Logger.warning(TAG, "Coil: error = ${result.throwable}") }
                )
                .crossfade(true)
                .build()
        }
    }

    //Preload
    LaunchedEffect(imageRequest) {
        imageRequest?.let { context.imageLoader.enqueue(it) }
    }

    val painter = rememberAsyncImagePainter(
        model = imageRequest,
        placeholder = null,
        error = painterResource(R.drawable.ic_unknown_icon)
    )

    val state by painter.state.collectAsStateWithLifecycle()
    val sizeModifier = modifier.size(size)

    when (state) {
        AsyncImagePainter.State.Empty -> {
            Box(modifier = sizeModifier)
        }
        is AsyncImagePainter.State.Loading -> {
            ShimmerBox(
                modifier = sizeModifier
            )
        }
        is AsyncImagePainter.State.Error,
        is AsyncImagePainter.State.Success -> {
            Image(
                painter = painter,
                contentDescription = null,
                alignment = Alignment.Center,
                contentScale = ContentScale.Fit,
                modifier = sizeModifier,
                colorFilter = colorFilter
            )
        }
    }
}

/**
 * Pack format badge element: format icon + name
 *
 * Renders the icon with Image
 */
@Composable
fun PackIdentifier(
    modifier: Modifier = Modifier,
    platform: PackPlatform,
    iconSize: Dp = 12.dp,
    color: Color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.1f),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    shape: Shape = MaterialTheme.shapes.large,
    textStyle: TextStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
) {
    Surface(
        modifier = modifier,
        color = color,
        contentColor = contentColor,
        shape = shape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Image(
                modifier = Modifier.size(iconSize),
                painter = platform.getIcon(),
                contentDescription = platform.getText()
            )
            Text(
                text = platform.getText(),
                style = textStyle
            )
        }
    }
}