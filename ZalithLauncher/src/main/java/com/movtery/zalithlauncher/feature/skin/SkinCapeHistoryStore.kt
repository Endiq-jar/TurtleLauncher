package com.movtery.zalithlauncher.feature.skin

import com.google.gson.reflect.TypeToken
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.utils.path.PathManager
import net.kdt.pojavlaunch.Tools
import java.io.File
import java.security.MessageDigest

/**
 * Local "recently applied" history backing the tap-to-apply gallery in
 * [com.movtery.zalithlauncher.ui.dialog.SkinCapeDialog].
 *
 * There is no free/stable/official public API to browse a gallery of arbitrary community
 * skins/capes - checked before building this. Every "browse" service found is either a
 * per-player lookup (like [LabyModSkinApi] itself, see its own doc comment) or a paid
 * third-party service scraping NameMC. Same shape of finding as the 9minecraft.net platform
 * check elsewhere in this project: no real API, so don't build a fragile/borrowed-legitimacy
 * scraper. Instead this stores what the user has *actually* applied before (from a URL, the
 * device gallery, or a player lookup) as a real, always-available, offline gallery - entirely
 * local, no network involved.
 */
internal object SkinCapeHistoryStore {
    private const val MAX_ENTRIES = 16

    data class HistoryEntry(
        val id: String,
        val label: String,
        val thumbFileName: String,
        val appliedAtMillis: Long
    )

    private val historyDir: File by lazy {
        File(PathManager.DIR_USER_SKIN, "history").apply { mkdirs() }
    }

    private fun indexFile(mode: String): File = File(historyDir, "${mode}_index.json")

    /** The actual applied-image file for [entry] (full res, not a downscaled thumbnail - reapplying just copies it back). */
    fun thumbFile(mode: String, entry: HistoryEntry): File {
        // mode is unused for pathing today (thumbFileName is already content-hash-unique across
        // both modes) but kept in the signature so a future skin/cape-specific dedupe scheme
        // doesn't need every call site touched.
        return File(historyDir, entry.thumbFileName)
    }

    /** Reads the persisted history for [mode] ("skin" or "cape"), newest first. Missing/corrupt files just come back empty. */
    fun loadHistory(mode: String): List<HistoryEntry> = runCatching {
        loadHistoryRaw(mode).filter { File(historyDir, it.thumbFileName).exists() }
    }.onFailure { e -> Logging.e("SkinCapeHistoryStore", "Failed to load $mode history", e) }.getOrDefault(emptyList())

    /**
     * Records a successful apply: copies [appliedFile]'s bytes into local history storage
     * (deduped by content hash - reapplying the same image just bumps it to the front) and
     * updates the index, trimming to [MAX_ENTRIES]. Safe to call from a background thread;
     * does real disk I/O.
     */
    fun recordApplied(mode: String, appliedFile: File, label: String) {
        runCatching {
            val bytes = appliedFile.readBytes()
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val thumbName = "$hash.png"
            val thumb = File(historyDir, thumbName)
            if (!thumb.exists()) thumb.writeBytes(bytes)

            val existing = loadHistoryRaw(mode).filterNot { it.thumbFileName == thumbName }
            val updated = (listOf(HistoryEntry(hash.take(16), label, thumbName, System.currentTimeMillis())) + existing)
                .take(MAX_ENTRIES)
            indexFile(mode).writeText(Tools.GLOBAL_GSON.toJson(updated))

            cleanupOrphanedThumbs()
        }.onFailure { e -> Logging.e("SkinCapeHistoryStore", "Failed to record $mode history", e) }
    }

    /** Deletes any thumb file no longer referenced by either mode's index (e.g. trimmed off the end). */
    private fun cleanupOrphanedThumbs() {
        val referenced = (loadHistoryRaw("skin") + loadHistoryRaw("cape")).map { it.thumbFileName }.toSet()
        historyDir.listFiles { f -> f.isFile && f.name.endsWith(".png") && f.name !in referenced }
            ?.forEach { it.delete() }
    }

    private fun loadHistoryRaw(mode: String): List<HistoryEntry> {
        val file = indexFile(mode)
        if (!file.exists()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<HistoryEntry>>() {}.type
            Tools.GLOBAL_GSON.fromJson<List<HistoryEntry>>(file.readText(), type) ?: emptyList()
        }.getOrDefault(emptyList())
    }
}
