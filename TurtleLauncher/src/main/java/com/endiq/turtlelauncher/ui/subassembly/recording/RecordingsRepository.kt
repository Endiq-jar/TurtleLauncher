package com.endiq.turtlelauncher.ui.subassembly.recording

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import com.endiq.turtlelauncher.feature.log.Logging
import java.io.File

/**
 * Mirrors the output locations ScreenRecorder.kt writes to (external Movies/recordings,
 * falling back to the app cache dir when external storage wasn't available at record time) so
 * the gallery always finds whatever ScreenRecorder actually saved.
 */
object RecordingsRepository {
    private const val TAG = "RecordingsRepository"

    fun recordingDirs(context: Context): List<File> {
        val dirs = mutableListOf<File>()
        context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.let { dirs.add(File(it, "recordings")) }
        dirs.add(File(context.cacheDir, "recordings"))
        return dirs
    }

    /** Lists every .mp4 across both locations, newest first. Safe to call off the main thread only. */
    fun listRecordings(context: Context): List<RecordingItem> {
        val files = recordingDirs(context)
            .filter { it.isDirectory }
            .flatMap { it.listFiles { f -> f.isFile && f.extension.equals("mp4", ignoreCase = true) }?.toList() ?: emptyList() }
            .distinctBy { it.absolutePath }

        return files.map { file ->
            val (durationMs, width, height) = readMetadata(file)
            RecordingItem(file, file.lastModified(), file.length(), durationMs, width, height)
        }.sortedByDescending { it.lastModified }
    }

    private fun readMetadata(file: File): Triple<Long, Int, Int> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            Triple(duration, width, height)
        } catch (t: Throwable) {
            Logging.e(TAG, "Could not read metadata for ${file.name}", t)
            Triple(0L, 0, 0)
        } finally {
            try { retriever.release() } catch (t: Throwable) { Logging.e(TAG, "retriever.release() failed", t) }
        }
    }

    /** @return true if the file no longer exists afterward (already-missing counts as success). */
    fun delete(item: RecordingItem): Boolean {
        return try {
            !item.file.exists() || item.file.delete() || !item.file.exists()
        } catch (t: Throwable) {
            Logging.e(TAG, "Failed to delete ${item.file.name}", t)
            false
        }
    }
}
