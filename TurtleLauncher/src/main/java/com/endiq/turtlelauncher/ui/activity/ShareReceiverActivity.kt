package com.endiq.turtlelauncher.ui.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.IntentCompat
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.utils.path.PathManager
import net.endiq.launcher.LauncherActivity
import java.io.File

class ShareReceiverActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_SEND) {
            finish()
            return
        }

        // A recording goes to the transcription model, everything else is treated as a log.
        val sharedAudio = extractSharedAudio(intent)
        if (sharedAudio != null) {
            val launchIntent = Intent(this, LauncherActivity::class.java).apply {
                putExtra(LauncherActivity.EXTRA_OPEN_ASSISTANT, true)
                putExtra(LauncherActivity.EXTRA_SHARED_AUDIO_PATH, sharedAudio.absolutePath)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(launchIntent)
            finish()
            return
        }

        val sharedText = extractSharedLog(intent)
        if (sharedText.isNullOrBlank()) {
            Toast.makeText(this, R.string.assistant_share_nothing, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val savedFile = saveSharedLog(sharedText)
        if (savedFile == null) {
            Toast.makeText(this, R.string.assistant_share_failed, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Hand the log to the Assistant inside the main launcher UI.
        val launchIntent = Intent(this, LauncherActivity::class.java).apply {
            putExtra(LauncherActivity.EXTRA_OPEN_ASSISTANT, true)
            putExtra(LauncherActivity.EXTRA_SHARED_LOG_PATH, savedFile.absolutePath)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(launchIntent)
        finish()
    }

    /**
     * Copies a shared recording into the launcher's own storage and returns it, or null when
     * this is not a share of audio or video.
     *
     * Detection is by mime type first (that is what a share comes with) and by file extension
     * second, because some senders report `application/octet-stream` for everything. Anything
     * larger than [MAX_SHARED_AUDIO_BYTES] is refused: the file has to be base64-encoded to be
     * sent to the model, which inflates it by a third, and a gigantic file would simply be
     * rejected after a long upload.
     */
    private fun extractSharedAudio(intent: Intent): File? {
        val streamUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?: return null
        val mime = intent.type.orEmpty().lowercase()
        val name = queryDisplayName(streamUri).lowercase()
        val looksLikeAudio = mime.startsWith("audio/") || mime.startsWith("video/") ||
            AUDIO_EXTENSIONS.any { name.endsWith(it) }
        if (!looksLikeAudio) return null

        return runCatching {
            contentResolver.openInputStream(streamUri)?.use { input ->
                val dir = File(PathManager.DIR_LAUNCHER_LOG)
                if (!dir.exists()) dir.mkdirs()
                val extension = AUDIO_EXTENSIONS.firstOrNull { name.endsWith(it) } ?: ".m4a"
                val target = File(dir, "shared_audio_" + System.currentTimeMillis() + extension)
                target.outputStream().use { output ->
                    val copied = input.copyTo(output, bufferSize = 64 * 1024)
                    if (copied > MAX_SHARED_AUDIO_BYTES) {
                        target.delete()
                        null
                    } else {
                        target
                    }
                }
            }
        }.onFailure { e ->
            Logging.e(TAG, "Could not copy the shared recording", e)
        }.getOrNull()
    }

    /** The sender's filename, when it tells us one - used only to guess the file type. */
    private fun queryDisplayName(uri: Uri): String = runCatching {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index).orEmpty() else ""
        }.orEmpty()
    }.getOrDefault("")

    /**
     * Reads whatever the sending app shared - plain text first (some apps share the log
     * content itself), then a shared file. Returns null when nothing usable came through
     * (unsupported payload, unreadable file, or binary data that can't be a log).
     */
    private fun extractSharedLog(intent: Intent): String? {
        val directText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        if (!directText.isNullOrBlank()) return directText.take(MAX_SHARED_CHARS)

        val streamUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?: return null
        return readSharedStream(streamUri)
    }

    private fun readSharedStream(uri: Uri): String? {
        return runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(64 * 1024)
                val output = java.io.ByteArrayOutputStream()
                var total = 0
                while (total < MAX_SHARED_BYTES) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    total += read
                }
                val text = output.toString(Charsets.UTF_8.name())
                // A log that contains NUL bytes is binary (e.g. a zipped bundle) - the
                // Assistant can't read that, so reject it instead of showing garbage.
                if (text.contains('\u0000')) null else text.take(MAX_SHARED_CHARS)
            }
        }.onFailure { e ->
            Logging.e(TAG, "Could not read the shared log stream", e)
        }.getOrNull()
    }

    private fun saveSharedLog(text: String): File? {
        return runCatching {
            val dir = File(PathManager.DIR_LAUNCHER_LOG)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, SHARED_LOG_FILE_NAME)
            file.writeText(text)
            file
        }.onFailure { e ->
            Logging.e(TAG, "Could not save the shared log", e)
        }.getOrNull()
    }

    companion object {
        private const val TAG = "ShareReceiverActivity"

        /** Name of the persisted copy inside DIR_LAUNCHER_LOG. */
        const val SHARED_LOG_FILE_NAME = "shared_log.txt"

        /** Shared payloads are capped: logs beyond this are truncated from the old end by
         *  the Assistant itself; the cap here only protects the copy/read step. */
        private const val MAX_SHARED_CHARS = 1_000_000
        private const val MAX_SHARED_BYTES = 2 * 1024 * 1024

        /** Recordings are capped at the same size the model accepts inline (~20MB). */
        private const val MAX_SHARED_AUDIO_BYTES = 18L * 1024 * 1024

        /** Extensions that mean "record this as audio" when the mime type is unhelpful. */
        private val AUDIO_EXTENSIONS = listOf(
            ".mp3", ".m4a", ".aac", ".wav", ".ogg", ".opus", ".flac", ".amr", ".3gp", ".mp4"
        )
    }
}
