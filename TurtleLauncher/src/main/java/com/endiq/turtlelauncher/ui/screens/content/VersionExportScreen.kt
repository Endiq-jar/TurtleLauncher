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

package com.endiq.turtlelauncher.ui.screens.content

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.google.gson.JsonSyntaxException
import com.endiq.turtlelauncher.BuildKeys
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.game.version.download.DownloadFailedException
import com.endiq.turtlelauncher.game.version.export.ExportInfo
import com.endiq.turtlelauncher.game.version.export.PackExporter
import com.endiq.turtlelauncher.game.version.export.PackType
import com.endiq.turtlelauncher.game.version.export.data.FileSelectionData
import com.endiq.turtlelauncher.game.version.export.data.Selected
import com.endiq.turtlelauncher.game.version.export.data.getSelectedFiles
import com.endiq.turtlelauncher.game.version.installed.Version
import com.endiq.turtlelauncher.game.version.installed.VersionsManager
import com.endiq.turtlelauncher.ui.base.BaseScreen
import com.endiq.turtlelauncher.ui.components.MarqueeText
import com.endiq.turtlelauncher.ui.components.SimpleAlertDialog
import com.endiq.turtlelauncher.ui.components.fadeEdge
import com.endiq.turtlelauncher.ui.components.verticalScrollWithBar
import com.endiq.turtlelauncher.ui.screens.NestedNavKey
import com.endiq.turtlelauncher.ui.screens.NormalNavKey
import com.endiq.turtlelauncher.ui.screens.TitledNavKey
import com.endiq.turtlelauncher.ui.screens.clearKeys
import com.endiq.turtlelauncher.ui.screens.content.elements.TitleTaskFlowDialog
import com.endiq.turtlelauncher.ui.screens.content.versions.export.ExportInfoScreen
import com.endiq.turtlelauncher.ui.screens.content.versions.export.ExportSelectFilesScreen
import com.endiq.turtlelauncher.ui.screens.content.versions.export.ExportTypeSelectScreen
import com.endiq.turtlelauncher.ui.screens.navigateTo
import com.endiq.turtlelauncher.ui.screens.onBack
import com.endiq.turtlelauncher.ui.screens.rememberTransitionSpec
import com.endiq.turtlelauncher.utils.logging.Logger
import com.endiq.turtlelauncher.viewmodel.EventViewModel
import com.endiq.turtlelauncher.viewmodel.ScreenBackStackViewModel
import com.endiq.turtlelauncher.viewmodel.sendKeepScreen
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import java.util.concurrent.TimeoutException

private const val TAG = "VersionExportScreen"

private sealed interface PackExportOperation {
    data object None : PackExportOperation
    /** Pack export in progress */
    data object Exporting : PackExportOperation
    /** Pack export succeeded */
    data object Finished : PackExportOperation
    /** Exception during export */
    data class Error(val throwable: Throwable) : PackExportOperation
}

/**
 * Files/folders not selected by default
 */
private val selectBlackList = listOf(
    //Fabric runtime libraries; no need to pack
    ".fabric",
    //Game config files: unselected by default
    "options.txt",
    //Folders containing natives, usually drivers
    //Nothing worth packing
    "natives",
    "downloads",
    //Config files of various launchers
    "PCL", BuildKeys.LAUNCHER_IDENTIFIER, "fclversion.cfg",
    //Game saves usually aren't packed
    "saves",
    //Realms config
    "realms_persistence.json",
    //UUID cache of accounts that logged in
    "usercache.json",
    //XXX logs
    ".log", "logs"
)

/**
 * @param versionName the current game version's custom name
 */
private class ExportModpackViewModel(
    val mcVersion: String,
    val versionName: String,
    val gamePath: File,
    val loader: ExportInfo.LoaderVersion?,
    /** The version's game arguments, prefilled on export */
    val gameArgs: String,
    /** The version's JVM arguments, prefilled on export */
    val javaArgs: String
): ViewModel() {
    private val _allFiles = MutableStateFlow<List<FileSelectionData>>(emptyList())
    /** All currently selectable files/directories */
    val allFiles = _allFiles.asStateFlow()

    private val _selectedFiles = MutableStateFlow(false)
    /** Whether any file is selected */
    val selectedFiles = _selectedFiles.asStateFlow()

    private val _isRefreshingFiles = MutableStateFlow(false)
    /** Whether the file list is refreshing */
    val isRefreshingFiles = _isRefreshingFiles.asStateFlow()

    private val _exportInfo = MutableStateFlow(defaultInfo())
    val exportInfo = _exportInfo.asStateFlow()

    fun editInfo(info: ExportInfo) {
        _exportInfo.update { info }
    }

    fun selectType(packType: PackType) {
        _exportInfo.update { defaultInfo(packType) }
    }

    private val _selectingFolder = MutableStateFlow(false)
    /** Whether the user is picking an export directory */
    val selectingFolder = _selectingFolder.asStateFlow()
    fun updateSelecting(value: Boolean) {
        _selectingFolder.update { value }
    }

    private fun defaultInfo(
        packType: PackType = PackType.Modrinth
    ): ExportInfo {
        val packModrinth = packType == PackType.Modrinth
        val packCurseForge = packType == PackType.Modrinth || packType == PackType.CurseForge
        return ExportInfo(
            gamePath = gamePath,
            name = versionName,
            version = "1.0",
            mcVersion = mcVersion,
            loader = loader,
            gameArgs = gameArgs,
            javaArgs = javaArgs,
            packType = packType,
            packModrinth = packModrinth,
            packCurseForge = packCurseForge
        )
    }

    private val _packExportOperation = MutableStateFlow<PackExportOperation>(PackExportOperation.None)
    val packExportOperation = _packExportOperation.asStateFlow()

    fun updateOperation(operation: PackExportOperation) {
        _packExportOperation.update { operation }
    }

    private val _packExporter = MutableStateFlow<PackExporter?>(null)
    /** Modpack exporter */
    val packExporter = _packExporter.asStateFlow()

    private val startMutex = Mutex()
    fun startExport(
        version: Version,
        context: Context,
        outputUri: Uri,
        onStart: () -> Unit = {},
        onStop: () -> Unit = {},
        onFinished: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.Main) {
            val info = startMutex.withLock {
                val files = withContext(Dispatchers.Default) {
                    allFiles.value.getSelectedFiles()
                }
                _exportInfo.value.copy(
                    selectedFiles = files
                )
            }

            _packExportOperation.update { PackExportOperation.Exporting }
            _packExporter.update {
                PackExporter(
                    context = context,
                    exportInfo = info,
                    scope = viewModelScope
                ).also {
                    it.startExport(
                        outputUri = outputUri,
                        version = version,
                        onFinished = {
                            _packExporter.update { null }
                            _packExportOperation.update { PackExportOperation.Finished }
                            onStop()
                            onFinished()
                        },
                        onError = { throwable ->
                            _packExporter.update { null }
                            _packExportOperation.update { PackExportOperation.Error(throwable) }
                            onStop()
                        }
                    )
                    onStart()
                }
            }
        }
    }

    fun cancelExport() {
        _packExporter.value?.cancel()
        _packExporter.update { null }
        _packExportOperation.update { PackExportOperation.None }
    }


    private var currentRefreshJob: Job? = null

    /**
     * Refreshes the selectable file list under the current version dir
     */
    fun refreshFiles(
        isInit: Boolean = true
    ) {
        currentRefreshJob?.cancel()
        currentRefreshJob = viewModelScope.launch(Dispatchers.Default) {
            withContext(Dispatchers.Main) {
                _allFiles.update { emptyList() }
                _selectedFiles.update { false }
                _isRefreshingFiles.update { true }
            }
            val temp = packFiles(gamePath)

            if (isInit) {
                //On initialization, preselect some files
                temp.forEach { data ->
                    if (selectBlackList.any { data.file.name.contains(it) }) {
                        data.updateSelectState(Selected.Unselected)
                        return@forEach
                    }
                    data.updateSelectState(Selected.Selected)
                }
            }

            withContext(Dispatchers.Main) {
                _allFiles.update { temp }
                _isRefreshingFiles.update { false }
            }

            refreshRootSelectSuspend()
        }
    }


    private var refreshRootJob: Job? = null
    fun refreshRootSelect() {
        refreshRootJob?.cancel()
        refreshRootJob = viewModelScope.launch(Dispatchers.Default) {
            refreshRootSelectSuspend()
        }
    }

    private suspend fun refreshRootSelectSuspend() {
        _selectedFiles.update { false }
        try {
            val count = FileSelectionData.refreshTreeSelect(_allFiles.value)
            //Whether any file is selected is judged by the selected count
            _selectedFiles.update { count > 0 }
        } catch (_: CancellationException) {

        }
    }

    private val packBlackList = listOf(
        "$versionName.json",
        "$versionName.jar"
    )

    data class StackNode(
        val dir: File,
        val depth: Int,
        val container: MutableList<FileSelectionData>
    )

    private fun packFiles(root: File): List<FileSelectionData> {
        if (!root.isDirectory) return emptyList()
        val result = mutableListOf<FileSelectionData>()

        val stack = ArrayDeque<StackNode>()
        stack.add(StackNode(root, 1, result))

        while (stack.isNotEmpty()) {
            val (dir, currentDepth, container) = stack.removeLast()
            val files = dir.listFiles() ?: continue

            val tempList = ArrayList<FileSelectionData>(files.size)

            for (file in files) {
                if (packBlackList.any { pattern -> file.name.contains(pattern) }) continue
                //Only root-level files may get aliases
                val alias = if (currentDepth == 1) getAlias(file.name) else null

                if (file.isDirectory) {
                    val childList = mutableListOf<FileSelectionData>()

                    val data = FileSelectionData(
                        file = file,
                        alias = alias,
                        child = childList
                    )

                    tempList.add(data)

                    stack.add(
                        StackNode(
                            dir = file,
                            depth = currentDepth + 1,
                            container = childList
                        )
                    )
                } else {
                    tempList.add(
                        FileSelectionData(
                            file = file,
                            alias = alias,
                            child = null
                        )
                    )
                }
            }

            tempList.sort()
            container.addAll(tempList)
        }

        return result
    }

    /**
     * Returns the file's alias (a player-readable name)
     */
    private fun getAlias(fileName: String): Int? {
        return when (fileName) {
            "options.txt" -> R.string.versions_export_alias_options
            "resourcepacks" -> R.string.versions_export_alias_resource_packs
            "mods" -> R.string.versions_export_alias_mods
            "saves" -> R.string.versions_export_alias_saves
            "shaderpacks" -> R.string.versions_export_alias_shaderpacks
            "config" -> R.string.versions_export_alias_config
            BuildKeys.LAUNCHER_IDENTIFIER -> R.string.versions_export_alias_launcher
            else -> null
        }
    }

    override fun onCleared() {
        currentRefreshJob?.cancel()
        currentRefreshJob = null
        refreshRootJob?.cancel()
        refreshRootJob = null
    }
}

@Composable
private fun rememberExportModpackViewModel(
    version: Version
) = viewModel(
    key = version.toString() + "_ExportModpack"
) {
    val info = version.getVersionInfo()!!

    ExportModpackViewModel(
        mcVersion = info.minecraftVersion,
        versionName = version.getVersionName(),
        gamePath = version.getGameDir(),
        loader = info.loaderInfo?.let { loader ->
            ExportInfo.LoaderVersion(loader.loader, loader.version)
        },
        gameArgs = version.getGameArgs(),
        javaArgs = version.getJvmArgs()
    )
}

@Composable
fun VersionExportScreen(
    key: NestedNavKey.VersionExport,
    backScreenViewModel: ScreenBackStackViewModel,
    eventViewModel: EventViewModel,
    backToMainScreen: () -> Unit,
) {
    if (!key.version.isValid()) {
        backToMainScreen()
        return
    }

    val cBackToMainScreen by rememberUpdatedState(backToMainScreen)
    DisposableEffect(key) {
        val listener = object : suspend () -> Unit {
            override suspend fun invoke() {
                cBackToMainScreen()
            }
        }
        VersionsManager.registerListener(listener)
        onDispose {
            VersionsManager.unregisterListener(listener)
        }
    }

    val viewModel = rememberExportModpackViewModel(version = key.version)

    val packExporter by viewModel.packExporter.collectAsStateWithLifecycle()
    val exportOperation by viewModel.packExportOperation.collectAsStateWithLifecycle()
    PackExportOperation(
        operation = exportOperation,
        onChange = { new ->
            viewModel.updateOperation(new)
        },
        packExporter = packExporter,
        onCancel = {
            viewModel.cancelExport()
        }
    )

    BaseScreen(
        screenKey = key,
        currentKey = backScreenViewModel.mainScreen.currentKey
    ) {
        NavigationUI(
            modifier = Modifier.fillMaxSize(),
            viewModel = viewModel,
            key = key,
            backScreenViewModel = backScreenViewModel,
            eventViewModel = eventViewModel,
            exportScreenKey = key.currentKey,
            onCurrentKeyChange = { key.currentKey = it },
            backToMainScreen = backToMainScreen,
            version = key.version
        )
    }
}

@Composable
private fun NavigationUI(
    viewModel: ExportModpackViewModel,
    key: NestedNavKey.VersionExport,
    backScreenViewModel: ScreenBackStackViewModel,
    eventViewModel: EventViewModel,
    exportScreenKey: TitledNavKey?,
    onCurrentKeyChange: (TitledNavKey?) -> Unit,
    backToMainScreen: () -> Unit,
    version: Version,
    modifier: Modifier = Modifier,
) {
    val mainScreenKey = backScreenViewModel.mainScreen.currentKey

    val backStack = key.backStack
    val stackTopKey = backStack.lastOrNull()
    LaunchedEffect(stackTopKey) {
        onCurrentKeyChange(stackTopKey)
    }

    if (backStack.isNotEmpty()) {
        NavDisplay(
            backStack = backStack,
            modifier = modifier,
            onBack = {
                onBack(backStack)
            },
            transitionSpec = rememberTransitionSpec(),
            popTransitionSpec = rememberTransitionSpec(),
            entryProvider = entryProvider {
                entry<NormalNavKey.VersionExports.SelectType> {
                    ExportTypeSelectScreen(
                        mainScreenKey = mainScreenKey,
                        exportScreenKey = exportScreenKey,
                        onTypeSelect = { type ->
                            viewModel.selectType(type)
                            backStack.navigateTo(NormalNavKey.VersionExports.EditInfo)
                        }
                    )
                }
                entry<NormalNavKey.VersionExports.EditInfo> {
                    val exportInfo by viewModel.exportInfo.collectAsStateWithLifecycle()
                    ExportInfoScreen(
                        info = exportInfo,
                        onInfoEdited = { viewModel.editInfo(it) },
                        onFinishClick = {
                            viewModel.refreshFiles()
                            viewModel.updateSelecting(false)
                            backStack.navigateTo(NormalNavKey.VersionExports.SelectFiles)
                        },
                        mainScreenKey = mainScreenKey,
                        exportScreenKey = exportScreenKey,
                    )
                }
                entry<NormalNavKey.VersionExports.SelectFiles> {
                    val allFiles by viewModel.allFiles.collectAsStateWithLifecycle()
                    val selectedFiles by viewModel.selectedFiles.collectAsStateWithLifecycle()
                    val isSelectingFolder by viewModel.selectingFolder.collectAsStateWithLifecycle()
                    val isRefreshingFiles by viewModel.isRefreshingFiles.collectAsStateWithLifecycle()

                    val context = LocalContext.current

                    val safLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        val uri = result.data?.data?.let { uri ->
                            //Get a persistable read/write uri
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                            uri
                        }
                        if (uri != null) {
                            viewModel.startExport(
                                context = context,
                                outputUri = uri,
                                version = version,
                                onStart = {
                                    eventViewModel.sendKeepScreen(true)
                                },
                                onStop = {
                                    eventViewModel.sendKeepScreen(false)
                                },
                                onFinished = {
                                    backStack.clearKeys(
                                        NormalNavKey.VersionExports.EditInfo,
                                        NormalNavKey.VersionExports.SelectFiles
                                    )
                                }
                            )
                        }
                        viewModel.updateSelecting(false)
                    }

                    var showSelectTip by remember { mutableStateOf(false) }
                    if (showSelectTip) {
                        SimpleAlertDialog(
                            title = stringResource(R.string.versions_export_pack_select_folder_title),
                            text = stringResource(R.string.versions_export_pack_select_folder_message),
                            onDismiss = {
                                showSelectTip = false
                            },
                            onConfirm = {
                                showSelectTip = false
                                //Start picking a directory
                                viewModel.updateSelecting(true)
                                safLauncher.launch(
                                    Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                                )
                            }
                        )
                    }

                    ExportSelectFilesScreen(
                        allFiles = allFiles,
                        selectedFiles = selectedFiles,
                        onRefreshRootSelect = { viewModel.refreshRootSelect() },
                        isSelectingFolder = isSelectingFolder,
                        isRefreshingFiles = isRefreshingFiles,
                        mainScreenKey = mainScreenKey,
                        exportScreenKey = exportScreenKey,
                        version = version,
                        backToMainScreen = backToMainScreen,
                        onFinish = {
                            showSelectTip = true
                        }
                    )
                }
            }
        )
    } else {
        Box(modifier)
    }
}

@Composable
private fun PackExportOperation(
    operation: PackExportOperation,
    onChange: (PackExportOperation) -> Unit,
    packExporter: PackExporter?,
    onCancel: () -> Unit,
) {
    when (operation) {
        is PackExportOperation.None -> {}
        is PackExportOperation.Exporting -> {
            if (packExporter != null) {
                val tasks by packExporter.taskFlow.collectAsStateWithLifecycle()
                if (tasks.isNotEmpty()) {
                    TitleTaskFlowDialog(
                        title = stringResource(R.string.versions_export),
                        tasks = tasks,
                        onCancel = {
                            onCancel()
                            onChange(PackExportOperation.None)
                        }
                    )
                }
            }
        }
        is PackExportOperation.Finished -> {
            SimpleAlertDialog(
                title = stringResource(R.string.versions_export),
                text = stringResource(R.string.versions_export_task_finished),
                onDismiss = {
                    onChange(PackExportOperation.None)
                }
            )
        }
        is PackExportOperation.Error -> {
            val th = operation.throwable
            Logger.error(TAG, "Failed to download the game!", th)
            val message = when (th) {
                is HttpRequestTimeoutException, is SocketTimeoutException, is TimeoutException -> stringResource(R.string.error_timeout)
                is UnknownHostException, is UnresolvedAddressException -> stringResource(R.string.error_network_unreachable)
                is ConnectException -> stringResource(R.string.error_connection_failed)
                is SerializationException, is JsonSyntaxException -> stringResource(R.string.error_parse_failed)
                is DownloadFailedException -> stringResource(R.string.download_install_error_download_failed)
                else -> {
                    th.localizedMessage ?: th.message ?: th::class.qualifiedName ?: "Unknown error"
                }
            }
            val dismiss = {
                onChange(PackExportOperation.None)
            }
            AlertDialog(
                onDismissRequest = dismiss,
                title = {
                    Text(text = stringResource(R.string.versions_export_task_error_title))
                },
                text = {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fadeEdge(state = scrollState)
                            .verticalScrollWithBar(state = scrollState),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = stringResource(R.string.versions_export_task_error_message))
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