package com.endiq.turtlelauncher.ui.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.task.Task
import com.endiq.turtlelauncher.task.TaskExecutors
import com.endiq.turtlelauncher.ui.subassembly.recording.RecordingAdapter
import com.endiq.turtlelauncher.ui.subassembly.recording.RecordingItem
import com.endiq.turtlelauncher.ui.subassembly.recording.RecordingsRepository

class RecordingsGalleryActivity : BaseActivity() {

    companion object {
        private const val TAG = "RecordingsGalleryActivity"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var countText: TextView
    private lateinit var adapter: RecordingAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recordings_gallery)

        findViewById<View>(R.id.recordings_back_button).setOnClickListener { finish() }
        recyclerView = findViewById(R.id.recordings_list)
        emptyView = findViewById(R.id.recordings_empty)
        countText = findViewById(R.id.recordings_count)

        adapter = RecordingAdapter(
            items = mutableListOf(),
            onPlay = ::playRecording,
            onShare = ::shareRecording,
            onDelete = ::confirmDelete
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        reload()
    }

    override fun onResume() {
        super.onResume()
        // Catches a recording finished/deleted elsewhere while this screen was backgrounded.
        reload()
    }

    private fun reload() {
        val context = applicationContext
        Task.runTask {
            runCatching { RecordingsRepository.listRecordings(context) }
                .onFailure { e -> Logging.e(TAG, "Failed to list recordings", e) }
                .getOrDefault(emptyList())
        }.ended(TaskExecutors.getAndroidUI()) { listed ->
            val items = listed ?: emptyList()
            adapter.replaceAll(items)
            emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            recyclerView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
            countText.text = resources.getQuantityString(
                R.plurals.recordings_gallery_count, items.size, items.size
            )
        }.onThrowable {
            Logging.e(TAG, "Recordings list task threw")
        }.execute()
    }

    private fun uriFor(item: RecordingItem): Uri =
        FileProvider.getUriForFile(this, "$packageName.provider", item.file)

    private fun playRecording(item: RecordingItem) {
        try {
            val uri = uriFor(item)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (t: Throwable) {
            Logging.e(TAG, "Could not open recording ${item.file.name}", t)
            Toast.makeText(this, R.string.recordings_gallery_play_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareRecording(item: RecordingItem) {
        try {
            val uri = uriFor(item)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.generic_share)))
        } catch (t: Throwable) {
            Logging.e(TAG, "Could not share recording ${item.file.name}", t)
            Toast.makeText(this, R.string.recordings_gallery_share_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete(item: RecordingItem) {
        AlertDialog.Builder(this)
            .setTitle(R.string.generic_delete)
            .setMessage(getString(R.string.recordings_gallery_delete_confirm, item.file.name))
            .setPositiveButton(R.string.generic_delete) { _, _ -> deleteRecording(item) }
            .setNegativeButton(R.string.recordings_gallery_cancel, null)
            .show()
    }

    private fun deleteRecording(item: RecordingItem) {
        Task.runTask {
            RecordingsRepository.delete(item)
        }.ended(TaskExecutors.getAndroidUI()) { deleted ->
            if (deleted == true) {
                adapter.removeItem(item)
                emptyView.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
                recyclerView.visibility = if (adapter.itemCount == 0) View.GONE else View.VISIBLE
                countText.text = resources.getQuantityString(
                    R.plurals.recordings_gallery_count, adapter.itemCount, adapter.itemCount
                )
            } else {
                Toast.makeText(this, R.string.recordings_gallery_delete_failed, Toast.LENGTH_SHORT).show()
            }
        }.onThrowable {
            Logging.e(TAG, "Delete task threw")
        }.execute()
    }
}
