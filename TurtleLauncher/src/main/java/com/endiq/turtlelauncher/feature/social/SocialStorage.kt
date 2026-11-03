package com.endiq.turtlelauncher.feature.social

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.utils.path.PathManager
import java.io.File

/**
 * Tiny JSON-file store for the Friends feature's own data.
 *
 * Deliberately separate from the launcher settings file: social data changes far more often
 * (every message, every presence tick), is bigger (message history), and one of the files
 * holds an OAuth refresh token that should not be copied around with the settings backup.
 * Everything lives under `files/social/`.
 *
 * All methods are cheap and synchronous; they are only called from the social worker thread
 * (see [SocialRepository]) with in-memory state as the source of truth, so a failed write
 * degrades to "this session isn't persisted" instead of crashing the UI.
 */
internal object SocialStorage {
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
    private const val TAG = "SocialStorage"

    private fun directory(): File? = runCatching {
        File(PathManager.DIR_FILE, "social").also { it.mkdirs() }
    }.getOrNull()

    private fun file(name: String): File? = directory()?.let { File(it, name) }

    fun <T> read(name: String, type: Class<T>): T? {
        val target = file(name) ?: return null
        if (!target.isFile) return null
        return runCatching { gson.fromJson(target.readText(), type) }
            .onFailure { Logging.w(TAG, "Could not read $name", it) }
            .getOrNull()
    }

    fun <T> readList(name: String, type: Class<T>): MutableList<T> {
        val target = file(name) ?: return mutableListOf()
        if (!target.isFile) return mutableListOf()
        val listType = TypeToken.getParameterized(MutableList::class.java, type).type
        return runCatching { gson.fromJson<MutableList<T>>(target.readText(), listType) }
            .onFailure { Logging.w(TAG, "Could not read $name", it) }
            .getOrNull() ?: mutableListOf()
    }

    fun write(name: String, value: Any?) {
        val target = file(name) ?: return
        runCatching {
            // Write-then-rename: a process kill mid-write leaves the previous file intact
            // instead of a truncated JSON blob that can never be read again.
            val temp = File(target.parentFile, "$name.tmp")
            temp.writeText(gson.toJson(value))
            if (!temp.renameTo(target)) {
                target.writeText(temp.readText())
                temp.delete()
            }
        }.onFailure { Logging.w(TAG, "Could not write $name", it) }
    }

    fun delete(name: String) {
        runCatching { file(name)?.delete() }
    }
}
