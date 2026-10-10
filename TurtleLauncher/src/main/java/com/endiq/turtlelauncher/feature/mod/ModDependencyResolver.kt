package com.endiq.turtlelauncher.feature.mod

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.endiq.turtlelauncher.feature.download.enums.ModLoader
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.feature.mod.parser.ModInfo
import java.io.File


object ModDependencyResolver {

    /** Mod/loader-provided "dependencies" that are never real, installable mods. */
    private val IGNORED_IDS = setOf(
        "minecraft", "java", "fabricloader", "fabric", "forge", "neoforge", "quilt_loader",
        "quilted_fabric_api", "fabric-api-base", "fabric_api", "fabric-api"
    )

    data class ResolveResult(
        /** Human-readable "filename (version)" descriptions of what got installed. */
        val installed: List<String>,
        /** Missing dependency IDs or actionable reasons that prevented auto-resolution. */
        val failed: List<String>
    ) {
        val isEmpty: Boolean get() = installed.isEmpty() && failed.isEmpty()
    }

    private val EMPTY_RESULT = ResolveResult(emptyList(), emptyList())

    private const val LEDGER_FILE_NAME = ".turtle_dependency_ledger.json"
    private val gson = Gson()
    private val ledgerType = object : TypeToken<MutableMap<String, LedgerEntry>>() {}.type

    /**
     * What we remember about a dependency we've already resolved once.
     * [declined] flips to true once the file we installed is gone from the mods
     * folder entirely (not just disabled) — that's the player telling us "no", and
     * we stop offering it back to them.
     */
    private data class LedgerEntry(val fileName: String, val declined: Boolean = false)

    private fun loadLedger(modsFolder: File): MutableMap<String, LedgerEntry> {
        val file = File(modsFolder, LEDGER_FILE_NAME)
        if (!file.isFile) return mutableMapOf()
        return runCatching {
            gson.fromJson<MutableMap<String, LedgerEntry>>(file.readText(), ledgerType) ?: mutableMapOf()
        }.getOrDefault(mutableMapOf())
    }

    private fun saveLedger(modsFolder: File, ledger: Map<String, LedgerEntry>) {
        runCatching {
            File(modsFolder, LEDGER_FILE_NAME).writeText(gson.toJson(ledger))
        }.onFailure { e -> Logging.e("ModDependencyResolver", "Failed to save dependency ledger", e) }
    }

    private fun normalizeId(value: String): String = value.trim().lowercase()

    /** Normalizes common mod filename separators without changing metadata-ID comparisons. */
    private fun normalizeFileName(value: String): String = value.trim().lowercase().replace('_', '-')

    private fun filenameLooksLikeMod(fileName: String, modId: String, disabled: Boolean): Boolean {
        val suffix = if (disabled) ".jar.disabled" else ".jar"
        if (!fileName.endsWith(suffix, ignoreCase = true)) return false
        val stem = normalizeFileName(fileName.dropLast(suffix.length))
        val id = normalizeFileName(modId)
        return id.isNotBlank() && (stem == id || stem.startsWith("$id-") || stem.startsWith("$id."))
    }

    /**
     * A disabled jar is not an installed dependency: loaders do not scan it. Keep it out of
     * [alreadyPresentOnDisk] so we can tell the player why resolution stopped instead of
     * silently treating a `.jar.disabled` file as satisfying a required dependency.
     */
    internal fun findMatchingDisabledJar(modsFolder: File, modId: String): File? {
        if (normalizeFileName(modId).isBlank()) return null
        return modsFolder.listFiles()?.firstOrNull { file ->
            file.isFile && filenameLooksLikeMod(file.name, modId, disabled = true)
        }
    }

    private fun alreadyPresentOnDisk(modsFolder: File, modId: String): Boolean {
        return modsFolder.listFiles()?.any { file ->
            file.isFile && filenameLooksLikeMod(file.name, modId, disabled = false)
        } ?: false
    }

    /** Returns required IDs not already satisfied by a mod's primary or advertised alias ID. */
    internal fun missingDependencyIds(modInfoList: List<ModInfo>): List<String> {
        val installedIds = modInfoList
            .flatMap { info -> listOfNotNull(info.id) + info.providedIds.orEmpty() }
            .map { normalizeId(it) }
            .filter { it.isNotBlank() }
            .toSet()
        val ignoredIds = IGNORED_IDS.map { normalizeId(it) }.toSet()
        return modInfoList
            .flatMap { it.dependencies.keys }
            .map { it.trim().lowercase() }
            .distinct()
            .filter { normalizeId(it) !in ignoredIds && normalizeId(it) !in installedIds }
    }

    /**
     * @param modsFolder   the version's "mods" directory — new jars get written here.
     * @param modInfoList  every mod already parsed for this version.
     * @param mcVersion    e.g. "1.21.5" — used to pick a compatible Modrinth version.
     * @param loader       the version's mod loader, or null if unknown (in which case
     *                     no resolution is attempted, since loader compatibility can't
     *                     be checked).
     */
    fun resolveMissingDependencies(
        modsFolder: File,
        modInfoList: List<ModInfo>,
        mcVersion: String,
        loader: ModLoader?
    ): ResolveResult {
        if (loader == null || loader == ModLoader.ALL || mcVersion.isBlank()) return EMPTY_RESULT
        if (modInfoList.isEmpty()) return EMPTY_RESULT

        // `provides` aliases are included here, so alternate IDs don't trigger a duplicate
        // auto-download. Disabled jars are handled separately below because loaders skip them.
        val missingIds = missingDependencyIds(modInfoList)
        if (missingIds.isEmpty()) return EMPTY_RESULT

        val ledger = loadLedger(modsFolder)
        var ledgerChanged = false

        val installed = mutableListOf<String>()
        val failed = mutableListOf<String>()

        missingIds.forEach { modId ->
            val disabledJar = findMatchingDisabledJar(modsFolder, modId)
            if (disabledJar != null) {
                // Do not auto-enable or install over a jar the player deliberately disabled.
                // More importantly, don't report success: the loader will still see this
                // required dependency as missing. Surface the actionable reason in the result.
                failed.add("$modId (matching jar is disabled: ${disabledJar.name})")
                return@forEach
            }

            val entry = ledger[modId]

            if (entry?.declined == true) return@forEach // player already said no to this one

            if (entry != null) {
                val installedFile = File(modsFolder, entry.fileName)
                if (installedFile.isFile) return@forEach
                val disabledFile = File(modsFolder, "${entry.fileName}.disabled")
                if (disabledFile.isFile) {
                    failed.add("$modId (matching jar is disabled: ${disabledFile.name})")
                    return@forEach
                }
                ledger[modId] = entry.copy(declined = true) // player deleted it outright
                ledgerChanged = true
                return@forEach
            }

            if (alreadyPresentOnDisk(modsFolder, modId)) return@forEach

            runCatching {
                val resolved = ModrinthDirectApi.downloadBestMatch(modId, mcVersion, loader, modsFolder)
                if (resolved != null) {
                    installed.add(resolved)
                    // resolved is "fileName (version)" or "fileName (already installed)" or
                    // just fileName — the actual on-disk name is whatever's before the first "(".
                    ledger[modId] = LedgerEntry(resolved.substringBefore(" ("))
                    ledgerChanged = true
                } else {
                    failed.add(modId)
                }
            }.onFailure { e ->
                Logging.e("ModDependencyResolver", "Failed to resolve dependency '$modId'", e)
                failed.add(modId)
            }
        }

        if (ledgerChanged) saveLedger(modsFolder, ledger)

        return ResolveResult(installed, failed)
    }
}
