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

/** 账号凭据已被服务端拒绝 */
private class LaunchReloginRequired(
    val account: Account
) : RuntimeException()

/** 账号校验或刷新失败时抛出 */
private class LaunchCheckFailed(
    val account: Account,
    cause: Throwable
) : RuntimeException(cause)


/**
 * 游戏启动器
 */
class GameLaunchFlow(scope: CoroutineScope) {
    private val taskExecutor = TaskFlowExecutor(scope)
    val tasksFlow: StateFlow<List<TitledTask>> = taskExecutor.tasksFlow

    /**
     * 启动游戏
     * @param version 指定版本
     * @param skipAccountRefresh 跳过启动前的账号校验，直接使用现有凭据
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
            //正在启动中，阻止这次启动请求
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
     * 取消当前的启动流程
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
        //检查是否联网，根据这个条件决定是否校验账号
        //以及，没有联网时，让微软账号、外置账号作为离线账号登录
        val hasNetwork = isNetworkAvailable(context)
        if (!hasNetwork && !account.isLocalAccount()) {
            version.offlineAccountLogin = true
        }

        return buildPhase {
            if (hasNetwork && !skipAccountRefresh && AccountsManager.isLaunchCheckNeeded(account)) {
                //账号管理页正在刷新该账号时，直接使用现有凭据启动
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
                //校验并修复游戏文件
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

            //启动游戏
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
     * 微软账号：向服务端校验缓存的凭据，被拒绝或已临近过期时静默刷新
     */
    private suspend fun checkMicrosoftAccount(task: Task, account: Account) {
        val expired = System.currentTimeMillis() > account.expiresAt - 5 * 60 * 1000
        if (expired || !validateAccessToken(account)) {
            account.refreshMicrosoft(task, currentCoroutineContext())
        }
    }

    /**
     * 外置账号：依次尝试 validate 与 refresh，均被服务端拒绝时再用账号密码重新登录
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
     * 检查是否安装了 TouchController，安装后开启控制代理
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
