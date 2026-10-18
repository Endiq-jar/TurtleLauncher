package com.endiq.turtlelauncher.launch

import java.io.File
import java.util.Properties

/**
 * Owns only maxFps/enableVsync while explicit Unlimited FPS is active. Before the first edit,
 * persist their original values. On disable, restore ONLY values still equal to our edits:
 * a player's subsequent in-game changes always win. No game files or backups are deleted.
 */
object GameFpsOptions {
    private val overrides = linkedMapOf("maxFps" to "260", "enableVsync" to "false")
    private const val ABSENT = "__turtle_absent__"

    fun apply(gameDir: File, uncapped: Boolean) {
        val options = File(gameDir, "options.txt")
        if (!options.isFile) return
        val backup = File(gameDir, ".turtle-fps-options.properties")
        val state = Properties()
        if (backup.isFile) backup.inputStream().use { state.load(it) }
        val original = options.readLines()
        val values = original.mapNotNull { line ->
            val colon = line.indexOf(':')
            if (colon < 0) null else line.substring(0, colon) to line.substring(colon + 1)
        }.toMap()
        val edits = linkedMapOf<String, String?>()
        if (uncapped) {
            overrides.forEach { (key, value) ->
                if (!state.containsKey(key)) state.setProperty(key, values[key] ?: ABSENT)
                edits[key] = value
            }
            // Write rollback data first; a failed backup must never modify Minecraft's file.
            backup.writer().use { state.store(it, "TurtleLauncher FPS rollback values") }
        } else {
            overrides.forEach { (key, applied) ->
                val saved = state.getProperty(key)
                if (saved != null && values[key] == applied) edits[key] = saved.takeUnless { it == ABSENT }
            }
        }
        val seen = mutableSetOf<String>()
        val updated = original.mapNotNull { line ->
            val key = line.substringBefore(':')
            if (key !in edits) line else {
                seen += key
                edits[key]?.let { "$key:$it" }
            }
        }.toMutableList()
        edits.forEach { (key, value) -> if (key !in seen && value != null) updated += "$key:$value" }
        if (updated != original) options.writeText(updated.joinToString("\n", postfix = "\n"))
        if (!uncapped && state.isNotEmpty()) {
            // Keep the backup file, but mark ownership released for the next explicit enable.
            state.clear()
            backup.writer().use { state.store(it, "FPS overrides inactive; original options restored or user edits preserved") }
        }
    }
}
