package com.endiq.turtlelauncher.bridge

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.FileObserver
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.annotation.Keep
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.task.TaskExecutors
import java.io.File
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/** Android side of the two-VM native bridge. Only game-worker threads may wait on a picker. */
@Keep
object NativeFileDialogs {
    private const val REQUEST_BASE = 0x7400
    private val requestIds = java.util.concurrent.atomic.AtomicInteger()
    private var host = WeakReference<Activity>(null)
    private val serial = ReentrantLock()
    private data class Request(val mode: Int, val code: Int, val done: CountDownLatch = CountDownLatch(1),
        @Volatile var paths: Array<String>? = null)
    @Volatile private var pending: Request? = null
    private val exports = mutableListOf<FileObserver>()
    @JvmStatic private external fun bind()

    @JvmStatic
    fun install(activity: Activity) {
        host = WeakReference(activity)
        System.loadLibrary("turtlefiledialogs")
        bind()
    }

    @JvmStatic
    fun release(activity: Activity) {
        if (host.get() !== activity) return
        host.clear()
        pending?.done?.countDown()
        pending = null
        synchronized(exports) { exports.forEach { it.stopWatching() }; exports.clear() }
    }

    /** modes: 0 open, 1 multi-open, 2 save, 3 import folder snapshot. */
    @Keep
    @JvmStatic
    fun request(mode: Int, title: String?, defaultName: String?, filters: String?): Array<String>? {
        if (Looper.myLooper() == Looper.getMainLooper() || !serial.tryLock()) return null
        try {
            val activity = host.get()?.takeUnless { it.isFinishing || it.isDestroyed } ?: return null
            val state = Request(mode, REQUEST_BASE + (requestIds.getAndIncrement() and 0xff))
            pending = state
            activity.runOnUiThread {
                if (pending !== state) return@runOnUiThread
                runCatching {
                    val intent = when (mode) {
                        3 -> Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                        2 -> Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE)
                            putExtra(Intent.EXTRA_TITLE, safeName(defaultName ?: "export"))
                        }
                        else -> Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE)
                            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, mode == 1)
                            val types = filters.orEmpty().split(';', ',', '|').mapNotNull { pattern ->
                                MimeTypeMap.getSingleton().getMimeTypeFromExtension(pattern.trim().removePrefix("*.").lowercase())
                            }.distinct()
                            if (types.isNotEmpty()) putExtra(Intent.EXTRA_MIME_TYPES, types.toTypedArray())
                        }
                    }
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (mode == 2) intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    activity.startActivityForResult(intent, state.code)
                }.onFailure { state.done.countDown() }
            }
            if (!state.done.await(10, TimeUnit.MINUTES)) return null
            return state.paths
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return null
        } finally {
            pending = null
            serial.unlock()
        }
    }

    @JvmStatic
    fun onResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if ((requestCode and 0xff00) != REQUEST_BASE) return false
        val state = pending?.takeIf { it.code == requestCode } ?: return true
        if (resultCode != Activity.RESULT_OK || data == null) { state.done.countDown(); return true }
        val uris = data.clipData?.let { clip -> (0 until clip.itemCount).map { clip.getItemAt(it).uri } }
            ?: listOfNotNull(data.data)
        TaskExecutors.getDefault().execute {
            val root = File(activity.filesDir, "native-dialogs/${UUID.randomUUID()}")
            try {
                check(root.mkdirs())
                val paths = uris.mapIndexed { index, uri ->
                    val directory = File(root, index.toString()).apply { check(mkdirs()) }
                    when (state.mode) {
                        2 -> exportFile(activity, uri, directory)
                        3 -> importTree(activity, uri, directory)
                        else -> importFile(activity, uri, directory)
                    }.absolutePath
                }.toTypedArray()
                if (pending === state && state.done.count > 0) state.paths = paths
            } catch (e: Exception) {
                root.deleteRecursively()
                activity.runOnUiThread {
                    Toast.makeText(activity, activity.getString(R.string.native_file_dialog_error, e.message), Toast.LENGTH_LONG).show()
                }
            } finally {
                state.done.countDown()
            }
        }
        return true
    }

    private fun safeName(name: String): String = name.substringAfterLast('/').substringAfterLast('\\')
        .replace(Regex("[\\x00-\\x1f|]"), "_").take(180).takeUnless { it.isBlank() || it == "." || it == ".." } ?: "file"

    private fun displayName(activity: Activity, uri: Uri): String =
        activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }?.let(::safeName) ?: "file"

    private fun importFile(activity: Activity, uri: Uri, directory: File): File {
        val target = File(directory, displayName(activity, uri))
        activity.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open document" }
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    private fun importTree(activity: Activity, tree: Uri, directory: File): File {
        val resolver = activity.contentResolver
        var entries = 0
        fun copy(id: String, target: File, depth: Int) {
            require(depth < 32) { "Folder is too deeply nested" }
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
            resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    require(++entries <= 10000) { "Too many files in folder" }
                    val childId = cursor.getString(0)
                    val child = File(target, safeName(cursor.getString(1)))
                    require(!child.exists()) { "Duplicate document name" }
                    if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        check(child.mkdir()); copy(childId, child, depth + 1)
                    } else {
                        resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, childId)).use { input ->
                            requireNotNull(input); child.outputStream().use { input.copyTo(it) }
                        }
                    }
                }
            } ?: error("Cannot read folder")
        }
        copy(DocumentsContract.getTreeDocumentId(tree), directory, 0)
        return directory
    }

    @Suppress("DEPRECATION")
    private fun exportFile(activity: Activity, uri: Uri, directory: File): File {
        val target = File(directory, displayName(activity, uri))
        // A filesystem path lets the mod use ordinary Java IO. Publish after its writer closes.
        val resolver = activity.applicationContext.contentResolver
        val observer = object : FileObserver(directory.absolutePath, FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                if (path != target.name || !target.isFile) return
                TaskExecutors.getDefault().execute {
                    runCatching {
                        target.inputStream().use { input ->
                            resolver.openOutputStream(uri, "wt").use { output ->
                                requireNotNull(output); input.copyTo(output)
                            }
                        }
                    }.onFailure { error ->
                        activity.runOnUiThread {
                            Toast.makeText(activity, activity.getString(R.string.native_file_dialog_error, error.message), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
        synchronized(exports) { exports.add(observer) }
        observer.startWatching()
        return target
    }
}
