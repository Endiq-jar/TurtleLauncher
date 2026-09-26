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

package com.endiq.turtlelauncher.game.download.assets.platform.modrinth.models

import com.endiq.turtlelauncher.game.download.assets.platform.Platform
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformClasses
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformDisplayLabel
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformFilterCode
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformProject
import com.endiq.turtlelauncher.game.download.assets.platform.UnsupportedClassesException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ModrinthSingleProject(
    /** Project slug */
    @SerialName("slug")
    val slug: String,

    /** The project's title or name */
    @SerialName("title")
    val title: String,

    /** Project summary */
    @SerialName("description")
    val description: String,

    /** List of the project's categories */
    @SerialName("categories")
    val categories: Array<String>,

    /** The project's client-side support */
    @SerialName("client_side")
    val clientSide: ModrinthSide,

    /** The project's server-side support */
    @SerialName("server_side")
    val serverSide: ModrinthSide,

    /** The project's long body description */
    @SerialName("body")
    val body: String,

    /** Project status */
    @SerialName("status")
    val status: String,

    /** Review/submission status when submitted for approval or scheduled for release */
    @SerialName("requested_status")
    val requestedStatus: String? = null,

    /** Categories that show in search but aren't main categories */
    @SerialName("additional_categories")
    val additionalCategories: Array<String>,

    /** Optional link to where the project's bugs or issues are reported */
    @SerialName("issues_url")
    val issuesUrl: String? = null,

    /** Optional link to the project's source code */
    @SerialName("source_url")
    val sourceUrl: String? = null,

    /** Optional link to the project's wiki or other relevant info */
    @SerialName("wiki_url")
    val wikiUrl: String? = null,

    /** Optional invite link to the project's Discord */
    @SerialName("discord_url")
    val discordUrl: String? = null,

    /** Donation links of the project */
    @SerialName("donation_urls")
    val donationUrls: Array<DonationUrl>,

    /** The project's project type */
    @SerialName("project_type")
    val projectType: String,

    /** The project's total downloads */
    @SerialName("downloads")
    val downloads: Long,

    /** Project icon URL */
    @SerialName("icon_url")
    val iconUrl: String? = null,

    /** The project's RGB color, extracted from its icon */
    @SerialName("color")
    val color: Int? = null,

    /** ID of the moderation thread associated with the project */
    @SerialName("thread_id")
    val threadId: String,

    @SerialName("monetization_status")
    val monetizationStatus: MonetizationStatus,

    /** The project's ID, encoded as a base62 string */
    @SerialName("id")
    val id: String,

    /** ID of the team owning the project */
    @SerialName("team")
    val team: String? = null,

    /** ID of the organization owning the project */
    @SerialName("organization")
    val organization: String? = null,

    /** Link to the project's body description. Always null, kept for legacy compatibility. */
    @SerialName("body_url")
    val bodyUrl: String? = null,

    /** Message a moderator sent about the project */
    @SerialName("moderator_message")
    val moderatorMessage: ModeratorMessage? = null,

    /** The project's release date */
    @SerialName("published")
    val published: String,

    /** Date the project was last updated */
    @SerialName("updated")
    val updated: String,

    /** Date the project's status became approved */
    @SerialName("approved")
    val approved: String? = null,

    /** Date the project was submitted to moderators */
    @SerialName("queued")
    val queued: String? = null,

    /** Total users following the project */
    @SerialName("followers")
    val followers: Long,

    /** The project's license */
    @SerialName("license")
    val license: License,

    /** List of the project's version IDs (never empty unless in draft status) */
    @SerialName("versions")
    val versions: Array<String>,

    /** List of all game versions the project supports */
    @SerialName("game_versions")
    val gameVersions: Array<String>,

    /** List of all loaders the project supports */
    @SerialName("loaders")
    val loaders: Array<String>,

    /** Images uploaded to the project's gallery */
    @SerialName("gallery")
    val gallery: Array<Gallery>
): PlatformProject {
    @Serializable
    class DonationUrl(
        /** Donation platform ID */
        @SerialName("id")
        val id: String,

        /** The donation platform this link points to */
        @SerialName("platform")
        val platform: String,

        /** URL of the donation platform and user */
        @SerialName("url")
        val url: String,
    )

    @Serializable
    class ModeratorMessage(
        /** Message a moderator left on the project */
        @SerialName("message")
        val message: String,

        /** Longer body of the moderator's message */
        @SerialName("body")
        val body: String? = null
    )

    @Serializable
    class License(
        /** The project's SPDX license ID */
        @SerialName("id")
        val id: String,

        /** The license's long name */
        @SerialName("name")
        val name: String,

        /** This license's URL */
        @SerialName("url")
        val url: String? = null
    )

    @Serializable
    class Gallery(
        /** Gallery image URL */
        @SerialName("url")
        val url: String,

        /** Whether the image is featured in the gallery */
        @SerialName("featured")
        val featured: Boolean,

        /** Gallery image title */
        @SerialName("title")
        val title: String? = null,

        /** Gallery image description */
        @SerialName("description")
        val description: String? = null,

        /** When the gallery image was created */
        @SerialName("created")
        val created: String,

        /** Ordering of the gallery image */
        @SerialName("ordering")
        val ordering: Int
    )

    override fun platform(): Platform = Platform.MODRINTH

    override fun platformId(): String = id

    override fun platformClasses(defaultClasses: PlatformClasses): PlatformClasses {
        return projectType.mapModrinthType()?.platform ?: defaultClasses
    }

    override fun platformSlug(): String = slug

    override fun platformIconUrl(): String? = iconUrl

    override fun platformTitle(): String = title

    override fun platformSummary(): String = description

    override fun platformAuthor(): String? = null

    override fun platformDownloadCount(): Long = downloads

    override fun platformFollows(): Long = followers

    override fun platformModLoaders(): List<PlatformDisplayLabel>? {
        val modloaders = loaders.mapNotNull { string ->
                ModrinthModLoaderCategory.entries.find { it.facetValue() == string }
            }.toSet().takeIf { it.isNotEmpty() }

        return modloaders?.sortedWith { o1, o2 -> o1.index() - o2.index() }
    }

    override fun checkClasses() {
        //fixme: plugin-type and datapack-type projects are tagged as mods
        if (projectType.mapModrinthType() == null) throw UnsupportedClassesException(projectType)
    }

    override fun platformCategories(classes: PlatformClasses): List<PlatformFilterCode>? {
        return categories.take(4) //without main categories, show the first 4
            .mapNotNull { string ->
                string.mapModrinthCategory(classes)
            }
            .toSet()
            .takeIf { it.isNotEmpty() }
            ?.sortedWith { o1, o2 -> o1.index() - o2.index() }
    }

    override fun platformUrls(defaultClasses: PlatformClasses): PlatformProject.Urls {
        val classes = projectType.mapModrinthType()?.platform ?: defaultClasses
        return PlatformProject.Urls(
            projectUrl = "https://modrinth.com/${classes.modrinth!!.facetValue()}/${slug}",
            sourceUrl = sourceUrl,
            issuesUrl = issuesUrl,
            wikiUrl = wikiUrl
        )
    }

    override fun platformScreenshots(): List<PlatformProject.Screenshot> {
        return gallery.map { gallery ->
            PlatformProject.Screenshot(
                imageUrl = gallery.url,
                title = gallery.title,
                description = gallery.description
            )
        }
    }
}

/**
 * @return whether the project is publicly visible
 */
fun ModrinthSingleProject.isPublic(): Boolean {
    return when (this.status) {
        "approved", "archived" -> true
        else -> false
    }
}