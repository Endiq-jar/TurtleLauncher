package com.endiq.turtlelauncher.feature.mod

import android.graphics.BitmapFactory
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.task.TaskExecutors
import com.endiq.turtlelauncher.utils.path.PathManager
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Finds and caches the icon a mod jar ships for itself, so the mod manager can
 * show the mod's own artwork instead of the same generic jar glyph on every row.
 *
 * Where the icon is declared, by loader:
 *   * Fabric   - `fabric.mod.json` -> `"icon"` (a path, or `{sizeLabel: path}`)
 *   * Quilt    - `quilt.mod.json` -> `quilt_loader.metadata.icon` (same shapes)
 *   * Forge    - `META-INF/mods.toml` -> `logoFile = "..."` (first `[[mods]]`)
 *   * NeoForge - `META-INF/neoforge.mods.toml`, same `logoFile` key
 *   * Legacy   - `mcmod.info` -> `logoFile`
 *   * Fallback - `pack.png` / `icon.png` / `logo.png` / `modicon.png` at the jar root
 *
 * Two entry points, because extracting from a jar is disk I/O:
 *   * [cachedIcon] - cache-only, safe to call while building a list on the UI
 *     thread. Returns null when the icon has not been extracted yet.
 *   * [requestIcon] - extracts off the UI thread and calls back on the main
 *     thread. Repeated requests for the same jar while one is in flight are
 *     coalesced, and results are memoised per file fingerprint, so a list
 *     rebind does not re-open jars.
 *
 * Cache files live in `cache/addons_info_cache/mod_icons` (the same directory
 * ModParser already uses for its metadata cache) so clearing app data/cache
 * removes them with everything else. Nothing here ever writes into the mods
 * folder.
 */
object ModIconLoader {
    private const val TAG = "ModIconLoader"
    private const val CACHE_DIR_NAME = "mod_icons"
    private const val MAX_CACHE_FILES = 400
    private const val MAX_ICON_BYTES = 2 * 1024 * 1024

    /** Values: the extracted icon file, or [NONE] when the jar has no usable icon. */
    private val memo = ConcurrentHashMap<String, File>()
    private val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()

    @JvmField
    val NONE: File = File("")

    private val cacheDir: File
        get() = File(PathManager.DIR_ADDONS_INFO_CACHE, CACHE_DIR_NAME).apply { if (!exists()) mkdirs() }

    /** Callback for async extraction; always delivered on the main thread. */
    fun interface Listener {
        fun onIcon(icon: File?)
    }

    /**
     * Same identity ModParser uses for its metadata cache: name + size +
     * last-modified. Re-downloading or toggling a mod (`.jar` <-> `.jar.disabled`)
     * changes at least one of them, so a stale icon can never be shown.
     */
    private fun fingerprint(file: File): String =
        "mod-icon:${file.name}:${file.length()}:${file.lastModified()}"

    private fun cacheFileFor(file: File): File {
        val name = Integer.toHexString(fingerprint(file).hashCode())
        return File(cacheDir, "$name.png")
    }

    /** Cache-only lookup; never opens the jar. Null means "not extracted yet". */
    @JvmStatic
    fun cachedIcon(file: File?): File? {
        file ?: return null
        val fingerprint = fingerprint(file)
        memo[fingerprint]?.let { return it.takeIf { cached -> cached != NONE } }
        val cached = cacheFileFor(file)
        if (cached.isFile && cached.length() > 0) {
            memo[fingerprint] = cached
            return cached
        }
        return null
    }

    /**
     * Extract [file]'s icon if needed, then deliver it (or null) on the main
     * thread. Safe to call from a RecyclerView binding pass.
     */
    @JvmStatic
    fun requestIcon(file: File?, listener: Listener) {
        if (file == null || !file.isFile) {
            listener.onIcon(null)
            return
        }
        cachedIcon(file)?.let {
            listener.onIcon(it)
            return
        }
        val fingerprint = fingerprint(file)
        if (!inFlight.add(fingerprint)) return // another row already asked for this jar
        TaskExecutors.getDefault().execute {
            val icon = runCatching { extractIcon(file) }.getOrElse { error ->
                Logging.w(TAG, "Could not read the icon of ${file.name}: ${error.message}")
                null
            }
            memo[fingerprint] = icon ?: NONE
            inFlight.remove(fingerprint)
            TaskExecutors.runInUIThread(Runnable { listener.onIcon(icon) })
        }
    }

    /** Synchronous extraction. Call off the main thread. */
    @JvmStatic
    fun extractIcon(file: File): File? {
        ZipFile(file).use { zip ->
            val entry = declaredIconEntry(zip) ?: fallbackIconEntry(zip) ?: return null
            val bytes = zip.getInputStream(entry).use { it.readBytes() }
            if (bytes.isEmpty() || bytes.size > MAX_ICON_BYTES) return null
            // Reject anything that is not a decodable image before caching it, so a
            // text file named "icon.png" cannot become a permanent cache entry.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            pruneCache()
            val target = cacheFileFor(file)
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
            return target
        }
    }

    /** The icon a loader's metadata points at, if that entry actually exists. */
    private fun declaredIconEntry(zip: ZipFile): ZipEntry? {
        fun jsonIcon(json: JsonObject?, vararg keys: String): String? {
            json ?: return null
            var node: com.google.gson.JsonElement = json
            for (key in keys) {
                node = node.takeIf { it.isJsonObject }?.asJsonObject?.get(key) ?: return null
            }
            return when {
                node.isJsonPrimitive -> node.asString
                // {"64": "icon_64.png", "128": "..."} - take the largest declared size.
                node.isJsonObject -> node.asJsonObject.entrySet()
                    .sortedByDescending { it.key.filter(Char::isDigit).toIntOrNull() ?: 0 }
                    .firstOrNull()?.value?.takeIf { it.isJsonPrimitive }?.asString
                else -> null
            }
        }

        fun readEntry(name: String): JsonObject? =
            runCatching {
                zip.getEntry(name)?.let { entry ->
                    zip.getInputStream(entry).bufferedReader().use { JsonParser.parseString(it.readText()).asJsonObject }
                }
            }.getOrNull()

        val declared = mutableListOf<String?>()
        readEntry("fabric.mod.json")?.let { declared += jsonIcon(it, "icon") }
        readEntry("quilt.mod.json")?.let { declared += jsonIcon(it, "quilt_loader", "metadata", "icon") }
        for (toml in listOf("META-INF/neoforge.mods.toml", "META-INF/mods.toml")) {
            zip.getEntry(toml)?.let { entry ->
                val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                // Oldest declaration wins inside a file; [[mods]] blocks come first
                // and every loader puts the jar's own icon in the first one.
                declared += Regex("""logoFile\s*=\s*["']([^"']+)["']""")
                    .find(text)?.groupValues?.get(1)
            }
        }
        // mcmod.info is a JSON array on most old Forge mods, but some pack it as a bare
        // object - accept both shapes.
        runCatching {
            zip.getEntry("mcmod.info")?.let { entry ->
                val parsed = zip.getInputStream(entry).bufferedReader().use {
                    JsonParser.parseString(it.readText())
                }
                val first = if (parsed.isJsonArray) parsed.asJsonArray.firstOrNull() else parsed
                declared += jsonIcon(first?.takeIf { it.isJsonObject }?.asJsonObject, "logoFile")
            }
        }

        for (path in declared) {
            val cleaned = path?.trim()?.trimStart('/') ?: continue
            if (cleaned.isEmpty()) continue
            zip.getEntry(cleaned)?.let { if (!it.isDirectory) return it }
            // Some packs declare a path the jar does not actually contain.
            Logging.w(TAG, "Declared mod icon '$cleaned' is missing from ${zip.name}")
        }
        return null
    }

    private fun fallbackIconEntry(zip: ZipFile): ZipEntry? {
        val candidates = listOf(
            "icon.png", "logo.png", "pack.png", "modicon.png",
            "assets/icon.png", "icon.webp", "logo.webp"
        )
        for (candidate in candidates) {
            zip.getEntry(candidate)?.let { if (!it.isDirectory) return it }
        }
        return null
    }

    /** Keep the icon cache roughly bounded: 400 jars' worth is a few MB at most. */
    private fun pruneCache() {
        val files = cacheDir.listFiles { f -> f.isFile } ?: return
        if (files.size < MAX_CACHE_FILES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_CACHE_FILES + 1)
            .forEach { it.delete() }
    }

    /** Drop memoised entries so a deleted mod's icon is not shown again. */
    @JvmStatic
    fun clear() {
        memo.clear()
        runCatching { org.apache.commons.io.FileUtils.deleteDirectory(cacheDir) }
    }
}
