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

package com.endiq.turtlelauncher.ui.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.context.COPY_LABEL_LINK
import com.endiq.turtlelauncher.coroutine.Task
import com.endiq.turtlelauncher.coroutine.TaskSystem
import com.endiq.turtlelauncher.filemanager.FileManagerLauncher
import com.endiq.turtlelauncher.filemanager.events.FileManagerEvent
import com.endiq.turtlelauncher.filemanager.events.FileManagerEventRegistrar
import com.endiq.turtlelauncher.game.control.ControlManager
import com.endiq.turtlelauncher.game.path.getVersionsHome
import com.endiq.turtlelauncher.game.plugin.PluginLoader
import com.endiq.turtlelauncher.game.renderer.Renderers
import com.endiq.turtlelauncher.game.version.installed.Version
import com.endiq.turtlelauncher.game.version.installed.VersionsManager
import com.endiq.turtlelauncher.notification.NotificationManager
import com.endiq.turtlelauncher.path.PathManager
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.ui.AndroidStringText
import com.endiq.turtlelauncher.ui.androidText
import com.endiq.turtlelauncher.ui.base.BaseAppCompatActivity
import com.endiq.turtlelauncher.ui.base.ObserveFullScreenSetting
import com.endiq.turtlelauncher.ui.buildAppendedText
import com.endiq.turtlelauncher.ui.screens.NestedNavKey
import com.endiq.turtlelauncher.ui.screens.NormalNavKey
import com.endiq.turtlelauncher.ui.screens.content.elements.Background
import com.endiq.turtlelauncher.ui.screens.content.elements.LaunchGameOperation
import com.endiq.turtlelauncher.ui.screens.content.elements.TitleTaskFlowDialog
import com.endiq.turtlelauncher.ui.screens.content.navigateToLogView
import com.endiq.turtlelauncher.ui.screens.content.navigateToWeb
import com.endiq.turtlelauncher.ui.screens.main.MainScreen
import com.endiq.turtlelauncher.ui.screens.main.crashlogs.LogShareMenu
import com.endiq.turtlelauncher.ui.screens.main.crashlogs.LogShareMenuOperation
import com.endiq.turtlelauncher.ui.screens.main.crashlogs.ShareLinkOperation
import com.endiq.turtlelauncher.ui.theme.TurtleLauncherTheme
import com.endiq.turtlelauncher.ui.theme.feativals.FestivalEffects
import com.endiq.turtlelauncher.ui.theme.showThemed
import com.endiq.turtlelauncher.ui.toAndroidString
import com.endiq.turtlelauncher.ui.vulkan_checker.VCOperation
import com.endiq.turtlelauncher.ui.vulkan_checker.VulkanChecker
import com.endiq.turtlelauncher.upgrade.TooFrequentOperationException
import com.endiq.turtlelauncher.utils.compareLangTag
import com.endiq.turtlelauncher.utils.copyText
import com.endiq.turtlelauncher.utils.festival.getTodayFestivals
import com.endiq.turtlelauncher.utils.file.shareFile
import com.endiq.turtlelauncher.utils.isChinese
import com.endiq.turtlelauncher.utils.logging.Logger
import com.endiq.turtlelauncher.utils.network.openLink
import com.endiq.turtlelauncher.utils.network.openLinkInternal
import com.endiq.turtlelauncher.utils.string.getMessageOrToString
import com.endiq.turtlelauncher.viewmodel.BackgroundViewModel
import com.endiq.turtlelauncher.viewmodel.ErrorViewModel
import com.endiq.turtlelauncher.viewmodel.EventViewModel
import com.endiq.turtlelauncher.viewmodel.LaunchGameViewModel
import com.endiq.turtlelauncher.viewmodel.LauncherUpgradeOperation
import com.endiq.turtlelauncher.viewmodel.LauncherUpgradeViewModel
import com.endiq.turtlelauncher.viewmodel.LogShareViewModel
import com.endiq.turtlelauncher.viewmodel.LogsUploadViewModel
import com.endiq.turtlelauncher.viewmodel.ModpackConfirmUseMobileDataOperation
import com.endiq.turtlelauncher.viewmodel.ModpackImportOperation
import com.endiq.turtlelauncher.viewmodel.ModpackImportViewModel
import com.endiq.turtlelauncher.viewmodel.ModpackVersionNameOperation
import com.endiq.turtlelauncher.viewmodel.ScreenBackStackViewModel
import com.endiq.turtlelauncher.viewmodel.VulkanCheckerViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private const val TAG = "MainActivity"

@AndroidEntryPoint
class MainActivity : BaseAppCompatActivity() {
    override fun isIgnoreNotch(): Boolean = AllSettings.launcherFullScreen.getValue()

    /**
     * Screen stack management ViewModel
     */
    private val screenBackStackModel: ScreenBackStackViewModel by viewModels()

    /**
     * Launches the gameViewModel
     */
    private val launchGameViewModel: LaunchGameViewModel by viewModels()

    /**
     * Error info ViewModel
     */
    private val errorViewModel: ErrorViewModel by viewModels()

    /**
     * Compose interaction event ViewModel
     */
    val eventViewModel: EventViewModel by viewModels()

    /**
     * Launcher background content management ViewModel
     */
    val backgroundViewModel: BackgroundViewModel by viewModels()

    /**
     * pack import ViewModel
     */
    val modpackImportViewModel: ModpackImportViewModel by viewModels()

    /**
     * Launcher update state ViewModel
     */
    val launcherUpgradeViewModel: LauncherUpgradeViewModel by viewModels()

    /**
     * Game log share menu ViewModel
     */
    private val logShareViewModel: LogShareViewModel by viewModels()

    /**
     * Game log upload ViewModel
     */
    private val logsUploadViewModel: LogsUploadViewModel by viewModels()

    /**
     * Vulkan detection state ViewModel
     */
    private val vulkanCheckerViewModel: VulkanCheckerViewModel by viewModels()

    /**
     * Whether key-capture mode is enabled
     */
    private var isCaptureKey = false

    /**
     * File manager event listener
     */
    private var fmEventRegistrar: FileManagerEventRegistrar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //Handle external imports
        val isImporting = handleImportIfNeeded(intent)

        //Load renderers
        Renderers.init()
        //Load plugins
        PluginLoader.loadAllPlugins(this, false)
        refreshData()

        //Register the file manager event listener
        fmEventRegistrar = FileManagerEventRegistrar(this, ::onFileManagerEvent).also { it.start() }

        //Initialize notification management (create channels)
        NotificationManager.initManager(this)

        //Check for updates
        if (!isImporting && launcherUpgradeViewModel.operation == LauncherUpgradeOperation.None) {
            lifecycleScope.launch {
                launcherUpgradeViewModel.checkOnAppStart()
            }
        }

        //Error info display
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                errorViewModel.errorEvents.collect { tm ->
                    errorViewModel.showErrorDialog(
                        context = this@MainActivity,
                        tm = tm
                    )
                }
            }
        }

        //Event handling
        lifecycleScope.launch {
            eventViewModel.events.collect { event ->
                when (event) {
                    is EventViewModel.Event.Key.StartKeyCapture -> {
                        Logger.info("CollectEvent", "Start key capture!")
                        isCaptureKey = true
                    }
                    is EventViewModel.Event.Key.StopKeyCapture -> {
                        Logger.info("CollectEvent", "Stop key capture!")
                        isCaptureKey = false
                    }
                    is EventViewModel.Event.OpenLink -> {
                        val url = event.url
                        withContext(Dispatchers.Main) {
                            this@MainActivity.openLink(url)
                        }
                    }
                    is EventViewModel.Event.CheckUpdate -> {
                        checkUpdate()
                    }
                    is EventViewModel.Event.KeepScreen -> {
                        keepScreen(event.on)
                    }
                    is EventViewModel.Event.ImportControls -> {
                        importControlFiles(event.uris)
                    }
                    is EventViewModel.Event.DownloadPlugins -> {
                        showDownloadPlugins(event.link)
                    }
                    is EventViewModel.Event.Launch.Game -> {
                        launchGameViewModel.tryLaunch(event.version)
                    }
                    is EventViewModel.Event.Launch.PlayServer -> {
                        launchGameViewModel.quickPlayServer(event.version, event.address)
                    }
                    is EventViewModel.Event.Launch.PlaySave -> {
                        launchGameViewModel.quickPlaySave(event.version, event.saveName)
                    }
                    is EventViewModel.Event.LogShare.ShareGameLog -> {
                        val file = event.logFile
                        if (file.exists()) {
                            logsUploadViewModel.check(file)
                            logShareViewModel.openMenu(file)
                        }
                    }
                    is EventViewModel.Event.VulkanCheck -> {
                        checkVulkan(event.version)
                    }
                    is EventViewModel.Event.ShowToast -> {
                        Toast.makeText(
                            this@MainActivity,
                            event.text.toAndroidString(this@MainActivity),
                            event.duration
                        ).show()
                    }
                    is EventViewModel.Event.OpenFileManager -> {
                        FileManagerLauncher.launch(
                            context = this@MainActivity,
                            rootPath = event.rootPath,
                            currentPath = event.currentPath,
                            logsDir = PathManager.DIR_LAUNCHER_LOGS.absolutePath
                        )
                    }
                    else -> {
                        //Ignored
                    }
                }
            }
        }

        val finishedGame = AllSettings.finishedGame

        val festivals = getTodayFestivals(
            containsChinese = isChinese(this@MainActivity)
        )

        setContent {
            TurtleLauncherTheme(
                backgroundViewModel = backgroundViewModel,
                festivals = festivals
            ) {
                ObserveFullScreenSetting(AllSettings.launcherFullScreen.state)
                Box {
                    Background(
                        modifier = Modifier.fillMaxSize(),
                        viewModel = backgroundViewModel
                    )

                    MainScreen(
                        screenBackStackModel = screenBackStackModel,
                        eventViewModel = eventViewModel,
                        modpackImportViewModel = modpackImportViewModel,
                        submitError = {
                            errorViewModel.showError(it)
                        }
                    )

                    //Holiday easter-egg effect layer
                    FestivalEffects(
                        modifier = Modifier.fillMaxSize(),
                        festivals = festivals
                    )

                    //Game-launch operation flow
                    LaunchGameOperation(
                        activity = this@MainActivity,
                        eventViewModel = eventViewModel,
                        launchGameViewModel = launchGameViewModel,
                        exitActivity = {
                            this@MainActivity.finish()
                        },
                        ensureVulkanSupported = vulkanCheckerViewModel::ensureSupported,
                        submitError = {
                            errorViewModel.showError(it)
                        },
                        toAccountManageScreen = { menu ->
                            screenBackStackModel.mainScreen.navigateTo(
                                screenKey = NormalNavKey.AccountManager(menu)
                            )
                        },
                        toVersionManageScreen = {
                            screenBackStackModel.mainScreen.removeAndNavigateTo(
                                remove = NestedNavKey.VersionSettings::class,
                                screenKey = NormalNavKey.VersionsManager
                            )
                        },
                        navigateToWeb = { url ->
                            screenBackStackModel.mainScreen.backStack.navigateToWeb(url)
                        },
                        backToMain = {
                            screenBackStackModel.mainScreen.clearWith(NormalNavKey.LauncherMain)
                        },
                        checkIfInWebScreen = {
                            screenBackStackModel.mainScreen.currentKey is NormalNavKey.WebScreen
                        }
                    )

                    //Game-launch flow display
                    val launchFlow by launchGameViewModel.launchFlow.collectAsStateWithLifecycle()
                    val flow = launchFlow
                    if (flow != null) {
                        val launchTasks by flow.tasksFlow.collectAsStateWithLifecycle()
                        TitleTaskFlowDialog(
                            title = stringResource(R.string.main_launch_game),
                            tasks = launchTasks,
                            onCancel = {
                                launchGameViewModel.cancel()
                            }
                        )
                    }
                }

                //Show the sponsorship support popup


                ModpackImportOperation(
                    operation = modpackImportViewModel.importOperation,
                    changeOperation = { modpackImportViewModel.importOperation = it },
                    importer = modpackImportViewModel.importer,
                    onCancel = {
                        modpackImportViewModel.cancel()
                        lifecycleScope.launch {
                            keepScreen(false)
                        }
                    }
                )

                //User version-name confirmation flow
                ModpackVersionNameOperation(
                    operation = modpackImportViewModel.versionNameOperation,
                    onConfirmVersionName = { name ->
                        modpackImportViewModel.confirmVersionName(name)
                    },
                    onCancel = {
                        modpackImportViewModel.cancel()
                    }
                )

                //User mobile-data confirmation flow
                ModpackConfirmUseMobileDataOperation(
                    operation = modpackImportViewModel.confirmMobileDataOperation,
                    onConfirmUse = { use ->
                        modpackImportViewModel.confirmUseMobileData(use)
                    }
                )

                //Game log share menu
                val logFile = logShareViewModel.currentLogFile
                if (logShareViewModel.showMenu && logFile != null) {
                    LogShareMenu(
                        operation = LogShareMenuOperation.ShowMenu,
                        onChange = { operation ->
                            if (operation == LogShareMenuOperation.None) {
                                logShareViewModel.closeMenu()
                            }
                        },
                        onView = {
                            screenBackStackModel.mainScreen.backStack.navigateToLogView(
                                logPath = logFile.absolutePath
                            )
                            logShareViewModel.closeMenu()
                        },
                        onShare = {
                            shareFile(this@MainActivity, logFile)
                            logShareViewModel.closeMenu()
                        },
                        canUpload = logsUploadViewModel.canUpload,
                        onUpload = {
                            logsUploadViewModel.operation = ShareLinkOperation.Tip
                            logShareViewModel.closeMenu()
                        }
                    )
                }

                ShareLinkOperation(
                    operation = logsUploadViewModel.operation,
                    onChange = { logsUploadViewModel.operation = it },
                    onUploadChancel = { logsUploadViewModel.cancel() },
                    onUpload = {
                        logFile?.let { file ->
                            logsUploadViewModel.upload(file) { link ->
                                openLink(link)
                                copyText(COPY_LABEL_LINK, link, this@MainActivity)
                            }
                        }
                    }
                )

                //Update-check operation flow
                LauncherUpgradeOperation(
                    operation = launcherUpgradeViewModel.operation,
                    onChanged = { launcherUpgradeViewModel.operation = it },
                    onIgnoredClick = { ver ->
                        AllSettings.lastIgnoredVersion.save(ver)
                    },
                    onLinkClick = { eventViewModel.sendEvent(EventViewModel.Event.OpenLink(it)) }
                )

                val vcOperation by vulkanCheckerViewModel.vcOperation.collectAsStateWithLifecycle()
                VulkanChecker(
                    operation = vcOperation,
                    onChange = {
                        vulkanCheckerViewModel.changeOperation(it)
                    },
                    startCheck = { version ->
                        eventViewModel.sendEvent(
                            EventViewModel.Event.VulkanCheck(version)
                        )
                    },
                    confirmResult = {
                        vulkanCheckerViewModel.resumeCont()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleImportIfNeeded(intent)
        // Reload renderers
        Renderers.init(true)
        // Reload plugins
        PluginLoader.loadAllPlugins(this, true)
    }

    override fun onDestroy() {
        fmEventRegistrar?.stop()
        fmEventRegistrar = null
        super.onDestroy()
    }

    /**
     * Handles the file manager's file change events
     */
    private fun onFileManagerEvent(event: FileManagerEvent) {
        val versionsHome = File(getVersionsHome()).absolutePath
        val touchesVersions = event.changedDirs.any { dir ->
            val normalized = File(dir).absolutePath
            normalized == versionsHome || normalized.startsWith("$versionsHome${File.separator}")
        }
        if (touchesVersions) {
            VersionsManager.refresh("[FileManager] ${event.type.name}")
        }
    }

    /**
     * Checks the device's Vulkan support
     */
    private suspend fun checkVulkan(version: Version) {
        withContext(Dispatchers.Main) {
            val (result, useTurnip) = vulkanCheckerViewModel.check(version)
            vulkanCheckerViewModel.changeOperation(VCOperation.Result(result, useTurnip))
        }
    }

    /**
     * Checks for launcher updates
     */
    private fun checkUpdate() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val success = launcherUpgradeViewModel.checkManually(
                    onInProgress = {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, getString(R.string.generic_in_progress), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onIsLatest = {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, getString(R.string.upgrade_is_latest), Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                if (!success) throw RuntimeException()
            } catch (_: TooFrequentOperationException) {
                //Too frequent
                return@launch
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.upgrade_get_remote_failed), Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
        }
    }

    /**
     * Whether to keep the screen awake
     */
    private suspend fun keepScreen(on: Boolean) {
        withContext(Dispatchers.Main) {
            window?.apply {
                if (on) {
                    addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }

    /**
     * Pops the dialog with plugin download links
     */
    private suspend fun showDownloadPlugins(link: EventViewModel.Event.DownloadPlugins.Links) {
        //Pick the netdisk link visible for the current system language
        val locale = Locale.getDefault()
        val cloudDrive = link.cloudDrives.sortedByDescending {
            it.language.contains("_")
        }.find { drive ->
            locale.compareLangTag(drive.language)
        }

        withContext(Dispatchers.Main) {
            val builder = MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(R.string.plugin_download_title)
                .setMessage(R.string.plugin_download_summary)
                .setPositiveButton("Github") { dialog, _ ->
                    openLinkInternal(link.github)
                    dialog.dismiss()
                }

            cloudDrive?.link?.let { link ->
                builder.setNegativeButton(R.string.upgrade_cloud_drive) { dialog, _ ->
                    openLinkInternal(link)
                    dialog.dismiss()
                }
            }

            builder.showThemed()
        }
    }

    /**
     * Imports a control layout
     */
    private fun importControlFiles(uris: List<Uri>) {
        fun showError(
            title: AndroidStringText = androidText(R.string.control_manage_import_failed),
            message: AndroidStringText
        ) {
            errorViewModel.showError(
                ErrorViewModel.ThrowableMessage(
                    title = title,
                    message = message
                )
            )
        }
        TaskSystem.submitTask(
            Task.runTask(
                dispatcher = Dispatchers.IO,
                task = {
                    var done = false
                    uris.forEach { uri ->
                        val inputStream = contentResolver.openInputStream(uri) ?: run {
                            showError(message = androidText(R.string.multirt_runtime_import_failed_input_stream))
                            return@forEach
                        }
                        ControlManager.importControl(
                            inputStream = inputStream,
                            onSerializationError = {
                                showError(
                                    message = buildAppendedText {
                                        append(R.string.control_manage_import_failed_to_parse)
                                        append("\n")
                                        append(it.getMessageOrToString())
                                    }
                                )
                            },
                            catchedError =  {
                                showError(message = androidText(it.getMessageOrToString()))
                            },
                            onFinished = {
                                done = true
                            }
                        )
                    }
                    ControlManager.refresh()
                    if (done) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.generic_done),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            )
        )
    }

    /**
     * Handles external imports
     * @return whether an import task is in progress
     */
    private fun handleImportIfNeeded(intent: Intent?): Boolean {
        if (intent == null) return false

        val type = intent.getStringExtra(EXTRA_IMPORT_TYPE) ?: return false

        val importing = when (type) {
            IMPORT_TYPE_MODPACK -> handleModpackImport(intent)
            IMPORT_TYPE_CONTROLS -> handleControlsImport(intent)
            else -> false
        }

        intent.removeExtra(EXTRA_IMPORT_TYPE)
        return importing
    }

    /**
     * @return whether the pack import flow has triggered
     */
    private fun handleModpackImport(intent: Intent): Boolean {
        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_IMPORT_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_IMPORT_URI)
        }
        if (uri != null) {
            modpackImportViewModel.import(
                context = this@MainActivity,
                uri = uri,
                onStart = {
                    lifecycleScope.launch {
                        keepScreen(true)
                    }
                },
                onStop = {
                    lifecycleScope.launch {
                        keepScreen(false)
                    }
                }
            )
        }
        return uri != null
    }

    /**
     * @return whether the control layout import flow has triggered
     */
    private fun handleControlsImport(intent: Intent): Boolean {
        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_IMPORT_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_IMPORT_URI)
        }
        if (uri != null) {
            importControlFiles(listOf(uri))
        }
        return uri != null
    }

    override fun onResume() {
        super.onResume()
        ControlManager.checkDefaultAndRefresh(this@MainActivity)
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isCaptureKey) {
            Logger.info(TAG, "Capture key event: $event")
            eventViewModel.sendEvent(EventViewModel.Event.Key.OnKeyDown(event))
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}