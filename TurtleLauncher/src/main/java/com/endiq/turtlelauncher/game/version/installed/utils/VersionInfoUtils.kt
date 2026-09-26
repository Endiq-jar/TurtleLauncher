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

package com.endiq.turtlelauncher.game.version.installed.utils

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.endiq.turtlelauncher.game.addons.modloader.ModLoader
import com.endiq.turtlelauncher.game.version.installed.VersionInfo
import com.endiq.turtlelauncher.utils.logging.Logger
import com.endiq.turtlelauncher.utils.string.isNotEmptyOrBlank
import java.io.File

private const val TAG = "VersionInfoUtils"

private const val VERSION_PATTERN = """(\d+\.\d+\.\d+|\d{2}w\d{2}[a-z])"""

// "1.20.4-OptiFine_HD_U_I7_pre3"       -> 1.20.4
// "1.21.3-OptiFine_HD_U_J2_pre6"       -> 1.21.3
private val OPTIFINE_ID_REGEX = """$VERSION_PATTERN-OptiFine""".toRegex()
// "1.20.2-forge-48.1.0"                -> 1.20.2
// "1.21.3-forge-53.0.23"               -> 1.21.3
private val FORGE_REGEX = """$VERSION_PATTERN-forge""".toRegex()
// "1.7.10-Forge10.13.4.1614-1.7.10"    -> 1.7.10
// "1.7.2-10.12.2.1161-mc172"           -> 1.7.2
private val FORGE_OLD_REGEX = """^$VERSION_PATTERN-(Forge[\d.]*)-mc\d+""".toRegex()
// "fabric-loader-0.15.7-1.20.4"        -> 1.20.4
// "fabric-loader-0.16.9-1.21.3"        -> 1.21.3
private val FABRIC_REGEX = """fabric-loader-[\w.-]+-$VERSION_PATTERN""".toRegex()
// "quilt-loader-0.23.1-1.20.4"         -> 1.20.4
// "quilt-loader-0.27.1-beta.1-1.21.3"  -> 1.21.3
private val QUILT_REGEX = """quilt-loader-[\w.-]+-$VERSION_PATTERN""".toRegex()

private val LOADER_DETECTORS = listOf<(String) -> String?>(
    { id ->
        OPTIFINE_ID_REGEX.find(id)?.groupValues?.get(1)
    },
    { id ->
        FORGE_REGEX.find(id)?.groupValues?.get(1)
    },
    { id ->
        FORGE_OLD_REGEX.find(id)?.groupValues?.get(1)
    },
    { id ->
        FABRIC_REGEX.find(id)?.groupValues?.get(1)
    },
    { id ->
        QUILT_REGEX.find(id)?.groupValues?.get(1)
    }
)

/**
 * Finds the version info inside the version's json file
 * @return the version number and ModLoader info
 */
fun parseJsonToVersionInfo(jsonFile: File): VersionInfo? {
    return runCatching {
        val jsonObject = JsonParser.parseString(jsonFile.readText()).asJsonObject
        val quickPlay = runCatching {
            ensureQuickPlay(jsonObject)
        }.getOrElse { e ->
            Logger.warning(TAG, "Failed to parse Quick Play", e)
            VersionInfo.QuickPlay(
                hasQuickPlaysSupport = false,
                isQuickPlaySingleplayer = false,
                isQuickPlayMultiplayer = false
            )
        }
        val (versionId, loaderInfo) = detectMinecraftAndLoader(jsonObject)
        VersionInfo(versionId, quickPlay, loaderInfo)
    }.getOrElse {
        Logger.error(TAG, "Error parsing version json", it)
        null
    }
}

/**
 * Confirms whether Quick Play is usable
 */
private fun ensureQuickPlay(versionJson: JsonObject): VersionInfo.QuickPlay {
    var hasQuickPlaysSupport = false
    var isQuickPlaySingleplayer = false
    var isQuickPlayMultiplayer = false

    versionJson.getAsJsonObject("arguments")?.getAsJsonArray("game")?.forEach outerFor@{ element ->
        if (!element.isJsonObject) return@outerFor

        element.asJsonObject.getAsJsonArray("rules")?.forEach innerFor@{ rule ->
            if (!rule.isJsonObject) return@innerFor

            val features = rule.asJsonObject.getAsJsonObject("features") ?: return@innerFor

            hasQuickPlaysSupport = hasQuickPlaysSupport || features.get("has_quick_plays_support")?.asBoolean ?: false
            isQuickPlaySingleplayer = isQuickPlaySingleplayer || features.get("is_quick_play_singleplayer")?.asBoolean ?: false
            isQuickPlayMultiplayer = isQuickPlayMultiplayer || features.get("is_quick_play_multiplayer")?.asBoolean ?: false

            if (hasQuickPlaysSupport && isQuickPlaySingleplayer && isQuickPlayMultiplayer) {
                return@ensureQuickPlay VersionInfo.QuickPlay(
                    hasQuickPlaysSupport = true,
                    isQuickPlaySingleplayer = true,
                    isQuickPlayMultiplayer = true
                )
            }
        }
    }

    return VersionInfo.QuickPlay(
        hasQuickPlaysSupport = hasQuickPlaysSupport,
        isQuickPlaySingleplayer = isQuickPlaySingleplayer,
        isQuickPlayMultiplayer = isQuickPlayMultiplayer
    )
}

private fun detectMinecraftAndLoader(versionJson: JsonObject): Pair<String, VersionInfo.LoaderInfo?> {
    val mcVersion = extractMinecraftVersion(versionJson)
    val loaderInfo = detectModLoader(versionJson)
    return mcVersion to loaderInfo
}

private fun extractMinecraftVersion(json: JsonObject): String {
    //Try recognizing HMCL versions
    if (json.has("patches") && json.get("patches").isJsonArray) {
        val patches = json.getAsJsonArray("patches")
        if (patches.size() > 0) {
            val minecraft = patches[0].asJsonObject
            if (minecraft.has("version")) {
                return minecraft.get("version").asString
            }
        }
    }

    //Try recognizing the versions PCL-exported packs carry
    //PCL's cheeky addition [Hold W to start pondering]
    if (json.has("clientVersion") && json.get("clientVersion").isJsonPrimitive) {
        val clientVersion = json.get("clientVersion").asString
        if (clientVersion.isNotEmptyOrBlank()) return clientVersion
    }

    //Try getting info from LaunchFor (TL-installed versions)
    json.getAsJsonObject("launchFor")
        ?.getAsJsonArray("infos")
        ?.firstOrNull { it.asJsonObject["name"]?.asString == "Minecraft" }
        ?.asJsonObject
        ?.getAsJsonPrimitive("version")
        ?.asString
        ?.let {
            return it
        }

    //Read from the minecraft library
    json.getAsJsonArray("libraries")?.forEach { lib ->
        val (group, artifact, version) = lib.asJsonObject["name"].asString.split(":").let {
            Triple(it[0], it[1], it.getOrNull(2) ?: "")
        }
        if (group == "net.minecraft" && (artifact == "client" || artifact == "server")) {
            return version
        }
    }

    val id = json["id"].asString
    return if (json.has("inheritsFrom")) json["inheritsFrom"].asString
    //Try parsing the MC version from the ID
    else LOADER_DETECTORS.firstNotNullOfOrNull { it(id) } ?: id
}

/**
 * Determines ModLoader info from libraries: ModLoader name and version
 * @param versionJson the version json object
 */
private fun detectModLoader(versionJson: JsonObject): VersionInfo.LoaderInfo? {
    var hasFabric = false
    var hasLegacyFabric = false
    var hasBabric = false
    var fabricLoaderVer: String? = null

    versionJson.getAsJsonArray("libraries")?.forEach { libElement ->
        val lib = libElement.asJsonObject
        val (group, artifact, version) = lib.get("name").asString.split(":").let {
            Triple(it[0], it[1], it.getOrNull(2) ?: "")
        }

        when {
            //Fabric Loader
            group == "net.fabricmc" && artifact == "fabric-loader" -> {
                hasFabric = true
                fabricLoaderVer = version
            }

            //Legacy Fabric
            group == "net.legacyfabric" && artifact == "intermediary" -> {
                hasLegacyFabric = true
            }

            //Babric
            group == "babric" && artifact == "intermediary-upstream" -> {
                hasBabric = true
            }

            //Forge
            group == "net.minecraftforge" && (artifact == "forge" || artifact == "fmlloader") -> {
                val forgeVersion = when {
                    //New style: 1.21.4-54.0.26                -> 54.0.26
                    version.count { it == '-' } == 1 -> version.substringAfterLast('-')
                    //Old style: 1.7.10-10.13.4.1614-1.7.10    -> 10.13.4.1614
                    //Old style: 1.7.2-10.12.2.1161-mc172      -> 10.12.2.1161
                    version.count { it == '-' } >= 2 -> version.split("-").let { parts ->
                        when {
                            parts.size >= 3 && parts.last().startsWith("mc") -> parts[1]
                            parts.size >= 3 && parts[0] == parts.last() -> parts[1]
                            else -> version
                        }
                    }
                    else -> version
                }
                return VersionInfo.LoaderInfo(ModLoader.FORGE, forgeVersion)
            }

            //NeoForge
            group == "net.neoforged.fancymodloader" && artifact == "loader" -> {
                val neoVersion = versionJson.getAsJsonObject("arguments")
                    ?.getAsJsonArray("game")
                    ?.findNeoForgeVersion()
                    ?: version
                return VersionInfo.LoaderInfo(ModLoader.NEOFORGE, neoVersion)
            }

            //OptiFine
            (group == "optifine" || group == "net.optifine") && artifact == "OptiFine" ->
                return VersionInfo.LoaderInfo(ModLoader.OPTIFINE, version)

            //Quilt
            group == "org.quiltmc" && artifact == "quilt-loader" ->
                return VersionInfo.LoaderInfo(ModLoader.QUILT, version)

            //LiteLoader
            group == "com.mumfrey" && artifact == "liteloader" ->
                return VersionInfo.LoaderInfo(ModLoader.LITE_LOADER, version)

            //Cleanroom
            group == "com.cleanroommc" && artifact == "cleanroom" ->
                return VersionInfo.LoaderInfo(ModLoader.CLEANROOM, version)
        }
    }

    //The Fabric family
    if (hasFabric && fabricLoaderVer != null) {
        //Contains the Fabric loader
        val loader = if (hasLegacyFabric) {
            ModLoader.LEGACY_FABRIC
        } else if (hasBabric) {
            ModLoader.BABRIC
        } else {
            ModLoader.FABRIC
        }
        return VersionInfo.LoaderInfo(loader, fabricLoaderVer)
    }

    return null
}

/**
 * NeoForge stores its version number inside game arguments
 * Tries to find NeoForge's version inside arguments: { "game": [] }
 */
private fun JsonArray.findNeoForgeVersion(): String? {
    val args = this.mapNotNull { it.takeIf(JsonElement::isJsonPrimitive)?.asString }
    return args.zipWithNext().find { it.first == "--fml.neoForgeVersion" }?.second
}