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

package com.endiq.turtlelauncher.ui.screens.content.elements

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.createBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.endiq.turtlelauncher.BuildKeys
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.game.account.Account
import com.endiq.turtlelauncher.game.account.AccountType
import com.endiq.turtlelauncher.game.account.AccountsManager
import com.endiq.turtlelauncher.game.account.accountErrorText
import com.endiq.turtlelauncher.game.account.accountUUID
import com.endiq.turtlelauncher.game.account.auth_server.data.AuthServer
import com.endiq.turtlelauncher.game.account.auth_server.models.AuthResult
import com.endiq.turtlelauncher.game.account.getAccountTypeName
import com.endiq.turtlelauncher.game.account.getUUIDFromUserName
import com.endiq.turtlelauncher.game.account.isLocalAccount
import com.endiq.turtlelauncher.game.account.isMicrosoftAccount
import com.endiq.turtlelauncher.game.account.isSkinChangeAllowed
import com.endiq.turtlelauncher.game.account.wardrobe.EmptyCape
import com.endiq.turtlelauncher.game.account.wardrobe.SkinModelType
import com.endiq.turtlelauncher.game.account.wardrobe.capeLocalRes
import com.endiq.turtlelauncher.game.account.yggdrasil.PlayerProfile
import com.endiq.turtlelauncher.game.account.yggdrasil.findUsing
import com.endiq.turtlelauncher.game.account.yggdrasil.getFile
import com.endiq.turtlelauncher.path.PathManager
import com.endiq.turtlelauncher.path.URL_MINECRAFT_PURCHASE
import com.endiq.turtlelauncher.ui.AndroidStringText
import com.endiq.turtlelauncher.ui.androidText
import com.endiq.turtlelauncher.ui.components.BaseIconTextButton
import com.endiq.turtlelauncher.ui.components.IconTextButton
import com.endiq.turtlelauncher.ui.components.ImePanContainer
import com.endiq.turtlelauncher.ui.components.MarqueeText
import com.endiq.turtlelauncher.ui.components.ModelAnimation
import com.endiq.turtlelauncher.ui.components.OwnOutlinedTextField
import com.endiq.turtlelauncher.ui.components.PlayerSkin
import com.endiq.turtlelauncher.ui.components.RadioCard
import com.endiq.turtlelauncher.ui.components.SimpleAlertDialog
import com.endiq.turtlelauncher.ui.components.SimpleListDialog
import com.endiq.turtlelauncher.ui.components.SingleLineTextCheck
import com.endiq.turtlelauncher.ui.components.fadeEdge
import com.endiq.turtlelauncher.ui.components.rememberDialogMaxHeight
import com.endiq.turtlelauncher.ui.components.verticalScrollWithBar
import com.endiq.turtlelauncher.ui.screens.main.control_editor.InfoLayoutTextItem
import com.endiq.turtlelauncher.ui.theme.cardColor
import com.endiq.turtlelauncher.ui.theme.itemColor
import com.endiq.turtlelauncher.ui.theme.onCardColor
import com.endiq.turtlelauncher.ui.theme.onItemColor
import com.endiq.turtlelauncher.utils.animation.getAnimateTween
import com.endiq.turtlelauncher.utils.logging.Logger
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.regex.Pattern
import kotlin.math.roundToInt

private const val TAG = "AccountElements"

/** Account login menu operation state */
sealed interface LoginMenuOperation {
    data object None : LoginMenuOperation

    /** Brings up the account login menu, showing all login methods in one dialog */
    data object Login : LoginMenuOperation
}

/**
 * Operation state of Microsoft login
 */
sealed interface MicrosoftLoginOperation {
    data object None : MicrosoftLoginOperation

    /** Dialog flow for Microsoft account related prompts */
    data object Tip : MicrosoftLoginOperation
}

/**
 * Operation state of offline login
 */
sealed interface LocalLoginOperation {
    data object None : LocalLoginOperation

    /** Username editing flow */
    data object Edit : LocalLoginOperation

    /** Account creation flow */
    data class Create(val userName: String, val userUUID: String?) : LocalLoginOperation

    /** Invalid username warning flow */
    data class Alert(val userName: String, val userUUID: String?) : LocalLoginOperation
}

/**
 * State while adding an authentication server
 */
sealed interface ServerOperation {
    data object None : ServerOperation
    /** Add authentication server dialog */
    data object AddNew : ServerOperation
    /** Delete authentication server dialog */
    data class Delete(val server: AuthServer) : ServerOperation
    data class OnThrowable(val throwable: Throwable) : ServerOperation
}

/**
 * State of account operations
 */
sealed interface AccountOperation {
    data object None : AccountOperation
    data class Delete(val account: Account) : AccountOperation
    data class OnFailed(val th: Throwable) : AccountOperation

    /** The account credentials were rejected by the server; a new login is required */
    data class OnRelogin(
        val account: Account,
        val logging: Boolean = false,
        val error: Throwable? = null
    ) : AccountOperation
}

/**
 * State while changing the account skin
 */
sealed interface AccountSkinOperation {
    data object None : AccountSkinOperation

    /** Main skin change dialog */
    data class ChangeSkin(val account: Account) : AccountSkinOperation
}

/**
 * State while logging in to an authentication server
 */
sealed interface OtherLoginOperation {
    data object None : OtherLoginOperation

    /** Account login flow (account/password input dialog) */
    data class OnLogin(val server: AuthServer) : OtherLoginOperation

    /** Login failure flow */
    data class OnFailed(val th: Throwable) : OtherLoginOperation

    /** Multi-profile handling flow for accounts that own several profiles */
    data class SelectRole(
        val profiles: List<AuthResult.AvailableProfiles>,
        val selected: (AuthResult.AvailableProfiles) -> Unit
    ) : OtherLoginOperation
}

@Composable
fun PlayerFace(
    modifier: Modifier = Modifier,
    account: Account,
    avatarSize: Dp = 64.dp,
    refreshKey: Any? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val refreshWardrobe by AccountsManager.refreshWardrobe.collectAsStateWithLifecycle()
    val avatarBitmap = remember(account, refreshKey, refreshWardrobe, density) {
        getSkinAvatarFromAccount(context, account, avatarSize, density).asImageBitmap()
    }

    Image(
        modifier = modifier.size(avatarSize),
        bitmap = avatarBitmap,
        contentDescription = null
    )
}

@Composable
fun AccountItem(
    modifier: Modifier = Modifier,
    currentAccount: Account?,
    account: Account,
    avatarSize: Dp = 46.dp,
    color: Color = itemColor(),
    contentColor: Color = onItemColor(),
    enabled: Boolean = true,
    refreshKey: Any? = null,
    onSelected: (Account) -> Unit = {},
    openChangeSkinDialog: () -> Unit = {},
    onRefreshClick: () -> Unit = {},
    onCopyUUID: () -> Unit = {},
    onDeleteClick: () -> Unit = {}
) {
    val selected = currentAccount?.uniqueUUID == account.uniqueUUID
    val scale = remember { Animatable(initialValue = 0.95f) }
    LaunchedEffect(Unit) {
        scale.animateTo(targetValue = 1f, animationSpec = getAnimateTween())
    }
    Surface(
        modifier = modifier.graphicsLayer(scaleY = scale.value, scaleX = scale.value),
        color = color,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.large,
        onClick = {
            if (selected || !enabled) return@Surface
            onSelected(account)
        },
        enabled = enabled
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape = MaterialTheme.shapes.large)
                .padding(all = 8.dp)
        ) {
            RadioButton(
                selected = selected,
                onClick = {
                    if (selected || !enabled) return@RadioButton
                    onSelected(account)
                },
                enabled = enabled
            )
            PlayerFace(
                modifier = Modifier.align(Alignment.CenterVertically),
                account = account,
                avatarSize = avatarSize,
                refreshKey = refreshKey
            )
            Spacer(modifier = Modifier.width(18.dp))
            Column(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .weight(1f)
            ) {
                Text(text = account.username)
                Text(
                    text = getAccountTypeName(account),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Row {
                //Change skin/cape
                Row {
                    IconButton(
                        onClick = { openChangeSkinDialog() },
                        enabled = account.isSkinChangeAllowed()
                    ) {
                        Icon(
                            modifier = Modifier.size(24.dp),
                            painter = painterResource(R.drawable.ic_checkroom),
                            contentDescription = stringResource(R.string.account_change_skin)
                        )
                    }
                }

                //Refresh
                IconButton(
                    onClick = onRefreshClick,
                    enabled = account.accountType != AccountType.LOCAL.tag
                ) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        painter = painterResource(R.drawable.ic_refresh),
                        contentDescription = stringResource(R.string.generic_refresh)
                    )
                }

                //Copy UUID
                IconButton(
                    onClick = onCopyUUID
                ) {
                    Icon(
                        modifier = Modifier.size(22.dp),
                        painter = painterResource(R.drawable.ic_copy_all_outlined),
                        contentDescription = stringResource(R.string.account_local_uuid_copy)
                    )
                }

                //Delete
                IconButton(
                    onClick = onDeleteClick
                ) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        painter = painterResource(R.drawable.ic_delete_outlined),
                        contentDescription = stringResource(R.string.generic_delete)
                    )
                }
            }
        }
    }
}

@Composable
fun LoginMenuDialog(
    onDismissRequest: () -> Unit,
    onMicrosoftLogin: () -> Unit,
    onLocalLogin: () -> Unit,
    authServers: List<AuthServer>,
    onAuthServerLogin: (server: AuthServer) -> Unit,
    onAddAuthServer: () -> Unit,
    onDeleteAuthServer: (server: AuthServer) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .padding(all = 16.dp)
                .heightIn(max = rememberDialogMaxHeight())
                .fillMaxHeight()
                .fillMaxWidth(0.6f),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .padding(all = 6.dp)
                    .fillMaxWidth()
                    .heightIn(max = (maxHeight - 12.dp).coerceAtMost(rememberDialogMaxHeight())),
                shape = MaterialTheme.shapes.extraLarge,
                color = cardColor(false),
                contentColor = onCardColor(),
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 12.dp)
                                .padding(start = 12.dp, end = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            //Microsoft login
                            LoginItem(
                                modifier = Modifier.fillMaxWidth(),
                                title = stringResource(R.string.account_type_microsoft),
                                onClick = {
                                    onMicrosoftLogin()
                                    onDismissRequest()
                                }
                            )
                            //Offline login
                            LoginItem(
                                modifier = Modifier.fillMaxWidth(),
                                title = stringResource(R.string.account_type_local),
                                onClick = {
                                    onLocalLogin()
                                    onDismissRequest()
                                }
                            )
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(
                                start = 6.dp,
                                top = 12.dp,
                                end = 12.dp,
                                bottom = 12.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                //Add authentication servers
                                InfoLayoutTextItem(
                                    modifier = Modifier.fillMaxWidth(),
                                    title = stringResource(R.string.account_add_new_server_button),
                                    showArrow = true,
                                    onClick = {
                                        onAddAuthServer()
                                        onDismissRequest()
                                    }
                                )
                            }

                            items(authServers) { server ->
                                LoginItem(
                                    title = server.serverName,
                                    icon = {
                                        IconButton(
                                            modifier = Modifier.size(22.dp),
                                            onClick = {
                                                onDeleteAuthServer(server)
                                            }
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_delete_outlined),
                                                contentDescription = stringResource(R.string.generic_delete)
                                            )
                                        }
                                    },
                                    onClick = {
                                        onAuthServerLogin(server)
                                        onDismissRequest()
                                    }
                                )
                            }
                        }
                    }

                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 8.dp),
                        onClick = onDismissRequest
                    ) {
                        Text(stringResource(R.string.generic_close))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 480)
@Composable
private fun PreviewLoginMenuDialog() {
    MaterialExpressiveTheme {
        LoginMenuDialog(
            onDismissRequest = {},
            onMicrosoftLogin = {},
            onLocalLogin = {},
            authServers = emptyList(),
            onAuthServerLogin = {},
            onAddAuthServer = {},
            onDeleteAuthServer = {}
        )
    }
}

@Composable
fun LoginItem(
    modifier: Modifier = Modifier,
    title: String,
    icon: @Composable () -> Unit = @Composable {
        Icon(
            modifier = Modifier.size(22.dp),
            painter = painterResource(R.drawable.ic_login),
            contentDescription = null
        )
    },
    onClick: () -> Unit
) {
    InfoLayoutTextItem(
        modifier = modifier,
        title = title,
        icon = icon,
        onClick = onClick
    )
}

@Preview(showBackground = true, widthDp = 400, heightDp = 120)
@Composable
private fun PreviewLoginItem() {
    MaterialExpressiveTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            LoginItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 32.dp),
                title = stringResource(R.string.account_type_microsoft),
                onClick = {}
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MicrosoftLoginTipDialog(
    onDismissRequest: () -> Unit = {},
    onConfirm: () -> Unit = {},
    openLink: (url: String) -> Unit = {}
) {
    SimpleAlertDialog(
        title = stringResource(R.string.account_supporting_microsoft_tip_title),
        text = {
            Text(
                text = stringResource(R.string.account_supporting_microsoft_tip_link_text),
                style = MaterialTheme.typography.bodyMedium
            )
            FlowRow {
                IconTextButton(
                    onClick = {
                        openLink(URL_MINECRAFT_PURCHASE)
                    },
                    painter = painterResource(R.drawable.ic_link),
                    contentDescription = null,
                    text = stringResource(R.string.account_supporting_microsoft_tip_link_purchase)
                )
                IconTextButton(
                    onClick = {
                        openLink("https://www.minecraft.net/msaprofile/mygames/editprofile")
                    },
                    painter = painterResource(R.drawable.ic_link),
                    contentDescription = null,
                    text = stringResource(R.string.account_supporting_microsoft_tip_link_make_gameid)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.account_supporting_microsoft_tip_hint_t1),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = buildAnnotatedString {
                    append(stringResource(R.string.account_supporting_microsoft_tip_hint_t2))
                    append(
                        stringResource(
                            R.string.account_supporting_microsoft_tip_hint_t3,
                            BuildKeys.LAUNCHER_NAME
                        )
                    )
                    append(stringResource(R.string.account_supporting_microsoft_tip_hint_t4))
                    append(stringResource(R.string.account_supporting_microsoft_tip_hint_t5))
                    append(stringResource(R.string.account_supporting_microsoft_tip_hint_t6))
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = buildAnnotatedString {
                    append(stringResource(R.string.account_supporting_microsoft_tip_hint_t7))
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(stringResource(R.string.account_supporting_microsoft_tip_hint_t8))
                    }
                },
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmText = stringResource(R.string.account_login),
        onConfirm = onConfirm,
        onCancel = onDismissRequest,
        onDismissRequest = onDismissRequest
    )
}

private val localNamePattern = Pattern.compile("[^a-zA-Z0-9_]")

@Composable
fun LocalLoginDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (isUserNameInvalid: Boolean, userName: String, userUUID: String?) -> Unit,
    openLink: (url: String) -> Unit
) {
    /** The username the user entered */
    var userName by rememberSaveable { mutableStateOf("") }

    /** Whether the username is invalid */
    var isUserNameInvalid by rememberSaveable { mutableStateOf(false) }

    /** The user edited the UUID */
    var userEditedUUID by rememberSaveable { mutableStateOf(false) }

    /** The UUID the user entered */
    var userUUID by rememberSaveable { mutableStateOf("") }

    /** The provisional UUID derived from the username */
    val pendingUUID = remember(userName) {
        runCatching {
            getUUIDFromUserName(userName).toString()
        }.getOrElse {
            ""
        }.also { uuid ->
            if (!userEditedUUID) userUUID = uuid
        }
    }

    /** Whether the user's UUID is invalid */
    val isUserUUIDInvalid: Boolean = remember(userUUID) {
        if (userUUID.isEmpty()) false
        else {
            runCatching {
                accountUUID(userUUID)
                false
            }.getOrElse {
                true
            }
        }
    }

    var editUUID by rememberSaveable { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(decorFitsSystemWindows = false)
    ) {
        ImePanContainer(
            modifier = Modifier
                .heightIn(max = rememberDialogMaxHeight())
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .padding(all = 6.dp)
                    .heightIn(max = (maxHeight - 12.dp).coerceAtMost(rememberDialogMaxHeight()))
                    .wrapContentHeight(),
                shape = MaterialTheme.shapes.extraLarge,
                color = cardColor(false),
                contentColor = onCardColor(),
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.account_local_create_account),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.size(16.dp))

                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fadeEdge(state = scrollState)
                            .weight(1f, fill = false)
                            .verticalScrollWithBar(state = scrollState)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SingleLineTextCheck(
                            text = userName,
                            onSingleLined = { userName = it }
                        )

                        OwnOutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = userName,
                            onValueChange = {
                                userName = it
                            },
                            isError = isUserNameInvalid,
                            label = { Text(text = stringResource(R.string.account_label_username)) },
                            supportingText = {
                                val errorText = when {
                                    userName.isEmpty() -> stringResource(R.string.account_supporting_username_invalid_empty)
                                    userName.length <= 2 -> stringResource(R.string.account_supporting_username_invalid_short)
                                    userName.length > 16 -> stringResource(R.string.account_supporting_username_invalid_long)
                                    localNamePattern.matcher(userName)
                                        .find() -> stringResource(R.string.account_supporting_username_invalid_illegal_characters)

                                    else -> ""
                                }.also {
                                    isUserNameInvalid = it.isNotEmpty()
                                }
                                if (isUserNameInvalid) {
                                    Text(text = errorText)
                                }
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.large
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconTextButton(
                                onClick = {
                                    openLink(URL_MINECRAFT_PURCHASE)
                                },
                                painter = painterResource(R.drawable.ic_link),
                                contentDescription = null,
                                text = stringResource(R.string.account_supporting_microsoft_tip_link_purchase)
                            )

                            //Open advanced settings
                            BaseIconTextButton(
                                onClick = {
                                    editUUID = !editUUID
                                },
                                icon = { iconModifier ->
                                    val rotate by animateFloatAsState(
                                        if (editUUID) 0f
                                        else 180f
                                    )

                                    Icon(
                                        modifier = iconModifier
                                            .size(24.dp)
                                            .rotate(rotate),
                                        painter = painterResource(R.drawable.ic_arrow_drop_up_rounded),
                                        contentDescription = null
                                    )
                                },
                                text = stringResource(R.string.account_advanced)
                            )
                        }

                        //Edit the custom UUID
                        AnimatedVisibility(
                            visible = editUUID
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Spacer(modifier = Modifier.size(8.dp))

                                SingleLineTextCheck(
                                    text = userUUID,
                                    onSingleLined = { userUUID = it }
                                )

                                OwnOutlinedTextField(
                                    modifier = Modifier.fillMaxWidth(),
                                    value = userUUID,
                                    onValueChange = {
                                        userUUID = it
                                        userEditedUUID = true
                                    },
                                    isError = isUserUUIDInvalid,
                                    label = { Text(text = stringResource(R.string.account_local_uuid)) },
                                    supportingText = {
                                        if (isUserUUIDInvalid) {
                                            Text(text = stringResource(R.string.account_local_uuid_invalid))
                                        }
                                    },
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.large
                                )

                                //Hint about UUIDs
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(all = 8.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.account_local_uuid_tip_1),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        Text(
                                            text = stringResource(R.string.account_local_uuid_tip_2),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        Text(
                                            text = stringResource(R.string.account_local_uuid_tip_3),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        Text(
                                            text = stringResource(R.string.account_local_uuid_tip_4),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.size(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        FilledTonalButton(
                            modifier = Modifier.weight(1f),
                            onClick = onDismissRequest
                        ) {
                            MarqueeText(text = stringResource(R.string.generic_cancel))
                        }
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (userName.isNotEmpty()) {
                                    if (userUUID.isNotEmpty()) {
                                        runCatching {
                                            val uuid = accountUUID(userUUID)
                                            val uuidString = accountUUID(uuid)
                                            onConfirm(isUserNameInvalid, userName, uuidString)
                                        }
                                    } else {
                                        //An empty UUID field falls back to the provisional UUID
                                        onConfirm(
                                            isUserNameInvalid,
                                            userName,
                                            pendingUUID.takeIf { it.isNotEmpty() })
                                    }
                                }
                            }
                        ) {
                            MarqueeText(text = stringResource(R.string.generic_confirm))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OtherServerLoginDialog(
    server: AuthServer,
    onRegisterClick: (url: String) -> Unit = {},
    onDismissRequest: () -> Unit = {},
    onConfirm: (email: String, password: String) -> Unit = { _, _ -> }
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    val confirmAction = { //confirm action
        if (email.isNotEmpty() && password.isNotEmpty()) {
            onConfirm(email, password)
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(decorFitsSystemWindows = false)
    ) {
        ImePanContainer(
            modifier = Modifier
                .heightIn(max = rememberDialogMaxHeight())
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .padding(all = 6.dp)
                    .heightIn(max = (maxHeight - 12.dp).coerceAtMost(rememberDialogMaxHeight()))
                    .wrapContentHeight(),
                shape = MaterialTheme.shapes.extraLarge,
                color = cardColor(false),
                contentColor = onCardColor(),
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = server.serverName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.size(16.dp))

                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fadeEdge(state = scrollState)
                            .weight(1f, fill = false)
                            .verticalScrollWithBar(state = scrollState)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val passwordFocus = remember { FocusRequester() }
                        val focusManager = LocalFocusManager.current

                        SingleLineTextCheck(
                            text = email,
                            onSingleLined = { email = it }
                        )

                        OwnOutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = email,
                            onValueChange = {
                                email = it
                            },
                            isError = email.isEmpty(),
                            label = { Text(text = stringResource(R.string.account_label_email)) },
                            supportingText = {
                                if (email.isEmpty()) {
                                    Text(text = stringResource(R.string.account_supporting_email_invalid_empty))
                                }
                            },
                            keyboardOptions = KeyboardOptions.Default.copy(
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = {
                                    //Jump straight to the password field seamlessly
                                    passwordFocus.requestFocus()
                                }
                            ),
                            singleLine = true,
                            shape = MaterialTheme.shapes.large
                        )

                        Spacer(modifier = Modifier.size(8.dp))
                        /** Whether the password is visible */
                        var showPassword by rememberSaveable { mutableStateOf(false) }

                        SingleLineTextCheck(
                            text = password,
                            onSingleLined = { password = it }
                        )

                        OwnOutlinedTextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(passwordFocus),
                            value = password,
                            onValueChange = {
                                password = it
                            },
                            isError = password.isEmpty(),
                            label = { Text(text = stringResource(R.string.account_label_password)) },
                            visualTransformation = if (showPassword) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = Transparent,
                            ),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        painter = painterResource(
                                            if (showPassword) {
                                                R.drawable.ic_visibility_outlined
                                            } else {
                                                R.drawable.ic_visibility_off_outlined
                                            }
                                        ),
                                        contentDescription = stringResource(R.string.account_label_password)
                                    )
                                }
                            },
                            supportingText = {
                                if (password.isEmpty()) {
                                    Text(text = stringResource(R.string.account_supporting_password_invalid_empty))
                                }
                            },
                            keyboardOptions = KeyboardOptions.Default.copy(
                                imeAction = ImeAction.Done,
                                keyboardType = KeyboardType.Password
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    //The user pressing back can even log in right here
                                    focusManager.clearFocus(true)
                                    confirmAction()
                                }
                            ),
                            singleLine = true,
                            shape = MaterialTheme.shapes.large
                        )
                        if (!server.register.isNullOrEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.Start
                            ) {
                                IconTextButton(
                                    onClick = {
                                        onRegisterClick(server.register!!)
                                    },
                                    painter = painterResource(R.drawable.ic_link),
                                    contentDescription = null,
                                    text = stringResource(R.string.account_other_login_register)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.size(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        FilledTonalButton(
                            modifier = Modifier.weight(1f),
                            onClick = onDismissRequest
                        ) {
                            MarqueeText(text = stringResource(R.string.generic_cancel))
                        }
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = confirmAction
                        ) {
                            MarqueeText(text = stringResource(R.string.generic_confirm))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MicrosoftReloginDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) {
    SimpleAlertDialog(
        title = stringResource(R.string.account_relogin_title),
        text = {
            Text(text = stringResource(R.string.account_relogin_microsoft_message))
        },
        confirmText = stringResource(R.string.account_relogin),
        onConfirm = onConfirm,
        onCancel = onDismissRequest,
        onDismissRequest = onDismissRequest
    )
}

@Composable
fun OtherAccountReloginDialog(
    account: Account,
    logging: Boolean,
    error: Throwable?,
    onDismissRequest: () -> Unit,
    onConfirm: (password: String) -> Unit
) {
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!logging) onDismissRequest() },
        properties = DialogProperties(decorFitsSystemWindows = false)
    ) {
        ImePanContainer(
            modifier = Modifier
                .heightIn(max = rememberDialogMaxHeight())
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .padding(all = 6.dp)
                    .heightIn(max = (maxHeight - 12.dp).coerceAtMost(rememberDialogMaxHeight()))
                    .wrapContentHeight(),
                shape = MaterialTheme.shapes.extraLarge,
                color = cardColor(false),
                contentColor = onCardColor(),
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = account.accountType ?: stringResource(R.string.account_relogin_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.size(16.dp))

                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.account_relogin_password_message, account.username),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.size(12.dp))

                    OwnOutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = password,
                        onValueChange = { password = it },
                        enabled = !logging,
                        label = { Text(text = stringResource(R.string.account_label_password)) },
                        visualTransformation = if (showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Transparent,
                        ),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    painter = painterResource(
                                        if (showPassword) {
                                            R.drawable.ic_visibility_outlined
                                        } else {
                                            R.drawable.ic_visibility_off_outlined
                                        }
                                    ),
                                    contentDescription = stringResource(R.string.account_label_password)
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions.Default.copy(
                            imeAction = ImeAction.Done,
                            keyboardType = KeyboardType.Password
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (password.isNotEmpty() && !logging) onConfirm(password)
                            }
                        ),
                        singleLine = true,
                        shape = MaterialTheme.shapes.large
                    )

                    error?.let { th ->
                        Spacer(modifier = Modifier.size(8.dp))
                        AndroidStringText(
                            text = accountErrorText(th),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.error
                            )
                        )
                    }

                    Spacer(modifier = Modifier.size(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        FilledTonalButton(
                            modifier = Modifier.weight(1f),
                            enabled = !logging,
                            onClick = onDismissRequest
                        ) {
                            MarqueeText(text = stringResource(R.string.generic_cancel))
                        }
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = !logging && password.isNotEmpty(),
                            onClick = { onConfirm(password) }
                        ) {
                            MarqueeText(text = stringResource(R.string.account_relogin))
                        }
                    }
                }
            }
        }
    }
}

/**
 * The skin change flow deeply binds the uri to the skin model
 * making data handling easier on reset and confirm
 */
sealed interface ChangeSkin {
    data object None : ChangeSkin

    data class ChangeSkinData(
        val cacheFile: File,
        val skinModel: SkinModelType = SkinModelType.STEVE
    ) : ChangeSkin

    /**
     * Resets the offline skin
     */
    data object ResetSkin : ChangeSkin
}

/**
 * Cape change flow
 */
sealed interface ChangeCape {
    data object None : ChangeCape
    data class ChangeCapeData(
        val cape: PlayerProfile.Cape
    ) : ChangeCape
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChangeSkinDialog(
    account: Account,
    availableCapes: List<PlayerProfile.Cape> = emptyList(),
    skinState: ChangeSkin,
    onSkinStateChange: (ChangeSkin) -> Unit,
    capeState: ChangeCape,
    onCapeStateChange: (ChangeCape) -> Unit,
    isImportingSkin: Boolean,
    onSkinPicked: (Uri) -> Unit,
    onDismissRequest: () -> Unit,
    onResetSkin: () -> Unit,
    onApplySkin: (File, SkinModelType) -> Unit,
    onApplyCape: (PlayerProfile.Cape) -> Unit,
    onFetchCapes: () -> Unit
) {
    val context = LocalContext.current
    val playerSkin = remember { PlayerSkin(context) }

    DisposableEffect(Unit) {
        onDispose {
            playerSkin.destroy()
        }
    }

    var showCapeSelector by remember { mutableStateOf(false) }

    var isFetchingCapes by remember { mutableStateOf(false) }

    var currentCapeToLoad by remember { mutableStateOf(EmptyCape) }
    var currentUsingCape by remember { mutableStateOf(EmptyCape) }

    LaunchedEffect(availableCapes) {
        if (account.isMicrosoftAccount()) {
            if (availableCapes.isNotEmpty()) {
                isFetchingCapes = false
                val currentUsingCape0 = availableCapes.findUsing() ?: EmptyCape
                currentUsingCape = currentUsingCape0
                currentCapeToLoad = currentUsingCape0
            } else {
                isFetchingCapes = true
                onFetchCapes()
            }
        }
    }

    val skinPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let(onSkinPicked)
        }

    /**
     * Initializes the skin set on the account
     */
    fun loadSkin() {
        playerSkin.loadSkin(
            skinId = account.uniqueUUID.takeIf { account.hasSkinFile },
            model = account.skinModelType
        )
    }

    /**
     * Resets the skin preview
     */
    fun resetSkin() {
        playerSkin.resetSkin()
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .padding(all = 16.dp)
                .heightIn(max = rememberDialogMaxHeight())
                .fillMaxHeight()
                .fillMaxWidth(0.6f),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .padding(all = 6.dp)
                    .heightIn(min = (maxHeight * 0.85f).coerceAtMost(rememberDialogMaxHeight()))
                    .heightIn(max = (maxHeight - 12.dp).coerceAtMost(rememberDialogMaxHeight())),
                shape = MaterialTheme.shapes.extraLarge,
                color = cardColor(false),
                contentColor = onCardColor(),
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(1f)
                                .clip(MaterialTheme.shapes.large)
                                .background(itemColor(false)),
                            contentAlignment = Alignment.Center
                        ) {
                            var pageFinished by remember { mutableStateOf(false) }

                            if (!pageFinished) {
                                //Loading the skin preview
                                LoadingIndicator()
                            }

                            AndroidView(
                                factory = { context ->
                                    playerSkin.loadWebView(
                                        context = context,
                                        onPageFinished = {
                                            pageFinished = true
                                            playerSkin.startAnim(ModelAnimation.Walking, 0.8f)
                                            playerSkin.setAzimuthAndPitch(0, 10, 50)
                                        }
                                    )
                                },
                                update = {
                                    if (pageFinished) {
                                        when (skinState) {
                                            ChangeSkin.None -> loadSkin()
                                            is ChangeSkin.ChangeSkinData -> {
                                                runCatching {
                                                    skinState.cacheFile.inputStream().use { stream ->
                                                        playerSkin.loadSkin(stream, skinState.skinModel)
                                                    }
                                                }.onFailure {
                                                    playerSkin.loadSkin(
                                                        skinId = null,
                                                        skinState.skinModel
                                                    )
                                                }
                                            }

                                            is ChangeSkin.ResetSkin -> resetSkin()
                                        }
                                        if (account.isMicrosoftAccount()) {
                                            playerSkin.loadCape(currentCapeToLoad)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            //Change skin: pick a skin image file
                            when (skinState) {
                                ChangeSkin.None, ChangeSkin.ResetSkin -> {
                                    InfoLayoutTextItem(
                                        modifier = Modifier.fillMaxWidth(),
                                        title = stringResource(R.string.account_change_skin),
                                        icon = {
                                            if (isImportingSkin) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(22.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Icon(
                                                    modifier = Modifier.size(22.dp),
                                                    painter = painterResource(R.drawable.ic_upload),
                                                    contentDescription = null
                                                )
                                            }
                                        },
                                        onClick = {
                                            skinPicker.launch(arrayOf("image/png"))
                                        },
                                        enabled = !isImportingSkin
                                    )
                                }

                                is ChangeSkin.ChangeSkinData -> {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.account_change_skin_arm_style),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        //Pick a style
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            //Classic (4px) arms
                                            RadioCard(
                                                selected = skinState.skinModel == SkinModelType.STEVE,
                                                text = stringResource(R.string.account_change_skin_arm_wide),
                                                onClick = {
                                                    onSkinStateChange(
                                                        skinState.copy(
                                                            skinModel = SkinModelType.STEVE
                                                        )
                                                    )
                                                }
                                            )
                                            //Slim (3px) arms
                                            RadioCard(
                                                selected = skinState.skinModel == SkinModelType.ALEX,
                                                text = stringResource(R.string.account_change_skin_arm_slim),
                                                onClick = {
                                                    onSkinStateChange(
                                                        skinState.copy(
                                                            skinModel = SkinModelType.ALEX
                                                        )
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            //Only Microsoft accounts support capes
                            if (account.isMicrosoftAccount()) {
                                InfoLayoutTextItem(
                                    modifier = Modifier.fillMaxWidth(),
                                    title = if (isFetchingCapes) {
                                        stringResource(R.string.account_change_cape_fetch_all)
                                    } else {
                                        stringResource(R.string.account_change_cape)
                                    },
                                    icon = {
                                        if (isFetchingCapes) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(22.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                modifier = Modifier.size(22.dp),
                                                painter = painterResource(R.drawable.ic_styler),
                                                contentDescription = null
                                            )
                                        }
                                    },
                                    onClick = {
                                        showCapeSelector = true
                                    },
                                    enabled = !isFetchingCapes
                                )
                            }

                            //Offline account skin reset
                            if (account.isLocalAccount() && account.hasSkinFile && skinState != ChangeSkin.ResetSkin) {
                                InfoLayoutTextItem(
                                    modifier = Modifier.fillMaxWidth(),
                                    title = stringResource(R.string.generic_reset),
                                    icon = {
                                        Icon(
                                            modifier = Modifier.size(22.dp),
                                            painter = painterResource(R.drawable.ic_restart_alt),
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        onSkinStateChange(ChangeSkin.ResetSkin)
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilledTonalButton(
                            modifier = Modifier.weight(1f),
                            onClick = onDismissRequest
                        ) {
                            Text(text = stringResource(R.string.generic_cancel))
                        }

                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = skinState != ChangeSkin.None || capeState != ChangeCape.None,
                            onClick = {
                                when (skinState) {
                                    is ChangeSkin.ChangeSkinData -> {
                                        onApplySkin(skinState.cacheFile, skinState.skinModel)
                                    }

                                    is ChangeSkin.ResetSkin -> {
                                        onResetSkin()
                                    }

                                    ChangeSkin.None -> {}
                                }

                                if (capeState is ChangeCape.ChangeCapeData) {
                                    onApplyCape(capeState.cape)
                                }

                                onDismissRequest()
                            }
                        ) {
                            Text(text = stringResource(R.string.generic_confirm))
                        }
                    }
                }
            }
        }
    }

    if (showCapeSelector) {
        //When the cape didn't change, keep the one in use
        val cape = if (capeState is ChangeCape.ChangeCapeData) {
            capeState.cape
        } else {
            currentUsingCape
        }

        SelectCapeDialog(
            capes = buildList {
                add(EmptyCape)
                addAll(availableCapes)
            },
            selectedCape = cape,
            onSelected = { cape ->
                //Check whether it's already the cape in use
                val state = if (cape != currentUsingCape) {
                    ChangeCape.ChangeCapeData(cape)
                } else {
                    ChangeCape.None
                }
                onCapeStateChange(state)
                currentCapeToLoad = cape
                showCapeSelector = false
            },
            onDismiss = {
                showCapeSelector = false
            }
        )
    }
}

@Composable
fun SelectCapeDialog(
    capes: List<PlayerProfile.Cape>,
    selectedCape: PlayerProfile.Cape?,
    onSelected: (PlayerProfile.Cape) -> Unit,
    onDismiss: () -> Unit,
    capeSize: Dp = 32.dp,
) {
    val density = LocalDensity.current

    val capeLocals = remember(capes) {
        buildMap {
            capes.forEach { cape ->
                cape.capeLocalRes()?.let { local ->
                    put(cape, androidText(local))
                }
            }
        }
    }

    SimpleListDialog(
        title = stringResource(R.string.account_change_cape_select_cape),
        items = capes,
        onItemSelected = { cape ->
            onSelected(cape)
        },
        current = selectedCape,
        itemLayout = { cape, isCurrent, onClick ->
            val name = capeLocals[cape] ?: androidText(cape.alias)
            CapeListItem(
                modifier = Modifier.fillMaxWidth(),
                selected = isCurrent,
                cape = cape,
                name = name,
                size = capeSize,
                density = density,
                onClick = onClick,
            )
        },
        onDismissRequest = { selected ->
            if (!selected) {
                onDismiss()
            }
        }
    )
}

@Composable
fun CapeListItem(
    modifier: Modifier = Modifier,
    selected: Boolean,
    cape: PlayerProfile.Cape,
    name: AndroidStringText,
    size: Dp,
    density: Density,
    onClick: () -> Unit,
) {
    val avatar = remember(cape, density) {
        if (cape != EmptyCape) {
            getCapeAvatar(
                cape = cape,
                size = size,
                density = density
            )?.asImageBitmap()
        } else null
    }

    Row(
        modifier = modifier
            .clip(shape = MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )

        if (avatar != null) {
            val displayWidth = with(density) { avatar.width.toDp() }
            val displayHeight = with(density) { avatar.height.toDp() }
            Image(
                modifier = Modifier
                    .width(displayWidth)
                    .height(displayHeight),
                bitmap = avatar,
                contentDescription = null
            )

            Spacer(Modifier.width(12.dp))
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AndroidStringText(
                text = name,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

private val avatarPaint = Paint().apply { isFilterBitmap = false }

private fun getCapeAvatar(
    cape: PlayerProfile.Cape,
    size: Dp,
    density: Density
): Bitmap? {
    val capeFile = cape.getFile(PathManager.DIR_ACCOUNT_CAPE)
    if (capeFile.exists()) {
        runCatching {
            Files.newInputStream(capeFile.toPath()).use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                    ?: throw IOException("Failed to read the cape picture and try to parse it to a bitmap")
                return getCapeAvatar(bitmap, size, density)
            }
        }.onFailure { e ->
            Logger.error(TAG, "Failed to load cape avatar from locally!", e)
        }
    }
    return null
}

private fun getCapeAvatar(
    cape: Bitmap,
    size: Dp,
    density: Density
): Bitmap {
    val pixelSize = with(density) { size.roundToPx() }
    val scaleFactor = cape.width / 64.0f
    val start = scaleFactor.roundToInt()
    val capeWidth = (10 * scaleFactor).roundToInt()
    val capeHeight = (16 * scaleFactor).roundToInt()
    val targetWidth = (pixelSize.toFloat() * capeWidth / capeHeight).roundToInt()
    val avatar = createBitmap(targetWidth, pixelSize)
    val canvas = Canvas(avatar)

    canvas.drawBitmap(
        cape,
        Rect(start, start, start + capeWidth, start + capeHeight),
        RectF(0f, 0f, targetWidth.toFloat(), pixelSize.toFloat()),
        avatarPaint
    )
    return avatar
}

private fun getSkinAvatarFromAccount(
    context: Context,
    account: Account,
    size: Dp,
    density: Density
): Bitmap {
    val skin = account.getSkinFile()
    if (skin.exists()) {
        runCatching {
            Files.newInputStream(skin.toPath()).use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                    ?: throw IOException("Failed to read the skin picture and try to parse it to a bitmap")
                return getSkinAvatar(bitmap, size, density)
            }
        }.onFailure { e ->
            Logger.error(TAG, "Failed to load skin avatar from locally!", e)
        }
    }
    return getDefaultAvatar(context, size, density)
}

@Throws(Exception::class)
private fun getDefaultAvatar(
    context: Context,
    size: Dp,
    density: Density
): Bitmap {
    return getSkinAvatar(
        skin = BitmapFactory.decodeStream(
            context.assets.open("steve.png")
        ),
        size = size,
        density = density
    )
}

private fun getSkinAvatar(
    skin: Bitmap,
    size: Dp,
    density: Density
): Bitmap {
    val pixelSize = with(density) { size.roundToPx() }
    val faceOffset = (pixelSize / 18.0).roundToInt().toFloat()
    val scaleFactor = skin.width / 64.0f
    val faceSize = (8 * scaleFactor).roundToInt()
    val faceEndY = faceSize * 2
    val hatSrcX = (40 * scaleFactor).roundToInt()
    val avatar = createBitmap(pixelSize, pixelSize)
    val canvas = Canvas(avatar)

    val innerEnd = pixelSize - faceOffset
    canvas.drawBitmap(
        skin,
        Rect(faceSize, faceSize, faceEndY, faceEndY),
        RectF(faceOffset, faceOffset, innerEnd, innerEnd),
        avatarPaint
    )

    canvas.drawBitmap(
        skin,
        Rect(hatSrcX, faceSize, hatSrcX + faceSize, faceEndY),
        RectF(0f, 0f, pixelSize.toFloat(), pixelSize.toFloat()),
        avatarPaint
    )
    return avatar
}
