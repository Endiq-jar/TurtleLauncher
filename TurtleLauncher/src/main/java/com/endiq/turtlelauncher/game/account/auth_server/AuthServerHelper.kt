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

package com.endiq.turtlelauncher.game.account.auth_server

import android.content.Context
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.coroutine.Task
import com.endiq.turtlelauncher.coroutine.TaskSystem
import com.endiq.turtlelauncher.game.account.Account
import com.endiq.turtlelauncher.game.account.AccountsManager
import com.endiq.turtlelauncher.game.account.auth_server.data.AuthServer
import com.endiq.turtlelauncher.game.account.auth_server.models.AuthResult
import com.endiq.turtlelauncher.ui.androidText
import com.endiq.turtlelauncher.utils.logging.Logger
import kotlinx.coroutines.Dispatchers
import java.util.Objects

private const val TAG = "AuthServerHelper"

/**
 * Helps log into external accounts (creating new ones, or just logging in with an existing one)
 */
class AuthServerHelper(
    private val baseUrl: String,
    private val serverName: String,
    private val email: String,
    private val password: String,
    private val onSuccess: suspend (Account, Task) -> Unit = { _, _ -> },
    private val onFailed: (th: Throwable) -> Unit = {},
    private val onFinally: () -> Unit = {}
) {
    constructor(
        server: AuthServer,
        email: String,
        password: String,
        onSuccess: suspend (Account, Task) -> Unit = { _, _ -> },
        onFailed: (th: Throwable) -> Unit = {},
        onFinally: () -> Unit = {}
    ): this(server.baseUrl, server.serverName, email, password, onSuccess, onFailed, onFinally)

    private val apiServer = AuthServerApi(baseUrl)

    private fun login(
        context: Context,
        taskId: String? = null,
        loggingString: String = serverName,
        onlyOneRole: suspend (AuthResult, Task) -> Unit = { _, _ -> },
        hasMultipleRoles: suspend (AuthResult, Task) -> Unit = { _, _ -> }
    ) : Task {
        return Task.runTask(
            id = taskId,
            dispatcher = Dispatchers.IO,
            task = { task ->
                apiServer.login(
                    context, email, password,
                    onSuccess = { authResult ->
                        if (!Objects.isNull(authResult.selectedProfile)) {
                            onlyOneRole(authResult, task)
                        } else if (!Objects.isNull(authResult.availableProfiles)) {
                            hasMultipleRoles(authResult, task)
                        }
                    },
                    onFailed = onFailed
                )
            },
            onError = { e ->
                Logger.error(TAG, "An exception was encountered while performing the login task.", e)
                onFailed(e)
            },
            onFinally = onFinally
        ).apply {
            updateMessage(
                androidText(R.string.account_logging_in, loggingString)
            )
        }
    }

    private fun updateAccountInfo(
        account: Account,
        authResult: AuthResult,
        userName: String,
        profileId: String
    ) {
        account.apply {
            this.accessToken = authResult.accessToken
            this.clientToken = authResult.clientToken
            this.otherBaseUrl = baseUrl
            this.otherAccount = email
            this.otherPassword = password
            this.accountType = serverName
            this.username = userName
            this.profileId = profileId
        }
    }

    /**
     * Pre-launch self-check
     * @return false when both were rejected by the server
     */
    suspend fun validateOrRefresh(context: Context, account: Account): Boolean {
        if (apiServer.validate(context, account)) return true

        return try {
            val authResult = apiServer.refreshToken(context, account, select = true)
            account.accessToken = authResult.accessToken
            true
        } catch (e: ResponseException) {
            if (e.statusCode == 403) false else throw e
        }
    }

    /**
     * Re-logins with username/password and matches the current role; only called when both validate and refresh were rejected
     */
    suspend fun passwordLogin(context: Context, account: Account) {
        val authResult = apiServer.authenticate(context, account.otherAccount!!, password)
        val selected = authResult.selectedProfile
        val matchedName: String
        val matchedId: String
        if (selected != null && selected.id == account.profileId) {
            matchedId = selected.id
            matchedName = selected.name
        } else {
            val profile = authResult.availableProfiles?.find { it.id == account.profileId }
                ?: throw ResponseException(context.getString(R.string.account_other_login_role_not_found))
            matchedId = profile.id
            matchedName = profile.name
        }
        updateAccountInfo(account, authResult, matchedName, matchedId)
    }

    /**
     * Finds the account with the same profileId in the account manager (for the current account type)
     */
    private fun loadFromProfileID(profileId: String): Account {
        return AccountsManager.loadFromProfileID(profileId, serverName) ?: Account()
    }

    /**
     * Logs into a new account with username/password
     * @param selectRole role selection, needed when the account has multiple roles
     */
    fun createNewAccount(
        context: Context,
        selectRole: (List<AuthResult.AvailableProfiles>, (AuthResult.AvailableProfiles) -> Unit) -> Unit
    ) {
        val task = login(
            context,
            onlyOneRole = { authResult, task ->
                val profileId = authResult.selectedProfile!!.id
                val account: Account = loadFromProfileID(profileId)
                updateAccountInfo(account, authResult, authResult.selectedProfile!!.name, profileId)
                onSuccess(account, task)
            },
            hasMultipleRoles = { authResult, _ ->
                selectRole(authResult.availableProfiles!!) { selectedProfile ->
                    val profileId = selectedProfile.id
                    val account: Account = loadFromProfileID(profileId)
                    updateAccountInfo(account, authResult, selectedProfile.name, profileId)
                    refresh(context, account)
                }
            }
        )
        TaskSystem.submitTask(task)
    }

    /**
     * Merely logs into an external account (username/password login)
     * JUST DO IT!!!
     * @return the login task object
     */
    fun justLogin(context: Context, account: Account): Task {
        fun roleNotFound() { //no matching role ID found
            onFailed(ResponseException(context.getString(R.string.account_other_login_role_not_found)))
        }

        return login(
            context,
            onlyOneRole = { authResult, task ->
                if (authResult.selectedProfile!!.id != account.profileId) {
                    roleNotFound()
                    return@login
                }
                updateAccountInfo(account, authResult, authResult.selectedProfile!!.name, authResult.selectedProfile!!.id)
                onSuccess(account, task)
            },
            hasMultipleRoles = { authResult, task ->
                authResult.availableProfiles!!.forEach { profile ->
                    if (profile.id == account.profileId) {
                        //When the ID matches the current account, that role belongs to the account
                        updateAccountInfo(account, authResult, profile.name, profile.id)
                        onSuccess(account, task)
                        return@login
                    }
                }
                roleNotFound()
            },
            taskId = account.uniqueUUID,
            loggingString = account.username
        )
    }

    private fun refresh(context: Context, account: Account) {
        val task = Task.runTask(
            task = { task ->
                apiServer.refresh(context, account, true,
                    onSuccess = { authResult ->
                        account.accessToken = authResult.accessToken
                        onSuccess(account, task)
                    },
                    onFailed = onFailed
                )
            },
            onError = { e ->
                Logger.error(TAG, "An exception was encountered while performing the refresh task.", e)
                onFailed(e)
            }
        ).apply {
            updateMessage(
                androidText(R.string.account_other_login_select_role_logging, account.username)
            )
        }

        TaskSystem.submitTask(task)
    }
}