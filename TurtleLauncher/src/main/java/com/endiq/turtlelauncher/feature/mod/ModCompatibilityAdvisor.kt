package com.endiq.turtlelauncher.feature.mod

import com.endiq.turtlelauncher.feature.mod.parser.ModInfo
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Conservative Android-specific mod advice. This reports only known limitation patterns; it
 * never changes a mod, disables a jar, or claims that a mod is universally incompatible.
 */
object ModCompatibilityAdvisor {
    data class Environment(
        val minecraftVersion: String,
        val rendererId: String,
        val rendererName: String,
        val rendererSupportsShaderPacks: Boolean,
        val rendererUsesAndroidOpenGlTranslationLayer: Boolean
    )

    data class Finding(
        val key: String,
        val affectedMods: List<String>,
        val message: String
    )

    private enum class Concern {
        SODIUM_FAMILY,
        SHADER_PIPELINE,
        RENDER_PIPELINE,
        CONTROLLER_API,
        TOUCH_MOUSE,
        HISTORICAL_POJAV_REPORT,
        VOXY_ANDROID_STACK,
        VULKAN_API,
        WEB_BROWSER_RUNTIME,
        PLATFORM_NATIVE_INTEGRATION
    }

    private data class ModSpec(
        val displayName: String,
        val idsAndNames: Set<String>,
        val concerns: Set<Concern> = emptySet(),
        val filenamePrefixes: Set<String> = emptySet()
    )

    private data class Match(val spec: ModSpec, val version: String, val fileName: String)

    private val specs = listOf(
        ModSpec("Sodium", setOf("sodium", "sodiumfabric", "sodiumneoforge"), setOf(Concern.SODIUM_FAMILY)),
        ModSpec("Embeddium", setOf("embeddium"), setOf(Concern.SODIUM_FAMILY)),
        ModSpec("Rubidium", setOf("rubidium"), setOf(Concern.SODIUM_FAMILY)),
        ModSpec("Chlorine", setOf("chlorine"), setOf(Concern.SODIUM_FAMILY, Concern.HISTORICAL_POJAV_REPORT)),
        ModSpec("Iris", setOf("iris", "irisfabric", "irisneoforge"), setOf(Concern.SHADER_PIPELINE)),
        ModSpec("OptiFine", setOf("optifine"), setOf(Concern.SHADER_PIPELINE), setOf("optifine")),
        ModSpec("ShadersMod", setOf("shadersmod", "shadermod"), setOf(Concern.SHADER_PIPELINE), setOf("shadersmod")),
        ModSpec("Distant Horizons", setOf("distanthorizons", "distanthorizon"), setOf(Concern.RENDER_PIPELINE)),
        ModSpec("Flywheel", setOf("flywheel"), setOf(Concern.RENDER_PIPELINE)),
        ModSpec("ImmediatelyFast", setOf("immediatelyfast"), setOf(Concern.RENDER_PIPELINE)),
        ModSpec("VulkanMod", setOf("vulkanmod"), setOf(Concern.VULKAN_API)),
        ModSpec("Voxy", setOf("voxy"), setOf(Concern.VOXY_ANDROID_STACK)),
        ModSpec("Immersive Portals", setOf("immersiveportals", "immersiveportal"), setOf(Concern.RENDER_PIPELINE)),
        ModSpec("Colormatic", setOf("colormatic"), setOf(Concern.HISTORICAL_POJAV_REPORT)),
        ModSpec("Dynamic Surroundings", setOf("dynamicsurroundings", "dsurround"), setOf(Concern.HISTORICAL_POJAV_REPORT)),

        ModSpec("MidnightControls", setOf("midnightcontrols"), setOf(Concern.CONTROLLER_API)),
        ModSpec("Controllable", setOf("controllable"), setOf(Concern.CONTROLLER_API)),
        ModSpec("Legacy4J", setOf("legacy4j"), setOf(Concern.CONTROLLER_API, Concern.TOUCH_MOUSE)),
        ModSpec("Mouse Tweaks", setOf("mousetweaks"), setOf(Concern.TOUCH_MOUSE)),
        ModSpec("TouchController", setOf("touchcontroller"), setOf(Concern.CONTROLLER_API)),

        // These entries reflect a non-exhaustive PojavLauncher report from 2021 only. They
        // intentionally produce historical context, not an unsupported/blocked status.
        ModSpec("Create", setOf("create"), setOf(Concern.HISTORICAL_POJAV_REPORT)),
        ModSpec("Scannable", setOf("scannable"), setOf(Concern.HISTORICAL_POJAV_REPORT)),
        ModSpec("ItemPhysic", setOf("itemphysic", "itemphysiclite"), setOf(Concern.HISTORICAL_POJAV_REPORT)),
        ModSpec("RandomPatches", setOf("randompatches"), setOf(Concern.HISTORICAL_POJAV_REPORT)),
        ModSpec("MrCrayfish's Gun Mod", setOf("cgm", "mrcrayfishgunmod", "mrcrayfishsgunmod"), setOf(Concern.HISTORICAL_POJAV_REPORT)),
        ModSpec("Immersive Vehicles", setOf("immersivevehicles"), setOf(Concern.HISTORICAL_POJAV_REPORT)),

        // Content/client mods that have platform-specific native or desktop-service paths.
        // These are informational leads, not blanket failure claims.
        ModSpec("WebDisplays", setOf("webdisplays"), setOf(Concern.WEB_BROWSER_RUNTIME)),
        ModSpec("DiscordRPC", setOf("discordrpc", "discordrichpresence"), setOf(Concern.PLATFORM_NATIVE_INTEGRATION)),
        ModSpec("Essential", setOf("essential"), setOf(Concern.PLATFORM_NATIVE_INTEGRATION))
    )

    /**
     * Return only advice relevant to the installed set and selected renderer. [modsFolder] is
     * also scanned for OptiFine/legacy ShadersMod jars, which do not always expose parseable
     * Fabric/Forge metadata.
     */
    fun inspect(
        modInfoList: List<ModInfo>,
        modsFolder: File,
        environment: Environment
    ): List<Finding> {
        val matches = specs.flatMap { spec -> findMatches(spec, modInfoList, modsFolder) }
        if (matches.isEmpty()) return emptyList()

        val findings = mutableListOf<Finding>()
        val sodiumMods = names(matches, Concern.SODIUM_FAMILY)
        if (sodiumMods.isNotEmpty() && environment.rendererUsesAndroidOpenGlTranslationLayer) {
            findings += Finding(
                "sodium-translation-layer",
                sodiumMods,
                "Sodium's current hardware guidance says Android OpenGL translation layers such as " +
                    "GL4ES and ANGLE are unsupported. That statement is Sodium-specific: forks and newer " +
                    "Vulkan backends can differ by Minecraft/mod version. The selected ${environment.rendererName} " +
                    "does not become desktop OpenGL through a launcher flag; verify this exact stack before " +
                    "changing mods or reporting a crash."
            )
        }

        val shaderMods = names(matches, Concern.SHADER_PIPELINE)
        if (shaderMods.isNotEmpty() && !environment.rendererSupportsShaderPacks) {
            findings += Finding(
                "shader-renderer",
                shaderMods,
                "${shaderMods.joinToString(", ")} uses a shader/render pipeline, but ${environment.rendererName} " +
                    "is not listed by TurtleLauncher as shader-pack capable. A shader setting cannot add missing " +
                    "OpenGL features. If shaders are required, try a renderer marked for shader packs (MobileGlues, " +
                    "VirGL or Zink) only when the exact mod/version supports it; that is not a guarantee for every pack."
            )
        }

        val renderPipelineMods = names(matches, Concern.RENDER_PIPELINE)
        if (renderPipelineMods.isNotEmpty()) {
            findings += Finding(
                "render-pipeline",
                renderPipelineMods,
                "These mods alter or depend on Minecraft's render pipeline. Android renderer capabilities are " +
                    "created when the game starts, so the launcher cannot safely infer every GL extension or patch " +
                    "the mod generically. If rendering fails, compare the same Minecraft, loader and mod versions " +
                    "on another renderer and use the first relevant crash/log error; do not auto-disable the mod."
            )
        }

        val historicalMods = names(matches, Concern.HISTORICAL_POJAV_REPORT)
        if (historicalMods.isNotEmpty()) {
            findings += Finding(
                "historical-pojav-reports",
                historicalMods,
                "A non-exhaustive PojavLauncher issue from 2021 recorded reports involving these mods. It is " +
                    "historical evidence, not a current compatibility verdict. Versions and loaders have changed; " +
                    "keep the mods installed unless a reproducible crash log points to one, then test that mod " +
                    "alone with the exact Minecraft/loader version."
            )
        }

        val controllerMods = names(matches, Concern.CONTROLLER_API)
        if (controllerMods.isNotEmpty()) {
            findings += Finding(
                "controller-api",
                controllerMods,
                "TurtleLauncher forwards Android gamepad input through its own game controls, but that does not " +
                    "emulate every desktop LWJGL/JInput controller backend a mod may probe. Use the launcher's " +
                    "Gamepad Mapper for ordinary controls; TouchController has a dedicated proxy integration. " +
                    "A controller mod can still require its own compatible backend/version."
            )
        }

        val touchMouseMods = names(matches, Concern.TOUCH_MOUSE)
        if (touchMouseMods.isNotEmpty()) {
            findings += Finding(
                "touch-mouse-events",
                touchMouseMods,
                "On touchscreens, in-game drags are mapped to relative mouse motion while GUI pointers use scaled " +
                    "coordinates. Mods that expect raw desktop mouse events may behave differently. Test with a " +
                    "physical mouse or the launcher's alternate control profile before attributing a crash to this mod."
            )
        }

        val voxyMods = names(matches, Concern.VOXY_ANDROID_STACK)
        if (voxyMods.isNotEmpty()) {
            findings += Finding(
                "voxy-special-stack",
                voxyMods,
                "Voxy is not a drop-in Android mod. The documented community setup is specific to Zalith Launcher 2, " +
                    "Adreno/Turnip plus a Kopper-Zink desktop-OpenGL stack, a RocksDB native plugin, a compatibility " +
                    "mod, and exact Minecraft/Sodium versions. TurtleLauncher has not validated or bundled those " +
                    "third-party components; installing Voxy alone cannot provide them. See " +
                    "https://github.com/LXK-98/zalith-voxy and check its current device/version requirements."
            )
        }

        val vulkanMods = names(matches, Concern.VULKAN_API)
        if (vulkanMods.isNotEmpty()) {
            findings += Finding(
                "vulkan-api",
                vulkanMods,
                "Selecting Zink does not by itself make VulkanMod compatible: Zink uses Vulkan to implement OpenGL, " +
                    "which is different from providing the desktop Vulkan API/backend a mod may require. Check the " +
                    "VulkanMod release's own compatibility list and the crash log before changing renderers."
            )
        }

        val webDisplayMods = names(matches, Concern.WEB_BROWSER_RUNTIME)
        if (webDisplayMods.isNotEmpty()) {
            findings += Finding(
                "web-browser-native-runtime",
                webDisplayMods,
                "WebDisplays depends on MCEF/Chromium browser runtime components. TurtleLauncher's Android " +
                    "mod check already warns when MCEF is present; downloading a desktop MCEF dependency does not " +
                    "supply Android-compatible Chromium binaries. Verify that your exact WebDisplays/MCEF build " +
                    "documents Android support before troubleshooting it as a normal missing dependency."
            )
        }

        val nativeIntegrationMods = names(matches, Concern.PLATFORM_NATIVE_INTEGRATION)
        if (nativeIntegrationMods.isNotEmpty()) {
            findings += Finding(
                "platform-native-integration",
                nativeIntegrationMods,
                "These client integrations may rely on desktop native libraries or services. The mod name alone " +
                    "does not prove incompatibility; a launcher cannot replace a missing Android-ABI library or " +
                    "desktop service. Use the exact crash report and mod version before applying a workaround."
            )
        }

        return findings
    }

    /** Stable opt-out key: remind again when the renderer or installed mod versions change. */
    fun reminderValue(modInfoList: List<ModInfo>, modsFolder: File, environment: Environment): String {
        val parsed = modInfoList.map {
            "${normalize(it.id)}:${it.version.orEmpty()}:${it.file?.name.orEmpty()}"
        }
        val jars = modsFolder.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.equals("jar", ignoreCase = true) }
            .map { "jar:${it.name}:${it.length()}:${it.lastModified()}" }
        val input = buildList {
            add("${normalize(environment.rendererId)}:${environment.minecraftVersion}")
            addAll(parsed)
            addAll(jars)
        }.sorted().joinToString("\n")
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return "compat-v1-" + digest.take(12).joinToString("") { "%02x".format(it) }
    }

    private fun findMatches(spec: ModSpec, modInfoList: List<ModInfo>, modsFolder: File): List<Match> {
        val aliases = spec.idsAndNames.mapTo(hashSetOf(), ::normalize)
        val parsedMatches = modInfoList.filter { info ->
            normalize(info.id) in aliases || normalize(info.name) in aliases
        }.map { info ->
            Match(spec, info.version.orEmpty(), info.file?.name.orEmpty())
        }

        val filenameMatches = if (spec.filenamePrefixes.isEmpty()) emptyList() else {
            val prefixes = spec.filenamePrefixes.map(::normalize)
            modsFolder.listFiles()
                .orEmpty()
                .filter { file ->
                    file.isFile && file.extension.equals("jar", ignoreCase = true) &&
                        prefixes.any { normalize(file.nameWithoutExtension).startsWith(it) }
                }
                .map { file -> Match(spec, "", file.name) }
        }

        return (parsedMatches + filenameMatches).distinctBy { it.fileName.ifBlank { spec.displayName } }
    }

    private fun names(matches: List<Match>, concern: Concern): List<String> =
        matches.filter { concern in it.spec.concerns }
            .map { match ->
                if (match.version.isBlank()) match.spec.displayName
                else "${match.spec.displayName} (${match.version})"
            }
            .distinct()
            .sorted()

    private fun normalize(value: String?): String =
        value.orEmpty().lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)
}
