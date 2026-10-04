package com.endiq.turtlelauncher.feature.ai

import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.utils.path.PathManager
import java.io.File

/**
 * Where generated images live on disk.
 *
 * The chat keeps the *path* of a generated image, not its bytes: the transcript is persisted
 * as JSON on every turn (see [AssistantHistory]), and embedding a megabyte of base64 per
 * picture would make that file grow without bound and be rewritten on every message. Files
 * under the app's own files directory are private to the app and survive restarts.
 *
 * Everything here is best-effort: a full disk or a revoked files directory must never turn
 * "I made you a picture" into a crash.
 */
object TurtleAiImages {

    private const val TAG = "TurtleAiImages"

    /** `<files>/ai_images/`. */
    private fun directory(): File = File(PathManager.DIR_FILE, "ai_images")

    /**
     * Writes [bytes] as a new image and returns the file, or null if it could not be written.
     * The name is unique per call, so two images generated in the same session never collide.
     */
    @JvmStatic
    fun save(bytes: ByteArray, mimeType: String): File? = runCatching {
        val dir = directory()
        if (!dir.isDirectory && !dir.mkdirs()) return@runCatching null
        val extension = when (mimeType.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "png"
        }
        val file = File(dir, "img_" + System.currentTimeMillis() + "." + extension)
        file.writeBytes(bytes)
        file
    }.onFailure { e -> Logging.w(TAG, "Couldn't save a generated image", e) }
        .getOrNull()

    /**
     * Deletes every generated image - called when the conversation is cleared, so "delete my
     * history" really does remove the pictures the conversation produced.
     */
    @JvmStatic
    fun deleteAll() {
        runCatching {
            directory().listFiles()?.forEach { file ->
                if (!file.delete()) Logging.w(TAG, "Couldn't delete " + file.name)
            }
        }.onFailure { e -> Logging.w(TAG, "Couldn't clear generated images", e) }
    }
}
