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

package com.endiq.turtlelauncher.game.launch

import android.content.Context
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.coroutine.Task
import com.endiq.turtlelauncher.coroutine.TaskFlowExecutor
import com.endiq.turtlelauncher.coroutine.TitledTask
import com.endiq.turtlelauncher.coroutine.addTask
import com.endiq.turtlelauncher.coroutine.buildPhase
import com.endiq.turtlelauncher.game.account.Account
import com.endiq.turtlelauncher.game.account.AccountsManager
import com.endiq.turtlelauncher.game.account.auth_server.AuthServerHelper
import com.endiq.turtlelauncher.game.account.isLocalAccount
import com.endiq.turtlelauncher.game.account.isMicrosoftAccount
import com.endiq.turtlelauncher.game.account.isReloginRequired
import com.endiq.turtlelauncher.game.account.microsoft.validateAccessToken
import com.endiq.turtlelauncher.game.account.refreshMicrosoft
import com.endiq.turtlelauncher.game.version.download.DownloadMode
import com.endiq.turtlelauncher.game.version.download.MinecraftDownloader
import com.endiq.turtlelauncher.game.version.installed.Version
import com.endiq.turtlelauncher.game.version.installed.VersionFolders
import com.endiq.turtlelauncher.game.version.mod.AllModReader
import com.endiq.turtlelauncher.ui.activities.runGame
import com.endiq.turtlelauncher.ui.androidText
import com.endiq.turtlelauncher.utils.network.isNetworkAvailable
import com.endiq.turtlelauncher.viewmodel.ErrorViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "GameLaunchFlow"

/** The account credential was rejected by the server */
private class LaunchReloginRequired(
    val account: Account
) : RuntimeException()

/** Thrown when account validation or refresh fails */
private class LaunchCheckFailed(
    val account: Account,
    cause: Throwable
) : RuntimeException(cause)


/**
 * Game launcher
 */
class GameLaunchFlow(scope: CoroutineScope) {
    private val taskExecutor = TaskFlowExecutor(scope)
    val tasksFlow: StateFlow<List<TitledTask>> = taskExecutor.tasksFlow

    /**
     * Launches the game
     * @param version the version to launch
     * @param skipAccountRefresh skips pre-launch account validation, using existing credentials
     */
    fun launch(
        context: Context,
        version: Version,
        skipAccountRefresh: Boolean = false,
        exitActivity: () -> Unit,
        submitError: (ErrorViewModel.ThrowableMessage) -> Unit,
        onReloginRequired: (Account) -> Unit = {},
        onRefreshFailed: (Account, Throwable) -> Unit = { _, _ -> },
        isRunning: () -> Unit = {},
        onComplete: () -> Unit,
    ) {
        if (taskExecutor.isRunning()) {
            //A launch is already in progress; block this request
            isRunning()
            return
        }

        val account = AccountsManager.currentAccountFlow.value ?: return

        taskExecutor.executePhasesAsync(
            onStart = {
                taskExecutor.addPhases(
                    listOf(
                        buildLaunchPhases(
                            context = context,
                            version = version,
                            account = account,
                            skipAccountRefresh = skipAccountRefresh,
                            exitActivity = exitActivity,
                            submitError = submitError
                        )
                    )
                )
            },
            onComplete = onComplete,
            onError = { th ->
                when (th) {
                    is LaunchReloginRequired -> onReloginRequired(th.account)
                    is LaunchCheckFailed -> onRefreshFailed(th.account, th.cause ?: th)
                    else -> {}
                }
            },
        )
    }

    /**
     * Cancels the current launch flow
     */
    fun cancel() {
        taskExecutor.cancel()
    }

    private fun buildLaunchPhases(
        context: Context,
        version: Version,
        account: Account,
        skipAccountRefresh: Boolean,
        exitActivity: () -> Unit,
        submitError: (ErrorViewModel.ThrowableMessage) -> Unit
    ): TaskFlowExecutor.TaskPhase {
        //Check connectivity; that decides whether to validate the account
        //and, when offline, log Microsoft/external accounts in as offline accounts
        val hasNetwork = isNetworkAvailable(context)
        if (!hasNetwork && !account.isLocalAccount()) {
            version.offlineAccountLogin = true
        }

        return buildPhase {
            if (hasNetwork && !skipAccountRefresh && AccountsManager.isLaunchCheckNeeded(account)) {
                //While the account page is refreshing this account, launch with the existing credentials
                addTask(
                    icon = R.drawable.ic_login,
                    title = androidText(R.string.account_logging_in, account.username),
                    dispatcher = Dispatchers.IO
                ) { task ->
                    try {
                        if (account.isMicrosoftAccount()) {
                            checkMicrosoftAccount(task, account)
                        } else {
                            checkOtherAccount(task, context, account)
                        }
                        AccountsManager.markSessionValidated(account)
                        AccountsManager.suspendSaveAccount(account)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        if (e.isReloginRequired()) throw LaunchReloginRequired(account)
                        throw LaunchCheckFailed(account, e)
                    }
                }
            }

            if (!version.skipGameIntegrityCheck()) {
                //Verify and repair game files
                addTask(
                    icon = R.drawable.ic_assignment_filled,
                    title = androidText(R.string.minecraft_download_stat_verify_task),
                    task = createGameDownloadTask(
                        context = context,
                        version = version,
                        submitError = submitError
                    )
                )
            }

            //Launch the game
            addTask(
                icon = R.drawable.ic_rocket_launch_filled,
                title = androidText(R.string.main_launch_game)
            ) { task ->
                checkEnableTouchProxy(version)

                runGame(context, version, account)
                exitActivity()
            }
        }
    }

    /**
     * Microsoft account: validates cached credentials server-side, silently refreshing when rejected or near expiry
     */
    private suspend fun checkMicrosoftAccount(task: Task, account: Account) {
        val expired = System.currentTimeMillis() > account.expiresAt - 5 * 60 * 1000
        if (expired || !validateAccessToken(account)) {
            account.refreshMicrosoft(task, currentCoroutineContext())
        }
    }

    /**
     * External account: tries validate then refresh; re-logins with username/password only when both are rejected
     */
    private suspend fun checkOtherAccount(task: Task, context: Context, account: Account) {
        task.updateMessage(androidText(R.string.account_logging_in, account.username))
        val helper = AuthServerHelper(
            baseUrl = account.otherBaseUrl!!,
            serverName = account.accountType!!,
            email = account.otherAccount!!,
            password = account.otherPassword!!
        )
        if (!helper.validateOrRefresh(context, account)) {
            helper.passwordLogin(context, account)
        }
    }

    private fun createGameDownloadTask(
        context: Context,
        version: Version,
        submitError: (ErrorViewModel.ThrowableMessage) -> Unit
    ): Task {
        return MinecraftDownloader(
            context = context,
            version = version.getVersionInfo()?.minecraftVersion ?: version.getVersionName(),
            customName = version.getVersionName(),
            gameHome = version.getGameHome(),
            mode = DownloadMode.VERIFY_AND_REPAIR,
            onError = { message ->
                submitError(
                    ErrorViewModel.ThrowableMessage(
                        title = androidText(R.string.minecraft_download_failed),
                        message = androidText(message)
                    )
                )
            }
        ).getDownloadTask()
    }

    /**
     * Checks TouchController is installed, enabling the control proxy afterwards
     */
    private suspend fun checkEnableTouchProxy(version: Version) {
        val modsDir = VersionFolders.MOD.getDir(version.getGameDir())
        val reader = AllModReader(modsDir)
        for (mod in reader.readAllLocals()) {
            if (mod.id == "touchcontroller") {
                version.enableTouchProxy = true
                break
            }
        }
    }
}
