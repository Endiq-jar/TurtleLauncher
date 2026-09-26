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

package com.endiq.turtlelauncher.ui.screens.content.versions

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.nonInteractiveScrollbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.game.addons.modloader.ModLoader
import com.endiq.turtlelauncher.game.addons.modloader.cleanroom.CleanroomVersions
import com.endiq.turtlelauncher.game.addons.modloader.fabriclike.fabric.FabricVersions
import com.endiq.turtlelauncher.game.addons.modloader.fabriclike.legacyfabric.LegacyFabricVersions
import com.endiq.turtlelauncher.game.addons.modloader.fabriclike.quilt.QuiltVersions
import com.endiq.turtlelauncher.game.addons.modloader.forgelike.forge.ForgeVersions
import com.endiq.turtlelauncher.game.addons.modloader.forgelike.neoforge.NeoForgeVersions
import com.endiq.turtlelauncher.game.download.game.GameDownloadInfo
import com.endiq.turtlelauncher.game.version.installed.Version
import com.endiq.turtlelauncher.game.version.installed.VersionInfo
import com.endiq.turtlelauncher.ui.base.BaseScreen
import com.endiq.turtlelauncher.ui.components.AnimatedLazyColumn
import com.endiq.turtlelauncher.ui.screens.NestedNavKey
import com.endiq.turtlelauncher.ui.screens.NormalNavKey
import com.endiq.turtlelauncher.ui.screens.TitledNavKey
import com.endiq.turtlelauncher.ui.screens.content.download.game.AddonList
import com.endiq.turtlelauncher.ui.screens.content.download.game.AddonState
import com.endiq.turtlelauncher.ui.screens.content.download.game.CleanroomList
import com.endiq.turtlelauncher.ui.screens.content.download.game.CurrentAddon
import com.endiq.turtlelauncher.ui.screens.content.download.game.FabricList
import com.endiq.turtlelauncher.ui.screens.content.download.game.ForgeList
import com.endiq.turtlelauncher.ui.screens.content.download.game.LegacyFabricList
import com.endiq.turtlelauncher.ui.screens.content.download.game.LoaderVerSupports
import com.endiq.turtlelauncher.ui.screens.content.download.game.NeoForgeList
import com.endiq.turtlelauncher.ui.screens.content.download.game.QuiltList
import com.endiq.turtlelauncher.ui.screens.content.download.game.rememberLoaderVerSupports
import com.endiq.turtlelauncher.ui.screens.content.download.game.runWithState
import com.endiq.turtlelauncher.utils.logging.Logger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "UpdateLoaderScreen"

data class AddonDiffs(
    val list: List<Diff>
) {
    sealed interface Diff {
        fun getLoader(): ModLoader
    }

    /**
     * Difference: mod loader version
     * @param original the version currently used
     * @param updateTo the version to switch to
     */
    data class VersionChangeDiff(
        val modloader: ModLoader,
        val original: String,
        val updateTo: String
    ): Diff {
        override fun getLoader(): ModLoader = modloader
    }

    /**
     * Difference: the mod loader is removed
     */
    data class RemoveDiff(
        val modloader: ModLoader
    ): Diff {
        override fun getLoader(): ModLoader = modloader
    }

    /**
     * Difference: a new mod loader is installed
     * @param version the version to install
     */
    data class NewLoadDiff(
        val modloader: ModLoader,
        val version: String
    ): Diff {
        override fun getLoader(): ModLoader = modloader
    }
}

/**
 * Builds the mod loader version difference info
 */
private fun CurrentAddon.generateDiff(
    loaderInfo: VersionInfo.LoaderInfo?
): AddonDiffs {
    val diffs = mutableListOf<AddonDiffs.Diff>()

    val loaderVersions = mapOf(
        ModLoader.FORGE to forgeVersion.value,
        ModLoader.NEOFORGE to neoforgeVersion.value,
        ModLoader.FABRIC to fabricVersion.value,
        ModLoader.LEGACY_FABRIC to legacyFabricVersion.value,
        ModLoader.QUILT to quiltVersion.value,
        ModLoader.CLEANROOM to cleanroomVersion.value
    )

    if (loaderInfo == null) {
        loaderVersions.forEach { (loader, version) ->
            if (version != null) {
                diffs.add(
                    AddonDiffs.NewLoadDiff(
                        modloader = loader,
                        version = version.getAddonVersion()
                    )
                )
            }
        }
        return AddonDiffs(list = diffs.toList())
    }

    val currentLoader = loaderInfo.loader
    val currentVersion = loaderInfo.version

    val currentAddonVersion = if (loaderVersions.containsKey(currentLoader)) {
        loaderVersions[currentLoader]
    } else {
        error("The launcher does not support automatically downloading this loader: ${currentLoader.displayName}")
    }

    //Changes to the current loader
    if (currentAddonVersion == null) {
        diffs.add(AddonDiffs.RemoveDiff(modloader = currentLoader))
    } else {
        diffs.add(
            AddonDiffs.VersionChangeDiff(
                modloader = currentLoader,
                original = currentVersion,
                updateTo = currentAddonVersion.getAddonVersion()
            )
        )
    }

    //Changes for the newly installed loader
    loaderVersions.forEach { (loader, version) ->
        if (loader != currentLoader && version != null) {
            diffs.add(
                AddonDiffs.NewLoadDiff(
                    modloader = loader,
                    version = version.getAddonVersion()
                )
            )
        }
    }

    return AddonDiffs(list = diffs.toList())
}

private class AddonsViewModel(
    private val gameVersion: String,
    private val loaderInfo: VersionInfo.LoaderInfo?,
    private val loaderSupports: LoaderVerSupports
) : ViewModel() {
    private val mutex = Mutex()

    val addonList = AddonList()
    val currentAddon = CurrentAddon()

    /** Whether the game's mod loader was identified */
    private var isLoaderVersionFound: Boolean = false
    /** Whether every loader finished initializing */
    var isLoaded by mutableStateOf(false)
        private set
    /** Whether the user may tap the update button */
    var canUpdate by mutableStateOf(false)
        private set

    private suspend fun updateLoadedState() {
        mutex.withLock {
            if (isLoaded) return@withLock
            //The game's loader was identified, or every loader list finished loading
            val isLoaded0 = isLoaderVersionFound || buildList {
                add(currentAddon.forgeState)
                if (loaderSupports.isNeoForgeSupports) {
                    add(currentAddon.neoforgeState)
                }
                if (loaderSupports.isFabricSupports) {
                    add(currentAddon.fabricState)
                }
                if (loaderSupports.isLegacyFabricSupports) {
                    add(currentAddon.legacyFabricState)
                }
                if (loaderSupports.isQuiltSupports) {
                    add(currentAddon.quiltState)
                }
                if (loaderSupports.isCleanroomSupports) {
                    add(currentAddon.cleanroomState)
                }
            }.all { it == AddonState.None }
            if (isLoaded0) Logger.info(TAG, "Game’s mod loader found, or all mod loaders loaded.")
            isLoaded = isLoaded0
        }
    }

    fun checkCanUpdate() {
        if (!isLoaded) {
            //Initialization incomplete
            canUpdate = false
            return
        }

        val unselectedLoader = buildList {
            add(currentAddon.forgeVersion)
            if (loaderSupports.isNeoForgeSupports) {
                add(addonList.neoforgeList)
            }
            if (loaderSupports.isFabricSupports) {
                add(addonList.fabricList)
            }
            if (loaderSupports.isLegacyFabricSupports) {
                add(addonList.legacyFabricList)
            }
            if (loaderSupports.isQuiltSupports) {
                add(addonList.quiltList)
            }
            if (loaderSupports.isCleanroomSupports) {
                add(addonList.cleanroomList)
            }
        }.isEmpty()

        if (loaderInfo == null) {
            //Without a loader on the version, decide by whether the user picked one
            canUpdate = !unselectedLoader
            return
        }

        if (unselectedLoader) {
            //The user picked no loader
            canUpdate = false
            return
        }

        val version = when (loaderInfo.loader) {
            ModLoader.FORGE -> currentAddon.forgeVersion.value
            ModLoader.NEOFORGE -> currentAddon.neoforgeVersion.value
            ModLoader.FABRIC -> currentAddon.fabricVersion.value
            ModLoader.LEGACY_FABRIC -> currentAddon.legacyFabricVersion.value
            ModLoader.QUILT -> currentAddon.quiltVersion.value
            ModLoader.CLEANROOM -> currentAddon.cleanroomVersion.value
            else -> null
        }

        if (version == null) {
            //An empty loader version means the user switched loaders, so allow the update
            canUpdate = true
            return
        }

        //Compare the picked loader version with the game's own loader version
        //Whether updating is allowed depends on actual changes
        canUpdate = !version.isVersion(loaderInfo.version)
    }

    fun reloadForge() {
        reloadSingleAndCheck {
            reloadForgeAsync()
        }
    }

    private suspend fun reloadForgeAsync() = runWithState(
        { currentAddon.forgeState = it },
        { ForgeVersions.fetchForgeList(gameVersion) }
    ).also { versions ->
        addonList.forgeList = versions
        if (loaderInfo == null) return@also
        if (loaderInfo.loader == ModLoader.FORGE && currentAddon.forgeVersion.value == null) {
            currentAddon.forgeVersion.value = versions?.find {
                it.isVersion(loaderInfo.version)
            }?.also { isLoaderVersionFound = true }
        }
    }

    fun reloadNeoForge() {
        reloadSingleAndCheck {
            reloadNeoForgeAsync()
        }
    }

    private suspend fun reloadNeoForgeAsync() = runWithState(
        { currentAddon.neoforgeState = it },
        { NeoForgeVersions.fetchNeoForgeList(gameVersion = gameVersion) }
    ).also { versions ->
        addonList.neoforgeList = versions
        if (loaderInfo == null) return@also
        if (loaderInfo.loader == ModLoader.NEOFORGE && currentAddon.neoforgeVersion.value == null) {
            currentAddon.neoforgeVersion.value = versions?.find {
                it.isVersion(loaderInfo.version)
            }?.also { isLoaderVersionFound = true }
        }
    }

    fun reloadFabric() {
        reloadSingleAndCheck {
            reloadFabricAsync()
        }
    }

    private suspend fun reloadFabricAsync() = runWithState(
        { currentAddon.fabricState = it },
        { FabricVersions.fetchFabricLoaderList(gameVersion) }
    ).also { versions ->
        addonList.fabricList = versions
        if (loaderInfo == null) return@also
        if (loaderInfo.loader == ModLoader.FABRIC && currentAddon.fabricVersion.value == null) {
            currentAddon.fabricVersion.value = versions?.find {
                it.isVersion(loaderInfo.version)
            }?.also { isLoaderVersionFound = true }
        }
    }

    fun reloadLegacyFabric() {
        reloadSingleAndCheck {
            reloadLegacyFabricAsync()
        }
    }

    private suspend fun reloadLegacyFabricAsync() = runWithState(
        { currentAddon.legacyFabricState = it },
        { LegacyFabricVersions.fetchFabricLoaderList(gameVersion) }
    ).also { versions ->
        addonList.legacyFabricList = versions
        if (loaderInfo == null) return@also
        if (loaderInfo.loader == ModLoader.LEGACY_FABRIC && currentAddon.legacyFabricVersion.value == null) {
            currentAddon.legacyFabricVersion.value = versions?.find {
                it.isVersion(loaderInfo.version)
            }?.also { isLoaderVersionFound = true }
        }
    }

    fun reloadQuilt() {
        reloadSingleAndCheck {
            reloadQuiltAsync()
        }
    }

    private suspend fun reloadQuiltAsync() = runWithState(
        { currentAddon.quiltState = it },
        { QuiltVersions.fetchQuiltLoaderList(gameVersion) }
    ).also { versions ->
        addonList.quiltList = versions
        if (loaderInfo == null) return@also
        if (loaderInfo.loader == ModLoader.QUILT && currentAddon.quiltVersion.value == null) {
            currentAddon.quiltVersion.value = versions?.find {
                it.isVersion(loaderInfo.version)
            }?.also { isLoaderVersionFound = true }
        }
    }


    fun reloadCleanroom() {
        reloadSingleAndCheck {
            reloadCleanroomAsync()
        }
    }

    private suspend fun reloadCleanroomAsync() = runWithState(
        { currentAddon.cleanroomState = it },
        { CleanroomVersions.fetchLoaderList(gameVersion) }
    ).also { versions ->
        addonList.cleanroomList = versions
        if (loaderInfo == null) return@also
        if (loaderInfo.loader == ModLoader.CLEANROOM && currentAddon.cleanroomVersion.value == null) {
            currentAddon.cleanroomVersion.value = versions?.find {
                it.isVersion(loaderInfo.version)
            }?.also { isLoaderVersionFound = true }
        }
    }

    /**
     * Call this to reload an individually failed list later.
     * Rechecks and applies the loaded state
     */
    private fun reloadSingleAndCheck(
        block: suspend () -> Unit
    ) {
        viewModelScope.launch {
            block()
            updateLoadedState()
        }
    }

    /**
     * Loads every mod loader version list in one shot
     */
    private fun reloadAllLoaders() {
        viewModelScope.launch {
            buildList {
                add(async {
                    reloadForgeAsync()
                    updateLoadedState()
                })
                if (loaderSupports.isNeoForgeSupports) {
                    add(async {
                        reloadNeoForgeAsync()
                        updateLoadedState()
                    })
                }
                if (loaderSupports.isFabricSupports) {
                    add(async {
                        reloadFabricAsync()
                        updateLoadedState()
                    })
                }
                if (loaderSupports.isLegacyFabricSupports) {
                    add(async {
                        reloadLegacyFabricAsync()
                        updateLoadedState()
                    })
                }
                if (loaderSupports.isQuiltSupports) {
                    add(async {
                        reloadQuiltAsync()
                        updateLoadedState()
                    })
                }
                if (loaderSupports.isCleanroomSupports) {
                    add(async {
                        reloadCleanroomAsync()
                        updateLoadedState()
                    })
                }
            }.awaitAll()
        }
    }

    init {
        reloadAllLoaders()
    }

    override fun onCleared() {
        viewModelScope.cancel()
    }
}

/**
 * Updates the mod loader
 */
@Composable
fun UpdateLoaderScreen(
    mainScreenKey: TitledNavKey?,
    versionsScreenKey: TitledNavKey?,
    version: Version,
    backToMainScreen: () -> Unit,
    onInstall: (AddonDiffs, GameDownloadInfo) -> Unit
) {
    val versionInfo = remember(version) {
        version.getVersionInfo() ?: error("Using the \"Loader Update Screen\" is not supported for versions with unspecified version information.")
    }
    if (versionInfo.loaderInfo?.loader?.autoDownloadable == false) {
        //Automatic install unsupported: forbid entering this screen
        //Unreachable in theory; kept as a safety net
        backToMainScreen()
        return
    }

    val loaderSupports = rememberLoaderVerSupports(versionInfo.minecraftVersion)

    val viewModel = viewModel(
        key = version.toString() + "_" + "UpdateLoader" + "_" + loaderSupports
    ) {
        AddonsViewModel(
            gameVersion = versionInfo.minecraftVersion,
            loaderInfo = versionInfo.loaderInfo,
            loaderSupports = loaderSupports
        )
    }

    BaseScreen(
        levels1 = listOf(
            Pair(NestedNavKey.VersionSettings::class.java, mainScreenKey)
        ),
        Triple(NormalNavKey.Versions.UpdateLoader, versionsScreenKey, false)
    ) { isVisible ->
        val unLoaded = stringResource(R.string.versions_update_loader_waiting_for_others).takeIf { !viewModel.isLoaded }

        val scrollState = rememberLazyListState()
        AnimatedLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nonInteractiveScrollbar(
                    state = scrollState.scrollIndicatorState!!,
                    orientation = Orientation.Vertical,
                ),
            isVisible = isVisible,
            contentPadding = PaddingValues(all = 12.dp),
            state = scrollState,
        ) { scope ->
            animatedItem(scope) { yOffset ->
                ForgeList(
                    modifier = Modifier.offset { IntOffset(x = 0, y = yOffset.roundToPx()) },
                    currentAddon = viewModel.currentAddon,
                    addonList = viewModel.addonList,
                    error = unLoaded,
                    onValueChanged = { viewModel.checkCanUpdate() },
                    onReload = { viewModel.reloadForge() }
                )
            }

            if (loaderSupports.isNeoForgeSupports) {
                animatedItem(scope) { yOffset ->
                    NeoForgeList(
                        modifier = Modifier.offset { IntOffset(x = 0, y = yOffset.roundToPx()) },
                        currentAddon = viewModel.currentAddon,
                        addonList = viewModel.addonList,
                        error = unLoaded,
                        onValueChanged = { viewModel.checkCanUpdate() },
                        onReload = { viewModel.reloadNeoForge() }
                    )
                }
            }

            if (loaderSupports.isCleanroomSupports) {
                animatedItem(scope) { yOffset ->
                    CleanroomList(
                        modifier = Modifier.offset { IntOffset(x = 0, y = yOffset.roundToPx()) },
                        currentAddon = viewModel.currentAddon,
                        addonList = viewModel.addonList,
                        error = unLoaded,
                        onValueChanged = { viewModel.checkCanUpdate() },
                        onReload = { viewModel.reloadCleanroom() }
                    )
                }
            }

            if (loaderSupports.isFabricSupports) {
                animatedItem(scope) { yOffset ->
                    FabricList(
                        modifier = Modifier.offset { IntOffset(x = 0, y = yOffset.roundToPx()) },
                        currentAddon = viewModel.currentAddon,
                        addonList = viewModel.addonList,
                        error = unLoaded,
                        onValueChanged = { viewModel.checkCanUpdate() },
                        onReload = { viewModel.reloadFabric() }
                    )
                }
            }

            if (loaderSupports.isLegacyFabricSupports) {
                animatedItem(scope) { yOffset ->
                    LegacyFabricList(
                        modifier = Modifier.offset { IntOffset(x = 0, y = yOffset.roundToPx()) },
                        currentAddon = viewModel.currentAddon,
                        addonList = viewModel.addonList,
                        error = unLoaded,
                        onValueChanged = { viewModel.checkCanUpdate() },
                        onReload = { viewModel.reloadLegacyFabric() }
                    )
                }
            }

            if (loaderSupports.isQuiltSupports) {
                animatedItem(scope) { yOffset ->
                    QuiltList(
                        modifier = Modifier.offset { IntOffset(x = 0, y = yOffset.roundToPx()) },
                        currentAddon = viewModel.currentAddon,
                        addonList = viewModel.addonList,
                        error = unLoaded,
                        onValueChanged = { viewModel.checkCanUpdate() },
                        onReload = { viewModel.reloadQuilt() }
                    )
                }
            }

            animatedItem(scope) { yOffset ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(x = 0, y = yOffset.roundToPx()) },
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        enabled = viewModel.canUpdate,
                        onClick = {
                            onInstall(
                                viewModel.currentAddon.generateDiff(versionInfo.loaderInfo),
                                GameDownloadInfo(
                                    gameVersion = versionInfo.minecraftVersion,
                                    customVersionName = version.getVersionName(),
                                    forge = viewModel.currentAddon.forgeVersion.value,
                                    neoforge = viewModel.currentAddon.neoforgeVersion.value
                                        .takeIf { loaderSupports.isNeoForgeSupports },
                                    fabric = viewModel.currentAddon.fabricVersion.value
                                        .takeIf { loaderSupports.isFabricSupports },
                                    legacyFabric = viewModel.currentAddon.legacyFabricVersion.value
                                        .takeIf { loaderSupports.isLegacyFabricSupports },
                                    quilt = viewModel.currentAddon.quiltVersion.value
                                        .takeIf { loaderSupports.isQuiltSupports },
                                    cleanroom = viewModel.currentAddon.cleanroomVersion.value
                                        .takeIf { loaderSupports.isCleanroomSupports }
                                )
                            )
                        }
                    ) {
                        Text(text = stringResource(R.string.download_install))
                    }
                }
            }
        }
    }
}