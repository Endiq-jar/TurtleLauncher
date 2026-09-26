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

import androidx.activity.ExperimentalActivityApi
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.filemanager.ui.components.FmAlertDialog
import com.endiq.turtlelauncher.filemanager.ui.dialogs.FmProgressDialog
import com.endiq.turtlelauncher.filemanager.ui.theme.FmAnimations
import com.endiq.turtlelauncher.filemanager.ui.theme.fmBackgroundColor
import com.endiq.turtlelauncher.filemanager.ui.theme.fmOnBackgroundColor
import com.endiq.turtlelauncher.filemanager.viewmodel.FileManagerViewModel
import com.endiq.turtlelauncher.filemanager.viewmodel.FmInitState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.nio.file.Paths

@Serializable
sealed interface FmNavKey : NavKey {
    /**
     * File manager main page
     */
    @Serializable
    data object FileManager : FmNavKey

    /**
     * Trash page
     */
    @Serializable
    data object Trash : FmNavKey

    /**
     * Text editor page
     * @param path absolute path of the file to edit
     */
    @Serializable
    data class Editor(val path: String) : FmNavKey
}

/**
 * File manager root screen
 * @param initResult the initialization result
 * @param vm the file manager view model
 * @param onExit exit-file-manager callback
 * @param onToggleOrientation orientation-toggle callback
 */
@OptIn(ExperimentalActivityApi::class)
@Composable
fun FileManagerRootScreen(
    initResult: FileManagerInitResult,
    vm: FileManagerViewModel?,
    onExit: () -> Unit = {},
    onToggleOrientation: () -> Unit = {}
) {
    val snackHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uiState = vm?.state?.collectAsStateWithLifecycle()?.value

    LaunchedEffect(uiState?.snackbar) {
        val s = uiState?.snackbar ?: return@LaunchedEffect
        scope.launch {
            snackHost.showSnackbar(
                message = s.text,
                withDismissAction = true,
                duration = if (s.long) SnackbarDuration.Long else SnackbarDuration.Short
            )
        }
        vm.consumeSnackbar()
    }

    // Error event collection
    var errorMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(vm) {
        vm?.initialize()
        vm?.errorEvents?.collect { errorMessage = it }
    }

    when {
        initResult is FileManagerInitResult.Pending -> {
            InitBox(stringResource(R.string.fm_initializing))
        }
        initResult is FileManagerInitResult.Failed -> {
            InitBox(stringResource(R.string.fm_init_failed, initResult.message), false)
        }
        vm == null || uiState == null -> {
            InitBox(stringResource(R.string.generic_loading))
        }
        else -> {
            val initState by vm.initState.collectAsStateWithLifecycle()
            when (val state = initState) {
                is FmInitState.Pending -> {
                    InitBox(stringResource(R.string.generic_loading))
                }
                is FmInitState.Failed -> {
                    InitBox(stringResource(R.string.fm_init_failed, state.message), false)
                }
                FmInitState.Ready -> {
                    val backStack = remember(vm) { NavBackStack<FmNavKey>(FmNavKey.FileManager) }
                    val saveableStateHolder = rememberSaveableStateHolder()

                    val isTrashOpen = backStack.size > 1
                    val atRoot = uiState.rawList?.let { it.currentDir == it.rootDir } ?: false
                    val backHandledInApp = isTrashOpen || uiState.multiSelect || !atRoot

                    val gestureAlpha = remember { Animatable(1f) }
                    var gestureActive by remember { mutableStateOf(false) }

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = fmBackgroundColor(),
                        contentColor = fmOnBackgroundColor(),
                    ) {
                        if (backStack.isNotEmpty()) {
                            NavDisplay(
                                modifier = Modifier.fillMaxSize(),
                                backStack = backStack,
                                entryProvider = entryProvider {
                                    entry<FmNavKey.FileManager> {
                                        saveableStateHolder.SaveableStateProvider("fm_main") {
                                            FmMainPage(
                                                vm = vm,
                                                snackHost = snackHost,
                                                contentAlpha = gestureAlpha,
                                                onOpenTrash = { backStack.openTrash() },
                                                onOpenEditor = { backStack.openEditor(it) },
                                                onExit = onExit,
                                                onToggleOrientation = onToggleOrientation
                                            )
                                        }
                                    }
                                    entry<FmNavKey.Trash> {
                                        FmTrashScreen(
                                            vm = vm,
                                            snackHost = snackHost,
                                            contentAlpha = gestureAlpha,
                                            onBack = {
                                                vm.closeTrash()
                                                backStack.closeTrash()
                                            },
                                            onExit = onExit,
                                            onToggleOrientation = onToggleOrientation
                                        )
                                    }
                                    entry<FmNavKey.Editor> { editorKey ->
                                        FmEditorScreen(
                                            path = Paths.get(editorKey.path),
                                            vm = vm,
                                            snackHost = snackHost,
                                            contentAlpha = gestureAlpha,
                                            onBack = { backStack.closeEditor() },
                                            onExit = onExit,
                                            onToggleOrientation = onToggleOrientation
                                        )
                                    }
                                }
                            )
                        }

                        if (gestureActive) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                awaitPointerEvent().changes.forEach { it.consume() }
                                            }
                                        }
                                    }
                            )
                        }
                    }

                    PredictiveBackHandler(enabled = backHandledInApp) { progressFlow ->
                        gestureActive = true
                        try {
                            // Flow completing normally = gesture committed
                            progressFlow.collect { event ->
                                gestureAlpha.snapTo(1f - event.progress)
                            }

                            if (backStack.size > 1) {
                                when (backStack.lastOrNull()) {
                                    is FmNavKey.Trash -> {
                                        vm.closeTrash()
                                        backStack.closeTrash()
                                    }

                                    is FmNavKey.Editor -> {
                                        // Unsaved changes: pop a confirmation first, handled by the editor page
                                        if (vm.editorHasDirty()) {
                                            vm.editorRequestExitConfirm()
                                        } else {
                                            backStack.closeEditor()
                                        }
                                    }

                                    else -> {
                                        backStack.closeTrash()
                                        backStack.closeEditor()
                                    }
                                }
                            } else if (!vm.consumeBack()) {
                                // Above the root directory: exit the file manager
                                onExit()
                            }
                            if (gestureAlpha.value < 1f) {
                                gestureAlpha.animateTo(1f, tween(FmAnimations.FADE_IN_MS))
                            }
                        } catch (e: CancellationException) {
                            // Gesture cancelled: the onBack coroutine was cancelled, so the rebound must run in NonCancellable
                            withContext(NonCancellable) {
                                gestureAlpha.animateTo(1f, spring())
                            }
                            throw e
                        } finally {
                            gestureActive = false
                        }
                    }

                    // Error dialog
                    errorMessage?.let { message ->
                        FmAlertDialog(
                            title = stringResource(R.string.generic_error),
                            text = message,
                            onDismiss = { errorMessage = null }
                        )
                    }

                    // Progress dialog
                    val progress = uiState.taskProgress
                    if (progress != null && progress.kind.shouldShowProgressDialog) {
                        FmProgressDialog(
                            progress = progress,
                            onCancel = { vm.cancelCurrentTask() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InitBox(
    text: String,
    showProgress: Boolean = true
) {
    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (showProgress) CircularProgressIndicator()
                Text(text = text)
            }
        }
    }
}
