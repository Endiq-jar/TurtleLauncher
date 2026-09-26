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

package com.endiq.turtlelauncher.ui.control.input

import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.PlatformTextInputModifierNode
import androidx.compose.ui.platform.establishTextInputSession
import androidx.compose.ui.unit.IntRect
import androidx.core.content.getSystemService
import com.endiq.turtlelauncher.game.input.CharacterSenderStrategy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.text.forEach

/**
 * A composable modifier for handling text input on UI elements
 *
 * @param mode the [TextInputMode] that enables or disables text input
 * @param sender the [CharacterSenderStrategy] used to send characters to the game
 */
@Deprecated("Deprecated for compatibility reasons; input now goes through the input-bar UI proxying the IME. This code is no longer used and is kept for reference only")
@Composable
fun Modifier.textInputHandler(
    mode: TextInputMode,
    sender: CharacterSenderStrategy,
    onCloseInputMethod: () -> Unit = {}
): Modifier {
    OnKeyboardClosed {
        if (mode == TextInputMode.ENABLE) {
            onCloseInputMethod()
        }
    }
    val textMode by rememberUpdatedState(mode)
    val onCloseInputMethod1 by rememberUpdatedState(onCloseInputMethod)
    return this then TextInputModifier(sender, textMode, onCloseInputMethod1)
}

private data class TextInputModifier(
    private val sender: CharacterSenderStrategy,
    private val textMode: TextInputMode,
    private val onCloseInputMethod: () -> Unit = {}
) : ModifierNodeElement<TextInputNode>() {
    override fun create() = TextInputNode(sender, textMode, onCloseInputMethod)
    override fun update(node: TextInputNode) {
        node.update(sender, textMode, onCloseInputMethod)
    }
    override fun InspectorInfo.inspectableProperties() {
        name = "simulatorTextInputCore"
    }
}

/**
 * Captures text input using the Android Input Method Engine (IME)
 *
 * This class bridges the Compose UI framework and the underlying Android text input system
 * It establishes the text input session, configures editor info (for example input type and IME action),
 * and provides an [InputConnection] to handle text commits, key events and other IME interactions
 *
 * @param sender the [CharacterSenderStrategy] used to send the processed characters
 */
private class TextInputNode(
    private var sender: CharacterSenderStrategy,
    private var textInputMode: TextInputMode,
    private var onCloseInputMethod: () -> Unit
) : Modifier.Node(), PlatformTextInputModifierNode {
    private var session: Job? = null
    private val fakeCursorRect = IntRect(100, 500, 100, 550)

    override fun onAttach() {
        if (textInputMode == TextInputMode.ENABLE) {
            session = coroutineScope.launch {
                try {
                    establishTextInputSession {
                        val inputMethodManager = view.context.getSystemService<InputMethodManager>()
                            ?: error("InputMethodManager not supported")

                        val inputMethodIdentifier = Settings.Secure.getString(
                            view.context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD
                        )

                        val connection = InputConnectionImpl(view, inputMethodManager, inputMethodIdentifier)

                        inputMethodManager.updateCursorAnchorInfo(
                            view,
                            CursorAnchorInfo.Builder().apply {
                                setSelectionRange(0, 0)
                                setInsertionMarkerLocation(
                                    fakeCursorRect.left.toFloat(),
                                    fakeCursorRect.top.toFloat(),
                                    fakeCursorRect.right.toFloat(),
                                    fakeCursorRect.bottom.toFloat(),
                                    CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION
                                )
                                setMatrix(view.matrix)
                            }.build()
                        )

                        startInputMethod { info ->
                            info.inputType = InputType.TYPE_CLASS_TEXT or
                                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                                    InputType.TYPE_TEXT_VARIATION_NORMAL
                            info.imeOptions = EditorInfo.IME_ACTION_DONE or
                                    //Try not to enter fullscreen mode
                                    EditorInfo.IME_FLAG_NO_FULLSCREEN or
                                    //Try not to show extra auxiliary UI
                                    EditorInfo.IME_FLAG_NO_EXTRACT_UI

                            info.packageName = view.context.packageName
                            info.fieldId = view.id

                            info.initialSelStart = 0
                            info.initialSelEnd = 0

                            connection
                        }
                    }
                } catch (_: CancellationException) {
                }
            }
        }
    }

    private fun stopInput() {
        session?.cancel()
        session = null
    }

    /**
     * Updates the [sender] and [textInputMode] values and restarts
     */
    fun update(
        sender: CharacterSenderStrategy,
        textInputMode: TextInputMode,
        onCloseInputMethod: () -> Unit
    ) {
        this.sender = sender
        this.onCloseInputMethod = onCloseInputMethod
        if (this.textInputMode != textInputMode) {
            this.textInputMode = textInputMode
            stopInput()
            if (textInputMode == TextInputMode.ENABLE) {
                onAttach() //restart
            }
        } else {
            this.textInputMode = textInputMode
        }
    }

    /**
     * Handles text input and key events coming from the IME
     * It converts the received characters and key operations into the corresponding actions sent through the provided [CharacterSenderStrategy]
     *
     * This class overrides various [InputConnection] methods to handle text commits, key events, composing text, etc.
     * Most unimplemented methods return default values or no-op, because they are not required for this particular use case
     */
    private inner class InputConnectionImpl(
        private val view: View,
        private val imm: InputMethodManager,
        private val inputMethodIdentifier: String
    ) : InputConnection {
        private val textBuffer = StringBuilder()
        private var cursorPosition = 0
        private var composingStart = -1
        private var composingEnd = -1

        private var inBatchEdit = false
        private var pendingBackspaceCount = 0
        private var pendingTextToSend = StringBuilder()

        private val isMicrosoftSwiftKey: Boolean
            get() = inputMethodIdentifier.contains("com.microsoft.swiftkey") ||
                    inputMethodIdentifier.contains("com.touchtype.swiftkey") ||
                    inputMethodIdentifier.contains("swiftkey")

        /**
         * Sends text input to the game
         */
        private fun sendText(text: String) {
            text.forEach { char -> sender.sendChar(char) }
            if (isMicrosoftSwiftKey) {
                //After sending text, the buffer should be fully cleared for Microsoft SwiftKey
                cursorPosition = 0
                textBuffer.clear()
            }
        }

        /**
         * Sends the pending text when the batch edit finishes
         */
        private fun flushPendingText() {
            repeat(pendingBackspaceCount) { sender.sendBackspace() }
            pendingBackspaceCount = 0

            if (pendingTextToSend.isNotEmpty()) {
                sendText(pendingTextToSend.toString())
                pendingTextToSend.clear()
            }
        }

        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
            var composingLength = 0
            //If composing text currently exists, delete the composing region first
            if (composingStart in 0..<composingEnd) {
                val safeStart = composingStart.coerceIn(0, textBuffer.length)
                val safeEnd = composingEnd.coerceIn(0, textBuffer.length)
                composingLength = safeEnd - safeStart
                if (safeStart < safeEnd) {
                    textBuffer.delete(safeStart, safeEnd)
                    cursorPosition = safeStart
                }
                composingStart = -1
                composingEnd = -1
            }

            //Insert the committed text
            textBuffer.insert(cursorPosition, text)
            cursorPosition += text.length

            val newText = text.toString()

            if (isMicrosoftSwiftKey) {
                sendText(newText)
            } else {
                if (inBatchEdit) {
                    if (composingLength > 0) {
                        pendingBackspaceCount += composingLength
                    }
                    pendingTextToSend.append(newText)
                } else {
                    if (composingLength > 0) {
                        repeat(composingLength) { sender.sendBackspace() }
                    }
                    if (newText.isNotEmpty()) {
                        sendText(newText)
                    }
                }
            }

            updateInputMethodState()
            return true
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_ENTER -> {
                            sender.sendEnter()
                            onCloseInputMethod()
                        }
                        KeyEvent.KEYCODE_DEL -> {
                            if (cursorPosition > 0) {
                                textBuffer.deleteCharAt(cursorPosition - 1)
                                cursorPosition--
                                updateInputMethodState()
                            }
                            sender.sendBackspace()
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> sender.sendLeft()
                        KeyEvent.KEYCODE_DPAD_RIGHT -> sender.sendRight()
                        KeyEvent.KEYCODE_DPAD_UP -> sender.sendUp()
                        KeyEvent.KEYCODE_DPAD_DOWN -> sender.sendDown()
                        else -> {
                            if (event.unicodeChar != 0) {
                                val char = event.unicodeChar.toChar()
                                textBuffer.insert(cursorPosition, char)
                                cursorPosition++
                                updateInputMethodState()
                                sender.sendChar(char)
                            } else {
                                sender.sendOther(event)
                            }
                        }
                    }
                }
            }
            return true
        }

        override fun setComposingRegion(start: Int, end: Int): Boolean {
            if (start in 0..textBuffer.length && end in 0..textBuffer.length && start <= end) {
                composingStart = start
                composingEnd = end
                updateInputMethodState()
                return true
            }
            return false
        }

        override fun getTextBeforeCursor(length: Int, flags: Int): CharSequence {
            val start = max(0, cursorPosition - length)
            return textBuffer.substring(start, cursorPosition)
        }

        override fun getTextAfterCursor(length: Int, flags: Int): CharSequence {
            val end = min(textBuffer.length, cursorPosition + length)
            return textBuffer.substring(cursorPosition, end)
        }

        override fun getSelectedText(p0: Int): CharSequence? = null

        override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
            //Delete the current composing region
            if (composingStart in 0..<composingEnd) {
                val safeStart = composingStart.coerceIn(0, textBuffer.length)
                val safeEnd = composingEnd.coerceIn(0, textBuffer.length)
                if (isMicrosoftSwiftKey || safeStart < safeEnd) {
                    textBuffer.delete(safeStart, safeEnd)
                    cursorPosition = safeStart
                }
            } else {
                //If there is no explicit composing region but the IME restarts composing, delete a possible trailing duplicate
                if (cursorPosition < textBuffer.length) {
                    textBuffer.delete(cursorPosition, textBuffer.length)
                }
                composingStart = cursorPosition
            }

            //Insert the new composing text
            textBuffer.insert(cursorPosition, text)
            composingStart = cursorPosition
            composingEnd = composingStart + text.length
            cursorPosition = composingEnd

            updateInputMethodState()
            return true
        }

        override fun finishComposingText(): Boolean {
            if (composingStart in 0..<composingEnd) {
                //Commit the composing text
                val safeStart = composingStart.coerceIn(0, textBuffer.length)
                val safeEnd = composingEnd.coerceIn(0, textBuffer.length)
                if (safeStart < safeEnd) {
                    val composedText = textBuffer.substring(safeStart, safeEnd)
                    if (isMicrosoftSwiftKey) {
                        sendText(composedText)
                    } else {
                        if (inBatchEdit) {
                            pendingTextToSend.append(composedText)
                        } else {
                            sendText(composedText)
                        }
                    }
                }

                composingStart = -1
                composingEnd = -1
            }

            updateInputMethodState()
            return true
        }

        override fun setSelection(start: Int, end: Int): Boolean {
            if (start in 0..textBuffer.length && end in 0..textBuffer.length) {
                cursorPosition = end //only the cursor position matters here
                updateInputMethodState()
                return true
            }
            return false
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            val deleteStart = max(0, cursorPosition - beforeLength)
            val deleteEnd = cursorPosition
            if (deleteStart < deleteEnd) {
                textBuffer.delete(deleteStart, deleteEnd)
                cursorPosition = deleteStart
                if (isMicrosoftSwiftKey || !inBatchEdit) {
                    repeat(beforeLength) { sender.sendBackspace() }
                } else {
                    pendingBackspaceCount += beforeLength
                }
            }
            updateInputMethodState()
            return true
        }

        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
            val deleteStart = max(0, cursorPosition - beforeLength)
            val deleteEnd = cursorPosition
            if (deleteStart < deleteEnd) {
                textBuffer.delete(deleteStart, deleteEnd)
                cursorPosition = deleteStart
                if (isMicrosoftSwiftKey) {
                    repeat(beforeLength) { sender.sendBackspace() }
                }
                updateInputMethodState()
            }
            return true
        }

        override fun beginBatchEdit(): Boolean {
            if (!isMicrosoftSwiftKey) {
                inBatchEdit = true
                //Reset the pending state
                pendingBackspaceCount = 0
                pendingTextToSend.clear()
            }
            return true
        }

        override fun endBatchEdit(): Boolean {
            if (!isMicrosoftSwiftKey) {
                inBatchEdit = false
                //Batch edit finished; send the accumulated operations
                flushPendingText()
            }
            return true
        }

        override fun clearMetaKeyStates(p0: Int): Boolean = true
        override fun closeConnection() {}
        override fun commitCompletion(p0: CompletionInfo?): Boolean = false
        override fun commitContent(p0: InputContentInfo, p1: Int, p2: Bundle?): Boolean = false
        override fun commitCorrection(p0: CorrectionInfo?): Boolean = false

        override fun performEditorAction(editorAction: Int): Boolean {
            //The user tapped the editor's action button (treated as pressing Enter)
            sender.sendEnter()
            onCloseInputMethod()
            return true
        }

        override fun performContextMenuAction(p0: Int): Boolean = false
        override fun performPrivateCommand(p0: String?, p1: Bundle?): Boolean = false
        override fun reportFullscreenMode(p0: Boolean): Boolean = true

        override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean {
            if (cursorUpdateMode and InputConnection.CURSOR_UPDATE_IMMEDIATE != 0) {
                updateCursorAnchorInfo()
                return true
            }
            return false
        }

        override fun getCursorCapsMode(p0: Int): Int = 0

        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText {
            return ExtractedText().apply {
                text = textBuffer
                startOffset = 0
                partialStartOffset = -1
                partialEndOffset = -1
                selectionStart = cursorPosition
                selectionEnd = cursorPosition
            }
        }

        override fun getHandler(): Handler? = null

        private fun updateCursorAnchorInfo() {
            imm.updateCursorAnchorInfo(
                view,
                CursorAnchorInfo.Builder().apply {
                    setSelectionRange(cursorPosition, cursorPosition)
                    //Set the composing text range
                    if (composingStart in 0..<composingEnd) {
                        val safeStart = composingStart.coerceIn(0, textBuffer.length)
                        val safeEnd = composingEnd.coerceIn(0, textBuffer.length)
                        if (safeStart < safeEnd) {
                            setComposingText(safeStart, textBuffer.substring(safeStart, safeEnd))
                        }
                    }
                    setInsertionMarkerLocation(
                        fakeCursorRect.left.toFloat(),
                        fakeCursorRect.top.toFloat(),
                        fakeCursorRect.right.toFloat(),
                        fakeCursorRect.bottom.toFloat(),
                        CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION
                    )
                    setMatrix(view.matrix)
                }.build()
            )
        }

        private fun updateInputMethodState() {
            imm.updateSelection(
                view,
                cursorPosition, cursorPosition,
                composingStart, composingEnd
            )
            updateCursorAnchorInfo()
        }
    }
}