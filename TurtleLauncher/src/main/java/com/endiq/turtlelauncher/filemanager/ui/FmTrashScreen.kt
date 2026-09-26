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

package com.endiq.turtlelauncher.filemanager.ui

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.filemanager.config.FmConfig
import com.endiq.turtlelauncher.filemanager.logic.ops.ConflictResolution
import com.endiq.turtlelauncher.filemanager.logic.trash.TrashItem
import com.endiq.turtlelauncher.filemanager.ui.components.AppBarSubTexts
import com.endiq.turtlelauncher.filemanager.ui.components.FmAlertDialog
import com.endiq.turtlelauncher.filemanager.ui.components.FmCardPosition
import com.endiq.turtlelauncher.filemanager.ui.components.FmIcons
import com.endiq.turtlelauncher.filemanager.ui.components.fmSwipeTrigger
import com.endiq.turtlelauncher.filemanager.ui.components.rememberFmCardShape
import com.endiq.turtlelauncher.filemanager.ui.components.rememberFmSwipeTriggerState
import com.endiq.turtlelauncher.filemanager.ui.dialogs.FmConflictDialog
import com.endiq.turtlelauncher.filemanager.ui.dialogs.FmTrashPropertyDialog
import com.endiq.turtlelauncher.filemanager.ui.theme.FmAnimations
import com.endiq.turtlelauncher.filemanager.ui.theme.fmBackgroundColor
import com.endiq.turtlelauncher.filemanager.ui.theme.fmCardColor
import com.endiq.turtlelauncher.filemanager.ui.theme.fmOnBackgroundColor
import com.endiq.turtlelauncher.filemanager.ui.theme.fmOnCardColor
import com.endiq.turtlelauncher.filemanager.ui.theme.fmSecondaryTextColor
import com.endiq.turtlelauncher.filemanager.ui.theme.fmSelectionColor
import com.endiq.turtlelauncher.filemanager.ui.theme.fmTopbarColors
import com.endiq.turtlelauncher.filemanager.viewmodel.DialogIntent
import com.endiq.turtlelauncher.filemanager.viewmodel.FileManagerUiState
import com.endiq.turtlelauncher.filemanager.viewmodel.FileManagerViewModel
import com.endiq.turtlelauncher.filemanager.viewmodel.TrashItemView
import com.endiq.turtlelauncher.filemanager.viewmodel.TrashListView
import com.endiq.turtlelauncher.filemanager.viewmodel.TrashSortConfig
import com.endiq.turtlelauncher.filemanager.viewmodel.TrashViewState
import com.endiq.turtlelauncher.utils.file.formatFileSize
import com.endiq.turtlelauncher.utils.formatDate
import kotlin.math.floor

private sealed interface FmTrashOperation {
    data object None : FmTrashOperation
    data class ItemDetail(val item: TrashItem) : FmTrashOperation
    data class RestoreConfirm(val item: TrashItem) : FmTrashOperation
    data class PurgeConfirm(val item: TrashItem) : FmTrashOperation
    data object RestoreAllConfirm : FmTrashOperation
    data object PurgeAllConfirm : FmTrashOperation
    data object ClearConfirm : FmTrashOperation
    data object RangeSelectHelp : FmTrashOperation
}

/**
 * Trash page: lists trash entries and offers restore, clear and similar actions
 * @param vm the file manager view model
 * @param snackHost the global Snackbar host
 * @param onBack back-to-main-page callback
 * @param onExit exit-file-manager callback
 * @param onToggleOrientation orientation-toggle callback
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FmTrashScreen(
    vm: FileManagerViewModel,
    snackHost: SnackbarHostState,
    /** Back-gesture opacity */
    contentAlpha: Animatable<Float, AnimationVector1D>,
    onBack: () -> Unit,
    onExit: () -> Unit,
    onToggleOrientation: () -> Unit
) {
    val uiState by vm.state.collectAsStateWithLifecycle()
    var trashOperation by remember { mutableStateOf<FmTrashOperation>(FmTrashOperation.None) }
    val updateTrashOperation: (FmTrashOperation) -> Unit = { trashOperation = it }
    val trash = uiState.trashView as? TrashViewState.Opened
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Load the trash list when the page loads
    LaunchedEffect(Unit) { vm.loadTrashList() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                colors = fmTopbarColors(),
                title = {
                    Column {
                        Text(stringResource(R.string.fm_trash_title))
                        val list = (trash?.trashListView ?: TrashListView())
                        AppBarSubTexts(
                            texts = listOf(
                                stringResource(R.string.fm_trash_items, list.total),
                                stringResource(R.string.fm_trash_size, formatFileSize(list.totalSize))
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.fm_nav_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onToggleOrientation) {
                        Icon(
                            painter = if (landscape) {
                                painterResource(R.drawable.ic_fullscreen_exit)
                            } else {
                                painterResource(R.drawable.ic_mobile_rotate_filled)
                            },
                            contentDescription = null
                        )
                    }
                    FmTrashMoreMenu(
                        sortConfig = uiState.trashSortConfig,
                        onRefresh = { vm.refreshTrashList() },
                        onSortChange = { vm.setTrashSortConfig(it) },
                        onExit = onExit
                    )
                }
            )
        },
        bottomBar = {
            if (trash != null && !landscape) {
                FmTrashBottomBar(
                    vm = vm,
                    list = trash.trashListView,
                    updateTrashOperation = updateTrashOperation
                )
            }
        },
        snackbarHost = { SnackbarHost(snackHost) },
        containerColor = fmBackgroundColor(),
        contentColor = fmOnBackgroundColor(),
    ) { inner ->
        if (landscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
            ) {
                if (trash != null) {
                    FmTrashSideBar(
                        vm = vm,
                        list = trash.trashListView,
                        updateTrashOperation = updateTrashOperation
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        TrashContent(
                            vm = vm,
                            trash = trash,
                            updateTrashOperation = updateTrashOperation,
                            contentAlpha = contentAlpha
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
            ) {
                if (trash == null) {
                    TrashStatusBox(stringResource(R.string.generic_loading))
                } else {
                    TrashContent(
                        vm = vm,
                        trash = trash,
                        updateTrashOperation = updateTrashOperation,
                        contentAlpha = contentAlpha
                    )
                }
            }
        }
    }

    TrashDialogs(
        vm = vm,
        uiState = uiState,
        trashOperation = trashOperation,
        updateTrashOperation = updateTrashOperation
    )
}

@Composable
private fun TrashContent(
    vm: FileManagerViewModel,
    trash: TrashViewState.Opened,
    updateTrashOperation: (FmTrashOperation) -> Unit,
    contentAlpha: Animatable<Float, AnimationVector1D>
) {
    val list = trash.trashListView

    val contentState = if (list.loading) null else list.items

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = contentAlpha.value }
    ) {
        AnimatedContent(
            targetState = contentState,
            transitionSpec = {
                fadeIn(tween(FmAnimations.FADE_IN_MS)) togetherWith
                    fadeOut(tween(FmAnimations.FADE_OUT_MS))
            },
            label = "fm-trash-content"
        ) { items ->
            when {
                items == null -> {
                    TrashStatusBox(stringResource(R.string.generic_loading))
                }
                items.isEmpty() -> {
                    TrashStatusBox(stringResource(R.string.fm_trash_empty))
                }
                else -> {
                    val rawItemByUuid = remember(trash.rawItems) {
                        trash.rawItems.associateBy { it.uuid }
                    }
                    val itemContent: @Composable (TrashItemView, FmCardPosition) -> Unit = { item, position ->
                        FmTrashItem(
                            item = item,
                            position = position,
                            multiSelect = list.multiSelect,
                            selected = item.uuid in list.selection,
                            onClick = {
                                if (list.multiSelect) vm.toggleTrashSelection(item.uuid)
                            },
                            onSwipeTrigger = {
                                rawItemByUuid[item.uuid]?.let { vm.trashRangeSelect(it) }
                            },
                            onDetail = {
                                rawItemByUuid[item.uuid]?.let { updateTrashOperation(FmTrashOperation.ItemDetail(it)) }
                            },
                            onMultiRestoreWithConfirm = {
                                updateTrashOperation(FmTrashOperation.RestoreAllConfirm)
                            },
                            onMultiPurgeWithConfirm = {
                                updateTrashOperation(FmTrashOperation.PurgeAllConfirm)
                            },
                            onRestoreWithConfirm = {
                                rawItemByUuid[item.uuid]?.let { updateTrashOperation(FmTrashOperation.RestoreConfirm(it)) }
                            },
                            onPurgeWithConfirm = {
                                rawItemByUuid[item.uuid]?.let { updateTrashOperation(FmTrashOperation.PurgeConfirm(it)) }
                            }
                        )
                    }

                    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                    if (isLandscape) {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val gap = 2.dp
                            val minColumnSize = 280.dp
                            // Same column formula as GridCells.Adaptive, keeping entry positions aligned with the actual grid
                            val columns = floor(
                                (maxWidth - 24.dp + gap).value / (minColumnSize + gap).value
                            ).toInt().coerceAtLeast(1)
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(gap),
                                verticalArrangement = Arrangement.spacedBy(gap)
                            ) {
                                itemsIndexed(items, key = { _, item -> item.uuid }) { index, item ->
                                    itemContent(item, FmCardPosition.of(index, items.size, columns))
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            itemsIndexed(items, key = { _, item -> item.uuid }) { index, item ->
                                itemContent(item, FmCardPosition.of(index, items.size))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashStatusBox(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = fmSecondaryTextColor()
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FmTrashItem(
    item: TrashItemView,
    multiSelect: Boolean,
    selected: Boolean,
    position: FmCardPosition,
    onClick: () -> Unit,
    onSwipeTrigger: () -> Unit,
    onDetail: () -> Unit,
    onRestoreWithConfirm: () -> Unit,
    onPurgeWithConfirm: () -> Unit,
    onMultiRestoreWithConfirm: () -> Unit,
    onMultiPurgeWithConfirm: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val shape = rememberFmCardShape(position)
    val swipeState = rememberFmSwipeTriggerState()
    val selectionColor = fmSelectionColor()
    val unselectionColor = selectionColor.copy(alpha = 0f)
    val bg by animateColorAsState(
        if (selected) selectionColor else unselectionColor
    )

    Surface(
        modifier = Modifier
            .zIndex(if (swipeState.isDragging) 1f else 0f)
            .fillMaxWidth()
            .fmSwipeTrigger(
                state = swipeState,
                triggerable = true,
                onTriggered = onSwipeTrigger
            ),
        shape = shape,
        color = fmCardColor(),
        shadowElevation = 6.dp * swipeState.dragFraction
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bg)
                .combinedClickable(
                    onClick = {
                        if (multiSelect) {
                            onClick()
                        } else {
                            // Outside multi-select mode, tapping pops the entry menu
                            menuExpanded = true
                        }
                    },
                    onLongClick = { menuExpanded = true }
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FmIcons.IconFor(
                name = item.name,
                isDirectory = item.isFolder,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!multiSelect) {
                    TrashSingleMenu(
                        expanded = menuExpanded,
                        onDismiss = { menuExpanded = false },
                        onDetail = onDetail,
                        onRestoreWithConfirm = onRestoreWithConfirm,
                        onPurgeWithConfirm = onPurgeWithConfirm
                    )
                }

                Column {
                    Text(
                        text = if (item.corrupted) {
                            "${item.name} (${stringResource(R.string.fm_trash_corrupted)})"
                        } else {
                            item.name
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.fm_trash_deleted_at, formatDate(item.deletedAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = fmSecondaryTextColor()
                    )
                }

                if (multiSelect) {
                    TrashMultiMenu(
                        expanded = menuExpanded,
                        onDismiss = { menuExpanded = false },
                        onRestoreWithConfirm = onMultiRestoreWithConfirm,
                        onPurgeWithConfirm = onMultiPurgeWithConfirm
                    )
                }
            }
        }
    }
}

@Composable
private fun TrashSingleMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onDetail: () -> Unit,
    onRestoreWithConfirm: () -> Unit,
    onPurgeWithConfirm: () -> Unit
) {
    Box {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.fm_property)) },
                onClick = {
                    onDismiss()
                    onDetail()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.fm_trash_restore)) },
                onClick = {
                    onDismiss()
                    onRestoreWithConfirm()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.fm_trash_purge)) },
                onClick = {
                    onDismiss()
                    onPurgeWithConfirm()
                }
            )
        }
    }
}

@Composable
private fun TrashMultiMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRestoreWithConfirm: () -> Unit,
    onPurgeWithConfirm: () -> Unit
) {
    Box {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.fm_trash_restore)) },
                onClick = {
                    onDismiss()
                    onRestoreWithConfirm()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.fm_trash_purge)) },
                onClick = {
                    onDismiss()
                    onPurgeWithConfirm()
                }
            )
        }
    }
}

@Composable
private fun FmTrashMoreMenu(
    sortConfig: TrashSortConfig,
    onRefresh: () -> Unit,
    onSortChange: (TrashSortConfig) -> Unit,
    onExit: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }

    IconButton(onClick = { menuExpanded = true }) {
        Icon(
            painter = painterResource(R.drawable.ic_more_vert),
            contentDescription = stringResource(R.string.generic_more)
        )
    }

    DropdownMenu(
        expanded = menuExpanded,
        onDismissRequest = {
            menuExpanded = false
            sortExpanded = false
        }
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.generic_refresh)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh),
                    contentDescription = null
                )
            },
            onClick = {
                menuExpanded = false
                onRefresh()
            }
        )

        DropdownMenuItem(
            text = { Text(stringResource(R.string.sort_by)) },
            trailingIcon = {
                val rotation by animateFloatAsState(
                    if (sortExpanded) 180f else 0f
                )
                Icon(
                    modifier = Modifier.rotate(rotation),
                    painter = painterResource(R.drawable.ic_arrow_drop_down_rounded),
                    contentDescription = null
                )
            },
            onClick = { sortExpanded = true }
        )

        DropdownMenu(
            expanded = sortExpanded,
            onDismissRequest = { sortExpanded = false }
        ) {
            FmTrashSortMenuItem(
                labelRes = R.string.fm_sort_name,
                candidate = sortConfig.copy(field = FmConfig.TrashSortField.NAME),
                current = sortConfig,
                onSortChange = onSortChange
            )
            FmTrashSortMenuItem(
                labelRes = R.string.fm_trash_sort_deleted,
                candidate = sortConfig.copy(field = FmConfig.TrashSortField.DELETED),
                current = sortConfig,
                onSortChange = onSortChange
            )
            TrashSortToggle(
                text = stringResource(R.string.fm_sort_ascending),
                checked = sortConfig.ascending
            ) {
                onSortChange(sortConfig.copy(ascending = !sortConfig.ascending))
            }
            TrashSortToggle(
                text = stringResource(R.string.fm_sort_folder_first),
                checked = sortConfig.folderFirst
            ) {
                onSortChange(sortConfig.copy(folderFirst = !sortConfig.folderFirst))
            }
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.fm_exit)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_exit_to_app),
                    contentDescription = null
                )
            },
            onClick = {
                menuExpanded = false
                onExit()
            }
        )
    }
}

@Composable
private fun FmTrashSortMenuItem(
    labelRes: Int,
    candidate: TrashSortConfig,
    current: TrashSortConfig,
    onSortChange: (TrashSortConfig) -> Unit
) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        trailingIcon = {
            if (candidate.field == current.field) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null
                )
            }
        },
        onClick = { onSortChange(candidate) }
    )
}

@Composable
private fun TrashSortToggle(
    text: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(text) },
        trailingIcon = {
            Checkbox(
                checked = checked,
                onCheckedChange = { onToggle() }
            )
        },
        onClick = onToggle
    )
}

private data class TrashBarAction(
    @field:DrawableRes
    val iconRes: Int,
    @field:StringRes
    val labelRes: Int,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

private fun trashBarActions(
    list: TrashListView,
    vm: FileManagerViewModel,
    updateTrashOperation: (FmTrashOperation) -> Unit
): List<TrashBarAction> = if (list.multiSelect) {
    listOf(
        TrashBarAction(R.drawable.ic_select_all, R.string.fm_select_all) { vm.selectAllTrash() },
        TrashBarAction(R.drawable.ic_deselect, R.string.fm_clear_selection) { vm.clearTrashSelection() },
        TrashBarAction(R.drawable.ic_swipe, R.string.fm_range_select_help) {
            updateTrashOperation(FmTrashOperation.RangeSelectHelp)
        }
    )
} else {
    // Disabled while the trash is empty
    val enabled = list.items.isNotEmpty()
    listOf(
        TrashBarAction(
            iconRes = R.drawable.ic_restore_from_trash_filled,
            labelRes = R.string.fm_trash_restore,
            enabled = enabled
        ) {
            updateTrashOperation(FmTrashOperation.RestoreAllConfirm)
        },
        TrashBarAction(
            iconRes = R.drawable.ic_delete_forever_filled,
            labelRes = R.string.fm_trash_clear,
            enabled = enabled
        ) {
            updateTrashOperation(FmTrashOperation.ClearConfirm)
        }
    )
}

@Composable
private fun FmTrashSideBar(
    vm: FileManagerViewModel,
    list: TrashListView,
    updateTrashOperation: (FmTrashOperation) -> Unit
) {
    val listScroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxHeight()
            .padding(vertical = 8.dp)
            .verticalScroll(listScroll),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        trashBarActions(list, vm, updateTrashOperation).forEach { action ->
            NavigationRailItem(
                selected = false,
                enabled = action.enabled,
                onClick = action.onClick,
                icon = {
                    Icon(
                        painter = painterResource(action.iconRes),
                        contentDescription = null
                    )
                },
                label = {
                    Text(
                        text = stringResource(action.labelRes),
                        maxLines = 1
                    )
                }
            )
        }
    }
}

@Composable
private fun FmTrashBottomBar(
    vm: FileManagerViewModel,
    list: TrashListView,
    updateTrashOperation: (FmTrashOperation) -> Unit
) {
    Surface(
        color = fmCardColor(),
        contentColor = fmOnCardColor(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            trashBarActions(list, vm, updateTrashOperation).forEach { action ->
                IconButton(onClick = action.onClick, enabled = action.enabled) {
                    Icon(
                        painter = painterResource(action.iconRes),
                        contentDescription = stringResource(action.labelRes)
                    )
                }
            }
        }
    }
}

@Composable
private fun TrashDialogs(
    vm: FileManagerViewModel,
    uiState: FileManagerUiState,
    trashOperation: FmTrashOperation,
    updateTrashOperation: (FmTrashOperation) -> Unit
) {
    when (val intent = uiState.dialogIntent) {
        is DialogIntent.TrashRestoreConflict -> {
            val cur = intent.conflictItems.getOrNull(intent.pendingIndex)
            if (cur == null) {
                vm.dismissDialog()
                vm.trashRestore(intent.trashItems, intent.resolutions)
            } else {
                FmConflictDialog(
                    conflictName = cur.first.meta.name,
                    totalConflicts = intent.conflictItems.size,
                    onSkip = { vm.resolveTrashRestoreConflict(ConflictResolution.SKIP) },
                    onOverwrite = { vm.resolveTrashRestoreConflict(ConflictResolution.OVERWRITE) },
                    onKeepBoth = { vm.resolveTrashRestoreConflict(ConflictResolution.KEEP_BOTH) }
                )
            }
        }

        else -> {}
    }

    when (trashOperation) {
        FmTrashOperation.None -> {}

        is FmTrashOperation.ItemDetail -> {
            val item = trashOperation.item
            if (item.isFolder) {
                LaunchedEffect(item.uuid) {
                    vm.startDirectoryScan(item.contentDir)
                }
            }
            DisposableEffect(item.uuid) {
                onDispose { vm.stopDirectoryScan() }
            }

            val dirScan by vm.dirScan.collectAsStateWithLifecycle()
            FmTrashPropertyDialog(
                name = item.meta.name,
                sourcePath = item.meta.originalPath,
                isDirectory = item.isFolder,
                sizeText = formatFileSize(item.size),
                deletedText = formatDate(item.deletedAt),
                dirScan = if (item.isFolder) dirScan else null,
                onDismiss = { updateTrashOperation(FmTrashOperation.None) }
            )
        }

        is FmTrashOperation.RestoreConfirm -> FmAlertDialog(
            title = stringResource(R.string.fm_trash_restore_confirm_title),
            text = stringResource(R.string.fm_trash_restore_confirm_text, trashOperation.item.meta.name),
            onConfirm = {
                updateTrashOperation(FmTrashOperation.None)
                vm.restoreTrashItem(trashOperation.item)
            },
            onDismiss = { updateTrashOperation(FmTrashOperation.None) }
        )

        is FmTrashOperation.PurgeConfirm -> FmAlertDialog(
            title = stringResource(R.string.fm_trash_purge_confirm_title),
            text = stringResource(R.string.fm_trash_purge_confirm_text, trashOperation.item.meta.name),
            confirmText = stringResource(R.string.generic_delete),
            onConfirm = {
                updateTrashOperation(FmTrashOperation.None)
                vm.purgeTrashItem(trashOperation.item)
            },
            onDismiss = { updateTrashOperation(FmTrashOperation.None) }
        )

        FmTrashOperation.RestoreAllConfirm -> {
            val total = (uiState.trashView as? TrashViewState.Opened)?.trashListView?.total ?: 0
            val selected = vm.selectedTrashItems()
            FmAlertDialog(
                title = stringResource(R.string.fm_trash_restore_all_title),
                text = if (selected.isNotEmpty()) {
                    stringResource(R.string.fm_trash_restore_selected_text, selected.size)
                } else {
                    stringResource(R.string.fm_trash_restore_all_text, total)
                },
                onConfirm = {
                    updateTrashOperation(FmTrashOperation.None)
                    if (selected.isNotEmpty()) {
                        vm.beginTrashRestore(selected)
                    } else {
                        vm.trashRestoreAll()
                    }
                },
                onDismiss = { updateTrashOperation(FmTrashOperation.None) }
            )
        }

        FmTrashOperation.PurgeAllConfirm -> {
            val selected = vm.selectedTrashItems()
            FmAlertDialog(
                title = stringResource(R.string.fm_trash_purge_confirm_title),
                text = stringResource(R.string.fm_trash_purge_selected_text, selected.size),
                confirmText = stringResource(R.string.generic_delete),
                onConfirm = {
                    updateTrashOperation(FmTrashOperation.None)
                    vm.trashPurge(selected)
                },
                onDismiss = { updateTrashOperation(FmTrashOperation.None) }
            )
        }

        FmTrashOperation.ClearConfirm -> FmAlertDialog(
            title = stringResource(R.string.fm_trash_clear),
            text = stringResource(
                R.string.fm_trash_confirm_purge,
                (uiState.trashView as? TrashViewState.Opened)?.trashListView?.total ?: 0
            ),
            confirmText = stringResource(R.string.generic_delete),
            onConfirm = {
                updateTrashOperation(FmTrashOperation.None)
                vm.trashClear()
            },
            onDismiss = { updateTrashOperation(FmTrashOperation.None) }
        )

        FmTrashOperation.RangeSelectHelp -> FmAlertDialog(
            title = stringResource(R.string.fm_range_select_help),
            text = stringResource(R.string.fm_range_select_help_text),
            onDismiss = { updateTrashOperation(FmTrashOperation.None) }
        )
    }
}
