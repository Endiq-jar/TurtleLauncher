package com.endiq.turtlelauncher.feature.skin

import com.google.gson.reflect.TypeToken
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.utils.path.PathManager
import net.endiq.launcher.Tools
import java.io.File
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.security.MessageDigest

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
            if (!appliedFile.isFile || appliedFile.length() <= 0L) {
                error("Applied image does not exist or is empty: ${appliedFile.path}")
            }
            historyDir.mkdirs()

            // Hash in bounded chunks rather than readBytes(), which created a second full-size
            // copy of the original skin/cape image on the heap.
            val digest = MessageDigest.getInstance("SHA-256")
            BufferedInputStream(appliedFile.inputStream()).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val thumbName = "$hash.png"
            val thumb = File(historyDir, thumbName)
            if (!thumb.exists()) {
                val temporary = File(historyDir, "$thumbName.tmp")
                try {
                    BufferedInputStream(appliedFile.inputStream()).use { input ->
                        BufferedOutputStream(temporary.outputStream()).use { output ->
                            input.copyTo(output, DEFAULT_BUFFER_SIZE)
                        }
                    }
                    if (!temporary.renameTo(thumb)) {
                        // A parallel call may have created the same hash file; accept that,
                        // otherwise fail rather than leave a partially written history item.
                        if (!thumb.exists()) error("Could not commit image history file")
                        temporary.delete()
                    }
                } finally {
                    if (temporary.exists()) temporary.delete()
                }
            }

            val existing = loadHistoryRaw(mode).filterNot { it.thumbFileName == thumbName }
            val updated = (listOf(HistoryEntry(hash.take(16), label, thumbName, System.currentTimeMillis())) + existing)
                .take(MAX_ENTRIES)
            val index = indexFile(mode)
            val temporaryIndex = File(historyDir, "${mode}_index.json.tmp")
            try {
                temporaryIndex.writeText(Tools.GLOBAL_GSON.toJson(updated))
                if (!temporaryIndex.renameTo(index)) {
                    index.writeText(Tools.GLOBAL_GSON.toJson(updated))
                    temporaryIndex.delete()
                }
            } finally {
                if (temporaryIndex.exists()) temporaryIndex.delete()
            }

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
