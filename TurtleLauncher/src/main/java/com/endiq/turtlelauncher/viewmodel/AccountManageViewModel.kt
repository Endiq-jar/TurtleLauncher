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
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.context.copyLocalFile
import com.endiq.turtlelauncher.coroutine.Task
import com.endiq.turtlelauncher.coroutine.TaskSystem
import com.endiq.turtlelauncher.game.account.Account
import com.endiq.turtlelauncher.game.account.AccountsManager
import com.endiq.turtlelauncher.game.account.accountErrorText
import com.endiq.turtlelauncher.game.account.addOtherServer
import com.endiq.turtlelauncher.game.account.auth_server.AuthServerHelper
import com.endiq.turtlelauncher.game.account.auth_server.data.AuthServer
import com.endiq.turtlelauncher.game.account.isLocalAccount
import com.endiq.turtlelauncher.game.account.isMicrosoftAccount
import com.endiq.turtlelauncher.game.account.isReloginRequired
import com.endiq.turtlelauncher.game.account.localLogin
import com.endiq.turtlelauncher.game.account.microsoft.MINECRAFT_SERVICES_URL
import com.endiq.turtlelauncher.game.account.microsoftLogin
import com.endiq.turtlelauncher.game.account.refreshMicrosoft
import com.endiq.turtlelauncher.game.account.wardrobe.EmptyCape
import com.endiq.turtlelauncher.game.account.wardrobe.SkinModelType
import com.endiq.turtlelauncher.game.account.wardrobe.capeLocalRes
import com.endiq.turtlelauncher.game.account.wardrobe.getLocalUUIDWithSkinModel
import com.endiq.turtlelauncher.game.account.wardrobe.isSlimModel
import com.endiq.turtlelauncher.game.account.wardrobe.validateSkinFile
import com.endiq.turtlelauncher.game.account.yggdrasil.PlayerProfile
import com.endiq.turtlelauncher.game.account.yggdrasil.cacheAllCapes
import com.endiq.turtlelauncher.game.account.yggdrasil.changeCape
import com.endiq.turtlelauncher.game.account.yggdrasil.executeWithAuthorization
import com.endiq.turtlelauncher.game.account.yggdrasil.getFile
import com.endiq.turtlelauncher.game.account.yggdrasil.getPlayerProfile
import com.endiq.turtlelauncher.game.account.yggdrasil.uploadSkin
import com.endiq.turtlelauncher.path.PathManager
import com.endiq.turtlelauncher.ui.AndroidStringText
import com.endiq.turtlelauncher.ui.androidText
import com.endiq.turtlelauncher.ui.screens.content.elements.AccountOperation
import com.endiq.turtlelauncher.ui.screens.content.elements.AccountSkinOperation
import com.endiq.turtlelauncher.ui.screens.content.elements.ChangeCape
import com.endiq.turtlelauncher.ui.screens.content.elements.ChangeSkin
import com.endiq.turtlelauncher.ui.screens.content.elements.LocalLoginOperation
import com.endiq.turtlelauncher.ui.screens.content.elements.LoginMenuOperation
import com.endiq.turtlelauncher.ui.screens.content.elements.MicrosoftLoginOperation
import com.endiq.turtlelauncher.ui.screens.content.elements.OtherLoginOperation
import com.endiq.turtlelauncher.ui.screens.content.elements.ServerOperation
import com.endiq.turtlelauncher.utils.network.toLocal
import com.endiq.turtlelauncher.utils.string.getMessageOrToString
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.UUID
import io.ktor.client.plugins.ResponseException as KtorResponseException
import kotlinx.coroutines.flow.combine as kotlinxCombine

/**
 * Account management screen user intents (MVI Intents)
 * Encapsulates every action request the UI layer emits
 */
sealed interface AccountManageIntent {
    /** Summon the account login menu */
    data class UpdateLoginMenuOp(val operation: LoginMenuOperation) : AccountManageIntent

    data class UpdateMicrosoftLoginOp(val operation: MicrosoftLoginOperation) :
        AccountManageIntent

    data class UpdateLocalLoginOp(val operation: LocalLoginOperation) : AccountManageIntent
    data class UpdateOtherLoginOp(val operation: OtherLoginOperation) : AccountManageIntent
    data class UpdateServerOp(val operation: ServerOperation) : AccountManageIntent
    data class UpdateAccountOp(val operation: AccountOperation) : AccountManageIntent
    data class UpdateAccountSkinOp(val operation: AccountSkinOperation) :
        AccountManageIntent
    data class UpdatePendingSkinData(val skinState: ChangeSkin) :
        AccountManageIntent
    data class UpdatePendingCapeData(val capeState: ChangeCape) :
        AccountManageIntent
    data class OnSkinPicked(val uri: Uri) : AccountManageIntent
    data object ResetAccountSkinDialogState : AccountManageIntent


    /** Run the Microsoft login flow */
    data class PerformMicrosoftLogin(
        val toWeb: (url: String) -> Unit,
        val backToMain: () -> Unit,
        val checkIfInWebScreen: () -> Boolean
    ) : AccountManageIntent

    /** Apply the selected skin */
    data class ApplySkin(val account: Account, val file: File, val model: SkinModelType) : AccountManageIntent

    /** Internal intent: upload a skin after file import */
    data class UploadMicrosoftSkin(
        val account: Account,
        val skinFile: File,
        val skinModel: SkinModelType
    ) : AccountManageIntent

    /** Fetch the Microsoft capes available to the account */
    data class FetchMicrosoftCapes(
        val account: Account,
    ) : AccountManageIntent

    /** Apply the selected Microsoft cape */
    data class ApplyMicrosoftCape(
        val account: Account,
        val cape: PlayerProfile.Cape
    ) : AccountManageIntent

    /** Create a new offline account */
    data class CreateLocalAccount(val userName: String, val userUUID: String?) :
        AccountManageIntent

    /** Log in via a third-party authentication server */
    data class LoginWithOtherServer(
        val server: AuthServer,
        val email: String,
        val pass: String
    ) : AccountManageIntent

    /** Add a new Yggdrasil authentication server */
    data class AddServer(val url: String) : AccountManageIntent

    /** Delete the given authentication server */
    data class DeleteServer(val server: AuthServer) : AccountManageIntent

    /** Delete an account and its related data */
    data class DeleteAccount(val account: Account) : AccountManageIntent

    /** Refreshes an account's login credentials (Token) */
    data class RefreshAccount(val account: Account) : AccountManageIntent

    /** Re-login of an offline account with the new password after credentials expire */
    data class ReloginOtherAccount(
        val account: Account,
        val password: String
    ) : AccountManageIntent

    /** Reset the account's skin to default */
    data class ResetSkin(val account: Account) : AccountManageIntent
}

/**
 * Account management screen one-shot effects (MVI Effects)
 * For transient events like error dialogs
 */
sealed class AccountManageEffect {
    /** Show an error dialog on the UI layer */
    data class ShowError(val title: AndroidStringText, val message: AndroidStringText) : AccountManageEffect()
}

/**
 * Account management screen ViewModel
 * 
 * The core logic handler, turning intents into state updates or side effects.
 * Default {ApplicationContext} as the context avoids Activity lifecycle leaks.
 * 
 * @property context the global application context
 */
@HiltViewModel(assistedFactory = AccountManageViewModel.Factory::class)
class AccountManageViewModel @AssistedInject constructor(
    @Assisted private val eventViewModel: EventViewModel,
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(eventViewModel: EventViewModel): AccountManageViewModel
    }
    private val _loginMenuOp = MutableStateFlow<LoginMenuOperation>(LoginMenuOperation.None)

    private val _microsoftLoginOp =
        MutableStateFlow<MicrosoftLoginOperation>(MicrosoftLoginOperation.None)
    private val _localLoginOp = MutableStateFlow<LocalLoginOperation>(LocalLoginOperation.None)
    private val _otherLoginOp = MutableStateFlow<OtherLoginOperation>(OtherLoginOperation.None)
    private val _serverOp = MutableStateFlow<ServerOperation>(ServerOperation.None)
    private val _accountOp = MutableStateFlow<AccountOperation>(AccountOperation.None)
    private val _accountSkinOp = MutableStateFlow<AccountSkinOperation>(AccountSkinOperation.None)
    private val _accountSkinDialogState = MutableStateFlow(AccountSkinDialogState())
    private val _accountCapeOpMap = MutableStateFlow<Map<String, List<PlayerProfile.Cape>>>(emptyMap())

    private val _effect = Channel<AccountManageEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    /**
     * Unified state flows for login-related operations
     */
    val loginUiState: StateFlow<LoginUiState> = kotlinxCombine(
        _loginMenuOp,
        _microsoftLoginOp,
        _localLoginOp,
        _otherLoginOp
    ) { loginMenuOp, microsoftLoginOp, localLoginOp, otherLoginOp ->
        LoginUiState(
            menuOp = loginMenuOp,
            microsoftOp = microsoftLoginOp,
            localOp = localLoginOp,
            otherOp = otherLoginOp
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LoginUiState()
    )

    data class LoginUiState(
        val menuOp: LoginMenuOperation = LoginMenuOperation.None,
        val microsoftOp: MicrosoftLoginOperation = MicrosoftLoginOperation.None,
        val localOp: LocalLoginOperation = LocalLoginOperation.None,
        val otherOp: OtherLoginOperation = OtherLoginOperation.None
    )

    /**
     * Unified state flows for account data
     */
    val profileUiState: StateFlow<ProfileUiState> = kotlinxCombine(
        AccountsManager.accountsFlow,
        AccountsManager.currentAccountFlow,
        AccountsManager.authServersFlow,
        _accountCapeOpMap,
        AccountsManager.isOffline
    ) { accounts, currentAccount, authServers, accountCapeOpMap, isOffline ->
        ProfileUiState(
            accounts = accounts,
            currentAccount = currentAccount,
            authServers = authServers,
            accountCapeOpMap = accountCapeOpMap,
            isOffline = isOffline
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProfileUiState()
    )

    data class ProfileUiState(
        val accounts: List<Account> = emptyList(),
        val currentAccount: Account? = null,
        val authServers: List<AuthServer> = emptyList(),
        val accountCapeOpMap: Map<String, List<PlayerProfile.Cape>> = emptyMap(),
        val isOffline: Boolean = false
    )

    /**
     * State flow of the account skin change
     * @param pendingSkinData the skin to apply
     * @param pendingCapeData the cape to apply
     * @param importingSkin whether a skin file is being imported; handled dialog-side rather than through onIntent
     */
    data class AccountSkinDialogState(
        val pendingSkinData: ChangeSkin = ChangeSkin.None,
        val pendingCapeData: ChangeCape = ChangeCape.None,
        val importingSkin: Boolean = false
    )

    /**
     * Unified state flows for data-related operations
     */
    val operationUiState: StateFlow<OperationUiState> = kotlinxCombine(
        _serverOp,
        _accountOp,
        _accountSkinOp,
        _accountSkinDialogState
    ) { serverOp, accountOp, accountSkinOp, accountSkinDialogState ->
        OperationUiState(serverOp, accountOp, accountSkinOp, accountSkinDialogState)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = OperationUiState()
    )

    data class OperationUiState(
        val serverOp: ServerOperation = ServerOperation.None,
        val accountOp: AccountOperation = AccountOperation.None,
        val accountSkinOp: AccountSkinOperation = AccountSkinOperation.None,
        val accountSkinDialogState: AccountSkinDialogState = AccountSkinDialogState()
    )

    /**
     * Handles every Intent from the UI layer
     */
    fun onIntent(intent: AccountManageIntent) {
        when (intent) {
            is AccountManageIntent.UpdateLoginMenuOp ->
                _loginMenuOp.value = intent.operation

            is AccountManageIntent.UpdateMicrosoftLoginOp ->
                _microsoftLoginOp.value = intent.operation

            is AccountManageIntent.UpdateLocalLoginOp -> _localLoginOp.value = intent.operation
            is AccountManageIntent.UpdateOtherLoginOp -> _otherLoginOp.value = intent.operation
            is AccountManageIntent.UpdateServerOp -> _serverOp.value = intent.operation
            is AccountManageIntent.UpdateAccountOp -> _accountOp.value = intent.operation
            is AccountManageIntent.UpdateAccountSkinOp -> {
                _accountSkinOp.value = intent.operation
            }

            is AccountManageIntent.UpdatePendingSkinData -> {
                _accountSkinDialogState.update {
                    it.copy(
                        pendingSkinData = intent.skinState
                    )
                }
            }

            is AccountManageIntent.UpdatePendingCapeData -> {
                _accountSkinDialogState.update {
                    it.copy(
                        pendingCapeData = intent.capeState
                    )
                }
            }

            is AccountManageIntent.OnSkinPicked -> onSkinPicked(intent)
            is AccountManageIntent.ResetAccountSkinDialogState -> {
                _accountSkinDialogState.update { AccountSkinDialogState() }
            }

            is AccountManageIntent.PerformMicrosoftLogin -> performMicrosoftLogin(intent)
            is AccountManageIntent.ApplySkin ->
                applySkin(intent.account, intent.file, intent.model)

            is AccountManageIntent.UploadMicrosoftSkin -> uploadMicrosoftSkin(intent)
            is AccountManageIntent.FetchMicrosoftCapes -> fetchMicrosoftCapes(intent.account)
            is AccountManageIntent.ApplyMicrosoftCape -> applyMicrosoftCape(intent)
            is AccountManageIntent.CreateLocalAccount -> createLocalAccount(
                intent.userName,
                intent.userUUID
            )

            is AccountManageIntent.LoginWithOtherServer -> loginWithOtherServer(intent)
            is AccountManageIntent.AddServer -> addServer(intent.url)
            is AccountManageIntent.DeleteServer -> deleteServer(intent.server)
            is AccountManageIntent.DeleteAccount -> deleteAccount(intent.account)
            is AccountManageIntent.RefreshAccount -> refreshAccount(intent.account)
            is AccountManageIntent.ReloginOtherAccount -> reloginOtherAccount(intent)
            is AccountManageIntent.ResetSkin -> resetSkin(intent.account)
        }
    }

    /**
     * After a skin is picked, the VM layer validates the file first, then advances the dialog flow state
     */
    private fun onSkinPicked(intent: AccountManageIntent.OnSkinPicked) {
        viewModelScope.launch(Dispatchers.IO) {
            _accountSkinDialogState.update {
                it.copy(importingSkin = true)
            }

            val cacheFile = File(
                PathManager.DIR_IMAGE_CACHE,
                "skin_pick_${UUID.randomUUID()}"
            )

            runCatching {
                context.copyLocalFile(intent.uri, cacheFile)
                validateSkinFile(cacheFile)
            }.onSuccess { isValid ->
                if (!isValid) {
                    emitError(
                        androidText(R.string.generic_warning),
                        androidText(R.string.account_change_skin_invalid)
                    )
                    return@onSuccess
                }
                val recommendedModel = if (cacheFile.isSlimModel()) {
                    SkinModelType.ALEX
                } else {
                    SkinModelType.STEVE
                }

                _accountSkinDialogState.update {
                    it.copy(
                        pendingSkinData = ChangeSkin.ChangeSkinData(
                            cacheFile = cacheFile,
                            skinModel = recommendedModel
                        )
                    )
                }
            }.onFailure { th ->
                emitError(
                    androidText(R.string.account_change_skin_failed_to_import),
                    androidText(th.getMessageOrToString())
                )
            }

            _accountSkinDialogState.update {
                it.copy(importingSkin = false)
            }
        }
    }

    /** Internal: post an error notification */
    private fun emitError(title: AndroidStringText, message: AndroidStringText) {
        viewModelScope.launch(Dispatchers.Main) {
            _effect.send(AccountManageEffect.ShowError(title, message))
        }
    }

    /** Internal: post a Toast message */
    private fun emitToast(
        text: AndroidStringText,
        duration: Int = Toast.LENGTH_SHORT
    ) {
        eventViewModel.sendToast(text, duration)
    }

    /** Run the Microsoft login flow */
    private fun performMicrosoftLogin(intent: AccountManageIntent.PerformMicrosoftLogin) {
        microsoftLogin(
            context,
            intent.toWeb,
            intent.backToMain,
            intent.checkIfInWebScreen,
            { onIntent(AccountManageIntent.UpdateMicrosoftLoginOp(it)) },
            showToast = ::emitToast,
            { emitError(it.title, it.message) }
        )
        onIntent(AccountManageIntent.UpdateMicrosoftLoginOp(MicrosoftLoginOperation.None))
    }

    /** Apply the selected skin */
    private fun applySkin(account: Account, file: File, model: SkinModelType) {
        when {
            account.isLocalAccount() -> saveLocalSkin(account, file, model)
            account.isMicrosoftAccount() -> importSkinFile(account, file, model)
        }
    }

    /** Handle skin file import */
    private fun importSkinFile(account: Account, file: File, model: SkinModelType) {
        TaskSystem.submitTask(
            Task.runTask(
                id = account.uniqueUUID,
                dispatcher = Dispatchers.IO,
                task = {
                    if (validateSkinFile(file)) {
                        onIntent(
                            AccountManageIntent.UploadMicrosoftSkin(
                                account = account,
                                skinFile = file,
                                skinModel = model
                            )
                        )
                    } else {
                        FileUtils.deleteQuietly(file)
                        emitError(
                            androidText(R.string.generic_warning),
                            androidText(R.string.account_change_skin_invalid)
                        )
                    }
                },
                onError = { th ->
                    FileUtils.deleteQuietly(file)
                    emitError(
                        androidText(R.string.account_change_skin_failed_to_import),
                        androidText(th.getMessageOrToString())
                    )
                }
            )
        )
    }

    /** Upload the Microsoft skin */
    private fun uploadMicrosoftSkin(intent: AccountManageIntent.UploadMicrosoftSkin) {
        val account = intent.account
        val skinFile = intent.skinFile
        val skinModel = intent.skinModel

        TaskSystem.submitTask(
            Task.runTask(
                dispatcher = Dispatchers.IO,
                task = { task ->
                    executeWithAuthorization(block = {
                        task.updateProgress(-1f)
                        task.updateMessage(androidText(R.string.account_change_skin_uploading))
                        uploadSkin(MINECRAFT_SERVICES_URL, account.accessToken, skinFile, skinModel)
                    }, onRefreshRequest = {
                        account.refreshMicrosoft(task = task, coroutineContext = coroutineContext)
                        AccountsManager.suspendSaveAccount(account)
                    })

                    task.updateMessage(androidText(R.string.account_change_skin_update_local))
                    runCatching {
                        account.downloadYggdrasil()
                    }.onFailure { th ->
                        emitError(
                            androidText(R.string.account_logging_in_failed),
                            formatAccountError(th)
                        )
                    }

                    emitToast(
                        androidText(R.string.account_change_skin_update_toast),
                        duration = Toast.LENGTH_LONG
                    )
                },
                onError = { th ->
                    when {
                        th.isReloginRequired() -> {
                            onIntent(
                                AccountManageIntent.UpdateAccountOp(
                                    AccountOperation.OnRelogin(account)
                                )
                            )
                        }
                        th is KtorResponseException -> {
                            emitError(
                                androidText(
                                    R.string.account_change_skin_failed_to_upload,
                                    th.response.status.value
                                ),
                                th.toLocal()
                            )
                        }
                        else -> {
                            emitError(
                                androidText(R.string.generic_error),
                                formatAccountError(th)
                            )
                        }
                    }
                }
            )
        )
    }

    /** Fetch the Microsoft cape list */
    private fun fetchMicrosoftCapes(account: Account) {
        TaskSystem.submitTask(
            Task.runTask(
                id = account.uniqueUUID,
                dispatcher = Dispatchers.IO,
                task = { task ->
                    executeWithAuthorization(block = {
                        task.updateProgress(-1f)
                        task.updateMessage(androidText(R.string.account_change_cape_fetch_all))
                        val profile = getPlayerProfile(MINECRAFT_SERVICES_URL, account.accessToken)
                        task.updateProgress(-1f)
                        task.updateMessage(androidText(R.string.account_change_cape_cache_all))
                        cacheAllCapes(profile)
                        //Also update the local skin/cape
                        account.downloadYggdrasil()
                        _accountCapeOpMap.update { it + (account.uniqueUUID to profile.capes) }
                    }, onRefreshRequest = {
                        account.refreshMicrosoft(task = task, coroutineContext = coroutineContext)
                        AccountsManager.suspendSaveAccount(account)
                    })
                },
                onError = { th ->
                    if (th.isReloginRequired()) {
                        onIntent(
                            AccountManageIntent.UpdateAccountOp(
                                AccountOperation.OnRelogin(account)
                            )
                        )
                    } else {
                        emitError(
                            androidText(R.string.account_change_cape_fetch_all_failed),
                            androidText(th.getMessageOrToString())
                        )
                    }
                }
            )
        )
    }

    /** Change the Microsoft account cape */
    private fun applyMicrosoftCape(intent: AccountManageIntent.ApplyMicrosoftCape) {
        val account = intent.account
        val cape = intent.cape
        val capeId = cape.id
        val isReset = cape == EmptyCape

        TaskSystem.submitTask(
            Task.runTask(
                id = account.uniqueUUID + "_cape",
                dispatcher = Dispatchers.IO,
                task = { task ->
                    executeWithAuthorization(block = {
                        task.updateMessage(androidText(R.string.account_change_cape_apply))
                        changeCape(
                            MINECRAFT_SERVICES_URL,
                            account.accessToken,
                            capeId
                        )
                    }, onRefreshRequest = {
                        account.refreshMicrosoft(task = task, coroutineContext = coroutineContext)
                        AccountsManager.suspendSaveAccount(account)
                    })

                    val capeFile = cape.getFile(PathManager.DIR_ACCOUNT_CAPE)
                    val targetCape = account.getCapeFile()
                    FileUtils.deleteQuietly(targetCape)
                    if (!isReset && capeFile.exists()) {
                        runCatching {
                            capeFile.copyTo(targetCape)
                        }
                    }

                    AccountsManager.refreshWardrobe()

                    _accountCapeOpMap.update { capesMap ->
                        if (!capesMap.containsKey(account.uniqueUUID)) return@update capesMap
                        buildMap {
                            capesMap.forEach { (accountId, capes) ->
                                if (accountId == account.uniqueUUID) {
                                    val newList = capes.map { cape ->
                                        when {
                                            cape.id == capeId -> cape.copy(state = "ACTIVE")
                                            cape.state == "ACTIVE" -> cape.copy(state = "INACTIVE")
                                            else -> cape
                                        }
                                    }
                                    put(accountId, newList)
                                } else put(accountId, capes)
                            }
                        }
                    }

                    if (isReset) {
                        emitToast(androidText(R.string.account_change_cape_apply_reset))
                    } else {
                        val capeName = cape.capeLocalRes()?.let { localRes ->
                            androidText(localRes)
                        } ?: androidText(cape.alias)
                        emitToast(
                            androidText(
                                R.string.account_change_cape_apply_success,
                                capeName
                            )
                        )
                    }
                },
                onError = { th ->
                    when {
                        th.isReloginRequired() -> {
                            onIntent(
                                AccountManageIntent.UpdateAccountOp(
                                    AccountOperation.OnRelogin(account)
                                )
                            )
                        }
                        th is KtorResponseException -> {
                            emitError(
                                androidText(
                                    R.string.account_change_cape_apply_failed,
                                    th.response.status.value
                                ),
                                th.toLocal()
                            )
                        }
                        else -> {
                            emitError(
                                androidText(R.string.generic_error),
                                formatAccountError(th)
                            )
                        }
                    }
                }
            )
        )
    }

    /** Create an offline account */
    private fun createLocalAccount(userName: String, userUUID: String?) {
        localLogin(userName, userUUID)
        onIntent(AccountManageIntent.UpdateLocalLoginOp(LocalLoginOperation.None))
    }

    /** Third-party Yggdrasil server login */
    private fun loginWithOtherServer(intent: AccountManageIntent.LoginWithOtherServer) {
        AuthServerHelper(intent.server, intent.email, intent.pass, onSuccess = { account, task ->
            task.updateMessage(androidText(R.string.account_logging_in_saving))
            account.downloadYggdrasil()
            AccountsManager.suspendSaveAccount(account)
        }, onFailed = {
            onIntent(AccountManageIntent.UpdateOtherLoginOp(OtherLoginOperation.OnFailed(it)))
        }).createNewAccount(context) { profiles, select ->
            onIntent(
                AccountManageIntent.UpdateOtherLoginOp(
                    OtherLoginOperation.SelectRole(profiles, select)
                )
            )
        }
    }

    /** Add a custom authentication server */
    private fun addServer(url: String) {
        addOtherServer(url) {
            onIntent(AccountManageIntent.UpdateServerOp(ServerOperation.OnThrowable(it)))
        }
        onIntent(AccountManageIntent.UpdateServerOp(ServerOperation.None))
    }

    private fun deleteServer(server: AuthServer) {
        AccountsManager.deleteAuthServer(server)
        onIntent(AccountManageIntent.UpdateServerOp(ServerOperation.None))
    }

    private fun deleteAccount(account: Account) {
        AccountsManager.deleteAccount(account)
        onIntent(AccountManageIntent.UpdateAccountOp(AccountOperation.None))
    }

    /** Force-refresh the account's credentials */
    private fun refreshAccount(account: Account) {
        AccountsManager.refreshAccount(context, account) { th ->
            onIntent(
                AccountManageIntent.UpdateAccountOp(
                    if (th.isReloginRequired()) {
                        AccountOperation.OnRelogin(account)
                    } else {
                        AccountOperation.OnFailed(th)
                    }
                )
            )
        }
    }

    /** Re-login of an offline account with the new password after credentials expire */
    private fun reloginOtherAccount(intent: AccountManageIntent.ReloginOtherAccount) {
        val account = intent.account
        AuthServerHelper(
            baseUrl = account.otherBaseUrl!!,
            serverName = account.accountType!!,
            email = account.otherAccount!!,
            password = intent.password,
            onSuccess = { acc, task ->
                task.updateMessage(androidText(R.string.account_logging_in_saving))
                acc.downloadYggdrasil()
                AccountsManager.markSessionValidated(acc)
                AccountsManager.suspendSaveAccount(acc)
                onIntent(AccountManageIntent.UpdateAccountOp(AccountOperation.None))
            },
            onFailed = { th ->
                onIntent(
                    AccountManageIntent.UpdateAccountOp(
                        AccountOperation.OnRelogin(account, error = th)
                    )
                )
            }
        ).let { helper ->
            TaskSystem.submitTask(helper.justLogin(context, account))
        }
    }

    /** Save the offline account skin locally */
    private fun saveLocalSkin(account: Account, file: File, model: SkinModelType) {
        val skinFile = account.getSkinFile()

        TaskSystem.submitTask(Task.runTask(dispatcher = Dispatchers.IO, task = {
            if (validateSkinFile(file)) {
                account.skinModelType = model
                account.profileId = getLocalUUIDWithSkinModel(account.username, model)
                file.copyTo(skinFile, true)
                FileUtils.deleteQuietly(file)
                AccountsManager.suspendSaveAccount(account)
                AccountsManager.refreshWardrobe()
                onIntent(
                    AccountManageIntent.UpdateAccountSkinOp(
                        AccountSkinOperation.None
                    )
                )
            } else {
                emitError(
                    androidText(R.string.generic_warning),
                    androidText(R.string.account_change_skin_invalid)
                )
                onIntent(
                    AccountManageIntent.UpdateAccountSkinOp(
                        AccountSkinOperation.None
                    )
                )
            }
        }, onError = { th ->
            FileUtils.deleteQuietly(file)
            emitError(androidText(R.string.error_import_image), androidText(th.getMessageOrToString()))
            AccountsManager.refreshWardrobe()
            onIntent(
                AccountManageIntent.UpdateAccountSkinOp(
                    AccountSkinOperation.None
                )
            )
        }))
    }

    /** Reset skin data */
    private fun resetSkin(account: Account) {
        TaskSystem.submitTask(Task.runTask(dispatcher = Dispatchers.IO, task = {
            account.apply {
                FileUtils.deleteQuietly(getSkinFile())
                skinModelType = SkinModelType.NONE
                profileId = getLocalUUIDWithSkinModel(username, skinModelType)
                AccountsManager.suspendSaveAccount(this)
                AccountsManager.refreshWardrobe()
            }
        }))
        onIntent(
            AccountManageIntent.UpdateAccountSkinOp(
                AccountSkinOperation.None
            )
        )
    }

    /**
     * Unifies many exception types into a user-readable localized string.
     *
     * @param th the caught exception
     * @return the formatted error message
     */
    fun formatAccountError(th: Throwable): AndroidStringText = accountErrorText(th)
}
