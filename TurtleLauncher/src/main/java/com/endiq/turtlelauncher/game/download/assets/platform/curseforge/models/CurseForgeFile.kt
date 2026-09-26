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

package com.endiq.turtlelauncher.game.download.assets.platform.curseforge.models

import com.endiq.turtlelauncher.game.download.assets.platform.Platform
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformDependencyType
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformDisplayLabel
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformReleaseType
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformVersion
import com.endiq.turtlelauncher.game.download.assets.platform.curseforge.models.CurseForgeFile.Hash
import com.endiq.turtlelauncher.game.download.assets.platform.mirroredCurseForgeSource
import com.endiq.turtlelauncher.game.download.assets.platform.mirroredPlatformSearcher
import com.endiq.turtlelauncher.game.versioninfo.filterRelease
import com.endiq.turtlelauncher.utils.logging.Logger
import com.endiq.turtlelauncher.utils.string.parseInstant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import java.io.FileNotFoundException
import java.io.IOException
import java.time.Instant

@Serializable
class CurseForgeFile(
    /**
     * File ID
     */
    @SerialName("id")
    val id: Int,

    /**
     * ID of the game related to the file's owning project
     */
    @SerialName("gameId")
    val gameId: Int,

    /**
     * Project ID
     */
    @SerialName("modId")
    val modId: Int,

    /**
     * Whether the file is downloadable
     */
    @SerialName("isAvailable")
    val isAvailable: Boolean,

    /**
     * The file's display name
     */
    @SerialName("displayName")
    val displayName: String,

    /**
     * The exact file name
     */
    @SerialName("fileName")
    val fileName: String? = null,

    /**
     * The file's release type
     */
    @SerialName("releaseType")
    val releaseType: PlatformReleaseType,

    /**
     * The file's status
     */
    @SerialName("fileStatus")
    val fileStatus: Int,

    /**
     * File hash (md5 or sha1)
     */
    @SerialName("hashes")
    val hashes: Array<Hash>,

    /**
     * The file's timestamp
     */
    @SerialName("fileDate")
    val fileDate: String,

    /**
     * File length, in bytes
     */
    @SerialName("fileLength")
    val fileLength: Long,

    /**
     * The file's download count
     */
    @SerialName("downloadCount")
    val downloadCount: Long,

    /**
     * File size on disk
     */
    @SerialName("fileSizeOnDisk")
    val fileSizeOnDisk: Long? = null,

    /**
     * The file's download URL
     */
    @SerialName("downloadUrl")
    val downloadUrl: String? = null,

    /**
     * Game versions related to this file
     */
    @SerialName("gameVersions")
    val gameVersions: Array<String>,

    /**
     * Metadata used for sorting by game version
     */
    @SerialName("sortableGameVersions")
    val sortableGameVersions: JsonArray,

    /**
     * Dependency file list
     */
    @SerialName("dependencies")
    val dependencies: Array<Dependency>,

    @SerialName("exposeAsAlternative")
    val exposeAsAlternative: Boolean? = null,

    @SerialName("parentProjectFileId")
    val parentProjectFileId: Int? = null,

    @SerialName("alternateFileId")
    val alternateFileId: Int? = null,

    @SerialName("isServerPack")
    val isServerPack: Boolean? = null,

    @SerialName("serverPackFileId")
    val serverPackFileId: Int? = null,

    @SerialName("isEarlyAccessContent")
    val isEarlyAccessContent: Boolean? = null,

    @SerialName("earlyAccessEndDate")
    val earlyAccessEndDate: String? = null,

    @SerialName("fileFingerprint")
    val fileFingerprint: Long,

    @SerialName("modules")
    val modules: Array<Module>? = null
) : PlatformVersion {
    @Serializable
    class Hash(
        @SerialName("value")
        val value: String,
        @SerialName("algo")
        val algo: Algo
    ) {
        @Serializable(with = Algo.Serializer::class)
        enum class Algo(val code: Int) {
            SHA1(1),
            MD5(2);

            companion object {
                private val map = entries.associateBy { it.code }
                fun fromCode(code: Int): Algo = map[code] ?: error("Unknown algo code: $code")
            }

            object Serializer : KSerializer<Algo> {
                override val descriptor: SerialDescriptor =
                    PrimitiveSerialDescriptor("Algo", PrimitiveKind.INT)

                override fun deserialize(decoder: Decoder): Algo {
                    val code = decoder.decodeInt()
                    return Algo.fromCode(code)
                }

                override fun serialize(encoder: Encoder, value: Algo) {
                    encoder.encodeInt(value.code)
                }
            }
        }
    }

    @Serializable
    class Dependency(
        @SerialName("modId")
        val modId: Int,
        @SerialName("relationType")
        val relationType: PlatformDependencyType
    )

    @Serializable
    class Module(
        @SerialName("name")
        val name: String,
        @SerialName("fingerprint")
        val fingerprint: Long
    )

    /**
     * The primary file of this version
     */
    @Transient
    private lateinit var thisPrimaryFile: CurseForgeFile

    /**
     * Primary file download URL
     */
    @Transient
    private lateinit var primaryDownloadUrl: String

    override suspend fun initFile(currentProjectId: String): Boolean {
        val file = this.takeIf { it.fileName != null && it.fixedFileUrl() != null }
        // File name or download URL missing
        // Fetch this file's info separately
            ?: run {
                val fileId = id.toString()
                runCatching {
                    mirroredPlatformSearcher(
                        searchers = mirroredCurseForgeSource()
                    ) { searcher ->
                        searcher.getVersion(
                            projectID = currentProjectId,
                            fileID = fileId
                        )
                    }.data
                }.onFailure { e ->
                    when (e) {
                        is FileNotFoundException -> Logger.warning("InitCFFile", "Could not query api.curseforge.com for deleted mods: $currentProjectId, $fileId", e)
                        is IOException, is SerializationException -> Logger.warning("InitCFFile", "Unable to fetch the file name projectID=$currentProjectId, fileID=$fileId", e)
                    }
                }.getOrNull() ?: return false
            }
        val link = file.fixedFileUrl() ?: run {
            Logger.warning("InitCFFile", "No download link available, projectID=$currentProjectId, fileID=${file.id}")
            return false
        }

        thisPrimaryFile = file
        primaryDownloadUrl = link
        return true
    }

    override fun platform(): Platform = Platform.CURSEFORGE

    override fun platformId(): String = id.toString()

    override fun platformProjectId(): String = modId.toString()

    override fun platformDisplayName(): String = thisPrimaryFile.displayName

    override fun platformFileName(): String = thisPrimaryFile.fileName!!

    override fun platformGameVersion(): Array<String> {
        return thisPrimaryFile.gameVersions.filter { gameVersion ->
            filterRelease(gameVersion)
        }.toTypedArray()
    }

    override fun platformLoaders(): List<PlatformDisplayLabel> {
        return thisPrimaryFile.gameVersions.mapNotNull { loaderName ->
            CurseForgeModLoader.entries.find {
                it.getDisplayName().equals(loaderName, true)
            }
        }
    }

    override fun platformReleaseType(): PlatformReleaseType = thisPrimaryFile.releaseType

    override fun platformDependencies(): List<PlatformVersion.PlatformDependency> {
        return thisPrimaryFile.dependencies.map { dependency ->
            PlatformVersion.PlatformDependency(
                platform = platform(),
                projectId = dependency.modId.toString(),
                type = dependency.relationType
            )
        }
    }

    override fun platformDownloadCount(): Long = thisPrimaryFile.downloadCount

    override fun platformDownloadUrl(): String = primaryDownloadUrl

    override fun platformDatePublished(): Instant = parseInstant(thisPrimaryFile.fileDate)

    override fun platformSha1(): String? = thisPrimaryFile.getSHA1()

    override fun platformFileSize(): Long = thisPrimaryFile.fileLength

    override fun platformVersion(): String = displayName
}

/**
 * Returns the corrected file download URL; computed from the id and file name when null
 */
fun CurseForgeFile.fixedFileUrl(): String? {
    return downloadUrl
        ?: if (fileName != null) {
            "https://edge.forgecdn.net/files/${id / 1000}/${id % 1000}/${fileName}"
        } else {
            null
        }
}

/**
 * Returns the SHA-1 value
 */
fun CurseForgeFile.getSHA1(): String? {
    return hashes.find { hash ->
        hash.algo == Hash.Algo.SHA1
    }?.value
}