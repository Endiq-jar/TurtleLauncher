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

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.nonInteractiveScrollbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.context.COPY_LABEL_ACCOUNT_UUID
import com.endiq.turtlelauncher.game.account.Account
import com.endiq.turtlelauncher.game.account.AccountsManager
import com.endiq.turtlelauncher.game.account.auth_server.data.AuthServer
import com.endiq.turtlelauncher.game.account.isAuthServerAccount
import com.endiq.turtlelauncher.game.account.isMicrosoftAccount
import com.endiq.turtlelauncher.game.account.isMicrosoftLogging
import com.endiq.turtlelauncher.game.account.yggdrasil.PlayerProfile
import com.endiq.turtlelauncher.ui.AndroidStringText
import com.endiq.turtlelauncher.ui.androidText
import com.endiq.turtlelauncher.ui.base.BaseScreen
import com.endiq.turtlelauncher.ui.components.BackgroundCard
import com.endiq.turtlelauncher.ui.components.MarqueeText
import com.endiq.turtlelauncher.ui.components.ModelAnimation
import com.endiq.turtlelauncher.ui.components.PlayerSkin
import com.endiq.turtlelauncher.ui.components.ScalingActionButton
import com.endiq.turtlelauncher.ui.components.ScalingLabel
import com.endiq.turtlelauncher.ui.components.SimpleAlertDialog
import com.endiq.turtlelauncher.ui.components.SimpleEditDialog
import com.endiq.turtlelauncher.ui.components.SimpleListDialog
import com.endiq.turtlelauncher.ui.components.SimpleListItem
import com.endiq.turtlelauncher.ui.screens.NormalNavKey
import com.endiq.turtlelauncher.ui.screens.content.elements.AccountItem
import com.endiq.turtlelauncher.ui.screens.content.elements.AccountOperation
import com.endiq.turtlelauncher.ui.screens.content.elements.AccountSkinOperation
import com.endiq.turtlelauncher.ui.screens.content.elements.ChangeSkinDialog
import com.endiq.turtlelauncher.ui.screens.content.elements.LocalLoginDialog
import com.endiq.turtlelauncher.ui.screens.content.elements.LocalLoginOperation
import com.endiq.turtlelauncher.ui.screens.content.elements.LoginMenuDialog
import com.endiq.turtlelauncher.ui.screens.content.elements.LoginMenuOperation
import com.endiq.turtlelauncher.ui.screens.content.elements.MicrosoftLoginOperation
import com.endiq.turtlelauncher.ui.screens.content.elements.MicrosoftLoginTipDialog
import com.endiq.turtlelauncher.ui.screens.content.elements.MicrosoftReloginDialog
import com.endiq.turtlelauncher.ui.screens.content.elements.OtherAccountReloginDialog
import com.endiq.turtlelauncher.ui.screens.content.elements.OtherLoginOperation
import com.endiq.turtlelauncher.ui.screens.content.elements.OtherServerLoginDialog
import com.endiq.turtlelauncher.ui.screens.content.elements.ServerOperation
import com.endiq.turtlelauncher.utils.animation.swapAnimateDpAsState
import com.endiq.turtlelauncher.utils.copyText
import com.endiq.turtlelauncher.utils.string.getMessageOrToString
import com.endiq.turtlelauncher.viewmodel.AccountManageEffect
import com.endiq.turtlelauncher.viewmodel.AccountManageIntent
import com.endiq.turtlelauncher.viewmodel.AccountManageViewModel
import com.endiq.turtlelauncher.viewmodel.ErrorViewModel
import com.endiq.turtlelauncher.viewmodel.EventViewModel
import com.endiq.turtlelauncher.viewmodel.LocalBackgroundViewModel
import com.endiq.turtlelauncher.viewmodel.ScreenBackStackViewModel

/**
 * Callbacks encapsulating the account screen's UI interactions
 * 
 * @property onIntent sends MVI Intents to the ViewModel
 * @property openLink opens external links
 * @property backToMainScreen goes back to the home screen
 * @property navigateToWeb navigates to the in-app browser screen
 * @property checkIfInWebScreen whether we're on the browser screen (Microsoft login logic)
 * @property formatError formats an exception into a localized string
 * @property submitError submits errors to the global error display
 */
private data class AccountActions(
    val onIntent: (AccountManageIntent) -> Unit,
    val openLink: (url: String) -> Unit,
    val backToMainScreen: () -> Unit,
    val navigateToWeb: (url: String) -> Unit,
    val checkIfInWebScreen: () -> Boolean,
    val formatError: (Throwable) -> AndroidStringText,
    val submitError: (ErrorViewModel.ThrowableMessage) -> Unit,
)

/**
 * Options to auto-open a login menu when entering the account manager
 */
enum class FirstLoginMenu {
    /** Don't open any menu */
    NONE,
    /** Open the Microsoft login menu */
    MICROSOFT,
    /** Open the combined login menu */
    NORMAL
}

/**
 * Account management main screen
 *
 * @param backStackViewModel the screen back stack manager
 * @param backToMainScreen back-to-home callback
 * @param openLink external link callback
 * @param submitError global error submission callback
 */
@Composable
fun AccountManageScreen(
    key: NormalNavKey.AccountManager,
    backStackViewModel: ScreenBackStackViewModel,
    backToMainScreen: () -> Unit,
    openLink: (url: String) -> Unit,
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit,
    eventViewModel: EventViewModel
) {
    val viewModel: AccountManageViewModel = hiltViewModel { factory: AccountManageViewModel.Factory ->
        factory.create(eventViewModel)
    }

    val loginUiState by viewModel.loginUiState.collectAsStateWithLifecycle()
    val profileUiState by viewModel.profileUiState.collectAsStateWithLifecycle()
    val operationUiState by viewModel.operationUiState.collectAsStateWithLifecycle()

    val actions = remember(
        viewModel,
        backToMainScreen,
        openLink,
        backStackViewModel,
        submitError
    ) {
        AccountActions(
            onIntent = viewModel::onIntent,
            openLink = openLink,
            backToMainScreen = backToMainScreen,
            navigateToWeb = { url -> backStackViewModel.mainScreen.backStack.navigateToWeb(url) },
            checkIfInWebScreen = { backStackViewModel.mainScreen.currentKey is NormalNavKey.WebScreen },
            formatError = { th -> viewModel.formatAccountError(th) },
            submitError = submitError,
        )
    }

    LaunchedEffect(Unit) {
        when (key.loginMenu) {
            FirstLoginMenu.NONE -> {}
            FirstLoginMenu.MICROSOFT -> {
                actions.onIntent(AccountManageIntent.UpdateMicrosoftLoginOp(MicrosoftLoginOperation.Tip))
            }
            FirstLoginMenu.NORMAL -> {
                actions.onIntent(AccountManageIntent.UpdateLoginMenuOp(LoginMenuOperation.Login))
            }
        }

        viewModel.effect.collect { effect ->
            when (effect) {
                is AccountManageEffect.ShowError -> {
                    submitError(ErrorViewModel.ThrowableMessage(effect.title, effect.message))
                }
            }
        }
    }

    BaseScreen(
        screenKey = key,
        currentKey = backStackViewModel.mainScreen.currentKey
    ) { isVisible ->
        AccountManageContent(
            isVisible = isVisible,
            loginUiState = loginUiState,
            profileUiState = profileUiState,
            operationUiState = operationUiState,
            actions = actions
        )
    }
}

/**
 * Account management screen content layout
 */
@Composable
private fun AccountManageContent(
    isVisible: Boolean,
    loginUiState: AccountManageViewModel.LoginUiState,
    profileUiState: AccountManageViewModel.ProfileUiState,
    operationUiState: AccountManageViewModel.OperationUiState,
    actions: AccountActions,
) {
    Row(
        modifier = Modifier.fillMaxSize()
    ) {
        ActionsLayout(
            isVisible = isVisible,
            modifier = Modifier
                .fillMaxHeight()
                .padding(all = 12.dp)
                .weight(3f),
            currentAccount = profileUiState.currentAccount,
            isOffline = profileUiState.isOffline,
            actions = actions
        )

        AccountsLayout(
            isVisible = isVisible,
            modifier = Modifier
                .fillMaxHeight()
                .padding(top = 12.dp, end = 12.dp, bottom = 12.dp)
                .weight(7f),
            accounts = profileUiState.accounts,
            currentAccount = profileUiState.currentAccount,
            isOffline = profileUiState.isOffline,
            accountOperation = operationUiState.accountOp,
            accountSkinOperation = operationUiState.accountSkinOp,
            accountSkinDialogState = operationUiState.accountSkinDialogState,
            accountCapes = profileUiState.accountCapeOpMap,
            actions = actions
        )
    }

    LoginMenuOperation(loginUiState.menuOp, actions, profileUiState.authServers)
    MicrosoftLoginOperation(loginUiState.microsoftOp, actions)
    LocalLoginOperation(loginUiState.localOp, actions)
    OtherLoginOperation(loginUiState.otherOp, actions)
    ServerTypeOperation(operationUiState.serverOp, actions)
}

/**
 * Left login-method menu component
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActionsLayout(
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    currentAccount: Account?,
    isOffline: Boolean,
    actions: AccountActions
) {
    val xOffset by swapAnimateDpAsState(
        targetValue = (-40).dp,
        swapIn = isVisible,
        isHorizontal = true
    )

    Column(
        modifier = modifier
            .offset { IntOffset(x = xOffset.roundToPx(), y = 0) }
            .fillMaxHeight()
    ) {
        //Player model preview
        val refreshWardrobe by AccountsManager.refreshWardrobe.collectAsStateWithLifecycle()
        val accountSkin = remember(currentAccount, refreshWardrobe) {
            currentAccount?.getSkinFile()?.takeIf { it.exists() }
        }
        val accountCape = remember(currentAccount, refreshWardrobe) {
            currentAccount?.getCapeFile()?.takeIf { it.exists() }
        }
        val context = LocalContext.current
        val playerSkin = remember {
            PlayerSkin(context)
        }
        var pageFinished by remember { mutableStateOf(false) }

        DisposableEffect(Unit) {
            onDispose {
                playerSkin.destroy()
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    playerSkin.loadWebView(
                        context = context,
                        onPageFinished = {
                            pageFinished = true
                            playerSkin.startAnim(ModelAnimation.NewIdle)
                            playerSkin.setAzimuthAndPitch(-35, 10)
                        }
                    )
                },
                update = {
                    if (pageFinished) {
                        runCatching {
                            accountSkin?.inputStream().use { inputStream ->
                                playerSkin.loadSkin(inputStream, currentAccount?.skinModelType)
                            }
                        }
                        runCatching {
                            accountCape?.inputStream().use { inputStream ->
                                playerSkin.loadCape(inputStream)
                            }
                        }
                    }
                }
            )
            if (!pageFinished) {
                LoadingIndicator()
            }
        }

        //Add an account
        ScalingActionButton(
            modifier = Modifier
                .fillMaxWidth(),
            onClick = {
                if (isOffline) {
                    //Non-genuine state: only Microsoft accounts may be created
                    actions.onIntent(AccountManageIntent.UpdateMicrosoftLoginOp(MicrosoftLoginOperation.Tip))
                } else {
                    actions.onIntent(AccountManageIntent.UpdateLoginMenuOp(LoginMenuOperation.Login))
                }
            }
        ) {
            MarqueeText(text = stringResource(R.string.account_add_new_account))
        }
    }
}

@Composable
private fun LoginMenuOperation(
    operation: LoginMenuOperation,
    actions: AccountActions,
    authServers: List<AuthServer>
) {
    when (operation) {
        LoginMenuOperation.None -> {}
        LoginMenuOperation.Login -> {
            LoginMenuDialog(
                onDismissRequest = {
                    actions.onIntent(
                        AccountManageIntent.UpdateLoginMenuOp(LoginMenuOperation.None)
                    )
                },
                authServers = authServers,
                onMicrosoftLogin = {
                    if (!isMicrosoftLogging()) {
                        actions.onIntent(
                            AccountManageIntent.UpdateMicrosoftLoginOp(
                                MicrosoftLoginOperation.Tip
                            )
                        )
                    }
                },
                onLocalLogin = {
                    actions.onIntent(AccountManageIntent.UpdateLocalLoginOp(LocalLoginOperation.Edit))
                },
                onAuthServerLogin = { server ->
                    actions.onIntent(
                        AccountManageIntent.UpdateOtherLoginOp(
                            OtherLoginOperation.OnLogin(server)
                        )
                    )
                },
                onAddAuthServer = {
                    actions.onIntent(AccountManageIntent.UpdateServerOp(ServerOperation.AddNew))
                },
                onDeleteAuthServer = { server ->
                    actions.onIntent(
                        AccountManageIntent.UpdateServerOp(
                            ServerOperation.Delete(server)
                        )
                    )
                }
            )
        }
    }
}

/**
 * Microsoft login logic handling
 */
@Composable
private fun MicrosoftLoginOperation(
    operation: MicrosoftLoginOperation,
    actions: AccountActions
) {
    when (operation) {
        is MicrosoftLoginOperation.None -> {}
        is MicrosoftLoginOperation.Tip -> {
            MicrosoftLoginTipDialog(
                onDismissRequest = {
                    actions.onIntent(
                        AccountManageIntent.UpdateMicrosoftLoginOp(
                            MicrosoftLoginOperation.None
                        )
                    )
                },
                onConfirm = {
                    actions.onIntent(
                        AccountManageIntent.UpdateMicrosoftLoginOp(
                            MicrosoftLoginOperation.None
                        )
                    )
                    actions.onIntent(
                        AccountManageIntent.PerformMicrosoftLogin(
                            toWeb = actions.navigateToWeb,
                            backToMain = actions.backToMainScreen,
                            checkIfInWebScreen = actions.checkIfInWebScreen
                        )
                    )
                },
                openLink = actions.openLink
            )
        }
    }
}

/**
 * Offline account login logic handling
 */
@Composable
private fun LocalLoginOperation(
    operation: LocalLoginOperation,
    actions: AccountActions
) {
    when (operation) {
        is LocalLoginOperation.None -> {}
        is LocalLoginOperation.Edit -> {
            LocalLoginDialog(
                onDismissRequest = {
                    actions.onIntent(
                        AccountManageIntent.UpdateLocalLoginOp(
                            LocalLoginOperation.None
                        )
                    )
                },
                onConfirm = { isInvalid, name, uuid ->
                    val nextOp = if (isInvalid) LocalLoginOperation.Alert(
                        name,
                        uuid
                    ) else LocalLoginOperation.Create(name, uuid)
                    actions.onIntent(AccountManageIntent.UpdateLocalLoginOp(nextOp))
                },
                openLink = actions.openLink
            )
        }

        is LocalLoginOperation.Create -> {
            LaunchedEffect(operation) {
                actions.onIntent(
                    AccountManageIntent.CreateLocalAccount(
                        operation.userName,
                        operation.userUUID
                    )
                )
            }
        }

        is LocalLoginOperation.Alert -> {
            SimpleAlertDialog(
                title = stringResource(R.string.account_supporting_username_invalid_title),
                text = {
                    Text(text = stringResource(R.string.account_supporting_username_invalid_local_message_hint1))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.account_supporting_username_invalid_local_message_hint2),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = stringResource(R.string.account_supporting_username_invalid_local_message_hint3))
                    Text(text = stringResource(R.string.account_supporting_username_invalid_local_message_hint4))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.account_supporting_username_invalid_local_message_hint5),
                        fontWeight = FontWeight.Bold
                    )
                },
                confirmText = stringResource(R.string.account_supporting_username_invalid_still_use),
                onConfirm = {
                    actions.onIntent(
                        AccountManageIntent.UpdateLocalLoginOp(
                            LocalLoginOperation.Create(operation.userName, operation.userUUID)
                        )
                    )
                },
                onCancel = {
                    actions.onIntent(
                        AccountManageIntent.UpdateLocalLoginOp(
                            LocalLoginOperation.None
                        )
                    )
                }
            )
        }
    }
}

/**
 * Third-party auth server login logic handling
 */
@Composable
private fun OtherLoginOperation(
    operation: OtherLoginOperation,
    actions: AccountActions
) {
    when (operation) {
        is OtherLoginOperation.None -> {}
        is OtherLoginOperation.OnLogin -> {
            OtherServerLoginDialog(
                server = operation.server,
                onRegisterClick = { url ->
                    actions.openLink(url)
                    actions.onIntent(AccountManageIntent.UpdateOtherLoginOp(OtherLoginOperation.None))
                },
                onDismissRequest = {
                    actions.onIntent(
                        AccountManageIntent.UpdateOtherLoginOp(
                            OtherLoginOperation.None
                        )
                    )
                },
                onConfirm = { email, password ->
                    actions.onIntent(AccountManageIntent.UpdateOtherLoginOp(OtherLoginOperation.None))
                    actions.onIntent(
                        AccountManageIntent.LoginWithOtherServer(
                            operation.server,
                            email,
                            password
                        )
                    )
                }
            )
        }

        is OtherLoginOperation.OnFailed -> {
            LaunchedEffect(operation) {
                actions.submitError(
                    ErrorViewModel.ThrowableMessage(
                        title = androidText(R.string.account_logging_in_failed),
                        message = actions.formatError(operation.th)
                    )
                )
                actions.onIntent(AccountManageIntent.UpdateOtherLoginOp(OtherLoginOperation.None))
            }
        }

        is OtherLoginOperation.SelectRole -> {
            SimpleListDialog(
                title = stringResource(R.string.account_other_login_select_role),
                items = operation.profiles,
                onItemSelected = { operation.selected(it) },
                onDismissRequest = {
                    actions.onIntent(
                        AccountManageIntent.UpdateOtherLoginOp(
                            OtherLoginOperation.None
                        )
                    )
                },
                itemLayout = { item, isCurrent, onClick ->
                    SimpleListItem(
                        selected = isCurrent,
                        itemName = item.name,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onClick
                    )
                }
            )
        }
    }
}

/**
 * Auth server management logic handling
 */
@Composable
private fun ServerTypeOperation(
    operation: ServerOperation,
    actions: AccountActions
) {
    when (operation) {
        is ServerOperation.AddNew -> {
            var serverUrl by rememberSaveable { mutableStateOf("") }
            SimpleEditDialog(
                title = stringResource(R.string.account_add_new_server),
                value = serverUrl,
                onValueChange = { serverUrl = it.trim() },
                label = { Text(text = stringResource(R.string.account_label_server_url)) },
                singleLine = true,
                onDismissRequest = {
                    actions.onIntent(
                        AccountManageIntent.UpdateServerOp(
                            ServerOperation.None
                        )
                    )
                },
                onConfirm = {
                    if (serverUrl.isNotEmpty()) {
                        actions.onIntent(AccountManageIntent.AddServer(serverUrl))
                    }
                }
            )
        }

        is ServerOperation.Delete -> {
            SimpleAlertDialog(
                title = stringResource(R.string.account_other_login_delete_server_title),
                text = stringResource(
                    R.string.account_other_login_delete_server_message,
                    operation.server.serverName
                ),
                onDismiss = { actions.onIntent(AccountManageIntent.UpdateServerOp(ServerOperation.None)) },
                onConfirm = { actions.onIntent(AccountManageIntent.DeleteServer(operation.server)) }
            )
        }

        is ServerOperation.OnThrowable -> {
            LaunchedEffect(operation) {
                actions.submitError(
                    ErrorViewModel.ThrowableMessage(
                        title = androidText(R.string.account_other_login_adding_failure),
                        message = androidText(operation.throwable.getMessageOrToString())
                    )
                )
                actions.onIntent(AccountManageIntent.UpdateServerOp(ServerOperation.None))
            }
        }

        is ServerOperation.None -> {}
    }
}

/**
 * Account list component
 */
@Composable
private fun AccountsLayout(
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    accounts: List<Account>,
    currentAccount: Account?,
    isOffline: Boolean,
    accountOperation: AccountOperation,
    accountSkinOperation: AccountSkinOperation,
    accountSkinDialogState: AccountManageViewModel.AccountSkinDialogState,
    accountCapes: Map<String, List<PlayerProfile.Cape>>,
    actions: AccountActions
) {
    val yOffset by swapAnimateDpAsState(targetValue = (-40).dp, swapIn = isVisible)
    val context = LocalContext.current

    AccountOperation(accountOperation, actions)

    AccountSkinOperation(
        accountSkinOperation = accountSkinOperation,
        skinDialogState = accountSkinDialogState,
        accountCapes = accountCapes,
        actions = actions
    )

    BackgroundCard(
        modifier = modifier.offset { IntOffset(x = 0, y = yOffset.roundToPx()) },
        shape = MaterialTheme.shapes.extraLarge
    ) {
        if (accounts.isNotEmpty()) {
            val scrollState = rememberLazyListState()
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nonInteractiveScrollbar(
                        state = scrollState.scrollIndicatorState!!,
                        orientation = Orientation.Vertical,
                    )
                    .clip(MaterialTheme.shapes.extraLarge),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                state = scrollState,
            ) {
                items(accounts, key = { it.uniqueUUID }) { account ->
                    AccountItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        currentAccount = currentAccount,
                        account = account,
                        enabled = !isOffline, //non-genuine state forbids picking anything
                        onSelected = { AccountsManager.setCurrentAccount(it) },
                        openChangeSkinDialog = {
                            if (!account.isAuthServerAccount()) {
                                actions.onIntent(
                                    AccountManageIntent.UpdateAccountSkinOp(
                                        AccountSkinOperation.ChangeSkin(account)
                                    )
                                )
                            }
                        },
                        onRefreshClick = {
                            actions.onIntent(
                                AccountManageIntent.RefreshAccount(
                                    account
                                )
                            )
                        },
                        onCopyUUID = {
                            copyText(COPY_LABEL_ACCOUNT_UUID, account.profileId, context, true)
                        },
                        onDeleteClick = {
                            actions.onIntent(
                                AccountManageIntent.UpdateAccountOp(
                                    AccountOperation.Delete(account)
                                )
                            )
                        }
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                ScalingLabel(
                    modifier = Modifier.align(Alignment.Center),
                    text = stringResource(R.string.account_no_account)
                )
            }
        }
    }
}

/**
 * Account skin operation logic handling
 */
@Composable
private fun AccountSkinOperation(
    accountSkinOperation: AccountSkinOperation,
    skinDialogState: AccountManageViewModel.AccountSkinDialogState,
    accountCapes: Map<String, List<PlayerProfile.Cape>>,
    actions: AccountActions
) {
    when (accountSkinOperation) {
        is AccountSkinOperation.None -> {}
        is AccountSkinOperation.ChangeSkin -> {
            val account = accountSkinOperation.account
            ChangeSkinDialog(
                account = account,
                availableCapes = accountCapes[account.uniqueUUID] ?: emptyList(),
                skinState = skinDialogState.pendingSkinData,
                onSkinStateChange = { skinState ->
                    actions.onIntent(
                        AccountManageIntent.UpdatePendingSkinData(
                            skinState
                        )
                    )
                },
                capeState = skinDialogState.pendingCapeData,
                onCapeStateChange = { capeState ->
                    actions.onIntent(
                        AccountManageIntent.UpdatePendingCapeData(
                            capeState
                        )
                    )
                },
                isImportingSkin = skinDialogState.importingSkin,
                onSkinPicked = { uri ->
                    actions.onIntent(
                        AccountManageIntent.OnSkinPicked(uri)
                    )
                },
                onDismissRequest = {
                    actions.onIntent(AccountManageIntent.ResetAccountSkinDialogState)
                    actions.onIntent(AccountManageIntent.UpdateAccountSkinOp(AccountSkinOperation.None))
                },
                onResetSkin = {
                    actions.onIntent(AccountManageIntent.ResetSkin(account))
                },
                onFetchCapes = {
                    actions.onIntent(AccountManageIntent.FetchMicrosoftCapes(account))
                },
                onApplySkin = { file, model ->
                    actions.onIntent(AccountManageIntent.ApplySkin(account, file, model))
                },
                onApplyCape = { cape ->
                    actions.onIntent(AccountManageIntent.ApplyMicrosoftCape(account, cape))
                }
            )
        }
    }
}

/**
 * Generic account management logic handling (e.g. delete confirmation)
 */
@Composable
private fun AccountOperation(
    operation: AccountOperation,
    actions: AccountActions
) {
    when (operation) {
        is AccountOperation.Delete -> {
            SimpleAlertDialog(
                title = stringResource(R.string.account_delete_title),
                text = stringResource(R.string.account_delete_message, operation.account.username),
                onConfirm = { actions.onIntent(AccountManageIntent.DeleteAccount(operation.account)) },
                onDismiss = { actions.onIntent(AccountManageIntent.UpdateAccountOp(AccountOperation.None)) }
            )
        }

        is AccountOperation.OnFailed -> {
            LaunchedEffect(operation) {
                actions.submitError(
                    ErrorViewModel.ThrowableMessage(
                        title = androidText(R.string.account_logging_in_failed),
                        message = actions.formatError(operation.th)
                    )
                )
                actions.onIntent(AccountManageIntent.UpdateAccountOp(AccountOperation.None))
            }
        }

        is AccountOperation.OnRelogin -> {
            if (operation.account.isMicrosoftAccount()) {
                MicrosoftReloginDialog(
                    onDismissRequest = {
                        actions.onIntent(AccountManageIntent.UpdateAccountOp(AccountOperation.None))
                    },
                    onConfirm = {
                        actions.onIntent(AccountManageIntent.UpdateAccountOp(AccountOperation.None))
                        actions.onIntent(
                            AccountManageIntent.PerformMicrosoftLogin(
                                toWeb = actions.navigateToWeb,
                                backToMain = actions.backToMainScreen,
                                checkIfInWebScreen = actions.checkIfInWebScreen
                            )
                        )
                    }
                )
            } else {
                OtherAccountReloginDialog(
                    account = operation.account,
                    logging = operation.logging,
                    error = operation.error,
                    onDismissRequest = {
                        actions.onIntent(AccountManageIntent.UpdateAccountOp(AccountOperation.None))
                    },
                    onConfirm = { password ->
                        actions.onIntent(
                            AccountManageIntent.UpdateAccountOp(
                                AccountOperation.OnRelogin(operation.account, logging = true)
                            )
                        )
                        actions.onIntent(
                            AccountManageIntent.ReloginOtherAccount(
                                account = operation.account,
                                password = password
                            )
                        )
                    }
                )
            }
        }

        is AccountOperation.None -> {}
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 480)
@Composable
private fun AccountManageContentPreview() {
    CompositionLocalProvider(LocalBackgroundViewModel provides null) {
        MaterialExpressiveTheme {
            Surface {
                AccountManageContent(
                    isVisible = true,
                    loginUiState = AccountManageViewModel.LoginUiState(),
                    profileUiState = AccountManageViewModel.ProfileUiState(),
                    operationUiState = AccountManageViewModel.OperationUiState(),
                    actions = AccountActions(
                        onIntent = {},
                        openLink = {},
                        backToMainScreen = {},
                        navigateToWeb = {},
                        checkIfInWebScreen = { false },
                        formatError = { AndroidStringText.Text("") },
                        submitError = {},
                    )
                )
            }
        }
    }
}
