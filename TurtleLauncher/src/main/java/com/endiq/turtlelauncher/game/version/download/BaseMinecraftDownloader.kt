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

package com.endiq.turtlelauncher.game.version.download

import com.endiq.turtlelauncher.game.addons.mirror.mapBMCLMirrorUrls
import com.endiq.turtlelauncher.game.path.getAssetsHome
import com.endiq.turtlelauncher.game.path.getGameHome
import com.endiq.turtlelauncher.game.path.getLibrariesHome
import com.endiq.turtlelauncher.game.path.getResourcesHome
import com.endiq.turtlelauncher.game.path.getVersionsHome
import com.endiq.turtlelauncher.game.versioninfo.MinecraftVersions
import com.endiq.turtlelauncher.game.versioninfo.models.AssetIndexJson
import com.endiq.turtlelauncher.game.versioninfo.models.GameManifest
import com.endiq.turtlelauncher.game.versioninfo.models.VersionManifest.Version
import com.endiq.turtlelauncher.utils.classes.Quadruple
import com.endiq.turtlelauncher.utils.file.ensureDirectory
import com.endiq.turtlelauncher.utils.file.ensureParentDirectory
import com.endiq.turtlelauncher.utils.string.isNotEmptyOrBlank
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

const val DOWNLOADER_TAG = "MinecraftDownloader"
const val MINECRAFT_RES: String = "https://resources.download.minecraft.net/"

/**
 * Designed as a generic full vanilla Minecraft download
 * @param gameHome the game directory the download targets
 */
class BaseMinecraftDownloader(
    gameHome: String = getGameHome()
) {
    //Dir
    val assetsTarget = File(getAssetsHome(gameHome)).ensureDirectory()
    val resourcesTarget = File(getResourcesHome(gameHome)).ensureDirectory()
    val versionsTarget = File(getVersionsHome(gameHome)).ensureDirectory()
    val librariesTarget = File(getLibrariesHome(gameHome)).ensureDirectory()
    val assetIndexTarget = File(assetsTarget, "indexes").ensureDirectory()

    suspend fun findVersion(version: String): Version? {
        val versionManifest = MinecraftVersions.getVersionManifest()
        return versionManifest.versions.find { it.id == version }
    }

    fun getVersionJsonPath(version: String, mcFolder: File = versionsTarget) =
        File(mcFolder, "$version/$version.json".replace("/", File.separator)).ensureParentDirectory()

    fun getVersionJarPath(version: String, mcFolder: File = versionsTarget) =
        File(mcFolder, "$version/$version.jar".replace("/", File.separator)).ensureParentDirectory()

    /**
     * Creates the version Json
     */
    suspend fun createVersionJson(version: Version): GameManifest {
        return createVersionJson(version, version.id)
    }

    /**
     * Creates the version Json
     * @param targetVersion the target version name
     */
    suspend fun createVersionJson(
        version: Version,
        targetVersion: String,
        mcFolder: File = versionsTarget
    ): GameManifest {
        return downloadAndParseJson(
            targetFile = getVersionJsonPath(targetVersion, mcFolder),
            url = version.url,
            expectedSHA = version.sha1,
            classOfT = GameManifest::class.java
        )
    }

    /**
     * Creates the assets index Json
     */
    suspend fun createAssetIndex(
        assetIndexTarget: File,
        gameManifest: GameManifest
    ): AssetIndexJson? {
        val indexFile = File(assetIndexTarget, "${gameManifest.assets}.json")
        return gameManifest.assetIndex?.let { assetIndex ->
            downloadAndParseJson(
                targetFile = indexFile,
                url = assetIndex.url,
                expectedSHA = assetIndex.sha1,
                classOfT = AssetIndexJson::class.java
            )
        }
    }

    /** Schedules the client jar download */
    fun loadClientJarDownload(
        gameManifest: GameManifest,
        clientName: String,
        mcFolder: File = versionsTarget,
        scheduleDownload: (urls: List<String>, hash: String?, targetFile: File, size: Long) -> Unit,
        scheduleCopy: (targetFile: File) -> Unit
    ) {
        val clientFile = getVersionJarPath(clientName, mcFolder)
        gameManifest.downloads?.client?.let { client ->
            scheduleDownload(client.url.mapBMCLMirrorUrls(), client.sha1, clientFile, client.size)
        } ?: run {
            //If no download method is given, it most likely needs the vanilla Jar copied
            scheduleCopy(clientFile)
        }
    }

    /** Schedules the assets download */
    suspend fun loadAssetsDownload(
        assetIndex: AssetIndexJson?,
        resourcesTargetDir: File = resourcesTarget,
        assetsTargetDir: File = assetsTarget,
        scheduleDownload: (urls: List<String>, hash: String?, targetFile: File, size: Long) -> Unit
    ) {
        assetIndex?.objects?.forEach { (path, objectInfo) ->
            currentCoroutineContext().ensureActive()

            val hashedPath = "${objectInfo.hash.substring(0, 2)}/${objectInfo.hash}"
            val targetPath = if (assetIndex.isMapToResources) resourcesTargetDir else assetsTargetDir
            val targetFile: File = if (assetIndex.isVirtual || assetIndex.isMapToResources) {
                File(targetPath, path)
            } else {
                File(targetPath, "objects/${hashedPath}".replace("/", File.separator))
            }
            scheduleDownload("$MINECRAFT_RES$hashedPath".mapBMCLMirrorUrls(), objectInfo.hash, targetFile, objectInfo.size)
        }
    }

    /** Schedules the library download */
    suspend fun loadLibraryDownloads(
        gameManifest: GameManifest,
        targetDir: File = librariesTarget,
        scheduleDownload: (urls: List<String>, hash: String?, targetFile: File, size: Long, isDownloadable: Boolean) -> Unit
    ) {
        gameManifest.libraries?.let { libraries ->
            processLibraries { libraries }
            libraries.forEach { library ->
                currentCoroutineContext().ensureActive()

                if (library.name.startsWith("org.lwjgl")) return@forEach

                val artifactPath: String = artifactToPath(library) ?: return@forEach
                val (sha1: String?, url, size, isDownloadable) = library.downloads?.let { downloads ->
                    downloads.artifact?.let { artifact ->
                        Quadruple(artifact.sha1, artifact.url, artifact.size, true)
                    } ?: return@forEach
                } ?: run {
                    var isDownloadable = true
                    val u1 = library.url
                        ?.takeIf {
                            // fix(#53): Forge could've omitted it but still left an empty value >:(
                            it.isNotEmptyOrBlank()
                        }
                        ?.replace("http://", "https://")
                        ?: run {
                            //Missing download URLs may mean the file should already be installed, not fetched ad hoc
                            //Still try the official source; a failed download proves this version's file is missing
                            isDownloadable = false
                            "https://libraries.minecraft.net/"
                        }
                    val url = u1.let { "${it}$artifactPath" }
                    Quadruple(library.sha1, url, library.size, isDownloadable)
                }

                scheduleDownload(url.mapBMCLMirrorUrls(), sha1, File(targetDir, artifactPath), size, isDownloadable)
            }
        }
    }

//    /** Schedule the logging config download */
//    fun loadLog4jXMLDownload(
//        gameManifest: GameManifest,
//        version: String,
//        scheduleDownload: (url: String, hash: String?, targetFile: File, size: Long) -> Unit
//    ) {
//        val versionLoggingTarget = getLog4jXMLPath(version)
//        gameManifest.logging?.client?.file?.let { loggingConfig ->
//            scheduleDownload(loggingConfig.url, loggingConfig.sha1, versionLoggingTarget, loggingConfig.size)
//        }
//    }
}