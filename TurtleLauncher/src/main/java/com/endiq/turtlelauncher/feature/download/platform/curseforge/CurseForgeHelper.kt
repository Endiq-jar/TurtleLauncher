package com.endiq.turtlelauncher.feature.download.platform.curseforge

import com.google.gson.JsonObject
import com.endiq.turtlelauncher.feature.download.Filters
import com.endiq.turtlelauncher.feature.download.enums.Category
import com.endiq.turtlelauncher.feature.download.enums.Classify
import com.endiq.turtlelauncher.feature.download.enums.ModLoader
import com.endiq.turtlelauncher.feature.download.enums.Platform
import com.endiq.turtlelauncher.feature.download.install.InstallHelper
import com.endiq.turtlelauncher.feature.download.item.InfoItem
import com.endiq.turtlelauncher.feature.download.item.ModInfoItem
import com.endiq.turtlelauncher.feature.download.item.ModLoaderWrapper
import com.endiq.turtlelauncher.feature.download.item.ScreenshotItem
import com.endiq.turtlelauncher.feature.download.item.SearchResult
import com.endiq.turtlelauncher.feature.download.item.VersionItem
import com.endiq.turtlelauncher.feature.download.platform.AbstractPlatformHelper
import com.endiq.turtlelauncher.feature.download.platform.PlatformNotSupportedException
import com.endiq.turtlelauncher.feature.download.utils.PlatformUtils
import com.endiq.turtlelauncher.feature.download.utils.VersionTypeUtils
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.utils.ZHTools
import net.endiq.launcher.modloaders.modpacks.api.ApiHandler
import java.io.File
import java.util.Date
import java.util.TreeSet

/**
 * CurseForge as a first-class Download source, alongside - never instead of - Modrinth.
 *
 * Categories, sort orders and mod loader ids were already recorded on the shared enums
 * ([Category.curseforgeID], [Sort.curseforge], [ModLoader.curseforgeId]) from the upstream
 * FCL/Zalith work, so this file only wires them to the v1 endpoints:
 *
 *   - search            GET /v1/mods/search
 *   - version list      GET /v1/mods/{id}/files
 *   - screenshots       GET /v1/mods/{id}
 *
 * Every endpoint needs a key; see [CurseForgeApiClient] and
 * [com.endiq.turtlelauncher.feature.mod.CurseForgeKeys]. When there is no key at all this
 * throws [PlatformNotSupportedException] carrying the reason, which the Download screen
 * already renders verbatim instead of a misleading "no results".
 *
 * Modpacks are installed through [CurseForgeModPackInstallHelper], which understands
 * CurseForge's manifest.json (and now resolves its mod list with the same key).
 */
class CurseForgeHelper : AbstractPlatformHelper(ApiHandler("https://api.curseforge.com/v1")) {

    override fun copy(): AbstractPlatformHelper = CurseForgeHelper()

    override fun getWebUrl(infoItem: InfoItem): String? {
        val section = when (infoItem.classify) {
            Classify.ALL -> return null
            Classify.MOD -> "mc-mods"
            Classify.MODPACK -> "modpacks"
            Classify.RESOURCE_PACK -> "texture-packs"
            Classify.WORLD -> "worlds"
            Classify.SHADER_PACK -> "shaders"
        }
        return "https://www.curseforge.com/minecraft/$section/${infoItem.slug}"
    }

    override fun getScreenshots(projectId: String): List<ScreenshotItem> {
        val screenshots = runCatching {
            CurseForgeApiClient.getMod(projectId)
                .getAsJsonObject("data")
                ?.getAsJsonArray("screenshots")
        }.onFailure { e -> Logging.w("CurseForgeHelper", "Screenshots unavailable for $projectId", e) }
            .getOrNull() ?: return emptyList()

        val items = mutableListOf<ScreenshotItem>()
        for (element in screenshots) {
            val shot = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val url = shot.stringOrNull("url") ?: shot.stringOrNull("thumbnailUrl") ?: continue
            items.add(
                ScreenshotItem(
                    url,
                    shot.stringOrNull("title"),
                    shot.stringOrNull("description")
                )
            )
        }
        return items
    }

    override fun searchMod(filters: Filters, lastResult: SearchResult): SearchResult? =
        searchClass(filters, lastResult, Classify.MOD)

    override fun searchModPack(filters: Filters, lastResult: SearchResult): SearchResult? =
        searchClass(filters, lastResult, Classify.MODPACK)

    override fun searchResourcePack(filters: Filters, lastResult: SearchResult): SearchResult? =
        searchClass(filters, lastResult, Classify.RESOURCE_PACK)

    override fun searchWorld(filters: Filters, lastResult: SearchResult): SearchResult? =
        searchClass(filters, lastResult, Classify.WORLD)

    override fun searchShaderPack(filters: Filters, lastResult: SearchResult): SearchResult? =
        searchClass(filters, lastResult, Classify.SHADER_PACK)

    override fun getModVersions(infoItem: InfoItem, force: Boolean): List<VersionItem>? =
        getFiles(infoItem)

    override fun getModPackVersions(infoItem: InfoItem, force: Boolean): List<VersionItem>? =
        getFiles(infoItem)

    override fun getResourcePackVersions(infoItem: InfoItem, force: Boolean): List<VersionItem>? =
        getFiles(infoItem)

    override fun getWorldVersions(infoItem: InfoItem, force: Boolean): List<VersionItem>? =
        getFiles(infoItem)

    override fun getShaderPackVersions(infoItem: InfoItem, force: Boolean): List<VersionItem>? =
        getFiles(infoItem)

    override fun installMod(infoItem: InfoItem, version: VersionItem, targetPath: File, progressKey: String) {
        InstallHelper.downloadFile(version, targetPath, progressKey)
    }

    override fun installModPack(version: VersionItem, customName: String): ModLoaderWrapper? =
        CurseForgeModPackInstallHelper.startInstall(version, customName)

    override fun installResourcePack(infoItem: InfoItem, version: VersionItem, targetPath: File, progressKey: String) {
        InstallHelper.downloadFile(version, targetPath, progressKey)
    }

    override fun installWorld(infoItem: InfoItem, version: VersionItem, targetPath: File, progressKey: String) {
        InstallHelper.downloadFile(version, targetPath, progressKey)
    }

    override fun installShaderPack(infoItem: InfoItem, version: VersionItem, targetPath: File, progressKey: String) {
        InstallHelper.downloadFile(version, targetPath, progressKey)
    }

    // ── Search ─────────────────────────────────────────────────────────────────────

    private fun searchClass(filters: Filters, lastResult: SearchResult, classify: Classify): SearchResult? {
        val classId = classIdOf(classify)
            ?: throw PlatformNotSupportedException("CurseForge has no project type for ${classify.name.lowercase()}.")

        if (filters.category != Category.ALL && filters.category.curseforgeID.isNullOrBlank()) {
            throw PlatformNotSupportedException(
                "CurseForge does not have the ${filters.category.name} category."
            )
        }

        // Search-by-Chinese-name goes through the same translation table the Modrinth path
        // uses, so a Chinese search still finds English-named CurseForge projects.
        if (classify == Classify.MOD || classify == Classify.MODPACK) {
            PlatformUtils.searchModLikeWithChinese(filters, classify == Classify.MOD)?.let { filters.name = it }
        }

        val params = linkedMapOf(
            "gameId" to CurseForgeApiClient.MINECRAFT_GAME_ID.toString(),
            "classId" to classId.toString(),
            "index" to lastResult.previousCount.toString(),
            "pageSize" to PAGE_SIZE.toString(),
            "sortOrder" to "desc",
            "sortField" to filters.sort.curseforge.toString(),
        )
        filters.name.trim().takeIf { it.isNotBlank() }?.let { params["searchFilter"] = it }
        filters.mcVersion?.takeIf { it.isNotBlank() }?.let { params["gameVersion"] = it }
        filters.modloader?.curseforgeId?.takeIf { it.isNotBlank() }?.let { params["modLoaderType"] = it }
        filters.category.curseforgeID?.takeIf { it.isNotBlank() && filters.category != Category.ALL }
            ?.let { params["categoryId"] = it }

        val response = try {
            CurseForgeApiClient.search(params)
        } catch (e: CurseForgeApiClient.MissingKeyException) {
            throw PlatformNotSupportedException(e.message ?: "A CurseForge API key is required.")
        } catch (e: CurseForgeApiClient.ApiException) {
            throw PlatformNotSupportedException(e.message ?: "CurseForge request failed.")
        }

        val (resultCount, totalCount) = CurseForgeApiClient.pagination(response)
        val items = CurseForgeApiClient.items(response).mapNotNull { mod -> infoItemOf(mod, classify) }

        return lastResult.apply {
            infoItems.addAll(items)
            previousCount += items.size
            totalResultCount = totalCount
            isLastPage = resultCount < PAGE_SIZE
        }
    }

    private fun infoItemOf(mod: JsonObject, classify: Classify): InfoItem? {
        val id = mod.longOrNull("id")?.toString() ?: return null
        val title = mod.stringOrNull("name") ?: return null
        val slug = mod.stringOrNull("slug") ?: id

        val authors = mod.getAsJsonArray("authors")?.mapNotNull { element ->
            element.takeIf { it.isJsonObject }?.asJsonObject?.stringOrNull("name")
        }?.toTypedArray()

        val categories = TreeSet<Category>()
        mod.getAsJsonArray("categories")?.forEach { element ->
            val categoryId = element.takeIf { it.isJsonObject }?.asJsonObject?.longOrNull("id")
            categoryId?.toString()?.let { CATEGORY_BY_CURSEFORGE_ID[it] }?.let { categories.addAll(it) }
        }

        val iconUrl = mod.getAsJsonObject("logo")?.stringOrNull("thumbnailUrl")
        val uploadDate = safeDate(mod.stringOrNull("dateModified") ?: mod.stringOrNull("dateCreated"))
        val downloadCount = mod.longOrNull("downloadCount") ?: 0L

        if (classify == Classify.MOD) {
            val loaders = TreeSet<ModLoader>()
            mod.getAsJsonArray("latestFilesIndexes")?.forEach { element ->
                val loaderId = element.takeIf { it.isJsonObject }?.asJsonObject?.intOrNull("modLoader")
                loaderId?.let { MOD_LOADER_BY_CURSEFORGE_ID[it] }?.let { loaders.add(it) }
            }
            return ModInfoItem(
                classify, Platform.CURSEFORGE, id, slug, authors, title,
                mod.stringOrNull("summary").orEmpty(), downloadCount, uploadDate, iconUrl,
                categories.toList(), loaders.toList()
            )
        }

        return InfoItem(
            classify, Platform.CURSEFORGE, id, slug, authors, title,
            mod.stringOrNull("summary").orEmpty(), downloadCount, uploadDate, iconUrl,
            categories.toList()
        )
    }

    // ── Files / versions ───────────────────────────────────────────────────────────

    private fun getFiles(infoItem: InfoItem): List<VersionItem>? {
        val files = try {
            CurseForgeApiClient.getFiles(infoItem.projectId, mapOf("pageSize" to FILE_PAGE_SIZE.toString())) ?: return null
        } catch (e: CurseForgeApiClient.MissingKeyException) {
            throw PlatformNotSupportedException(e.message ?: "A CurseForge API key is required.")
        } catch (e: CurseForgeApiClient.ApiException) {
            throw PlatformNotSupportedException(e.message ?: "CurseForge request failed.")
        }

        val items = mutableListOf<VersionItem>()
        for (element in files) {
            val file = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            // A file with no downloadUrl means the author opted out of third-party
            // downloads; there is nothing to install, so it is not offered at all.
            val downloadUrl = file.stringOrNull("downloadUrl") ?: continue
            val fileName = file.stringOrNull("fileName") ?: continue

            val sha1 = file.getAsJsonArray("hashes")?.firstNotNullOfOrNull { hash ->
                val obj = hash.takeIf { it.isJsonObject }?.asJsonObject ?: return@firstNotNullOfOrNull null
                if (obj.intOrNull("algo") == 1) obj.stringOrNull("value") else null
            }

            val gameVersions = file.getAsJsonArray("gameVersions")
                ?.mapNotNull { it.asString }
                ?.filterNot { version -> LOADER_NAMES.any { it.equals(version, ignoreCase = true) } }
                .orEmpty()

            items.add(
                VersionItem(
                    infoItem.projectId,
                    file.stringOrNull("displayName") ?: fileName,
                    file.longOrNull("downloadCount") ?: 0L,
                    safeDate(file.stringOrNull("fileDate")),
                    gameVersions,
                    VersionTypeUtils.getVersionType(file.intOrNull("releaseType")?.toString() ?: "1"),
                    fileName,
                    sha1,
                    downloadUrl
                )
            )
        }
        return items
    }

    private fun classIdOf(classify: Classify): Int? = when (classify) {
        Classify.ALL -> null
        Classify.MOD -> 6
        Classify.MODPACK -> 4471
        Classify.RESOURCE_PACK -> 12
        Classify.WORLD -> 17
        Classify.SHADER_PACK -> 6552
    }

    private companion object {
        const val PAGE_SIZE = 20
        const val FILE_PAGE_SIZE = 50

        /** Loader names CurseForge mixes into `gameVersions` next to the real MC versions. */
        val LOADER_NAMES = listOf("Forge", "NeoForge", "Fabric", "Quilt", "LiteLoader", "Rift", "Cauldron")

        val MOD_LOADER_BY_CURSEFORGE_ID: Map<Int, ModLoader> = mapOf(
            1 to ModLoader.FORGE,
            4 to ModLoader.FABRIC,
            5 to ModLoader.QUILT,
            6 to ModLoader.NEOFORGE,
        )

        /** Built once so a result page does not rescan the whole Category enum per item. */
        val CATEGORY_BY_CURSEFORGE_ID: Map<String, List<Category>> = Category.entries
            .filter { !it.curseforgeID.isNullOrBlank() }
            .groupBy { it.curseforgeID!! }
    }
}

// ── Small null-safe JSON helpers ───────────────────────────────────────────────────
// CurseForge frequently returns explicit nulls, and JsonObject.get(...).asString throws
// on those, so every field read goes through these instead.

private fun JsonObject.stringOrNull(name: String): String? =
    get(name)?.takeIf { !it.isJsonNull }?.asString

private fun JsonObject.longOrNull(name: String): Long? =
    runCatching { get(name)?.takeIf { !it.isJsonNull }?.asLong }.getOrNull()

private fun JsonObject.intOrNull(name: String): Int? =
    runCatching { get(name)?.takeIf { !it.isJsonNull }?.asInt }.getOrNull()

/**
 * ZHTools.getDate throws on an unparseable string (it only catches the first two of three
 * parse attempts), and an epoch fallback keeps a malformed API timestamp from taking the
 * whole result page down.
 */
private fun safeDate(value: String?): Date =
    value?.takeIf { it.isNotBlank() }?.let { runCatching { ZHTools.getDate(it) }.getOrNull() } ?: Date(0L)
