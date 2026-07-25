package com.movtery.zalithlauncher.feature.log

import com.movtery.zalithlauncher.feature.version.Version
import com.movtery.zalithlauncher.utils.path.PathManager
import java.io.File
import java.io.RandomAccessFile

/**
 * TurtleLauncher Crash & Log Analyzer.
 *
 * Looks at the launcher's "latestlog.txt" (and, if available, Minecraft's own
 * crash-report file) and tries to recognise known failure signatures, surfacing a
 * plain-language cause + a concrete checklist of fixes instead of a raw stack trace.
 *
 * This is intentionally *not* a generic log-parsing framework: it is a small,
 * hand-maintained knowledge base of patterns we've actually seen cause real crashes
 * on Android (native library loading, renderer driver bugs, OOM, broken/incompatible
 * mods, corrupted downloads, auth issues...). New patterns can be added to [rules]
 * as they're discovered.
 *
 * Used from two places:
 *  - After the game process exits with a non-zero code (a real "crash"), via
 *    [analyzeGameExit], wired into JREUtils → ErrorActivity.
 *  - While the game process is still alive but appears stuck (a "black screen" that
 *    never crashes), via [analyzeFrozenState], wired into [GameWatchdog].
 */
object CrashAnalyzer {

    enum class Severity { CRITICAL, WARNING, INFO }

    data class Diagnosis(
        val title: String,
        val cause: String,
        val fixSteps: List<String>,
        val severity: Severity = Severity.CRITICAL
    )

    private class Rule(
        val title: String,
        val matches: (text: String) -> Boolean,
        val diagnosis: (text: String) -> Diagnosis
    )

    private fun fixed(title: String, cause: String, fixSteps: List<String>, severity: Severity = Severity.CRITICAL): (String) -> Diagnosis =
        { Diagnosis(title, cause, fixSteps, severity) }

    private fun has(text: String, vararg needles: String): Boolean =
        needles.any { text.contains(it, ignoreCase = true) }

    // ── Knowledge base ───────────────────────────────────────────────────────
    // Order matters: more specific rules are checked first so generic ones don't
    // "steal" a match that already has a much better explanation.
    private val rules: List<Rule> by lazy {
        listOf(
            // 1. Native renderer library failed to load (liblwjgl.so / libpojavexec.so).
            // This was the root cause behind every crash log we've analysed so far —
            // Mojang's own version JSON re-asserts an incomplete java.library.path
            // *after* the launcher sets the correct one. Fixed in LaunchArgs.kt, but
            // kept here in case an older/custom build still hits it.
            Rule(
                title = "native_library_path",
                matches = { has(it, "Failed to locate library:", "no pojavexec in java.library.path", "no pojavexec_awt in java.library.path") },
                diagnosis = fixed(
                    "Native renderer libraries failed to load (liblwjgl.so / libpojavexec.so)",
                    "The JVM could not find the launcher's bundled native libraries on java.library.path. " +
                        "This happens when Minecraft's own version JSON re-asserts an incomplete native library " +
                        "path JVM argument after the launcher sets the correct (full) one — since the JVM applies " +
                        "-D system properties in order and the last one for a given key wins, the incomplete one " +
                        "silently takes over and the game can never find liblwjgl.so / libpojavexec.so / libopenal.so.",
                    listOf(
                        "Update to the latest TurtleLauncher build — this exact bug is fixed in LaunchArgs.kt (the native library path is now re-asserted last, after Minecraft's own JVM args, so it can't be overridden).",
                        "If it still happens after updating: open the version's settings → clear the natives cache for this Minecraft version, then relaunch.",
                        "Make sure the APK was installed as the universal/arm64 build and not a stripped split APK with native libraries removed."
                    )
                )
            ),
            // 1b. pojavInitOpenGL crashes at pc=0x0 when POJAV_RENDERER doesn't match one
            // of libpojavexec.so's own hardcoded recognized strings ("opengles",
            // "custom_gallium", "vulkan_zink", "gallium_freedreno", "gallium_panfrost",
            // "gallium_virgl" - confirmed by disassembly, see Renderers.kt's top-of-file
            // doc comment). Fixed for all six built-in renderers via
            // RendererInterface.getNativeRendererId(); kept here because a renderer
            // plugin (RendererPluginManager) could still declare a POJAV_RENDERER value
            // that isn't one of those six and hit this exact crash.
            Rule(
                title = "pojav_renderer_unrecognized",
                matches = { has(it, "pojavInitOpenGL") && has(it, "SIGSEGV") },
                diagnosis = fixed(
                    "Renderer's POJAV_RENDERER value isn't recognized by the native launcher (pojavInitOpenGL crash)",
                    "libpojavexec.so only recognizes a fixed set of legacy POJAV_RENDERER strings. An " +
                        "unrecognized value falls through its whole comparison chain into an unguarded call " +
                        "through an uninitialized function pointer, crashing at pc=0x0 on every launch, " +
                        "regardless of device or GPU.",
                    listOf(
                        "If you're on a built-in renderer (Holy GL4ES, Krypton Wrapper, VirGL, Zink, Freedreno, VGPU), update to the latest TurtleLauncher build — this is fixed in RendererInterface.getNativeRendererId().",
                        "If you're on a renderer plugin: check the plugin's declared POJAV_RENDERER value against the six libpojavexec.so recognizes (opengles, custom_gallium, vulkan_zink, gallium_freedreno, gallium_panfrost, gallium_virgl) and report a mismatch to the plugin's author.",
                        "As an immediate workaround, switch to a different renderer in Settings → Video → Renderer."
                    )
                )
            ),
            // 2. Zink renderer + libOSMesa missing OSMesaFlushFrontbuffer symbol.
            Rule(
                title = "osmesa_flush_frontbuffer",
                matches = { has(it, "OSMesaFlushFrontbuffer") },
                diagnosis = fixed(
                    "Zink/OSMesa renderer is missing a required symbol (OSMesaFlushFrontbuffer)",
                    "The bundled libOSMesa_8.so does not export OSMesaFlushFrontbuffer, which the Zink (OpenGL-over-Vulkan) " +
                        "renderer path needs on Minecraft 26.x to present finished frames.",
                    listOf(
                        "Switch renderer: Settings → Video → Renderer, pick a non-Zink option (OpenGL ES / Vulkan) instead of VulkanZink.",
                        "If you specifically need Zink, wait for a TurtleLauncher build with an updated libOSMesa_8.so that exports this symbol.",
                        "Try the Auto Graphics Optimizer (Settings → Video) so the launcher picks a renderer your GPU/driver actually supports."
                    ),
                    Severity.WARNING
                )
            ),
            // 3. AccessDeniedException creating dirs under the read-only APK lib folder.
            Rule(
                title = "access_denied_natives_dir",
                matches = { has(it, "AccessDeniedException") && has(it, "natives", "lib/arm64", "Files.createDirectories") },
                diagnosis = fixed(
                    "Permission denied while preparing native libraries (AccessDeniedException)",
                    "Minecraft 26.x tries to create subfolders inside its native-library extraction directory at startup. " +
                        "If that directory points at the app's read-only APK lib folder instead of a writable cache " +
                        "folder, every mkdir call throws AccessDeniedException and the game can't start.",
                    listOf(
                        "Update to the latest TurtleLauncher build, which always points the natives directory at a writable per-version cache folder.",
                        "Clear the app's cache (Android Settings → Apps → TurtleLauncher → Storage → Clear Cache) and relaunch.",
                        "Make sure you're not running from a read-only/sandboxed storage location (e.g. some custom ROM \"app cloning\" features)."
                    )
                )
            ),
            // 4. Out of memory.
            Rule(
                title = "oom",
                matches = { has(it, "OutOfMemoryError", "Java heap space", "GC overhead limit exceeded") },
                diagnosis = fixed(
                    "Out of memory (Java heap space)",
                    "The JVM ran out of allocated heap memory. This is usually caused by too many/heavy mods, a high " +
                        "render distance, or a RAM allocation that's too small for the modpack you're running.",
                    listOf(
                        "Increase the allocated RAM in Settings → Game → RAM Allocation (but leave enough free for Android itself).",
                        "Lower render distance / particle / entity settings in-game once it loads.",
                        "Remove resource-heavy mods/shaders/resource packs you don't need.",
                        "Close other background apps before launching to free up real device RAM."
                    )
                )
            ),
            // 5. Could not reserve heap at JVM startup (Xmx too high for the device).
            Rule(
                title = "heap_reserve_failed",
                matches = { has(it, "Could not reserve enough space for", "Could not allocate metaspace", "Failed to map") },
                diagnosis = fixed(
                    "The JVM couldn't reserve enough memory to even start",
                    "The configured RAM allocation (-Xmx) is higher than what this device can actually provide as one " +
                        "contiguous block, so the JVM fails before Minecraft even begins loading.",
                    listOf(
                        "Lower RAM Allocation in Settings → Game — try reducing it by 512–1024MB and relaunching.",
                        "On 32-bit devices, addressable memory is much lower than total RAM; keep allocation well under that limit.",
                        "Restart the device to defragment memory if this only started happening after a long uptime."
                    )
                )
            ),
            // 6. Wrong Java version for the mod/Minecraft version.
            Rule(
                title = "wrong_java_version",
                matches = { has(it, "UnsupportedClassVersionError", "has been compiled by a more recent version of the Java Runtime") },
                diagnosis = fixed(
                    "Installed Java runtime is too old for this Minecraft/mod version",
                    "A class file was compiled for a newer Java version than the runtime currently selected for this " +
                        "Minecraft version supports.",
                    listOf(
                        "Let TurtleLauncher auto-pick the Java runtime, or manually select a newer one in the version's Java settings (Java 17/21/25 depending on the Minecraft version).",
                        "If you just updated Minecraft/a modpack, delete the version's cached Java selection so it re-resolves the requirement."
                    )
                )
            ),
            // 7. Mixin application failure (a mod's bytecode patch failed to apply).
            Rule(
                title = "mixin_failure",
                matches = { has(it, "MixinApplicatorStandard", "MixinTransformationException", "mixin apply failed", "MixinApplyError") },
                diagnosis = fixed(
                    "A mod's Mixin patch failed to apply",
                    "One of your mods uses Mixin to modify Minecraft's code, and that patch could not be applied — " +
                        "almost always because the mod is for a different Minecraft version or conflicts with another mod.",
                    listOf(
                        "Check the full log for the mixin's package name (usually matches a mod's id) and update or remove that mod.",
                        "Make sure every mod targets the exact Minecraft version and mod loader you're running.",
                        "Try removing recently-added mods one at a time to find the conflicting one."
                    )
                )
            ),
            // 8. Duplicate mod jars.
            Rule(
                title = "duplicate_mods",
                matches = { has(it, "DuplicateModsFoundException", "Duplicate mod", "found multiple mods") },
                diagnosis = fixed(
                    "Duplicate mod files detected",
                    "Two or more jar files in your mods folder declare the same mod ID, which the mod loader refuses " +
                        "to load (it can't know which one you actually want).",
                    listOf(
                        "Open the mods folder and remove the older/duplicate copy — keep only one jar per mod.",
                        "Check for a mod that got downloaded twice (once manually, once via the modpack installer)."
                    )
                )
            ),
            // 9. Missing/incompatible mandatory mod dependency.
            Rule(
                title = "missing_dependency",
                matches = {
                    has(it, "missing or unsupported mandatory dependencies", "ModResolutionException",
                        "Could not find required mod", "requires {", "is missing dependencies")
                },
                diagnosis = fixed(
                    "A mod is missing one of its required dependencies",
                    "A mod declares another mod as a mandatory dependency, but that dependency isn't present (or its " +
                        "version doesn't satisfy what's required).",
                    listOf(
                        "TurtleLauncher's automatic dependency installer will try to fetch common missing dependencies from Modrinth the next time you launch this version.",
                        "If it can't be auto-resolved, check the error for the missing mod's name/ID and install it manually into the mods folder.",
                        "Make sure the dependency's version matches what the dependent mod expects (don't mix Minecraft versions)."
                    )
                )
            ),
            // 10. Vulkan device feature requirements not met.
            Rule(
                title = "vulkan_feature",
                matches = { has(it, "vkCreateDevice", "VK_ERROR", "required device feature", "VulkanBackend") && has(it, "vulkan", "Vulkan") },
                diagnosis = fixed(
                    "Vulkan renderer failed: a required GPU feature isn't supported",
                    "Minecraft's Vulkan backend requires certain GPU/driver features that this device's Vulkan driver " +
                        "doesn't expose, so device creation fails before rendering can start.",
                    listOf(
                        "Switch to OpenGL ES (Settings → Video → Renderer) if Vulkan keeps failing on this device — most renderers don't need Vulkan at all.",
                        "Update your device's GPU driver/Turnip driver plugin if one is installed.",
                        "Some Adreno/Mali chips don't expose every Vulkan 1.2 feature Minecraft's experimental Vulkan backend requires — this may not be fixable on this device."
                    )
                )
            ),
            // 11. Native (C/C++) crash — driver-level segfault, common on PowerVR GPUs.
            Rule(
                title = "native_crash",
                matches = { has(it, "SIGSEGV", "Native crash", "tombstone", "backtrace:") },
                diagnosis = fixed(
                    "The GPU driver crashed natively (SIGSEGV)",
                    "A native (non-Java) crash happened inside the graphics driver itself, not in Minecraft's Java " +
                        "code. This is most common on devices with PowerVR GPUs, where certain OpenGL code paths are " +
                        "known to crash the driver.",
                    listOf(
                        "Switch renderer: try Vulkan-Zink instead of OpenGL ES (or vice-versa) in Settings → Video.",
                        "Enable the Auto Graphics Optimizer so the launcher picks a renderer/driver combo known to work on your GPU.",
                        "Lower resolution scale, which reduces load on the driver and can avoid some driver crash conditions.",
                        "Update TurtleLauncher — PowerVR-specific renderer fixes are an active area of work."
                    ),
                    Severity.WARNING
                )
            ),
            // 12. Corrupted / truncated downloaded file.
            Rule(
                title = "corrupted_zip",
                matches = { has(it, "zip END header not found", "invalid LOC header", "ZipException", "Truncated ZIP file") },
                diagnosis = fixed(
                    "A downloaded file is corrupted or incomplete",
                    "A jar/zip file (a library, a mod, or the Minecraft client itself) is truncated or corrupted, " +
                        "usually from an interrupted download.",
                    listOf(
                        "Go to Version Manager → this version → Verify/repair files, or simply re-download the version.",
                        "If it's a manually-installed mod, delete it and download it again.",
                        "Check your storage isn't full — downloads can silently truncate when disk space runs out."
                    )
                )
            ),
            // 13. Authentication / session problems.
            Rule(
                title = "auth_failure",
                matches = { has(it, "InvalidCredentialsException", "Invalid session", "401 Unauthorized", "ForbiddenOperationException") },
                diagnosis = fixed(
                    "Account session is invalid or expired",
                    "The selected account's login session was rejected by the authentication server.",
                    listOf(
                        "Open Accounts and log back in to refresh the session.",
                        "If using a third-party login (authlib-injector/ely.by/LittleSkin), double-check the server URL is still correct.",
                        "For offline play, switch to a local/offline account instead."
                    )
                )
            ),
            // 14. Generic NoClassDefFoundError / ClassNotFoundException NOT already covered
            //     by the native-library rule above (that one also triggers these as a
            //     side-effect, so we explicitly exclude it here to avoid a confusing
            //     double diagnosis).
            Rule(
                title = "generic_missing_class",
                matches = {
                    has(it, "NoClassDefFoundError", "ClassNotFoundException") &&
                        !has(it, "Failed to locate library:", "no pojavexec in java.library.path", "UnsatisfiedLinkError")
                },
                diagnosis = { text ->
                    val missingClass = Regex("(?:NoClassDefFoundError|ClassNotFoundException):?\\s*([\\w./$]+)")
                        .find(text)?.groupValues?.getOrNull(1)
                    Diagnosis(
                        "A required class could not be found" + (missingClass?.let { " ($it)" } ?: ""),
                        "Something on the classpath is missing or incompatible — most often a mod built for a " +
                            "different mod loader/Minecraft version, or a mod whose own dependency jar is missing.",
                        listOf(
                            "If this started after adding a mod, remove the most recently added mod and try again.",
                            "Double-check every mod matches both the Minecraft version and the mod loader (Fabric/Forge/NeoForge/Quilt) you're using.",
                            "TurtleLauncher's automatic dependency installer will try to fetch common missing dependencies on the next launch."
                        )
                    )
                }
            ),
            // 15. Fast Boot was enabled for this launch — surface it as a likely contributor
            // whenever the crash also looks like a checksum/corruption-class failure, since
            // Fast Boot skips the checks that would normally have caught a bad file.
            Rule(
                title = "fast_boot_skipped_verification",
                matches = {
                    com.movtery.zalithlauncher.setting.AllSettings.fastBoot.getValue() &&
                        has(it, "zip END header not found", "invalid LOC header", "ZipException", "Truncated ZIP file",
                            "NoClassDefFoundError", "ClassNotFoundException", "Failed to locate library:")
                },
                diagnosis = fixed(
                    "Fast Boot may have let a corrupted/incomplete file through",
                    "Fast Boot was enabled for this launch, which skips the checksum verification that normally " +
                        "catches truncated or corrupted downloads before the game starts. The error above is consistent " +
                        "with a bad file slipping through.",
                    listOf(
                        "TurtleLauncher has already turned Fast Boot off for your next launch attempt — try launching again first.",
                        "If it still fails, open Version Manager → this version → Verify/repair files to force a fresh checksum pass.",
                        "You can re-enable Fast Boot afterward in Experimental Settings once you've confirmed the files are good."
                    ),
                    Severity.WARNING
                )
            )
        )
    }

    // ── Lightweight local telemetry (counts only, never leaves the device) ────
    // Tracks how many times each rule has actually fired, purely so you can see
    // which CrashAnalyzer rules matter in practice. No data is ever uploaded.
    private const val TELEMETRY_PREFS = "crash_analyzer_telemetry"

    private fun recordRuleFired(ruleTitle: String) {
        runCatching {
            val ctx = com.movtery.zalithlauncher.context.ContextExecutor.getApplication()
            val prefs = ctx.getSharedPreferences(TELEMETRY_PREFS, android.content.Context.MODE_PRIVATE)
            val current = prefs.getInt(ruleTitle, 0)
            prefs.edit().putInt(ruleTitle, current + 1).apply()
        }
    }

    /** Returns every rule's local fire-count, sorted most-frequent first. Counts only, no upload. */
    @JvmStatic
    fun getTelemetrySnapshot(context: android.content.Context): List<Pair<String, Int>> {
        return runCatching {
            val prefs = context.getSharedPreferences(TELEMETRY_PREFS, android.content.Context.MODE_PRIVATE)
            prefs.all.mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }.sortedByDescending { it.second }
        }.getOrDefault(emptyList())
    }

    // ── Custom rules (user-editable, no rebuild required) ─────────────────────
    // Stored as a JSON array in AllSettings.customCrashRules:
    // [{"pattern":"some substring or /regex/","tip":"what to do about it","title":"optional"}]
    // Matching is substring (case-insensitive) unless the pattern is wrapped in "/.../"
    // in which case it's treated as a regular expression.
    data class CustomRule(val pattern: String, val tip: String, val title: String? = null)

    @JvmStatic
    fun getCustomRules(): List<CustomRule> {
        return runCatching {
            val json = com.google.gson.JsonParser.parseString(
                com.movtery.zalithlauncher.setting.AllSettings.customCrashRules.getValue()
            ).asJsonArray
            json.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val obj = el.asJsonObject
                val pattern = obj.get("pattern")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
                val tip = obj.get("tip")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
                val title = obj.get("title")?.takeIf { it.isJsonPrimitive }?.asString
                CustomRule(pattern, tip, title)
            }
        }.getOrDefault(emptyList())
    }

    @JvmStatic
    fun addCustomRule(pattern: String, tip: String, title: String? = null) {
        val current = getCustomRules().toMutableList()
        current.add(CustomRule(pattern, tip, title))
        saveCustomRules(current)
    }

    @JvmStatic
    fun removeCustomRule(index: Int) {
        val current = getCustomRules().toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            saveCustomRules(current)
        }
    }

    private fun saveCustomRules(rules: List<CustomRule>) {
        val array = com.google.gson.JsonArray()
        rules.forEach { rule ->
            val obj = com.google.gson.JsonObject()
            obj.addProperty("pattern", rule.pattern)
            obj.addProperty("tip", rule.tip)
            rule.title?.let { obj.addProperty("title", it) }
            array.add(obj)
        }
        com.movtery.zalithlauncher.setting.AllSettings.customCrashRules.put(array.toString()).save()
    }

    private fun matchCustomRules(text: String): List<Diagnosis> {
        return getCustomRules().mapNotNull { rule ->
            val matched = runCatching {
                if (rule.pattern.startsWith("/") && rule.pattern.endsWith("/") && rule.pattern.length > 1) {
                    Regex(rule.pattern.substring(1, rule.pattern.length - 1), RegexOption.IGNORE_CASE).containsMatchIn(text)
                } else {
                    text.contains(rule.pattern, ignoreCase = true)
                }
            }.getOrDefault(false)
            if (!matched) return@mapNotNull null
            Diagnosis(
                title = rule.title ?: "Custom rule matched: ${rule.pattern.take(40)}",
                cause = "This matched a custom crash-analyzer rule you added.",
                fixSteps = listOf(rule.tip),
                severity = Severity.INFO
            )
        }
    }

    // ── Crash history (last N crashes, not just the most recent) ──────────────
    private const val MAX_HISTORY = 20

    data class CrashHistoryEntry(val timestampMs: Long, val exitCode: Int, val summary: String)

    @JvmStatic
    fun getCrashHistory(): List<CrashHistoryEntry> {
        return runCatching {
            val json = com.google.gson.JsonParser.parseString(
                com.movtery.zalithlauncher.setting.AllSettings.crashHistoryList.getValue()
            ).asJsonArray
            json.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val obj = el.asJsonObject
                val ts = obj.get("ts")?.takeIf { it.isJsonPrimitive }?.asLong ?: return@mapNotNull null
                val code = obj.get("code")?.takeIf { it.isJsonPrimitive }?.asInt ?: -1
                val summary = obj.get("summary")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
                CrashHistoryEntry(ts, code, summary)
            }
        }.getOrDefault(emptyList())
    }

    private fun pushCrashHistory(exitCode: Int, summary: String) {
        val current = getCrashHistory().toMutableList()
        current.add(0, CrashHistoryEntry(System.currentTimeMillis(), exitCode, summary.take(500)))
        while (current.size > MAX_HISTORY) current.removeAt(current.lastIndex)

        val array = com.google.gson.JsonArray()
        current.forEach { entry ->
            val obj = com.google.gson.JsonObject()
            obj.addProperty("ts", entry.timestampMs)
            obj.addProperty("code", entry.exitCode)
            obj.addProperty("summary", entry.summary)
            array.add(obj)
        }
        com.movtery.zalithlauncher.setting.AllSettings.crashHistoryList.put(array.toString()).save()
    }

    @JvmStatic
    fun clearCrashHistory() {
        com.movtery.zalithlauncher.setting.AllSettings.crashHistoryList.put("[]").save()
    }

    /** Runs every rule against [text], returning every diagnosis that matched (deduplicated by title). */
    private fun matchRules(text: String): List<Diagnosis> {
        if (text.isBlank()) return emptyList()
        val seen = HashSet<String>()
        val results = mutableListOf<Diagnosis>()
        for (rule in rules) {
            if (rule.matches(text)) {
                val diagnosis = rule.diagnosis(text)
                if (seen.add(diagnosis.title)) {
                    results.add(diagnosis)
                    recordRuleFired(rule.title)
                }
            }
        }
        matchCustomRules(text).forEach { d ->
            if (seen.add(d.title)) results.add(d)
        }
        return results
    }

    /** Best-effort extraction of the "real" exception for the generic fallback diagnosis. */
    private fun extractKeyException(text: String): String? {
        val causedByLines = Regex("^Caused by:.*$", RegexOption.MULTILINE).findAll(text).map { it.value.trim() }.toList()
        if (causedByLines.isNotEmpty()) return causedByLines.last()

        val exceptionLine = Regex("^Exception in thread \"[^\"]*\".*$", RegexOption.MULTILINE).find(text)?.value?.trim()
        if (exceptionLine != null) return exceptionLine

        val descriptionLine = Regex("^Description:\\s*(.+)$", RegexOption.MULTILINE).find(text)?.groupValues?.getOrNull(1)?.trim()
        return descriptionLine
    }

    private fun genericExitFallback(text: String): Diagnosis {
        val keyException = extractKeyException(text)
        return Diagnosis(
            title = "Unhandled error (no specific known cause matched)",
            cause = keyException?.let { "The clearest error found in the log was:\n$it" }
                ?: "No specific cause could be automatically identified from the available log output.",
            fixSteps = listOf(
                "Open the full log (in-game log viewer, or share the log file) for the complete stack trace.",
                "If this started after adding/updating a mod, try removing the most recent one.",
                "Make sure the selected Java runtime matches what this Minecraft version requires (Settings → Java).",
                "Try switching renderer (OpenGL ES / Vulkan-Zink) in Settings → Video.",
                "Verify this version's files in Version Manager in case a download is corrupted."
            ),
            severity = Severity.WARNING
        )
    }

    /** Analyzes raw log/crash-report text and returns every matched diagnosis (never empty if [logText] is non-blank). */
    @JvmStatic
    fun analyze(logText: String, crashReportText: String? = null): List<Diagnosis> {
        val combined = buildString {
            append(logText)
            if (!crashReportText.isNullOrBlank()) {
                append("\n")
                append(crashReportText)
            }
        }
        if (combined.isBlank()) return emptyList()

        val matched = matchRules(combined)
        return matched.ifEmpty { listOf(genericExitFallback(combined)) }
    }

    /**
     * Specialised analysis for a game that is still running but appears to have
     * stopped producing any output (a hang / black screen that never crashes).
     * Prefers a real matched rule if the partial log already shows one; otherwise
     * falls back to a "frozen, no crash" specific message instead of the generic
     * post-exit fallback (which assumes the process already died).
     */
    @JvmStatic
    fun analyzeFrozenState(partialLogText: String): Diagnosis {
        val matched = matchRules(partialLogText)
        if (matched.isNotEmpty()) return matched.first()

        return Diagnosis(
            title = "Game appears to be frozen (no crash reported)",
            cause = "Minecraft's process is still running, but no new log output has appeared for a while. This " +
                "usually happens during slow shader/world/datafixer loading on a weaker GPU driver, or when the " +
                "render thread is stuck waiting on the GPU without ever producing an error.",
            fixSteps = listOf(
                "Wait a little longer — the first launch after installing/updating can take a long time compiling shaders and data fixers.",
                "If it never recovers, force-close and try a different renderer (OpenGL ES / Vulkan-Zink) in Settings → Video.",
                "Lower allocated RAM slightly if the device is under memory pressure — a fully swapping device can stall like this.",
                "Check whether the device is thermal-throttling; a hot device may pause the GPU for long stretches."
            ),
            severity = Severity.WARNING
        )
    }

    /** Formats one or more diagnoses as plain text suitable for a TextView/TipDialog message. */
    @JvmStatic
    fun formatForDisplay(diagnoses: List<Diagnosis>, exitCode: Int? = null): String {
        if (diagnoses.isEmpty()) return ""
        val multiple = diagnoses.size > 1
        val sb = StringBuilder()
        sb.append(if (multiple) "Crash analysis — ${diagnoses.size} possible issues found:" else "Crash analysis:")

        diagnoses.forEachIndexed { index, d ->
            sb.append("\n\n")
            sb.append(if (multiple) "${index + 1}. ${d.title}" else d.title)
            sb.append("\n").append(d.cause)
            if (d.fixSteps.isNotEmpty()) {
                sb.append("\n\nSuggested fix:")
                d.fixSteps.forEach { step -> sb.append("\n • ").append(step) }
            }
        }

        exitCode?.let { sb.append("\n\n(Process exit code: $it)") }
        return sb.toString()
    }

    /** Reads up to [maxBytes] from the end of [file]. Returns "" if the file doesn't exist or can't be read. */
    @JvmStatic
    fun tailOf(file: File, maxBytes: Int): String {
        if (!file.exists() || !file.isFile) return ""
        return runCatching {
            val length = file.length()
            val readLength = minOf(length, maxBytes.toLong()).toInt()
            if (readLength <= 0) return@runCatching ""
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(length - readLength)
                val buffer = ByteArray(readLength)
                raf.readFully(buffer)
                String(buffer, Charsets.UTF_8)
            }
        }.getOrDefault("")
    }

    private const val MAX_LOG_BYTES = 64 * 1024
    private const val MAX_CRASH_REPORT_BYTES = 48 * 1024

    /**
     * Turns any [ModConflictDetector] findings for [gameVersion]'s mods folder into
     * crash-diagnosis entries. A detected mixin-target conflict is one of the most
     * concrete, actionable things CrashAnalyzer can tell someone — surfaced here so it
     * shows up automatically as part of crash diagnosis, not just as a separate
     * pre-launch warning dialog (see [com.movtery.zalithlauncher.feature.mod.ModAutoMaintenance]).
     */
    private fun modConflictDiagnoses(gameVersion: Version?): List<Diagnosis> {
        if (gameVersion == null) return emptyList()
        return runCatching {
            val modsFolder = File(gameVersion.getGameDir(), "mods")
            if (!modsFolder.isDirectory) return@runCatching emptyList()
            com.movtery.zalithlauncher.feature.mod.ModConflictDetector.detectConflicts(modsFolder)
                .map { conflict ->
                    Diagnosis(
                        title = "Mod conflict detected: ${conflict.modNames.joinToString(" + ")}",
                        cause = "${conflict.modNames.joinToString(", ")} all patch the same game class " +
                            "(${conflict.targetClass}) via Mixin. When two mods rewrite the same class's bytecode, " +
                            "one patch can silently break the other — this is one of the most common causes of an " +
                            "otherwise cryptic Mixin crash on startup.",
                        fixSteps = listOf(
                            "Update every mod listed above to its latest version first — this exact conflict is often already fixed upstream.",
                            "If it still conflicts after updating, remove one of the listed mods (whichever you need less) and relaunch.",
                            "Check each mod's page/Discord for a known incompatibility notice with the others listed here."
                        ),
                        severity = Severity.WARNING
                    )
                }
        }.getOrDefault(emptyList())
    }

    /**
     * Ported: if [diagnoses] is *only* the generic "no specific known cause matched" fallback,
     * ask [AiCrashAdvisor] for a suggestion and append it. Never called when a real rule (or a
     * mod-conflict finding) already explains the crash — the AI is strictly a last resort for
     * when TurtleLauncher's own knowledge base genuinely doesn't know what happened.
     */
    private fun withAiFallback(diagnoses: List<Diagnosis>, fullLogText: String): List<Diagnosis> {
        val isOnlyGenericFallback = diagnoses.size == 1 &&
            diagnoses[0].title.startsWith("Unhandled error")
        if (!isOnlyGenericFallback) return diagnoses

        val aiSuggestion = runCatching { AiCrashAdvisor.getSuggestion(fullLogText) }.getOrNull() ?: return diagnoses
        return diagnoses + Diagnosis(
            title = "AI-suggested fix (experimental)",
            cause = "None of TurtleLauncher's known crash patterns matched, so this suggestion came from the " +
                "AI crash helper you enabled in Settings — double-check it before relying on it.",
            fixSteps = listOf(aiSuggestion),
            severity = Severity.INFO
        )
    }

    /**
     * Convenience entry point for the post-exit crash flow: reads the launcher's
     * latestlog.txt plus (if [gameVersion] is known) the newest crash-report file for
     * that version, runs [analyze] on the combined text, and returns ready-to-display
     * text. Never throws — any failure just results in an empty string.
     */
    @JvmStatic
    fun analyzeGameExit(gameVersion: Version?, exitCode: Int): String {
        return runCatching {
            val logTail = tailOf(File(PathManager.DIR_GAME_HOME, "latestlog.txt"), MAX_LOG_BYTES)

            val crashReportText = gameVersion?.let { version ->
                runCatching {
                    val crashDir = File(version.getGameDir(), "crash-reports")
                    crashDir.takeIf { it.isDirectory }
                        ?.listFiles { f -> f.isFile && f.name.endsWith(".txt") }
                        ?.maxByOrNull { it.lastModified() }
                        ?.let { tailOf(it, MAX_CRASH_REPORT_BYTES) }
                }.getOrNull()
            }

            val ruleDiagnoses = analyze(logTail, crashReportText)
            val conflictDiagnoses = modConflictDiagnoses(gameVersion)
            // Mod-conflict findings are concrete and specific to *this* install, so they lead;
            // if a rule ALSO matched (e.g. a generic mixin_failure), both still show — they're
            // complementary, not competing explanations.
            val withConflicts = conflictDiagnoses + ruleDiagnoses
            val diagnoses = withAiFallback(
                withConflicts.ifEmpty { ruleDiagnoses },
                buildString { append(logTail); if (!crashReportText.isNullOrBlank()) { append("\n"); append(crashReportText) } }
            )

            val formatted = if (diagnoses.isEmpty()) "" else formatForDisplay(diagnoses, exitCode)
            if (diagnoses.isNotEmpty()) {
                pushCrashHistory(exitCode, diagnoses.first().title)
            }
            formatted
        }.getOrDefault("")
    }
}
