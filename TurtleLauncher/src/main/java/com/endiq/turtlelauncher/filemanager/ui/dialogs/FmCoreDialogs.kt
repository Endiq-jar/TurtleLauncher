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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.filemanager.logic.entry.FmEntry
import com.endiq.turtlelauncher.filemanager.ui.components.ExposedDropdown
import com.endiq.turtlelauncher.filemanager.ui.components.FmAlertDialog
import com.endiq.turtlelauncher.filemanager.ui.components.FmDialogSurface
import com.endiq.turtlelauncher.filemanager.ui.components.FmEditDialog
import com.endiq.turtlelauncher.filemanager.ui.components.PropertyRow
import com.endiq.turtlelauncher.filemanager.ui.components.fmFilenameInvalid
import com.endiq.turtlelauncher.filemanager.ui.theme.fmErrorColor
import com.endiq.turtlelauncher.filemanager.viewmodel.DirScanUiState
import com.endiq.turtlelauncher.ui.components.OwnOutlinedTextField
import com.endiq.turtlelauncher.utils.file.formatFileSize

private enum class CreateType {
    File,
    Folder
}

@Composable
private fun CreateType.stringResource(): String {
    return when (this) {
        CreateType.File -> stringResource(R.string.fm_new_file)
        CreateType.Folder -> stringResource(R.string.resource_pack_manage_type_folder)
    }
}

/**
 * New file or folder dialog.
 * @param onConfirmFile creates a file with the entered name
 * @param onConfirmFolder creates a folder with the entered name
 */
@Composable
fun FmCreateDialog(
    onDismiss: () -> Unit,
    onConfirmFile: (String) -> Unit,
    onConfirmFolder: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(CreateType.File) }

    // Validates the filename in real time; errors are annotated beneath the input
    val filenameError = key(name) { fmFilenameInvalid(name) }
    val isError = name.isEmpty() || filenameError != null

    FmDialogSurface(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.control_editor_layers_create),
            style = MaterialTheme.typography.titleMedium
        )

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            OwnOutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.fm_new_name)) },
                isError = isError,
                supportingText = {
                    when {
                        name.isEmpty() -> Text(stringResource(R.string.generic_cannot_empty))
                        filenameError != null -> Text(filenameError)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            )

            ExposedDropdown(
                modifier = Modifier.fillMaxWidth(),
                selectedText = type.stringResource(),
                label = stringResource(R.string.fm_property_type),
                options = CreateType.entries,
                optionLabel = { type ->
                    type.stringResource()
                },
                onSelect = { type0 ->
                    type = type0
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            FilledTonalButton(
                onClick = onDismiss
            ) {
                Text(stringResource(R.string.generic_cancel))
            }

            Button(
                enabled = !isError,
                onClick = {
                    when (type) {
                        CreateType.File -> onConfirmFile(name)
                        CreateType.Folder -> onConfirmFolder(name)
                    }
                }
            ) {
                Text(stringResource(R.string.generic_confirm))
            }
        }
    }
}

/**
 * Jump-to-directory dialog.
 * @param currentPath current path pre-filled in the input
 * @param onConfirm jumps with the validated target path
 */
@Composable
fun FmJumpDialog(
    currentPath: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    // Place the cursor at the path end on init, for easy appending
    var input by remember {
        mutableStateOf(TextFieldValue(currentPath, TextRange(currentPath.length)))
    }
    var error by remember { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val emptyError = stringResource(R.string.fm_jump_invalid)

    FmEditDialog(
        title = stringResource(R.string.fm_jump_to),
        value = input,
        onValueChange = {
            input = it
            error = null
        },
        label = {
            Text(stringResource(R.string.fm_jump_hint))
        },
        isError = error != null,
        supportingText = error?.let {
            { Text(it) }
        },
        singleLine = true,
        onDismissRequest = onDismiss,
        onCancel = onDismiss,
        onConfirm = {
            if (input.text.isBlank()) {
                error = emptyError
            } else {
                keyboard?.hide()
                onConfirm(input.text.trim())
            }
        }
    )
}

/**
 * Delete confirmation dialog, with optional move-to-trash
 * @param count number of entries to delete
 * @param onConfirm confirm-delete callback; the argument says whether to move to trash
 */
@Composable
fun FmDeleteConfirmDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: (toTrash: Boolean) -> Unit
) {
    var toTrash by remember { mutableStateOf(true) }

    FmAlertDialog(
        title = stringResource(R.string.generic_delete),
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.fm_delete, count),
                    style = MaterialTheme.typography.bodyMedium
                )

                if (!toTrash) {
                    Text(
                        text = stringResource(R.string.fm_permanent_delete),
                        style = MaterialTheme.typography.bodyMedium,
                        color = fmErrorColor(),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = toTrash,
                        onCheckedChange = { toTrash = it }
                    )
                    Text(
                        text = stringResource(R.string.fm_move_to_trash),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmText = stringResource(R.string.generic_delete),
        onConfirm = { onConfirm(toTrash) },
        onDismiss = onDismiss
    )
}

/**
 * Rename dialog with live validation and inline errors.
 * @param entry the entry to rename
 * @param initialName initial name in the input
 * @param isFile whether it's a file; determines the default selection range
 * @param onConfirm confirms the rename with the validated new name
 * @param validate name validation function; null means valid, otherwise an error message
 */
@Composable
fun FmRenameDialog(
    entry: FmEntry,
    initialName: String,
    isFile: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    validate: (FmEntry, String) -> String?
) {
    val initialSelection: TextRange = if (isFile) {
        val dot = initialName.lastIndexOf('.')
        if (dot > 0) {
            TextRange(0, dot)
        } else {
            TextRange(0, initialName.length)
        }
    } else {
        TextRange(0, initialName.length)
    }
    var tfv by remember { mutableStateOf(TextFieldValue(initialName, initialSelection)) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }

    // Validate on every input change; disable confirm while errors exist
    val err = key(tfv.text) { validate(entry, tfv.text) }

    FmEditDialog(
        title = stringResource(R.string.generic_rename),
        value = tfv,
        onValueChange = { tfv = it },
        isError = err != null,
        supportingText = err?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        focusRequester = focus,
        confirmEnabled = err == null,
        onDismissRequest = onDismiss,
        onCancel = onDismiss,
        onConfirm = {
            // Confirm is disabled on errors, so submit directly here
            keyboard?.hide()
            onConfirm(tfv.text)
        }
    )
}

/**
 * File properties dialog.
 * @param name entry name
 * @param path full path of the entry
 * @param isDirectory whether it's a directory
 * @param sizeText size text
 * @param modifiedText modification time text
 * @param dirScan directory scan state, used to show async directory stats
 * @param onDismiss closes the dialog
 */
@Composable
fun FmPropertyDialog(
    name: String,
    path: String,
    isDirectory: Boolean,
    sizeText: String,
    modifiedText: String,
    dirScan: DirScanUiState? = null,
    onDismiss: () -> Unit
) {
    val typeText = if (isDirectory) {
        stringResource(R.string.resource_pack_manage_type_folder)
    } else {
        stringResource(R.string.fm_new_file)
    }
    val dirStats = dirScan?.stats

    FmAlertDialog(
        title = stringResource(R.string.fm_property),
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                PropertyRow(R.string.fm_new_name, name)
                PropertyRow(R.string.fm_property_path, path)
                PropertyRow(R.string.fm_property_type, typeText)
                PropertyRow(R.string.fm_property_modified, modifiedText)

                if (isDirectory) {
                    val scanning = dirScan?.running == true
                    PropertyRow(
                        R.string.fm_property_size,
                        if (dirStats != null) {
                            formatFileSize(dirStats.totalSize)
                        } else if (scanning) {
                            stringResource(R.string.fm_calculating)
                        } else "-"
                    )
                    PropertyRow(
                        R.string.fm_property_files,
                        dirStats?.fileCount?.toString() ?: if (scanning) stringResource(R.string.fm_calculating) else "-"
                    )
                    PropertyRow(
                        R.string.fm_property_folders,
                        dirStats?.dirCount?.toString() ?: if (scanning) stringResource(R.string.fm_calculating) else "-"
                    )
                    if (scanning) {
                        LinearProgressIndicator(modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp))
                    }
                } else {
                    PropertyRow(R.string.fm_property_size, sizeText)
                }
            }
        },
        confirmText = stringResource(R.string.generic_close),
        onDismiss = onDismiss
    )
}
