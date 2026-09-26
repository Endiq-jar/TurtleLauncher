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

package com.endiq.turtlelauncher.utils.device

import com.endiq.turtlelauncher.game.version.installed.utils.isBiggerVer
import com.endiq.turtlelauncher.game.version.installed.utils.isLowerVer
import org.jackhuang.hmcl.util.versioning.GameVersionNumber

/** Matches the trailing numeric segment of a version */
private val NUMBER_SUFFIX = Regex("^(.*?)(\\d+)$")

/** Increments the version's numeric tail */
private fun String.nextNumberVersion(): String? {
    val match = NUMBER_SUFFIX.find(this) ?: return null
    val number = match.groupValues[2].toIntOrNull() ?: return null
    return match.groupValues[1] + (number + 1)
}

/** Decrements the version's numeric tail */
private fun String.prevNumberVersion(): String? {
    val match = NUMBER_SUFFIX.find(this) ?: return null
    val number = match.groupValues[2].toIntOrNull() ?: return null
    if (number <= 0) return null
    return match.groupValues[1] + (number - 1)
}

/** The numeric suffix of snapshot/pre/rc versions */
private val PRERELEASE_SUFFIX = Regex("^(.+)-(?:snapshot|pre|rc)-\\d+$")

/**
 * Normalizes snapshot/pre/rc versions onto their target stable release
 */
fun normalizeMcVersion(mcVersion: String): String {
    return PRERELEASE_SUFFIX.find(mcVersion)?.groupValues?.get(1) ?: mcVersion
}

/**
 * Minecraft version dependency level on a Vulkan feature/extension
 */
enum class VulkanDependencyLevel {
    /** Depends on this item; missing means Vulkan cannot launch */
    REQUIRED,
    /** Optionally uses this item; Vulkan still launches without it */
    OPTIONAL,
    /** Not used */
    UNUSED
}

/**
 * Minecraft version range
 */
sealed interface McVersionSpan {
    /** The range's starting version */
    val since: String
    /** The range's display text */
    val displayText: String
    /** The range's exclusive upper bound; no fit unless set, used to segment evaluation ranges */
    val exclusiveEnd: String?

    operator fun contains(mcVersion: String): Boolean
}

/**
 * A single Minecraft version: matches only this release number (incl. its normalized snapshot/pre/rc)
 */
data class McSingleVersion(val version: String) : McVersionSpan {
    override val since: String
        get() = version
    override val displayText: String
        get() = version
    override val exclusiveEnd: String?
        get() = version.nextNumberVersion()

    override fun contains(mcVersion: String): Boolean {
        return normalizeMcVersion(mcVersion) == version
    }
}

/**
 * A Minecraft version interval, both ends inclusive, exact-version matching
 */
data class McVersionRange(
    val from: String,
    val to: String
) : McVersionSpan {
    override val since: String
        get() = from
    override val displayText: String
        get() = "$from-$to"
    override val exclusiveEnd: String?
        get() = to.nextNumberVersion()

    override fun contains(mcVersion: String): Boolean {
        val version = normalizeMcVersion(mcVersion)
        return !version.isLowerVer(from) && !version.isBiggerVer(to)
    }
}

/**
 * An open-ended Minecraft version range, from the start version (inclusive) through the latest
 */
data class McVersionOnward(override val since: String) : McVersionSpan {
    override val displayText: String
        get() = "$since+"
    override val exclusiveEnd: String?
        get() = null

    override fun contains(mcVersion: String): Boolean {
        return !normalizeMcVersion(mcVersion).isLowerVer(since)
    }
}

/**
 * A wrapper over a single Vulkan feature/extension
 */
data class VulkanDependency(
    val name: String,
    /** Minecraft version intervals depending on this item */
    val requiredIn: List<McVersionSpan> = emptyList(),
    /** Minecraft version intervals optionally using this item */
    val optionalIn: List<McVersionSpan> = emptyList(),
    /** Minecraft version intervals not using this item */
    val unusedIn: List<McVersionSpan> = emptyList()
) {
    /** Query a given Minecraft version's dependency level on this item */
    fun levelAt(mcVersion: String): VulkanDependencyLevel {
        return when {
            requiredIn.any { mcVersion in it } -> VulkanDependencyLevel.REQUIRED
            optionalIn.any { mcVersion in it } -> VulkanDependencyLevel.OPTIONAL
            else -> VulkanDependencyLevel.UNUSED
        }
    }
}

class VulkanDependencyBuilder(private val name: String) {
    private val requiredIn = mutableListOf<McVersionSpan>()
    private val optionalIn = mutableListOf<McVersionSpan>()
    private val unusedIn = mutableListOf<McVersionSpan>()

    /** Mark a single version as depending on this item */
    fun requiredAt(version: String) {
        requiredIn += McSingleVersion(version)
    }

    /** Mark versions [from] to [to] (inclusive) as depending on this item */
    fun requiredBetween(from: String, to: String) {
        requiredIn += McVersionRange(from, to)
    }

    /** Mark versions from [since] (inclusive) as depending on this item */
    fun requiredFrom(since: String) {
        requiredIn += McVersionOnward(since)
    }

    /** Mark a single version as optionally using this item */
    fun optionalAt(version: String) {
        optionalIn += McSingleVersion(version)
    }

    /** Mark versions [from] to [to] (inclusive) as optionally using this item */
    fun optionalBetween(from: String, to: String) {
        optionalIn += McVersionRange(from, to)
    }

    /** Mark versions from [since] (inclusive) as optionally using this item */
    fun optionalFrom(since: String) {
        optionalIn += McVersionOnward(since)
    }

    /** Mark a single version as not using this item */
    fun unusedAt(version: String) {
        unusedIn += McSingleVersion(version)
    }

    /** Mark versions [from] to [to] (inclusive) as not using this item */
    fun unusedBetween(from: String, to: String) {
        unusedIn += McVersionRange(from, to)
    }

    /** Mark versions from [since] (inclusive) as not using this item */
    fun unusedFrom(since: String) {
        unusedIn += McVersionOnward(since)
    }

    fun build(): VulkanDependency = VulkanDependency(name, requiredIn, optionalIn, unusedIn)
}

private fun vulkanDependency(name: String, block: VulkanDependencyBuilder.() -> Unit): VulkanDependency {
    return VulkanDependencyBuilder(name).apply(block).build()
}

/**
 * Extensions and features the Vulkan backend depends on per Minecraft version
 */
object VulkanRequirements {
    /** Current Vulkan checker version */
    const val VULKAN_REQUIREMENTS_VERSION = 1
    /** First Minecraft version providing the Vulkan backend */
    const val MIN_MC_VERSION = "26.2"

    /** Vulkan extensions per version */
    val EXTENSIONS: List<VulkanDependency> = listOf(
        vulkanDependency("VK_KHR_dynamic_rendering") {
            requiredBetween(MIN_MC_VERSION, "26.3")
        },
        vulkanDependency("VK_KHR_push_descriptor") {
            requiredBetween(MIN_MC_VERSION, "26.3")
        },
        vulkanDependency("VK_KHR_synchronization2") { requiredFrom(MIN_MC_VERSION) },
        vulkanDependency("VK_EXT_vertex_attribute_divisor") { requiredFrom(MIN_MC_VERSION) },
        vulkanDependency("VK_KHR_swapchain") { requiredFrom(MIN_MC_VERSION) }
    )

    /** Vulkan features per version */
    val FEATURES: List<VulkanDependency> = listOf(
        vulkanDependency("multiDrawIndirect") { requiredFrom(MIN_MC_VERSION) },
        vulkanDependency("fillModeNonSolid") {
            requiredAt(MIN_MC_VERSION)
            optionalFrom("26.3")
        },
        vulkanDependency("drawIndirectFirstInstance") {
            unusedAt(MIN_MC_VERSION)
            requiredFrom("26.3")
        },
        vulkanDependency("samplerAnisotropy") { requiredFrom(MIN_MC_VERSION) },
        vulkanDependency("shaderDrawParameters") { requiredFrom(MIN_MC_VERSION) },
        vulkanDependency("timelineSemaphore") { requiredFrom(MIN_MC_VERSION) },
        vulkanDependency("hostQueryReset") { requiredFrom(MIN_MC_VERSION) },
        vulkanDependency("synchronization2") { requiredFrom(MIN_MC_VERSION) },
        vulkanDependency("dynamicRendering") {
            requiredBetween(MIN_MC_VERSION, "26.3")
        },
        vulkanDependency("vertexAttributeInstanceRateDivisor") { requiredFrom(MIN_MC_VERSION) }
    )

    /** Minecraft version breakpoints where dependency requirements change (ascending), formed by the starts and ends of all marked ranges */
    val PROFILE_VERSIONS: List<String> = (EXTENSIONS + FEATURES)
        .flatMap { it.requiredIn + it.optionalIn + it.unusedIn }
        .flatMap { listOfNotNull(it.since, it.exclusiveEnd) }
        .distinct()
        .sortedWith { a, b -> GameVersionNumber.compare(a, b) }
}

/**
 * Device support of a single feature/extension (for a given Minecraft version)
 */
data class VulkanDependencyStatus(
    val dependency: VulkanDependency,
    val level: VulkanDependencyLevel,
    /** Whether the device supports this item */
    val supported: Boolean
)

/**
 * Device Vulkan support for a given Minecraft version
 */
data class VulkanSupport(
    val mcVersion: String,
    val extensionStatuses: List<VulkanDependencyStatus>,
    val featureStatuses: List<VulkanDependencyStatus>
) {
    private val allStatuses: List<VulkanDependencyStatus>
        get() = extensionStatuses + featureStatuses

    /** Features/extensions the version depends on but the device lacks */
    val missingRequired: List<VulkanDependencyStatus>
        get() = allStatuses.filter { it.level == VulkanDependencyLevel.REQUIRED && !it.supported }

    /** Features/extensions the version optionally uses but the device lacks */
    val missingOptional: List<VulkanDependencyStatus>
        get() = allStatuses.filter { it.level == VulkanDependencyLevel.OPTIONAL && !it.supported }

    /** Whether the device can launch this version with Vulkan */
    val isSupported: Boolean
        get() = missingRequired.isEmpty()
}

/**
 * Vulkan support across Minecraft version ranges with identical dependency needs
 */
data class VulkanProfileSupport(
    val since: String,
    /** Exclusive upper bound; null = none */
    val until: String?,
    val supported: Boolean
) {
    /** The version range's display text */
    val versionRangeText: String
        get() = when (until) {
            null -> "$since+"
            since.nextNumberVersion() -> since
            else -> until.prevNumberVersion()?.let { "$since-$it" } ?: "$since~$until"
        }
}

/**
 * Whether the device supports the given feature/extension, Minecraft-version agnostic
 */
fun VulkanCapabilities.supports(dependency: VulkanDependency): Boolean {
    val isExtension = VulkanRequirements.EXTENSIONS.any { it.name == dependency.name }
    return if (isExtension) {
        dependency.name in extensions
    } else {
        features[dependency.name] == true
    }
}

/**
 * Evaluates the device's Vulkan support for the given Minecraft version
 */
fun VulkanCapabilities.supportFor(mcVersion: String): VulkanSupport {
    fun statusOf(dependency: VulkanDependency): VulkanDependencyStatus {
        return VulkanDependencyStatus(dependency, dependency.levelAt(mcVersion), supports(dependency))
    }

    return VulkanSupport(
        mcVersion = mcVersion,
        extensionStatuses = VulkanRequirements.EXTENSIONS.map(::statusOf),
        featureStatuses = VulkanRequirements.FEATURES.map(::statusOf)
    )
}

/**
 * Evaluates the device's Vulkan support per Minecraft version range; adjacent intervals with identical support are merged;
 * Below Vulkan 1.2, the runtime baseline is not met, and every version range counts as unsupported
 */
fun VulkanCapabilities.profileSupport(): List<VulkanProfileSupport> {
    val versions = VulkanRequirements.PROFILE_VERSIONS
    if (!isVersionSupported) {
        return listOf(VulkanProfileSupport(since = versions.first(), until = null, supported = false))
    }
    return versions.mapIndexed { index, since ->
        VulkanProfileSupport(
            since = since,
            until = versions.getOrNull(index + 1),
            supported = supportFor(since).isSupported
        )
    }.fold(mutableListOf()) { merged, profile ->
        val last = merged.lastOrNull()
        if (last != null && last.supported == profile.supported) {
            merged[merged.lastIndex] = last.copy(until = profile.until)
        } else {
            merged.add(profile)
        }
        merged
    }
}
