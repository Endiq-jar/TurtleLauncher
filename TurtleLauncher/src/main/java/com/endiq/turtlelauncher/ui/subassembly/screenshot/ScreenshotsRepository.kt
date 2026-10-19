package com.endiq.turtlelauncher.ui.subassembly.screenshot

import android.content.Context
import android.os.Environment
import com.endiq.turtlelauncher.feature.log.Logging
import java.io.File

/** Mirrors ScreenshotHelper.saveAndNotify()'s output dir: external Pictures/screenshots. */
object ScreenshotsRepository {
    private const val TAG = "ScreenshotsRepository"

    fun screenshotsDir(context: Context): File? =
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.let { File(it, "screenshots") }

    /** Newest first. Safe to call off the main thread only. */
    fun listScreenshots(context: Context): List<ScreenshotItem> {
        val dir = screenshotsDir(context) ?: return emptyList()
        if (!dir.isDirectory) return emptyList()
        val files = dir.listFiles { f -> f.isFile && f.extension.equals("png", ignoreCase = true) } ?: return emptyList()
        return files.map { ScreenshotItem(it, it.lastModified(), it.length()) }
            .sortedByDescending { it.lastModified }
    }

    /** @return true if the file no longer exists afterward (already-missing counts as success). */
    fun delete(item: ScreenshotItem): Boolean {
        return try {
            !item.file.exists() || item.file.delete() || !item.file.exists()
        } catch (t: Throwable) {
            Logging.e(TAG, "Failed to delete ${item.file.name}", t)
            false
        }
    }
}
