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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.filemanager.ui.components.FmDialogSurface
import com.endiq.turtlelauncher.filemanager.ui.theme.fmSecondaryTextColor
import com.endiq.turtlelauncher.ui.components.ButtonPosition
import com.endiq.turtlelauncher.ui.components.PositionButton
import com.endiq.turtlelauncher.ui.components.PositionFilledTonalButton

/**
 * Bulk-operation dialog for multi-select mode
 * @param selectedCount number of selected entries
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FmBulkActionsDialog(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDelete: () -> Unit,
    onCompress: () -> Unit
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Text(
                text = stringResource(R.string.fm_batch_actions, selectedCount),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            HorizontalDivider()
            FmBulkActionItem(
                text = stringResource(R.string.generic_copy),
                icon = painterResource(R.drawable.ic_content_copy_filled),
                onClick = {
                    onDismiss()
                    onCopy()
                }
            )
            FmBulkActionItem(
                text = stringResource(R.string.fm_cut),
                icon = painterResource(R.drawable.ic_drive_file_move_filled),
                onClick = {
                    onDismiss()
                    onCut()
                }
            )
            FmBulkActionItem(
                text = stringResource(R.string.generic_delete),
                icon = painterResource(R.drawable.ic_delete_filled),
                colors = ListItemDefaults.colors(
                    contentColor = MaterialTheme.colorScheme.error,
                    leadingContentColor = MaterialTheme.colorScheme.error
                ),
                onClick = {
                    onDismiss()
                    onDelete()
                }
            )
            FmBulkActionItem(
                text = stringResource(R.string.fm_archive),
                icon = painterResource(R.drawable.ic_archive_filled),
                onClick = {
                    onDismiss()
                    onCompress()
                }
            )
            //Spacer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(MaterialTheme.colorScheme.surface)
            )
        }
    }
}

@Composable
private fun FmBulkActionItem(
    text: String,
    icon: Painter,
    colors: ListItemColors = ListItemDefaults.colors(),
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = colors,
        headlineContent = {
            Text(text = text)
        },
        leadingContent = {
            Icon(
                painter = icon,
                contentDescription = null
            )
        }
    )
}

/**
 * Conflict-resolution dialog offering skip, overwrite and keep-both
 * @param conflictName name of the conflicting entry
 * @param totalConflicts total conflict count
 * @param onSkip skips this conflicting entry
 * @param onOverwrite overwrites the target
 * @param onKeepBoth keeps both
 */
@Composable
fun FmConflictDialog(
    conflictName: String,
    totalConflicts: Int,
    onSkip: () -> Unit,
    onOverwrite: () -> Unit,
    onKeepBoth: () -> Unit
) {
    FmDialogSurface(onDismissRequest = onSkip) {
        Text(
            text = stringResource(R.string.fm_conflict_title),
            style = MaterialTheme.typography.titleMedium
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.fm_conflict_text, conflictName))

            if (totalConflicts > 1) {
                Text(
                    text = stringResource(R.string.fm_conflict_count, totalConflicts),
                    style = MaterialTheme.typography.bodySmall,
                    color = fmSecondaryTextColor(),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            PositionButton(
                modifier = Modifier.fillMaxWidth(),
                position = ButtonPosition.Top,
                onClick = onOverwrite
            ) {
                Text(stringResource(R.string.fm_conflict_overwrite))
            }

            PositionButton(
                modifier = Modifier.fillMaxWidth(),
                position = ButtonPosition.Middle,
                onClick = onKeepBoth
            ) {
                Text(stringResource(R.string.fm_conflict_keep_both))
            }

            PositionFilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                position = ButtonPosition.Bottom,
                onClick = onSkip
            ) {
                Text(stringResource(R.string.settings_gamepad_remapping_skip))
            }
        }
    }
}
