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

package com.endiq.turtlelauncher.filemanager.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.filemanager.ui.components.FmAlertDialog
import com.endiq.turtlelauncher.filemanager.ui.components.FmEditDialog
import com.endiq.turtlelauncher.filemanager.ui.theme.fmErrorColor

/**
 * Extraction settings dialog.
 * @param archiveName name of the archive to extract
 * @param onDismiss cancels and closes the dialog
 * @param onConfirm confirm-extraction callback; the argument says whether to extract into a standalone folder
 */
@Composable
fun FmExtractDialog(
    archiveName: String,
    onDismiss: () -> Unit,
    onConfirm: (independentFolder: Boolean) -> Unit
) {
    var independent by remember { mutableStateOf(true) }

    FmAlertDialog(
        title = stringResource(R.string.fm_extract),
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.fm_extract_body, archiveName),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Checkbox(
                        checked = independent,
                        onCheckedChange = { independent = it }
                    )
                    Text(
                        text = stringResource(R.string.fm_extract_independent_folder),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmText = stringResource(R.string.fm_extract),
        onConfirm = { onConfirm(independent) },
        onDismiss = onDismiss
    )
}

/**
 * Archive password input dialog.
 * @param errorText text shown when the password is wrong; may be null
 * @param onDismiss cancels the extraction and closes the dialog
 * @param onConfirm continues extraction with the entered password
 */
@Composable
fun FmExtractPasswordDialog(
    errorText: String?,
    onDismiss: () -> Unit,
    onConfirm: (password: String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    FmEditDialog(
        title = stringResource(R.string.fm_extract_password_title),
        value = password,
        onValueChange = { password = it },
        label = { Text(stringResource(R.string.fm_compress_password)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = PasswordVisualTransformation(),
        extraBody = {
            if (errorText != null) {
                Text(
                    text = errorText,
                    color = fmErrorColor(),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        onDismissRequest = onDismiss,
        onCancel = onDismiss,
        onConfirm = {
            keyboard?.hide()
            onConfirm(password)
        }
    )
}
