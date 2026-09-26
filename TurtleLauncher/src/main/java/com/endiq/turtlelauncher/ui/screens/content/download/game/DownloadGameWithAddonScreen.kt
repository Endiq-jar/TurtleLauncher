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

package com.endiq.turtlelauncher.ui.screens.content.download.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.game.addons.modloader.ModLoader
import com.endiq.turtlelauncher.game.addons.modloader.cleanroom.CleanroomVersions
import com.endiq.turtlelauncher.game.addons.modloader.fabriclike.fabric.FabricAPIVersions
import com.endiq.turtlelauncher.game.addons.modloader.fabriclike.fabric.FabricVersions
import com.endiq.turtlelauncher.game.addons.modloader.fabriclike.legacyfabric.LegacyFabricAPIVersions
import com.endiq.turtlelauncher.game.addons.modloader.fabriclike.legacyfabric.LegacyFabricVersions
import com.endiq.turtlelauncher.game.addons.modloader.fabriclike.quilt.QuiltAPIVersions
import com.endiq.turtlelauncher.game.addons.modloader.fabriclike.quilt.QuiltVersions
import com.endiq.turtlelauncher.game.addons.modloader.forgelike.forge.ForgeVersion
import com.endiq.turtlelauncher.game.addons.modloader.forgelike.forge.ForgeVersions
import com.endiq.turtlelauncher.game.addons.modloader.forgelike.neoforge.NeoForgeVersions
import com.endiq.turtlelauncher.game.addons.modloader.optifine.OptiFineVersion
import com.endiq.turtlelauncher.game.addons.modloader.optifine.OptiFineVersions
import com.endiq.turtlelauncher.game.download.game.GameDownloadInfo
import com.endiq.turtlelauncher.game.version.installed.VersionsManager
import com.endiq.turtlelauncher.game.version.installed.VersionsManager.isVersionExists
import com.endiq.turtlelauncher.ui.base.BaseScreen
import com.endiq.turtlelauncher.ui.components.AnimatedColumn
import com.endiq.turtlelauncher.ui.components.SimpleTextInputField
import com.endiq.turtlelauncher.ui.components.verticalScrollWithBar
import com.endiq.turtlelauncher.ui.screens.NestedNavKey
import com.endiq.turtlelauncher.ui.screens.NormalNavKey
import com.endiq.turtlelauncher.ui.screens.TitledNavKey
import com.endiq.turtlelauncher.ui.screens.content.elements.CommonVersionInfoLayout
import com.endiq.turtlelauncher.ui.screens.content.elements.isFilenameInvalid
import com.endiq.turtlelauncher.ui.theme.cardColor
import com.endiq.turtlelauncher.ui.theme.onCardColor
import com.endiq.turtlelauncher.utils.animation.getAnimateTween
import com.endiq.turtlelauncher.utils.animation.swapAnimateDpAsState
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private class AddonsViewModel(
    private val gameVersion: String,
    loaderSupports: LoaderVerSupports
) : ViewModel() {
    val addonList = AddonList()
    val currentAddon = CurrentAddon()
    var refreshIcon by mutableStateOf(false)
        private set

    fun refreshIcon() {
        refreshIcon = !refreshIcon
    }

    fun reloadOptiFine() = launchAddonReload(
        { currentAddon.optifineState = it },
        { OptiFineVersions.fetchOptiFineList(gameVersion = gameVersion) },
        { addonList.optifineList = it }
    )

    fun reloadForge() = launchAddonReload(
        { currentAddon.forgeState = it },
        { ForgeVersions.fetchForgeList(gameVersion) },
        { addonList.forgeList = it }
    )

    fun reloadNeoForge() = launchAddonReload(
        { currentAddon.neoforgeState = it },
        { NeoForgeVersions.fetchNeoForgeList(gameVersion = gameVersion) },
        { addonList.neoforgeList = it }
    )

    fun reloadFabric() = launchAddonReload(
        { currentAddon.fabricState = it },
        { FabricVersions.fetchFabricLoaderList(gameVersion) },
        { addonList.fabricList = it }
    )

    fun reloadFabricAPI() = launchAddonReload(
        { currentAddon.fabricAPIState = it },
        { FabricAPIVersions.fetchVersionList(gameVersion) },
        {
            addonList.fabricAPIList = it
            //Check whether the user already picked Fabric Loader
            if (currentAddon.fabricVersion.value != null) {
                //If so, auto-select Fabric API here
                currentAddon.fabricAPIVersion.value = it?.firstOrNull()
            }
        }
    )

    fun reloadLegacyFabric() = launchAddonReload(
        { currentAddon.legacyFabricState = it },
        { LegacyFabricVersions.fetchFabricLoaderList(gameVersion) },
        { addonList.legacyFabricList = it }
    )

    fun reloadLegacyFabricAPI() = launchAddonReload(
        { currentAddon.legacyFabricAPIState = it },
        { LegacyFabricAPIVersions.fetchVersionList(gameVersion) },
        {
            addonList.legacyFabricAPIList = it
            //Check whether the user already picked Legacy Fabric
            if (currentAddon.legacyFabricVersion.value != null) {
                //If so, auto-select Legacy Fabric API here
                currentAddon.legacyFabricAPIVersion.value = it?.firstOrNull()
            }
        }
    )

    fun reloadQuilt() = launchAddonReload(
        { currentAddon.quiltState = it },
        { QuiltVersions.fetchQuiltLoaderList(gameVersion) },
        { addonList.quiltList = it }
    )

    fun reloadQuiltAPI() = launchAddonReload(
        { currentAddon.quiltAPIState = it },
        { QuiltAPIVersions.fetchVersionList(gameVersion) },
        {
            addonList.quiltAPIList = it
            //Check whether the user already picked Quilt Loader
            if (currentAddon.quiltVersion.value != null) {
                //If so, auto-select Quilted Fabric API here
                currentAddon.quiltAPIVersion.value = it?.firstOrNull()
            }
        }
    )

    fun reloadCleanroom() = launchAddonReload(
        { currentAddon.cleanroomState = it },
        { CleanroomVersions.fetchLoaderList(gameVersion) },
        { addonList.cleanroomList = it }
    )

    private fun <T> launchAddonReload(
        updateState: (AddonState) -> Unit,
        fetch: suspend () -> T?,
        onSuccess: (T?) -> Unit
    ) {
        viewModelScope.launch {
            runWithState(updateState, fetch).also(onSuccess)
        }
    }

    init {
        reloadOptiFine()
        reloadForge()
        if (loaderSupports.isNeoForgeSupports) {
            reloadNeoForge()
        }
        if (loaderSupports.isFabricSupports) {
            reloadFabric()
            reloadFabricAPI()
        }
        if (loaderSupports.isLegacyFabricSupports) {
            reloadLegacyFabric()
            reloadLegacyFabricAPI()
        }
        if (loaderSupports.isQuiltSupports) {
            reloadQuilt()
            reloadQuiltAPI()
        }
        if (loaderSupports.isCleanroomSupports) {
            reloadCleanroom()
        }
    }

    override fun onCleared() {
        viewModelScope.cancel()
    }
}

/**
 * Game download page (picking addons)
 * @param refreshErrorCheck refreshes the version name error check
 */
@Composable
fun DownloadGameWithAddonScreen(
    mainScreenKey: TitledNavKey?,
    downloadScreenKey: TitledNavKey?,
    downloadGameScreenKey: TitledNavKey?,
    key: NormalNavKey.DownloadGame.Addons,
    refreshErrorCheck: Any? = null,
    onInstall: (GameDownloadInfo) -> Unit = {}
) {
    val loaderSupports = rememberLoaderVerSupports(key.gameVersion)

    val viewModel = viewModel(
        key = key.toString() + "_" + loaderSupports
    ) {
        AddonsViewModel(key.gameVersion, loaderSupports)
    }

    BaseScreen(
        listOf(
            Pair(NestedNavKey.Download::class.java, mainScreenKey),
            Pair(NestedNavKey.DownloadGame::class.java, downloadScreenKey),
            Pair(NormalNavKey.DownloadGame.Addons::class.java, downloadGameScreenKey)
        )
    ) { isVisible ->
        val yOffset by swapAnimateDpAsState(
            targetValue = (-40).dp,
            swapIn = isVisible
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(x = 0, y = yOffset.roundToPx()) }
        ) {
            ScreenHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                itemContainerColor = cardColor(),
                itemContentColor = onCardColor(),
                gameVersion = key.gameVersion,
                currentAddon = viewModel.currentAddon,
                refreshIcon = viewModel.refreshIcon,
                refreshErrorCheck = refreshErrorCheck,
                onInstall = { customVersionName ->
                    onInstall(
                        GameDownloadInfo(
                            gameVersion = key.gameVersion,
                            customVersionName = customVersionName,
                            overwrite = isVersionExists(customVersionName, true),
                            optifine = viewModel.currentAddon.optifineVersion.value,
                            forge = viewModel.currentAddon.forgeVersion.value,
                            neoforge = viewModel.currentAddon.neoforgeVersion.value
                                .takeIf { loaderSupports.isNeoForgeSupports },
                            fabric = viewModel.currentAddon.fabricVersion.value
                                .takeIf { loaderSupports.isFabricSupports },
                            fabricAPI = viewModel.currentAddon.fabricAPIVersion.value
                                .takeIf { loaderSupports.isFabricSupports },
                            legacyFabric = viewModel.currentAddon.legacyFabricVersion.value
                                .takeIf { loaderSupports.isLegacyFabricSupports },
                            legacyFabricAPI = viewModel.currentAddon.legacyFabricAPIVersion.value
                                .takeIf { loaderSupports.isLegacyFabricSupports },
                            quilt = viewModel.currentAddon.quiltVersion.value
                                .takeIf { loaderSupports.isQuiltSupports },
                            quiltAPI = viewModel.currentAddon.quiltAPIVersion.value
                                .takeIf { loaderSupports.isQuiltSupports },
                            cleanroom = viewModel.currentAddon.cleanroomVersion.value
                                .takeIf { loaderSupports.isCleanroomSupports }
                        )
                    )
                }
            )

            AnimatedColumn(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .verticalScrollWithBar(state = rememberScrollState()),
                isVisible = isVisible
            ) { scope ->
                Spacer(Modifier)

                AnimatedItem(scope) { yOffset ->
                    OptiFineList(
                        modifier = Modifier.offset { IntOffset(x = 0, y = yOffset.roundToPx()) },
                        currentAddon = viewModel.currentAddon,
                        onValueChanged = { viewModel.refreshIcon() },
                        addonList = viewModel.addonList
                    ) { viewModel.reloadOptiFine() }
                }

                AnimatedItem(scope) { yOffset ->
                    ForgeList(
                        modifier = Modifier.offset { IntOffset(x = 0, y = yOffset.roundToPx()) },
                        currentAddon = viewModel.currentAddon,
                        onValueChanged = { viewModel.refreshIcon() },
                        addonList = viewModel.addonList
                    ) { viewModel.reloadForge() }
                }

                if (loaderSupports.isNeoForgeSupports) {
                    AnimatedItem(scope) { yOffset ->
                        NeoForgeList(
                            modifier = Modifier.offset {
                                IntOffset(
                                    x = 0,
                                    y = yOffset.roundToPx()
                                )
                            },
                            currentAddon = viewModel.currentAddon,
                            onValueChanged = { viewModel.refreshIcon() },
                            addonList = viewModel.addonList
                        ) { viewModel.reloadNeoForge() }
                    }
                }

                if (loaderSupports.isCleanroomSupports) {
                    AnimatedItem(scope) { yOffset ->
                        CleanroomList(
                            modifier = Modifier.offset {
                                IntOffset(
                                    x = 0,
                                    y = yOffset.roundToPx()
                                )
                            },
                            currentAddon = viewModel.currentAddon,
                            onValueChanged = { viewModel.refreshIcon() },
                            addonList = viewModel.addonList
                        ) { viewModel.reloadCleanroom() }
                    }
                }

                if (loaderSupports.isFabricSupports) {
                    AnimatedItem(scope) { yOffset ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset { IntOffset(x = 0, y = yOffset.roundToPx()) },
                        ) {
                            val isFabricAPIWarning =
                                viewModel.currentAddon.fabricVersion.value != null &&
                                        viewModel.currentAddon.fabricAPIState == AddonState.None &&
                                        !viewModel.addonList.fabricAPIList.isNullOrEmpty() &&
                                        viewModel.currentAddon.fabricAPIVersion.value == null

                            AnimatedVisibility(
                                visible = isFabricAPIWarning
                            ) {
                                AddonWarningItem(
                                    modifier = Modifier.padding(bottom = 12.dp),
                                    text = stringResource(
                                        R.string.download_game_addon_warning_api,
                                        ModLoader.FABRIC_API.displayName
                                    )
                                )
                            }

                            FabricList(
                                modifier = Modifier.fillMaxWidth(),
                                currentAddon = viewModel.currentAddon,
                                onValueChanged = { version ->
                                    viewModel.refreshIcon()
                                    //If the user manually picked Fabric
                                    if (version != null) {
                                        //the latest Fabric API auto-selects here
                                        val lastAPIVersion =
                                            viewModel.addonList.fabricAPIList?.firstOrNull()
                                        viewModel.currentAddon.fabricAPIVersion.value = lastAPIVersion
                                    }
                                },
                                addonList = viewModel.addonList
                            ) { viewModel.reloadFabric() }
                        }
                    }

                    AnimatedItem(scope) { yOffset ->
                        FabricAPIList(
                            modifier = Modifier.offset {
                                IntOffset(
                                    x = 0,
                                    y = yOffset.roundToPx()
                                )
                            },
                            currentAddon = viewModel.currentAddon,
                            onValueChanged = { viewModel.refreshIcon() },
                            addonList = viewModel.addonList
                        ) { viewModel.reloadFabricAPI() }
                    }
                }

                if (loaderSupports.isLegacyFabricSupports) {
                    AnimatedItem(scope) { yOffset ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset { IntOffset(x = 0, y = yOffset.roundToPx()) },
                        ) {
                            val isFabricAPIWarning =
                                viewModel.currentAddon.legacyFabricVersion.value != null &&
                                        viewModel.currentAddon.legacyFabricAPIState == AddonState.None &&
                                        !viewModel.addonList.legacyFabricAPIList.isNullOrEmpty() &&
                                        viewModel.currentAddon.legacyFabricAPIVersion.value == null

                            AnimatedVisibility(
                                visible = isFabricAPIWarning
                            ) {
                                AddonWarningItem(
                                    modifier = Modifier.padding(bottom = 12.dp),
                                    text = stringResource(
                                        R.string.download_game_addon_warning_api,
                                        ModLoader.LEGACY_FABRIC_API.displayName
                                    )
                                )
                            }

                            LegacyFabricList(
                                modifier = Modifier.fillMaxWidth(),
                                currentAddon = viewModel.currentAddon,
                                onValueChanged = { version ->
                                    viewModel.refreshIcon()
                                    //If the user manually picked Legacy Fabric
                                    if (version != null) {
                                        //the latest Legacy Fabric API auto-selects here
                                        val lastAPIVersion =
                                            viewModel.addonList.legacyFabricAPIList?.firstOrNull()
                                        viewModel.currentAddon.legacyFabricAPIVersion.value = lastAPIVersion
                                    }
                                },
                                addonList = viewModel.addonList
                            ) { viewModel.reloadLegacyFabric() }
                        }
                    }

                    AnimatedItem(scope) { yOffset ->
                        LegacyFabricAPIList(
                            modifier = Modifier.offset {
                                IntOffset(
                                    x = 0,
                                    y = yOffset.roundToPx()
                                )
                            },
                            currentAddon = viewModel.currentAddon,
                            onValueChanged = { viewModel.refreshIcon() },
                            addonList = viewModel.addonList
                        ) { viewModel.reloadLegacyFabricAPI() }
                    }
                }

                if (loaderSupports.isQuiltSupports) {
                    AnimatedItem(scope) { yOffset ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset { IntOffset(x = 0, y = yOffset.roundToPx()) },
                        ) {
                            val isQuiltAPIWarning =
                                viewModel.currentAddon.quiltVersion.value != null &&
                                        viewModel.currentAddon.quiltAPIState == AddonState.None &&
                                        !viewModel.addonList.quiltAPIList.isNullOrEmpty() &&
                                        viewModel.currentAddon.quiltAPIVersion.value == null

                            AnimatedVisibility(
                                visible = isQuiltAPIWarning
                            ) {
                                AddonWarningItem(
                                    modifier = Modifier.padding(bottom = 12.dp),
                                    text = stringResource(
                                        R.string.download_game_addon_warning_api,
                                        ModLoader.QUILT_API.displayName
                                    )
                                )
                            }

                            QuiltList(
                                modifier = Modifier.fillMaxWidth(),
                                currentAddon = viewModel.currentAddon,
                                onValueChanged = { version ->
                                    viewModel.refreshIcon()
                                    //If the user manually picked Quilt
                                    if (version != null) {
                                        //the latest Quilted Fabric API auto-selects here
                                        val lastAPIVersion =
                                            viewModel.addonList.quiltAPIList?.firstOrNull()
                                        viewModel.currentAddon.quiltAPIVersion.value = lastAPIVersion
                                    }
                                },
                                addonList = viewModel.addonList
                            ) { viewModel.reloadQuilt() }
                        }
                    }

                    AnimatedItem(scope) { yOffset ->
                        QuiltAPIList(
                            modifier = Modifier.offset {
                                IntOffset(
                                    x = 0,
                                    y = yOffset.roundToPx()
                                )
                            },
                            currentAddon = viewModel.currentAddon,
                            onValueChanged = { viewModel.refreshIcon() },
                            addonList = viewModel.addonList
                        ) { viewModel.reloadQuiltAPI() }
                    }
                }

                Spacer(Modifier)
            }
        }
    }
}

@Composable
private fun ScreenHeader(
    modifier: Modifier = Modifier,
    itemContainerColor: Color,
    itemContentColor: Color,
    gameVersion: String,
    currentAddon: CurrentAddon,
    refreshIcon: Any? = null,
    refreshErrorCheck: Any? = null,
    onInstall: (String) -> Unit = {}
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.width(8.dp))

            VersionIconPreview(
                modifier = Modifier.size(28.dp),
                currentAddon = currentAddon,
                refreshIcon = refreshIcon
            )

            var nameValue by remember { mutableStateOf(gameVersion) }
            //Whether the user edited the version name
            var editedByUser by remember { mutableStateOf(false) }

            AutoChangeVersionName(
                gameVersion = gameVersion,
                currentAddon = currentAddon,
                editedByUser = editedByUser,
                changeValue = {
                    nameValue = it
                }
            )

            val emptyError = stringResource(R.string.generic_cannot_empty)
            val overwriteMessage = stringResource(R.string.download_game_version_overwrite, nameValue)

            val filenameInvalidMessage = key(nameValue) {
                isFilenameInvalid(nameValue)
            }
            val isVersionOverwrite = remember(nameValue) {
                //When the target version exists, install via overwrite
                isVersionExists(nameValue, true)
            }

            val isError = remember(nameValue, refreshErrorCheck) {
                nameValue.isEmpty() || filenameInvalidMessage != null
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
                    .animateContentSize(animationSpec = getAnimateTween())
            ) {
                SimpleTextInputField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(all = 4.dp),
                    value = nameValue,
                    onValueChange = {
                        nameValue = it
                        if (!editedByUser) {
                            //The user has edited the version name
                            editedByUser = true
                        }
                    },
                    color = itemContainerColor,
                    contentColor = itemContentColor,
                    singleLine = true,
                    hint = {
                        Text(
                            text = stringResource(R.string.download_game_version_name),
                            style = TextStyle(color = itemContentColor).copy(fontSize = 12.sp)
                        )
                    }
                )

                if (isError || isVersionOverwrite) {
                    val message = if (isError) {
                        filenameInvalidMessage ?: emptyError
                    } else {
                        overwriteMessage
                    }

                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        text = message,
                        color = if (isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            val versions by VersionsManager.versions.collectAsStateWithLifecycle()
            if (versions.isNotEmpty()) {
                Row {
                    //Deliberately not stored in the viewModel, avoiding stale state across version refreshes
                    var showMenu by remember { mutableStateOf(false) }
                    //Pick the version to overwrite
                    IconButton(
                        onClick = {
                            showMenu = true
                        }
                    ) {
                        Icon(
                            painter = painterResource(
                                if (showMenu) {
                                    R.drawable.ic_menu_open
                                } else {
                                    R.drawable.ic_menu
                                }
                            ),
                            contentDescription = stringResource(R.string.download_game_version_overwrite_select)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        //A reminder Text
                        Text(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            text = stringResource(R.string.download_game_version_overwrite_select_subtitle),
                            style = MaterialTheme.typography.labelLarge
                        )

                        versions.forEach { version ->
                            DropdownMenuItem(
                                text = {
                                    CommonVersionInfoLayout(
                                        modifier = Modifier.weight(1f),
                                        version = version
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    //Update the edited name directly
                                    nameValue = version.getVersionName()
                                    editedByUser = true //counts as user-edited; prevents loader selection from overwriting
                                }
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = {
                    if (!isError) {
                        onInstall(nameValue)
                    }
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_download_2_filled),
                    contentDescription = stringResource(R.string.download_install)
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
    }
}

@Composable
private fun VersionIconPreview(
    modifier: Modifier = Modifier,
    currentAddon: CurrentAddon,
    refreshIcon: Any? = null
) {
    val iconRes = remember(refreshIcon) {
        when {
            currentAddon.optifineVersion.value != null && currentAddon.forgeVersion.value != null -> R.drawable.img_anvil //OptiFine & Forge together
            currentAddon.optifineVersion.value != null -> R.drawable.img_loader_optifine
            currentAddon.forgeVersion.value != null -> R.drawable.img_anvil
            currentAddon.neoforgeVersion.value != null -> R.drawable.img_loader_neoforge
            currentAddon.fabricVersion.value != null -> R.drawable.img_loader_fabric
            currentAddon.legacyFabricVersion.value != null -> R.drawable.img_loader_legacy_fabric
            currentAddon.quiltVersion.value != null -> R.drawable.img_loader_quilt
            currentAddon.cleanroomVersion.value != null -> R.drawable.img_loader_cleanroom
            else -> R.drawable.img_minecraft
        }
    }

    Image(
        modifier = modifier,
        painter = painterResource(id = iconRes),
        contentDescription = null
    )
}

/**
 * Auto-derives the version name from the selected Addons
 * @param editedByUser whether the user already edited the name, blocking auto-derivation
 */
@Composable
private fun AutoChangeVersionName(
    gameVersion: String,
    currentAddon: CurrentAddon,
    editedByUser: Boolean,
    changeValue: (String) -> Unit = {}
) {
    LaunchedEffect(
        currentAddon.optifineVersion.value,
        currentAddon.forgeVersion.value,
        currentAddon.neoforgeVersion.value,
        currentAddon.fabricVersion.value,
        currentAddon.legacyFabricVersion.value,
        currentAddon.quiltVersion.value,
        currentAddon.cleanroomVersion.value,
    ) {
        if (editedByUser) return@LaunchedEffect

        fun formatModloader(name: String, version: String) = "$name $version"
        fun formatOptiFine(optifine: OptiFineVersion) = formatModloader(ModLoader.OPTIFINE.displayName, optifine.realVersion)
        fun formatForge(forge: ForgeVersion) = formatModloader(ModLoader.FORGE.displayName, forge.versionName)

        val modloaderValue = buildString {
            with(currentAddon) {
                when {
                    optifineVersion.value != null && forgeVersion.value != null -> {
                        append(formatForge(forgeVersion.value!!))
                        append('-')
                        append(formatOptiFine(optifineVersion.value!!))
                    }
                    optifineVersion.value != null -> append(formatOptiFine(optifineVersion.value!!))
                    forgeVersion.value != null -> append(formatForge(forgeVersion.value!!))
                    neoforgeVersion.value != null -> append(formatModloader(ModLoader.NEOFORGE.displayName, neoforgeVersion.value!!.versionName))
                    fabricVersion.value != null -> append(formatModloader(ModLoader.FABRIC.displayName, fabricVersion.value!!.version))
                    legacyFabricVersion.value != null -> append(formatModloader(ModLoader.LEGACY_FABRIC.displayName, legacyFabricVersion.value!!.version))
                    quiltVersion.value != null -> append(formatModloader(ModLoader.QUILT.displayName, quiltVersion.value!!.version))
                    cleanroomVersion.value != null -> append(formatModloader(ModLoader.CLEANROOM.displayName, cleanroomVersion.value!!.version))
                    else -> {
                        changeValue(gameVersion)
                        return@LaunchedEffect
                    }
                }
            }
        }

        changeValue("$gameVersion $modloaderValue")
    }
}