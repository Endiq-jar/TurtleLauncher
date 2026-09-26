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

package com.endiq.turtlelauncher.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/**
 * Android displayable string proxy interface, offering several text sources and presentations
 */
sealed interface AndroidStringText {
    /**
     * Directly displays a plain string
     *
     * @property value the string content
     */
    data class Text(val value: String) : AndroidStringText

    /**
     * Displays styled rich text
     *
     * @property value the [AnnotatedString] content
     */
    data class Annotated(val value: AnnotatedString) : AndroidStringText

    /**
     * Loads a string from an Android resource ID, supporting format args
     *
     * @property key the string resource ID
     * @property args format args; [AndroidStringText] supported
     */
    data class StringRes(
        @field:androidx.annotation.StringRes
        val key: Int,
        val args: Array<out Any>?
    ) : AndroidStringText {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as StringRes

            if (key != other.key) return false
            if (!args.contentEquals(other.args)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = key
            result = 31 * result + (args?.contentHashCode() ?: 0)
            return result
        }
    }

    /**
     * Concatenates multiple [AndroidStringText] instances
     *
     * @property texts the strings to concatenate
     */
    data class Appended(
        val texts: List<AndroidStringText>
    ) : AndroidStringText
}

/**
 * Creates an [AndroidStringText.Text] instance
 *
 * @param string the string content
 */
fun androidText(string: String) = AndroidStringText.Text(string)

/**
 * Creates an [AndroidStringText.Annotated] instance
 *
 * @param annotated the [AnnotatedString] content
 */
fun androidText(annotated: AnnotatedString) = AndroidStringText.Annotated(annotated)

/**
 * Creates an [AndroidStringText.StringRes] instance
 *
 * @param key the string resource ID
 */
fun androidText(@StringRes key: Int) = AndroidStringText.StringRes(key, null)

/**
 * Creates a [AndroidStringText.StringRes] instance with format arguments
 *
 * @param key the string resource ID
 * @param args the format args
 */
fun androidText(
    @StringRes
    key: Int,
    vararg args: Any
) = AndroidStringText.StringRes(key, args)

/**
 * Creates an [AndroidStringText.Appended] instance
 */
fun androidText(vararg texts: AndroidStringText) = AndroidStringText.Appended(texts.toList())


/**
 * Builds an [AndroidStringText.Appended] instance via DSL
 */
inline fun buildAppendedText(
    block: AndroidStringTextBuilder.() -> Unit
): AndroidStringText = AndroidStringTextBuilder().apply(block).build()

@DslMarker
private annotation class AndroidStringTextDsl
@AndroidStringTextDsl
class AndroidStringTextBuilder {
    private val texts = mutableListOf<AndroidStringText>()
    /**
     * Appends a plain string
     */
    fun append(text: String) {
        texts.add(AndroidStringText.Text(text))
    }
    /**
     * Appends styled rich text
     */
    fun append(text: AnnotatedString) {
        texts.add(AndroidStringText.Annotated(text))
    }
    /**
     * Loads a string from an Android resource ID
     */
    fun append(@StringRes resId: Int) {
        texts.add(AndroidStringText.StringRes(resId, null))
    }
    /**
     * Loads a string from an Android resource ID, supporting format args
     */
    fun append(@StringRes resId: Int, vararg args: Any) {
        texts.add(AndroidStringText.StringRes(resId, args))
    }
    /**
     * Appends another string proxy
     */
    fun append(other: AndroidStringText) {
        texts.add(other)
    }
    /**
     * Builds an [AndroidStringText.Appended] instance
     */
    fun build(): AndroidStringText = AndroidStringText.Appended(texts.toList())
}



/**
 * A Composable for displaying an [AndroidStringText]
 *
 * A wrapper over [Text]; picks the right renderer per [AndroidStringText] class
 * variant automatically
 */
@Composable
fun AndroidStringText(
    text: AndroidStringText,
    modifier: Modifier = Modifier,
    autoSize: TextAutoSize? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    style: TextStyle = LocalTextStyle.current,
) {
    Text(
        text = resolveAndroidString(text),
        modifier = modifier,
        autoSize = autoSize,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        style = style,
    )
}

/**
 * Resolves an [AndroidStringText] into an [AnnotatedString]
 */
@Composable
fun resolveAndroidString(text: AndroidStringText): AnnotatedString {
    return when (text) {
        is AndroidStringText.Text -> AnnotatedString(text.value)
        is AndroidStringText.Annotated -> text.value
        is AndroidStringText.StringRes -> {
            val args = text.args
            AnnotatedString(
                if (args == null) {
                    stringResource(text.key)
                } else {
                    val resolvedArgs = args.map { arg ->
                        if (arg is AndroidStringText) {
                            resolveAndroidString(arg).text
                        } else {
                            arg
                        }
                    }.toTypedArray()
                    stringResource(text.key, *resolvedArgs)
                }
            )
        }
        is AndroidStringText.Appended -> {
            buildAnnotatedString {
                text.texts.forEach {
                    append(resolveAndroidString(it))
                }
            }
        }
    }
}

/**
 * Resolves an [AndroidStringText] into a [String] outside Composable contextsng]
 *
 * @param context the Android [Context] used to load [AndroidStringText.StringRes] string resources
 */
fun AndroidStringText.toAndroidString(context: Context): String = when (this) {
    is AndroidStringText.Text -> value
    is AndroidStringText.Annotated -> value.toString()
    is AndroidStringText.StringRes -> if (args == null) {
        context.getString(key)
    } else {
        val resolvedArgs = args.map { arg ->
            if (arg is AndroidStringText) {
                arg.toAndroidString(context)
            } else {
                arg
            }
        }.toTypedArray()
        context.getString(key, *resolvedArgs)
    }
    is AndroidStringText.Appended -> texts.joinToString("") { it.toAndroidString(context) }
}
