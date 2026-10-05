package com.endiq.turtlelauncher.feature.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.utils.path.PathManager
import java.io.File

/**
 * Where AI-generated files live, and how to open them.
 *
 * Everything the Assistant produces - images, video, spoken audio - is written under
 * `TurtleAI/` inside the launcher's game home directory. That location is deliberate:
 *
 *  - it is inside the app's own storage, so no permission is needed to write there;
 *  - the launcher already exposes that directory through its DocumentsProvider, which means
 *    these files can be opened and shared like any other file the launcher owns (see
 *    [openFile]) without a second provider or a `file://` URI leaking out;
 *  - users can find them in the launcher's own file browser, next to their worlds and mods,
 *    instead of in a hidden internal folder.
 *
 * The chat transcript stores only the path - never the bytes - because it is rewritten on every
 * turn (see [AssistantHistory]); a few megabytes of base64 per picture would make that file
 * unusable.
 *
 * Everything here is best-effort: a full disk or a missing directory must never turn "here is
 * your picture" into a crash.
 */
object TurtleAiFiles {

    private const val TAG = "TurtleAiFiles"

    private const val FOLDER = "TurtleAI"

    /** `<game home>/TurtleAI/`. */
    fun directory(): File = File(PathManager.DIR_GAME_HOME, FOLDER)

    private fun uniqueFile(prefix: String, extension: String): File? {
        val dir = directory()
        if (!dir.isDirectory && !dir.mkdirs()) {
            Logging.w(TAG, "Couldn't create " + dir.absolutePath)
            return null
        }
        return File(dir, prefix + "_" + System.currentTimeMillis() + "." + extension)
    }

    /**
     * Writes [bytes] as a new file and returns it, or null if it could not be written. The name
     * is unique per call, so two generations in the same second never overwrite each other.
     */
    @JvmStatic
    fun save(bytes: ByteArray, mimeType: String, prefix: String = "ai"): File? = runCatching {
        val extension = extensionFor(mimeType)
        val file = uniqueFile(prefix, extension) ?: return@runCatching null
        file.writeBytes(bytes)
        file
    }.onFailure { e -> Logging.w(TAG, "Couldn't save a generated file", e) }
        .getOrNull()

    private fun extensionFor(mimeType: String): String {
        val mime = mimeType.substringBefore(';').trim().lowercase()
        return when (mime) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "video/mp4" -> "mp4"
            "video/webm" -> "webm"
            "audio/wav", "audio/x-wav", "audio/l16" -> "wav"
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/ogg" -> "ogg"
            "audio/mp4", "audio/m4a" -> "m4a"
            "text/plain" -> "txt"
            else -> "bin"
        }
    }

    /** A rough mime type for a file we are about to open, from its extension. */
    @JvmStatic
    fun mimeTypeOf(file: File): String = when (file.extension.lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "wav" -> "audio/wav"
        "mp3" -> "audio/mpeg"
        "ogg" -> "audio/ogg"
        "m4a" -> "audio/mp4"
        "txt", "log" -> "text/plain"
        else -> "*/*"
    }

    /**
     * Hands a generated file to whatever app can display it (gallery, video player, music
     * player), through the launcher's DocumentsProvider so the receiving app gets a proper
     * content URI with read permission instead of a file path it cannot open.
     */
    @JvmStatic
    fun openFile(context: Context, file: File) {
        runCatching {
            if (!file.isFile) return@runCatching
            val uri: Uri = DocumentsContract.buildDocumentUri(
                context.getString(R.string.storageProviderAuthorities),
                file.absolutePath
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeTypeOf(file))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure { e ->
            // No app wants the file type, or it is not inside the provider root after all.
            Logging.w(TAG, "Couldn't open " + file.name, e)
        }
    }

    /**
     * Deletes every generated file - called when the conversation is cleared, so "delete my
     * history" really does remove the pictures, videos and recordings the Assistant produced.
     */
    @JvmStatic
    fun deleteAll() {
        runCatching {
            directory().listFiles()?.forEach { file ->
                if (!file.delete()) Logging.w(TAG, "Couldn't delete " + file.name)
            }
        }.onFailure { e -> Logging.w(TAG, "Couldn't clear generated files", e) }
    }
}
