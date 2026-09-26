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

package com.endiq.turtlelauncher.game.crash

import com.endiq.turtlelauncher.utils.logging.Logger
import java.io.File

/**
 * Rule-based crash analyzer for Minecraft-on-Android game logs.
 * Scans a game log for well-known error signatures and produces human
 * readable causes plus concrete fixes, inspired by Turtle-Launcher's
 * diagnostics facilities. Pure / offline: no network access.
 */
object CrashAnalyzer {
    private const val TAG = "CrashAnalyzer"
    /** Read at most the last ~1 MiB of a log so huge logs stay fast */
    private const val MAX_TAIL_BYTES = 1024 * 1024

    data class Finding(
        val cause: String,
        val suggestion: String,
        val signature: String
    )

    private data class Rule(
        val pattern: Regex,
        val cause: String,
        val suggestion: String
    ) {
        fun toFinding(match: String) = Finding(cause, suggestion, match.take(120).trim())
    }

    private val rules = listOf(
        Rule(
            Regex("OutOfMemoryError|GC overhead limit exceeded|Failed to allocate .* bytes|Java heap space"),
            "The game ran out of memory.",
            "Lower the allocated RAM in version settings, or increase it if this happened during loading. Reduce render distance/shaders for this instance."
        ),
        Rule(
            Regex("java.lang.UnsupportedClassVersionError"),
            "The Java runtime is too old for this game or mod.",
            "Use Java 17 for Minecraft 1.18-1.20.4 and Java 21 for 1.20.5+. Change the runtime under version settings."
        ),
        Rule(
            Regex("UnsatisfiedLinkError.*(lwjgl|nanovg|stb)|no lwjgl.*in java.library.path", RegexOption.IGNORE_CASE),
            "A native LWJGL library failed to load.",
            "For Fabric/Quilt instances the bundled nanovg-natives compatibility mod now fixes this at launch; otherwise reinstall the instance and avoid third-party LWJGL jars."
        ),
        Rule(
            Regex("NoSuchMethodError|NoClassDefFoundError|ClassNotFoundException"),
            "A mod calls code that does not exist in this version (mod/loader mismatch).",
            "Update the mod shown in the stack, or downgrade it to a build matching your Minecraft and mod loader."
        ),
        Rule(
            Regex("mixin apply failed|MixinApplyError|InjectionError|Error: Mixin", RegexOption.IGNORE_CASE),
            "A Mixin injection from a mod failed.",
            "The mod named in the lines above conflicts with the game or another mod. Remove or update it, then launch again."
        ),
        Rule(
            Regex("Failed to load mod loader|Incompatible mod set|ModResolutionException|requires .* but|could not find required mod", RegexOption.IGNORE_CASE),
            "Mods have missing or incompatible dependencies.",
            "Read the dependency message in the log: install the missing library mod (Fabric API, QFAPI, etc.) or the version range it asks for."
        ),
        Rule(
            Regex("Duplicate mods? found|DuplicateModException"),
            "The same mod is present twice in the mods folder.",
            "Delete one of the duplicate jars listed in the log."
        ),
        Rule(
            Regex("GLFW error 65542|wglChoosePixelFormatARB|The driver does not appear to support OpenGL", RegexOption.IGNORE_CASE),
            "The graphics driver cannot create an OpenGL context.",
            "Switch the renderer: try LTW or MobileGlues for GLES/Vulkan devices, or a Mesa/Zink renderer for modern GPUs."
        ),
        Rule(
            Regex("GLFW error 65543|GLX: Failed to load GLX|EGL: Failed to initialize"),
            "The system GL/EGL driver failed to initialize.",
            "Switch to a different renderer in launcher settings; the current one is not compatible with this device."
        ),
        Rule(
            Regex("SIGSEGV|Segmentation fault|tombstone|signal 11|Fatal signal"),
            "The native renderer or driver crashed (SIGSEGV).",
            "This is usually a GPU driver issue. Switch renderers, lower graphics and shaders, and retry. Report it if it persists with the log."
        ),
        Rule(
            Regex("vulkan|VK_ERROR", RegexOption.IGNORE_CASE),
            "A Vulkan call failed while initializing the renderer.",
            "Disable Vulkan-based renderers (e.g. MobileGlues) on devices whose Vulkan driver is incomplete; fall back to LTW or GL4ES."
        ),
        Rule(
            Regex("Failed to authenticate|Invalid credentials|InvalidAccessToken|401.*(auth|mojang|xbox)", RegexOption.IGNORE_CASE),
            "Authentication with Minecraft services failed.",
            "Log out and sign back into your account. If this is an offline (no-auth) server, this message can be ignored."
        ),
        Rule(
            Regex("ZipException|zip END header not found|jar is corrupt|EndOfCentralDirectoryRecord", RegexOption.IGNORE_CASE),
            "A jar/zip file is corrupted.",
            "Re-download the file named in the log (client jar or the mod jar); it was interrupted or damaged."
        ),
        Rule(
            Regex("Failed to load resource pack|ResourceReloadException|CompletableFuture.*resource"),
            "A resource/data pack broken the loading phase.",
            "Remove or update the resource pack listed; crash usually names the offending pack or registry entry."
        ),
        Rule(
            Regex("KeysUpdatedNotification|mod conflicts with key|Keybinding conflicts"),
            "Two mods register the same keybind.",
            "Change the conflicting keybinds in Options > Controls; this is usually harmless."
        ),
        Rule(
            Regex("Error loading config|JsonSyntaxException|Failed to parse config|compeneos|serialization"),
            "A mod configuration file is invalid.",
            "Delete the config file named in the log (files/app/config) to let the mod regenerate it."
        ),
        Rule(
            Regex("unable to connect to world|ConnectException|Connection refused|UnknownHostException", RegexOption.IGNORE_CASE),
            "Connecting to a server failed (network).",
            "Check the address/port, the server status, and your network. Offline usernames require the server to allow them."
        ),
        Rule(
            Regex("turtle-lwjgl-nanovg-natives-fix|nanovg fix"),
            "The nanovg natives compatibility mod was attempted. (informational)",
            "If nanovg errors persist, the fix jar may be missing in mods/ - reinstall it from assets/compat_mods."
        )
    )

    /**
     * Analyzes [logFile] and returns deduplicated probable causes, best match first.
     * Returns an empty list when no known pattern matches or the log can't be read.
     */
    fun analyze(logFile: File): List<Finding> {
        val text = runCatching { readTail(logFile) }.getOrNull() ?: return emptyList()
        if (text.isBlank()) return emptyList()

        val results = LinkedHashMap<String, Finding>()
        for (rule in rules) {
            val first = rule.pattern.findAll(text).map { it.value }.maxByOrNull { it.length } ?: continue
            val finding = rule.toFinding(first)
            results.putIfAbsent(finding.cause, finding)
        }
        return results.values.toList()
    }

    private fun readTail(file: File): String {
        if (!file.exists() || !file.isFile) return ""
        val length = file.length()
        return file.inputStream().buffered().use { stream ->
            val skip = (length - MAX_TAIL_BYTES).coerceAtLeast(0)
            if (skip > 0) runCatching { stream.skip(skip) }
            stream.readBytes().decodeToString()
        }
    }

    init {
        Logger.info(TAG, "Crash analyzer initialized with ${rules.size} rules")
    }
}
