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

package com.endiq.turtlelauncher.game.account

import android.content.Context
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.coroutine.Task
import com.endiq.turtlelauncher.coroutine.TaskSystem
import com.endiq.turtlelauncher.database.AppDatabase
import com.endiq.turtlelauncher.game.account.auth_server.data.AuthServer
import com.endiq.turtlelauncher.game.account.auth_server.data.AuthServerDao
import com.endiq.turtlelauncher.path.PathManager
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.ui.androidText
import com.endiq.turtlelauncher.utils.isInGreaterChina
import com.endiq.turtlelauncher.utils.logging.Logger
import com.endiq.turtlelauncher.utils.network.isNetworkAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

private const val TAG = "AccountManager"

object AccountsManager {
    private val scope = CoroutineScope(Dispatchers.IO)

    //Account-related
    private val _accounts = CopyOnWriteArrayList<Account>()
    private val _accountsFlow = MutableStateFlow<List<Account>>(emptyList())
    val accountsFlow = _accountsFlow.asStateFlow()

    private val _currentAccountFlow = MutableStateFlow<Account?>(null)
    val currentAccountFlow = _currentAccountFlow.asStateFlow()

    //Authentication servers
    private val _authServers = CopyOnWriteArrayList<AuthServer>()
    private val _authServersFlow = MutableStateFlow<List<AuthServer>>(emptyList())
    val authServersFlow = _authServersFlow.asStateFlow()

    private val _refreshWardrobe = MutableStateFlow(false)
    /** Controls refreshing all account wardrobes */
    val refreshWardrobe = _refreshWardrobe.asStateFlow()

    private val _isOffline = MutableStateFlow(false)
    val isOffline = _isOffline

    //Accounts already server-validated within this launcher session
    private val sessionValidatedAccounts: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private lateinit var database: AppDatabase
    private lateinit var accountDao: AccountDao
    private lateinit var authServerDao: AuthServerDao

    /**
     * Initializes the whole account system
     */
    fun initialize(context: Context) {
        database = AppDatabase.getInstance(context)
        accountDao = database.accountDao()
        authServerDao = database.authServerDao()
    }

    /**
     * Refreshes the logged-in accounts, which are kept in the database
     */
    fun reloadAccounts() {
        scope.launch {
            suspendReloadAccounts()
        }
    }

    /**
     * Refreshes all account wardrobes
     */
    fun refreshWardrobe() {
        _refreshWardrobe.update { !it }
    }

    private suspend fun suspendReloadAccounts() {
        val loadedAccounts = accountDao.getAllAccounts()
        _accounts.clear()
        _accounts.addAll(loadedAccounts)

        _accounts.sortWith(compareBy<Account>(
            { it.accountTypePriority() },
            { it.username },
        ))
        _accountsFlow.value = _accounts.toList()

        if (_accounts.isNotEmpty() && !isAccountExists(AllSettings.currentAccount.getValue())) {
            setCurrentAccountInternal(_accounts[0])
        }

        refreshCurrentAccountState()

        Logger.info(TAG, "Loaded ${_accounts.size} accounts")
    }

    /**
     * Refreshes the saved authentication servers, which are kept in the database
     */
    fun reloadAuthServers() {
        scope.launch {
            val loadedServers = authServerDao.getAllServers()
            _authServers.clear()
            _authServers.addAll(loadedServers)

            _authServers.sortWith { o1, o2 -> o1.serverName.compareTo(o2.serverName) }
            _authServersFlow.value = _authServers.toList()

            Logger.info(TAG, "Loaded ${_authServers.size} auth servers")
        }
    }

    /**
     * Executes the login
     */
    fun performLogin(
        context: Context,
        account: Account,
        onSuccess: suspend (Account, task: Task) -> Unit = { _, _ -> },
        onFailed: (th: Throwable) -> Unit = {}
    ) {
        val task = performLoginTask(context, account, onSuccess, onFailed)
        task?.let { TaskSystem.submitTask(it) }
    }

    /**
     * Returns the login task object
     */
    fun performLoginTask(
        context: Context,
        account: Account,
        onSuccess: suspend (Account, task: Task) -> Unit = { _, _ -> },
        onFailed: (th: Throwable) -> Unit = {},
        onFinally: () -> Unit = {}
    ): Task? =
        when {
            account.isNoLoginRequired() -> null
            account.isAuthServerAccount() -> {
                otherLogin(context = context, account = account, onSuccess = onSuccess, onFailed = onFailed, onFinally = onFinally)
            }
            account.isMicrosoftAccount() -> {
                microsoftRefresh(account = account, onSuccess = onSuccess, onFailed = onFailed, onFinally = onFinally)
            }
            else -> null
        }

    /**
     * Refreshes an account
     */
    fun refreshAccount(
        context: Context,
        account: Account,
        onFailed: (th: Throwable) -> Unit = {},
    ) {
        if (isNetworkAvailable(context)) {
            performLogin(
                context = context,
                account = account,
                onSuccess = { account, task ->
                    task.updateMessage(androidText(R.string.account_logging_in_saving))
                    account.downloadYggdrasil()
                    markSessionValidated(account)
                    suspendSaveAccount(account)
                },
                onFailed = onFailed
            )
        }
    }

    /**
     * Whether the account has passed server validation in this session
     */
    fun isSessionValidated(account: Account): Boolean =
        sessionValidatedAccounts.contains(account.uniqueUUID)

    fun markSessionValidated(account: Account) {
        sessionValidatedAccounts.add(account.uniqueUUID)
    }

    /**
     * Whether the pre-launch account validation is needed
     */
    fun isLaunchCheckNeeded(account: Account): Boolean = when {
        account.isNoLoginRequired() -> false
        account.isMicrosoftAccount() -> !isSessionValidated(account) ||
                System.currentTimeMillis() > account.expiresAt - 5 * 60 * 1000
        else -> !isSessionValidated(account)
    }

    /**
     * Returns the current logged-in account
     */
    private fun getCurrentAccount(): Account? {
        return _accounts.find {
            it.uniqueUUID == AllSettings.currentAccount.getValue()
        } ?: _accounts.firstOrNull()
    }

    /**
     * Sets and persists the current account
     */
    fun setCurrentAccount(account: Account) {
        setCurrentAccountInternal(account)
        refreshCurrentAccountState()
    }

    private fun setCurrentAccountInternal(account: Account) {
        AllSettings.currentAccount.save(account.uniqueUUID)
    }

    /**
     * Refreshes the current account, also refreshing the genuine status for non-mainland-China regions
     */
    private fun refreshCurrentAccountState() {
        val currentAccount = getCurrentAccount()
        val isOffline = checkLimit()
        _currentAccountFlow.update {
            //Refuse account use while the status is non-genuine
            if (isOffline) null else currentAccount
        }
        _isOffline.update { isOffline }
    }

    private fun checkLimit(): Boolean {
        val circumventLimit = File(PathManager.DIR_FILES_EXTERNAL, "circumventLimit")
        return !circumventLimit.exists() && !isInGreaterChina() && !hasMicrosoftAccount()
    }

    /**
     * Saves the account to the database
     */
    fun saveAccount(account: Account) {
        scope.launch {
            suspendSaveAccount(account)
        }
    }

    /**
     * Saves the account to the database
     */
    suspend fun suspendSaveAccount(account: Account) {
        runCatching {
            accountDao.saveAccount(account)
            Logger.info(TAG, "Saved account: ${account.username}")
            //Also set it as the current account
            setCurrentAccountInternal(account)
        }.onFailure { e ->
            Logger.error(TAG, "Failed to save account: ${account.username}", e)
        }
        suspendReloadAccounts()
    }

    /**
     * Deletes the account from the database and refreshes
     */
    fun deleteAccount(account: Account) {
        scope.launch {
            accountDao.deleteAccount(account)
            val skinFile = account.getSkinFile()
            FileUtils.deleteQuietly(skinFile)
            suspendReloadAccounts()
        }
    }

    /**
     * Saves the authentication server to the database
     */
    suspend fun saveAuthServer(server: AuthServer) {
        runCatching {
            authServerDao.saveServer(server)
            Logger.info(TAG, "Saved auth server: ${server.serverName} -> ${server.baseUrl}")
        }.onFailure { e ->
            Logger.error(TAG, "Failed to save auth server: ${server.serverName}", e)
        }
        reloadAuthServers()
    }

    /**
     * Deletes the authentication server from the database and refreshes
     */
    fun deleteAuthServer(server: AuthServer) {
        scope.launch {
            authServerDao.deleteServer(server)
            reloadAuthServers()
        }
    }

    /**
     * Whether a Microsoft account has ever been logged in
     */
    fun hasMicrosoftAccount(): Boolean = _accounts.any { it.isMicrosoftAccount() }

    /**
     * Reads an account by its profileId
     */
    fun loadFromProfileID(
        profileId: String,
        accountType: String? = null
    ): Account? =
        _accounts.find { it.profileId == profileId && it.accountType == accountType }

    /**
     * Whether the account exists
     */
    fun isAccountExists(uniqueUUID: String): Boolean {
        return uniqueUUID.isNotEmpty() && _accounts.any { it.uniqueUUID == uniqueUUID }
    }

    /**
     * Whether the authentication server exists
     */
    fun isAuthServerExists(baseUrl: String): Boolean {
        return baseUrl.isNotEmpty() && _authServers.any { it.baseUrl == baseUrl }
    }
}