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

package com.endiq.turtlelauncher.ui.screens.game.multiplayer

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.terracotta.Terracotta
import com.endiq.turtlelauncher.ui.AndroidStringText
import com.endiq.turtlelauncher.ui.androidText
import com.endiq.turtlelauncher.ui.components.SimpleEditDialog
import com.endiq.turtlelauncher.utils.string.isEmptyOrBlank
import net.burningtnt.terracotta.TerracottaAndroidAPI

/** Waiting: guest operation states */
sealed interface GuestWaitingOperation {
    data object None : GuestWaitingOperation
    /** Clicked: start entering the invite code */
    data object OnClick : GuestWaitingOperation
}

@Composable
fun GuestWaitingOperation(
    operation: GuestWaitingOperation,
    onChange: (GuestWaitingOperation) -> Unit,
    onPositive: (roomCode: String) -> Unit,
    onShowToast: (AndroidStringText) -> Unit
) {
    when (operation) {
        is GuestWaitingOperation.None -> {}
        is GuestWaitingOperation.OnClick -> {
            InviteCodeInputDialog(
                onPositive = onPositive,
                onDismiss = {
                    onChange(GuestWaitingOperation.None)
                },
                onShowToast = onShowToast
            )
        }
    }
}

/**
 * Guest invite-code entry dialog
 */
@Composable
private fun InviteCodeInputDialog(
    onPositive: (roomCode: String) -> Unit,
    onDismiss: () -> Unit,
    onShowToast: (AndroidStringText) -> Unit
) {
    var code by remember { mutableStateOf("") }

    /** When validation fails */
    var isError by remember { mutableStateOf(false) }
    val supportingText: AndroidStringText? = remember(code) {
        if (code.isEmpty()) {
            //Nothing filled in yet
            isError = false
            return@remember null
        }

        val type = Terracotta.parseRoomCode(code)
        when (type) {
            TerracottaAndroidAPI.RoomType.TERRACOTTA_LEGACY -> androidText(R.string.terracotta_status_waiting_guest_prompt_terracotta_legacy)
            TerracottaAndroidAPI.RoomType.PCL2CE -> androidText(R.string.terracotta_status_waiting_guest_prompt_pcl2ce)
            TerracottaAndroidAPI.RoomType.SCAFFOLDING -> androidText(R.string.terracotta_status_waiting_guest_prompt_scaffolding)
            else -> null
        }.also { text ->
            //Judge by whether the expected format was detected
            isError = text == null
        } ?: androidText(R.string.terracotta_status_waiting_guest_prompt_invalid)
    }

    SimpleEditDialog(
        title = stringResource(R.string.terracotta_status_waiting_guest_prompt_title),
        value = code,
        onValueChange = { value ->
            code = value
        },
        label = (@Composable { Text(text = "U/XXXX-XXXX-XXXX-XXXX") }).takeIf { code.isEmpty() },
        supportingText = supportingText?.let { text ->
            {
                AndroidStringText(text = text)
            }
        },
        singleLine = true,
        isError = isError,
        onConfirm = {
            if (isError || code.isEmptyOrBlank() || Terracotta.parseRoomCode(code) == null) {
                onShowToast(androidText(R.string.terracotta_status_waiting_guest_prompt_invalid))
            } else {
                onPositive(code)
                onDismiss()
            }
        },
        onDismissRequest = onDismiss
    )
}