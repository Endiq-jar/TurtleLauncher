package com.endiq.turtlelauncher.ui.subassembly.recording

import java.io.File

/**
 * A single recorded gameplay video on disk, as shown in the Recordings Gallery.
 * durationMs/widthPx/heightPx are 0 until [RecordingsRepository] has read them off the file
 * (MediaMetadataRetriever is slow enough that it always happens off the UI thread).
 */
data class RecordingItem(
    val file: File,
    val lastModified: Long,
    val sizeBytes: Long,
    val durationMs: Long = 0L,
    val widthPx: Int = 0,
    val heightPx: Int = 0
)
