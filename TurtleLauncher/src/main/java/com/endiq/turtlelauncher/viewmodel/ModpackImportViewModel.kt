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

package com.endiq.turtlelauncher.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonSyntaxException
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.game.download.jvm_server.JvmCrashException
import com.endiq.turtlelauncher.game.download.jvm_server.isProcessStartRefused
import com.endiq.turtlelauncher.game.download.modpack.install.ModpackImporter
import com.endiq.turtlelauncher.game.download.modpack.install.PackNotSupportedException
import com.endiq.turtlelauncher.game.download.modpack.install.UnsupportedPackReason
import com.endiq.turtlelauncher.game.download.modpack.platform.PackPlatform
import com.endiq.turtlelauncher.game.version.download.DownloadFailedException
import com.endiq.turtlelauncher.game.version.installed.VersionsManager
import com.endiq.turtlelauncher.ui.components.MarqueeText
import com.endiq.turtlelauncher.ui.components.SimpleAlertDialog
import com.endiq.turtlelauncher.ui.components.fadeEdge
import com.endiq.turtlelauncher.ui.components.verticalScrollWithBar
import com.endiq.turtlelauncher.ui.screens.content.download.ModpackVersionNameDialog
import com.endiq.turtlelauncher.ui.screens.content.download.assets.elements.PackIdentifier
import com.endiq.turtlelauncher.ui.screens.content.elements.TitleTaskFlowDialog
import com.endiq.turtlelauncher.utils.logging.Logger
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import java.util.concurrent.TimeoutException
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

private const val TAG = "ModpackImportVM"

/** pack import operations */
sealed interface ModpackImportOperation {
    data object None : ModpackImportOperation
    /** Starts importing a modpack */
    data object Import : ModpackImportOperation
    /** Unsupported pack or invalid format */
    data class NotSupport(val reason: UnsupportedPackReason) : ModpackImportOperation
    /** pack import finished */
    data object Finished : ModpackImportOperation
    /** Exception during pack import */
    data class Error(val th: Throwable) : ModpackImportOperation
}

/** pack version-name customization state operations */
sealed interface VersionNameOperation {
    data object None : VersionNameOperation
    /** Waiting for the user's version name */
    data class Waiting(val name: String) : VersionNameOperation
}

/** State op for the modpack-install cellular-confirmation dialog */
sealed interface ConfirmMobileDataOperation {
    data object None : ConfirmMobileDataOperation
    /** Waiting for the user's mobile-data confirmation */
    data object Waiting : ConfirmMobileDataOperation
}

/**
 * pack import ViewModel
 */
class ModpackImportViewModel : ViewModel() {
    var importOperation by mutableStateOf<ModpackImportOperation>(ModpackImportOperation.None)
    var versionNameOperation by mutableStateOf<VersionNameOperation>(VersionNameOperation.None)
    var confirmMobileDataOperation by mutableStateOf<ConfirmMobileDataOperation>(ConfirmMobileDataOperation.None)

    //Version name input wait handling
    private var versionNameContinuation: (Continuation<String>)? = null
    suspend fun waitForVersionName(name: String): String {
        return suspendCancellableCoroutine { cont ->
            versionNameContinuation = cont
            versionNameOperation = VersionNameOperation.Waiting(name)
        }
    }

    /**
     * The user confirmed the version name
     */
    fun confirmVersionName(name: String) {
        //Resume the continuation
        versionNameContinuation?.resume(name)
        versionNameContinuation = null
        versionNameOperation = VersionNameOperation.None
    }

    //Mobile-data warning handling
    private var confirmMobileData : (Continuation<Boolean>)? = null
    suspend fun waitForConfirmMobileData(): Boolean {
        return suspendCancellableCoroutine { cont ->
            confirmMobileData = cont
            confirmMobileDataOperation = ConfirmMobileDataOperation.Waiting
        }
    }

    /**
     * Whether the user confirmed mobile data
     */
    fun confirmUseMobileData(use: Boolean) {
        //Resume the continuation
        confirmMobileData?.resume(use)
        confirmMobileData = null
        confirmMobileDataOperation = ConfirmMobileDataOperation.None
    }

    /**
     * pack importer
     */
    var importer by mutableStateOf<ModpackImporter?>(null)

    /**
     * Starts importing a modpack
     */
    fun import(
        context: Context,
        uri: Uri,
        onStart: () -> Unit = {},
        onStop: () -> Unit = {}
    ) {
        if (importOperation != ModpackImportOperation.None) {
            //Another import is running; refuse this one
            return
        }
        importOperation = ModpackImportOperation.Import
        importer = ModpackImporter(
            context = context,
            uri = uri,
            scope = viewModelScope,
            waitForVersionName = ::waitForVersionName,
            waitForConfirmMobileData = ::waitForConfirmMobileData
        ).also {
            it.startImport(
                onFinished = { version ->
                    importer = null
                    VersionsManager.refresh("[Modpack] ModpackImporter.onFinished", version)
                    importOperation = ModpackImportOperation.Finished
                    onStop()
                },
                onCancelled = {
                    importer = null
                    importOperation = ModpackImportOperation.None
                    onStop()
                },
                onError = { th ->
                    importer = null
                    importOperation = if (th is PackNotSupportedException) {
                        //Unsupported pack; cannot import
                        ModpackImportOperation.NotSupport(th.reason)
                    } else {
                        ModpackImportOperation.Error(th)
                    }
                    onStop()
                }
            )
        }
        onStart()
    }

    fun cancel() {
        importer?.cancel()
        importer = null
        importOperation = ModpackImportOperation.None
        versionNameOperation = VersionNameOperation.None
        confirmMobileDataOperation = ConfirmMobileDataOperation.None
    }

    override fun onCleared() {
        cancel()
    }
}

@Composable
fun ModpackImportOperation(
    operation: ModpackImportOperation,
    changeOperation: (ModpackImportOperation) -> Unit,
    importer: ModpackImporter?,
    onCancel: () -> Unit
) {
    when (operation) {
        is ModpackImportOperation.None -> {}
        is ModpackImportOperation.Import -> {
            if (importer != null) {
                val tasks by importer.taskFlow.collectAsStateWithLifecycle()
                val installLog = importer.logOutput.collectAsStateWithLifecycle()
                if (tasks.isNotEmpty()) {
                    TitleTaskFlowDialog(
                        title = stringResource(R.string.import_modpack),
                        tasks = tasks,
                        onCancel = {
                            onCancel()
                            changeOperation(ModpackImportOperation.None)
                        },
                        logOutput = installLog.value
                    )
                }
            }
        }
        is ModpackImportOperation.NotSupport -> {
            AlertDialog(
                onDismissRequest = {},
                title = {
                    Text(text = stringResource(R.string.import_modpack_not_supported_title))
                },
                text = {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fadeEdge(state = scrollState)
                            .verticalScrollWithBar(state = scrollState),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        when (operation.reason) {
                            UnsupportedPackReason.CorruptedArchive -> {
                                //Unimportable because extraction failed
                                Text(text = stringResource(R.string.import_modpack_not_supported_text1))

                                Text(text = stringResource(R.string.import_modpack_not_supported_text2))
                                Text(text = stringResource(R.string.import_modpack_not_supported_text3))

                                Text(text = stringResource(R.string.import_modpack_not_supported_text4))
                            }
                            UnsupportedPackReason.UnsupportedFormat -> {
                                //The launcher truly doesn't support this format
                                Text(text = stringResource(R.string.import_modpack_not_supported_formats))
                                AllSupportPackDisplay(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            changeOperation(ModpackImportOperation.None)
                        }
                    ) {
                        MarqueeText(text = stringResource(R.string.generic_confirm))
                    }
                }
            )
        }
        is ModpackImportOperation.Finished -> {
            SimpleAlertDialog(
                title = stringResource(R.string.import_modpack_finished_title),
                text = stringResource(R.string.import_modpack_finished_text)
            ) {
                changeOperation(ModpackImportOperation.None)
            }
        }
        is ModpackImportOperation.Error -> {
            val th = operation.th
            Logger.error(TAG, "Failed to download the game!", th)
            val message = when (th) {
                is HttpRequestTimeoutException, is SocketTimeoutException, is TimeoutException -> stringResource(R.string.error_timeout)
                is UnknownHostException, is UnresolvedAddressException -> stringResource(R.string.error_network_unreachable)
                is ConnectException -> stringResource(R.string.error_connection_failed)
                is SerializationException, is JsonSyntaxException -> stringResource(R.string.error_parse_failed)
                is JvmCrashException -> stringResource(R.string.download_install_error_jvm_crash, th.code)
                is DownloadFailedException -> stringResource(R.string.download_install_error_download_failed)
                else -> when {
                    th.isProcessStartRefused() -> stringResource(R.string.download_install_error_process_start)
                    else -> th.localizedMessage ?: th.message ?: th::class.qualifiedName ?: "Unknown error"
                }
            }
            val dismiss = {
                changeOperation(ModpackImportOperation.None)
            }
            AlertDialog(
                onDismissRequest = dismiss,
                title = {
                    Text(text = stringResource(R.string.import_modpack_failed_title))
                },
                text = {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fadeEdge(state = scrollState)
                            .verticalScrollWithBar(state = scrollState),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = stringResource(R.string.import_modpack_failed_text))
                        Text(text = message)
                    }
                },
                confirmButton = {
                    Button(onClick = dismiss) {
                        MarqueeText(text = stringResource(R.string.generic_confirm))
                    }
                }
            )
        }
    }
}

/**
 * Displays all supported pack formats
 */
@Composable
fun AllSupportPackDisplay(
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PackPlatform.entries.forEach { platform ->
            PackIdentifier(
                platform = platform
            )
        }
    }
}

@Composable
fun ModpackVersionNameOperation(
    operation: VersionNameOperation,
    onConfirmVersionName: (String) -> Unit,
    onCancel: () -> Unit
) {
    when (operation) {
        is VersionNameOperation.None -> {}
        is VersionNameOperation.Waiting -> {
            ModpackVersionNameDialog(
                name = operation.name,
                onConfirmVersionName = onConfirmVersionName,
                onCancel = onCancel
            )
        }
    }
}

@Composable
fun ModpackConfirmUseMobileDataOperation(
    operation: ConfirmMobileDataOperation,
    onConfirmUse: (Boolean) -> Unit
) {
    when (operation) {
        is ConfirmMobileDataOperation.None -> {}
        is ConfirmMobileDataOperation.Waiting -> {
            SimpleAlertDialog(
                title = stringResource(R.string.generic_warning),
                text = stringResource(R.string.download_install_warning_mobile_data),
                confirmText = stringResource(R.string.generic_anyway),
                onDismiss = {
                    onConfirmUse(false)
                },
                onConfirm = {
                    //The user insists on mobile data
                    onConfirmUse(true)
                }
            )
        }
    }
}