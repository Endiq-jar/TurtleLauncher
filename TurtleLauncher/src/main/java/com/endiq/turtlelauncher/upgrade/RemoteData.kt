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

package com.endiq.turtlelauncher.upgrade

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The latest launcher info returned remotely, checked against the local version for updates
 * @param code the latest launcher version code
 * @param version the latest launcher version name
 * @param createdAt publish time
 * @param defaultCloudDrive the default cloud-drive link
 * @param cloudDrives available cloud-drive links
 * @param files downloadable package files
 * @param defaultBody the default changelog, used when [bodies] has no matching language
 * @param bodies per-language changelogs
 */
@Serializable
data class RemoteData(
    @SerialName("code")
    val code: Int,
    @SerialName("version")
    val version: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("default_cloud_drive")
    val defaultCloudDrive: CloudDrive? = null,
    @SerialName("cloud_drives")
    val cloudDrives: List<CloudDrive> = emptyList(),
    @SerialName("files")
    val files: List<RemoteFile>,
    @SerialName("default_body")
    val defaultBody: RemoteBody,
    @SerialName("bodies")
    val bodies: List<RemoteBody>
) {
    /**
     * Netdisk links, keyed by language
     * @param language the language tag
     * @param link the netdisk link
     * @param links every concurrently supported cloud-drive link
     */
    @Serializable
    data class CloudDrive(
        @SerialName("language")
        val language: String,
        @SerialName("link")
        val link: String,
        @SerialName("links")
        val links: List<Link> = emptyList()
    ) {
        /**
         * A single supported cloud-drive link
         * @param name the cloud drive's name
         * @param link the cloud drive share link
         */
        @Serializable
        data class Link(
            @SerialName("name")
            val name: String,
            @SerialName("link")
            val link: String
        )
    }

    /**
     * The latest launcher package file
     * @param fileName a directly displayable file name
     * @param uri a link downloadable straight in browser
     * @param arch the package's architecture
     * @param size the package file size (bytes)
     */
    @Serializable
    data class RemoteFile(
        @SerialName("file_name")
        val fileName: String,
        @SerialName("uri")
        val uri: String,
        @SerialName("arch")
        val arch: Arch,
        @SerialName("size")
        val size: Long = 0L
    ) {
        @Serializable
        enum class Arch {
            @SerialName("all")
            ALL,
            @SerialName("arm")
            ARM,
            @SerialName("arm64")
            ARM64,
            @SerialName("x86")
            X86,
            @SerialName("x86_64")
            X86_64
        }
    }

    /**
     * The latest launcher changelog, keyed by language
     * @param language the language tag
     * @param markdown the Markdown content
     */
    @Serializable
    data class RemoteBody(
        @SerialName("language")
        val language: String,
        @SerialName("markdown")
        val markdown: String
    )
}
