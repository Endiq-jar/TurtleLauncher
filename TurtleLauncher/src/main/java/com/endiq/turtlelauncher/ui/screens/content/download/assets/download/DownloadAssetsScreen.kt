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

package com.endiq.turtlelauncher.ui.screens.content.download.assets.download

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.game.download.assets.favorites.FavoriteProjectsRepository
import com.endiq.turtlelauncher.game.download.assets.platform.Platform
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformClasses
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformProject
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformVersion
import com.endiq.turtlelauncher.game.download.assets.platform.cacheKey
import com.endiq.turtlelauncher.game.download.assets.platform.getProject
import com.endiq.turtlelauncher.game.download.assets.platform.getVersionById
import com.endiq.turtlelauncher.game.download.assets.platform.getVersions
import com.endiq.turtlelauncher.game.download.assets.platform.isAllNull
import com.endiq.turtlelauncher.game.download.assets.utils.ModTranslations
import com.endiq.turtlelauncher.game.download.assets.utils.getMcmodTitle
import com.endiq.turtlelauncher.game.download.assets.utils.getTranslations
import com.endiq.turtlelauncher.game.version.mod.InstalledMod
import com.endiq.turtlelauncher.game.versioninfo.filterRelease
import com.endiq.turtlelauncher.ui.AndroidStringText
import com.endiq.turtlelauncher.ui.androidText
import com.endiq.turtlelauncher.ui.base.BaseScreen
import com.endiq.turtlelauncher.ui.components.BackgroundCard
import com.endiq.turtlelauncher.ui.components.CheckChip
import com.endiq.turtlelauncher.ui.components.ScalingLabel
import com.endiq.turtlelauncher.ui.components.ShimmerBox
import com.endiq.turtlelauncher.ui.components.SimpleTextInputField
import com.endiq.turtlelauncher.ui.screens.NestedNavKey
import com.endiq.turtlelauncher.ui.screens.NormalNavKey
import com.endiq.turtlelauncher.ui.screens.TitledNavKey
import com.endiq.turtlelauncher.ui.screens.content.download.assets.elements.AssetsIcon
import com.endiq.turtlelauncher.ui.screens.content.download.assets.elements.AssetsVersionItemLayout
import com.endiq.turtlelauncher.ui.screens.content.download.assets.elements.ClassesIdentifier
import com.endiq.turtlelauncher.ui.screens.content.download.assets.elements.DependencyEntry
import com.endiq.turtlelauncher.ui.screens.content.download.assets.elements.DownloadAssetsState
import com.endiq.turtlelauncher.ui.screens.content.download.assets.elements.DownloadAssetsVersionLoading
import com.endiq.turtlelauncher.ui.screens.content.download.assets.elements.FavoriteIdentifier
import com.endiq.turtlelauncher.ui.screens.content.download.assets.elements.ProjectUrlsContent
import com.endiq.turtlelauncher.ui.screens.content.download.assets.elements.ScreenshotItemLayout
import com.endiq.turtlelauncher.ui.screens.content.download.assets.elements.VersionInfoMap
import com.endiq.turtlelauncher.ui.screens.content.download.assets.elements.initAll
import com.endiq.turtlelauncher.ui.screens.content.download.assets.elements.mapWithVersions
import com.endiq.turtlelauncher.ui.theme.onCardColor
import com.endiq.turtlelauncher.utils.animation.swapAnimateDpAsState
import com.endiq.turtlelauncher.viewmodel.EventViewModel
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Duration.Companion.milliseconds

private class DownloadScreenViewModel(
    private val platform: Platform,
    private val projectId: String,
    initialClasses: PlatformClasses
): ViewModel() {
    /**
     * Resource type; the project detail's accurate type wins
     */
    var classes by mutableStateOf(initialClasses)
        private set

    //Versions
    private var _versionsList by mutableStateOf<List<VersionInfoMap>>(emptyList())
    //Unmapped raw version data, remapped after the type fix
    private var rawVersions: List<PlatformVersion> = emptyList()
    var versionsResult by mutableStateOf<DownloadAssetsState<List<VersionInfoMap>>>(DownloadAssetsState.Getting())
    var versionsLoading by mutableStateOf<DownloadAssetsVersionLoading>(DownloadAssetsVersionLoading.None)
        private set

    var showOnlyMCRelease by mutableStateOf(true)
    var searchMCVersion by mutableStateOf("")

    fun filterWith(
        showOnlyMCRelease: Boolean = this.showOnlyMCRelease,
        searchMCVersion: String = this.searchMCVersion
    ) {
        this.showOnlyMCRelease = showOnlyMCRelease
        this.searchMCVersion = searchMCVersion
        viewModelScope.launch {
            versionsLoading = DownloadAssetsVersionLoading.None
            val infos = _versionsList.filterInfos()
            versionsResult = DownloadAssetsState.Success(infos)
        }
    }

    private fun List<VersionInfoMap>.filterInfos(): List<VersionInfoMap> {
        return filter { info ->
            (!showOnlyMCRelease || filterRelease(info.gameVersion)) &&
                    (searchMCVersion.isEmpty() || info.gameVersion.contains(searchMCVersion, true))
        }
    }

    /**
     * After the type fix, remap the version list from raw data
     */
    private fun remapVersions() {
        if (rawVersions.isEmpty()) return
        _versionsList = rawVersions.mapWithVersions(classes)
        versionsResult = DownloadAssetsState.Success(_versionsList.filterInfos())
    }

    fun getVersions() {
        viewModelScope.launch {
            versionsResult = DownloadAssetsState.Getting()
            //A reload retries the dependency projects that failed before
            failedDependencyProjects.clear()
            if (platform == Platform.CURSEFORGE) {
                versionsLoading = DownloadAssetsVersionLoading.StartLoadPage
            }

            getVersions(
                projectID = projectId,
                platform = platform,
                pageCallback = { chunk, page ->
                    versionsLoading = DownloadAssetsVersionLoading.LoadingPage(chunk, page)
                },
                onSuccess = { result ->
                    val versions: List<PlatformVersion> = result.initAll(projectId)
                    rawVersions = versions

                    //Versions show first; dependency project info caches in the background without blocking
                    _versionsList = versions.mapWithVersions(classes)
                    versionsResult = DownloadAssetsState.Success(_versionsList.filterInfos())
                    versionsLoading = DownloadAssetsVersionLoading.None

                    if (classes == PlatformClasses.MOD_PACK) return@getVersions
                    val dependencies = versions
                        .flatMap { it.platformDependencies() }
                        .distinctBy { it.cacheKey() }
                    if (dependencies.isEmpty()) return@getVersions

                    viewModelScope.launch {
                        val semaphore = Semaphore(8)
                        dependencies.map { dependency ->
                            async {
                                semaphore.withPermit {
                                    cacheDependencyProject(
                                        platform = dependency.platform,
                                        dependency = dependency
                                    )
                                }
                            }
                        }.awaitAll()
                    }
                },
                onError = {
                    versionsResult = it
                    versionsLoading = DownloadAssetsVersionLoading.None
                }
            )
        }
    }

    //Project info
    var projectResult by mutableStateOf<DownloadAssetsState<Triple<PlatformProject, ModTranslations, ModTranslations.McMod?>>>(DownloadAssetsState.Getting())

    fun getProject() {
        viewModelScope.launch {
            projectResult = DownloadAssetsState.Getting()
            getProject(
                projectID = projectId,
                platform = platform,
                onSuccess = { result ->
                    //Take the project detail's type as authoritative
                    val accurateClasses = result.platformClasses(classes)
                    if (accurateClasses != classes) {
                        classes = accurateClasses
                        remapVersions()
                    }
                    val mod = classes.getTranslations()
                    val mcmod = mod.getModBySlugId(result.platformSlug())
                    projectResult = DownloadAssetsState.Success(Triple(result, mod, mcmod))
                },
                onError = { state, _ ->
                    projectResult = state
                }
            )
        }
    }

    //Cache dependency projects
    val cachedDependencyProject = mutableStateMapOf<String, PlatformProject>()
    //The dependency wasn't found, yet many versions depend on this ghost project
    //which would waste a lot of slow fetches
    //Record the missing dependency IDs to skip refetching
    val notFoundDependencyProjects = mutableStateListOf<String>()
    //Dependency info fetch failed; show a placeholder row in the dialog
    val failedDependencyProjects = mutableStateListOf<String>()

    /**
     * Caches dependency projects
     */
    private suspend fun cacheDependencyProject(
        platform: Platform,
        dependency: PlatformVersion.PlatformDependency
    ) {
        val key = dependency.cacheKey()
        if (notFoundDependencyProjects.contains(key) || cachedDependencyProject.containsKey(key)) return

        try {
            val projectId = dependency.projectId ?: run {
                //Dependencies only pin a version; resolve the owning project from the version first
                getVersionById(
                    versionId = dependency.versionId
                        ?: error("The dependency does not provide a project id or a version id."),
                    platform = platform,
                    printLog = false
                ).platformProjectId()
            }
            getProject<PlatformProject>(
                projectID = projectId,
                platform = platform,
                onSuccess = { result ->
                    cachedDependencyProject[key] = result
                },
                onError = { _, e ->
                    if (e.isNotFound()) {
                        notFoundDependencyProjects.add(key)
                    } else {
                        if (!failedDependencyProjects.contains(key)) failedDependencyProjects.add(key)
                    }
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (e.isNotFound()) {
                notFoundDependencyProjects.add(key)
            } else {
                if (!failedDependencyProjects.contains(key)) failedDependencyProjects.add(key)
            }
        }
    }

    init {
        //After init, fetch project and version info
        getVersions()
        getProject()
    }

    override fun onCleared() {
        viewModelScope.cancel()
    }
}

/**
 * Whether the platform API reported not-found
 */
private fun Throwable.isNotFound(): Boolean =
    this is ClientRequestException && response.status.value == 404

@Composable
private fun rememberDownloadAssetsViewModel(
    key: NormalNavKey.DownloadAssets
): DownloadScreenViewModel {
    return viewModel(
        key = key.toString()
    ) {
        DownloadScreenViewModel(
            platform = key.platform,
            projectId = key.projectId,
            initialClasses = key.classes
        )
    }
}

/**
 * @param parentScreenKey the parent screen key
 * @param parentCurrentKey the parent screen's current key
 * @param currentKey the current key
 * @param installedChecker checks local install state; null skips installed marks
 */
@Composable
fun DownloadAssetsScreen(
    mainScreenKey: TitledNavKey?,
    parentScreenKey: TitledNavKey,
    parentCurrentKey: TitledNavKey?,
    currentKey: TitledNavKey?,
    key: NormalNavKey.DownloadAssets,
    eventViewModel: EventViewModel,
    onItemClicked: (PlatformClasses, PlatformVersion, iconUrl: String?, deps: List<DependencyEntry>) -> Unit,
    nestedNavKeyClass: Class<out TitledNavKey>? = null,
    versionsUIWeight: Float = 6.5f,
    projectUIWeight: Float = 3.5f,
    installedChecker: ((PlatformVersion) -> InstalledMod?)? = null,
) {
    val viewModel: DownloadScreenViewModel = rememberDownloadAssetsViewModel(key)

    BaseScreen(
        levels1 = listOf(
            Pair(nestedNavKeyClass ?: NestedNavKey.Download::class.java, mainScreenKey)
        ),
        Triple(parentScreenKey, parentCurrentKey, false),
        Triple(key, currentKey, false),
    ) { isVisible ->
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            val yOffset by swapAnimateDpAsState(targetValue = (-40).dp, swapIn = isVisible)
            Versions(
                modifier = Modifier
                    .weight(versionsUIWeight)
                    .fillMaxHeight()
                    .offset { IntOffset(x = 0, y = yOffset.roundToPx()) },
                viewModel = viewModel,
                installedChecker = installedChecker,
                onReload = { viewModel.getVersions() },
                onItemClicked = { version ->
                    val deps = version.platformDependencies().mapNotNull { dep ->
                        val key = dep.cacheKey()
                        val project = viewModel.cachedDependencyProject[key]
                        when {
                            project != null -> DependencyEntry(dep, project)
                            viewModel.notFoundDependencyProjects.contains(key) ->
                                DependencyEntry(dep, null, notFound = true)
                            viewModel.failedDependencyProjects.contains(key) ->
                                DependencyEntry(dep, null)
                            //Dependency info still being fetched
                            else -> null
                        }
                    }
                    onItemClicked(viewModel.classes, version, key.iconUrl, deps)
                },
            )

            val xOffset by swapAnimateDpAsState(
                targetValue = 40.dp,
                swapIn = isVisible,
                isHorizontal = true
            )
            ProjectInfo(
                modifier = Modifier
                    .weight(projectUIWeight)
                    .fillMaxHeight()
                    .padding(vertical = 12.dp)
                    .padding(end = 12.dp)
                    .offset { IntOffset(x = xOffset.roundToPx(), y = 0) },
                projectResult = viewModel.projectResult,
                platform = key.platform,
                projectId = key.projectId,
                classes = viewModel.classes,
                onReload = { viewModel.getProject() },
                openLink = { url ->
                    eventViewModel.sendEvent(EventViewModel.Event.OpenLink(url))
                }
            )
        }
    }
}

/**
 * All version lists
 */
@Composable
private fun Versions(
    modifier: Modifier = Modifier,
    viewModel: DownloadScreenViewModel,
    installedChecker: ((PlatformVersion) -> InstalledMod?)? = null,
    onReload: () -> Unit = {},
    onItemClicked: (PlatformVersion) -> Unit = {}
) {
    when (val versions = viewModel.versionsResult) {
        is DownloadAssetsState.Getting -> {
            Box(
                modifier.padding(all = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier.animateContentSize()
                    ) {
                        when (val state = viewModel.versionsLoading) {
                            is DownloadAssetsVersionLoading.None -> {}
                            is DownloadAssetsVersionLoading.StartLoadPage -> {
                                Text(
                                    text = stringResource(R.string.download_assets_loading_page_data),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                            }
                            is DownloadAssetsVersionLoading.LoadingPage -> {
                                Text(
                                    text = stringResource(R.string.download_assets_loaded_chunk_page, state.chunk, state.page),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    LinearWavyProgressIndicator(
                        modifier = Modifier.width(168.dp),
                        wavelength = 32.dp
                    )
                }
            }
        }
        is DownloadAssetsState.Success -> {
            Column(modifier = modifier) {
                //Simple filter criteria
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CheckChip(
                        selected = viewModel.showOnlyMCRelease,
                        onClick = {
                            viewModel.filterWith(showOnlyMCRelease = viewModel.showOnlyMCRelease.not())
                        },
                        label = {
                            Text(text = stringResource(R.string.download_assets_show_only_mc_release))
                        },
                    )

                    SimpleTextInputField(
                        modifier = Modifier.weight(1f),
                        value = viewModel.searchMCVersion,
                        onValueChange = { viewModel.filterWith(searchMCVersion = it) },
                        singleLine = true,
                        textStyle = TextStyle(color = onCardColor()).copy(fontSize = 12.sp),
                        hint = {
                            Text(
                                text = stringResource(R.string.download_assets_search_mc_versions),
                                style = TextStyle(color = onCardColor()).copy(fontSize = 12.sp)
                            )
                        }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )

                val scrollState = rememberLazyListState()

                LaunchedEffect(Unit) {
                    delay(100L.milliseconds)
                    runCatching {
                        val result = versions.result
                        val index = versions.result.indexOfFirst { it.isAdapt }
                        if (index >= 0 && index < result.size) {
                            //Auto-scroll to a matching resource version
                            scrollState.animateScrollToItem(index)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    state = scrollState
                ) {
                    items(
                        items = versions.result,
                        key = { "${it.gameVersion}_${it.loader?.getDisplayName()}" }
                    ) { info ->
                        AssetsVersionItemLayout(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            infoMap = info,
                            installedChecker = installedChecker,
                            onItemClicked = onItemClicked
                        )
                    }
                }
            }
        }
        is DownloadAssetsState.Error -> {
            Box(modifier.padding(all = 12.dp)) {
                ScalingLabel(
                    modifier = Modifier.align(Alignment.Center),
                    text = {
                        AndroidStringText(
                            text = androidText(
                                R.string.download_assets_failed_to_get_versions,
                                versions.message
                            )
                        )
                    },
                    onClick = onReload
                )
            }
        }
    }
}

/**
 * Project info section
 */
@Composable
private fun ProjectInfo(
    modifier: Modifier = Modifier,
    projectResult: DownloadAssetsState<Triple<PlatformProject, ModTranslations, ModTranslations.McMod?>>,
    platform: Platform,
    projectId: String,
    classes: PlatformClasses,
    onReload: () -> Unit = {},
    openLink: (url: String) -> Unit = {}
) {
    val context = LocalContext.current
    BackgroundCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Box(modifier = Modifier) {
            when (projectResult) {
                is DownloadAssetsState.Getting -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(all = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        //Icon/title/intro skeleton
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                ShimmerBox(
                                    modifier = Modifier
                                        .clip(shape = RoundedCornerShape(10.dp))
                                        .size(72.dp)
                                )
                                Column(
                                    modifier = Modifier.padding(top = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    //Title
                                    ShimmerBox(
                                        modifier = Modifier
                                            .fillMaxWidth(0.6f)
                                            .height(20.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                    //Intro
                                    ShimmerBox(
                                        modifier = Modifier
                                            .fillMaxWidth(0.9f)
                                            .height(16.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                }
                            }
                        }
                    }
                }
                is DownloadAssetsState.Success -> {
                    val (project, mod, mcmod) = projectResult.result
                    //Project basic info
                    val platform = remember { project.platform() }
                    val iconUrl = remember { project.platformIconUrl() }
                    val title = remember { project.platformTitle() }
                    val summary = remember { project.platformSummary() }
                    val urls = remember(classes) { project.platformUrls(classes) }
                    val screenshots = remember { project.platformScreenshots() }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(all = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        //Icon, title, intro
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AssetsIcon(
                                    modifier = Modifier.clip(shape = RoundedCornerShape(10.dp)),
                                    size = 72.dp,
                                    iconUrl = iconUrl
                                )
                                //Title, intro
                                Column(
                                    modifier = Modifier.padding(top = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = mcmod.getMcmodTitle(title, context),
                                        style = MaterialTheme.typography.titleMedium,
                                        textAlign = TextAlign.Center
                                    )
                                    summary?.let { summary ->
                                        Text(
                                            text = summary,
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        //Related links
                        if (!urls.isAllNull()) {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.download_assets_links),
                                        style = MaterialTheme.typography.titleMedium
                                    )

                                    ProjectUrlsContent(
                                        platform = platform,
                                        urls = urls,
                                        mcmod = mcmod,
                                        mod = mod,
                                        openLink = openLink,
                                    )
                                }
                            }
                        }

                        //Screenshot
                        items(screenshots) { screenshot ->
                            ScreenshotItemLayout(
                                modifier = Modifier.fillMaxWidth(),
                                screenshot = screenshot
                            )
                        }
                    }
                }
                is DownloadAssetsState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(all = 12.dp)
                    ) {
                        ScalingLabel(
                            modifier = Modifier.align(Alignment.Center),
                            text = {
                                AndroidStringText(
                                    text = androidText(
                                        R.string.download_assets_failed_to_get_project,
                                        projectResult.message
                                    )
                                )
                            },
                            onClick = onReload
                        )
                    }
                }
            }

            // Resource type and favorite toggle
            Row(
                modifier = Modifier.padding(all = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClassesIdentifier(
                    classes = classes,
                    iconSize = 16.dp,
                    textStyle = MaterialTheme.typography.labelMedium
                )

                val isFavorite = FavoriteProjectsRepository.isFavorite(platform, projectId)
                FavoriteIdentifier(
                    isFavorite = isFavorite,
                    iconSize = 16.dp,
                    textStyle = MaterialTheme.typography.labelMedium,
                    onClick = {
                        if (isFavorite) {
                            FavoriteProjectsRepository.unfavorite(platform, projectId)
                        } else {
                            //Without project data no favorite cache can form; ignore this action
                            (projectResult as? DownloadAssetsState.Success)
                                ?.result?.first?.let { project ->
                                    FavoriteProjectsRepository.favorite(project, classes)
                                }
                        }
                    }
                )
            }
        }
    }
}