package com.endiq.turtlelauncher.ui.activity

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.task.Task
import com.endiq.turtlelauncher.task.TaskExecutors
import com.endiq.turtlelauncher.ui.subassembly.screenshot.ScreenshotGalleryAdapter
import com.endiq.turtlelauncher.ui.subassembly.screenshot.ScreenshotItem
import com.endiq.turtlelauncher.ui.subassembly.screenshot.ScreenshotsRepository

class ScreenshotsGalleryActivity : BaseActivity() {

    companion object {
        private const val TAG = "ScreenshotsGalleryActivity"
        private const val GRID_COLUMNS = 3
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var countText: TextView
    private lateinit var adapter: ScreenshotGalleryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screenshots_gallery)

        findViewById<View>(R.id.screenshots_back_button).setOnClickListener { finish() }
        recyclerView = findViewById(R.id.screenshots_grid)
        emptyView = findViewById(R.id.screenshots_empty)
        countText = findViewById(R.id.screenshots_count)

        val thumbnailSizePx = resources.displayMetrics.widthPixels / GRID_COLUMNS
        adapter = ScreenshotGalleryAdapter(
            items = mutableListOf(),
            thumbnailSizePx = thumbnailSizePx,
            onOpen = ::openFullscreen,
            onDelete = ::confirmDelete
        )
        recyclerView.layoutManager = GridLayoutManager(this, GRID_COLUMNS)
        recyclerView.adapter = adapter

        reload()
    }

    override fun onResume() {
        super.onResume()
        // Catches a screenshot taken/deleted elsewhere while this screen was backgrounded.
        reload()
    }

    private fun reload() {
        val context = applicationContext
        Task.runTask {
            runCatching { ScreenshotsRepository.listScreenshots(context) }
                .onFailure { e -> Logging.e(TAG, "Failed to list screenshots", e) }
                .getOrDefault(emptyList())
        }.ended(TaskExecutors.getAndroidUI()) { items: List<ScreenshotItem>? ->
            val screenshots = items ?: emptyList()
            adapter.replaceAll(screenshots)
            updateEmptyState(screenshots.isEmpty(), screenshots.size)
        }.onThrowable {
            Logging.e(TAG, "Screenshots list task threw")
        }.execute()
    }

    private fun updateEmptyState(empty: Boolean, count: Int) {
        emptyView.visibility = if (empty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (empty) View.GONE else View.VISIBLE
        countText.text = resources.getQuantityString(R.plurals.screenshots_gallery_count, count, count)
    }

    private fun uriFor(item: ScreenshotItem): Uri =
        FileProvider.getUriForFile(this, "$packageName.provider", item.file)

    private fun openFullscreen(item: ScreenshotItem) {
        try {
            val uri = uriFor(item)
            val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            val container = android.widget.FrameLayout(this)

            val imageView = ImageView(this).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageURI(uri)
                setOnClickListener { dialog.dismiss() }
            }
            container.addView(imageView)

            val shareButton = ImageButton(this).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { gravity = android.view.Gravity.TOP or android.view.Gravity.END; topMargin = 32; rightMargin = 32 }
                setImageResource(R.drawable.ic_share)
                background = null
                setColorFilter(Color.WHITE)
                contentDescription = getString(R.string.generic_share)
                setOnClickListener { shareScreenshot(item) }
            }
            container.addView(shareButton)

            dialog.setContentView(container)
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
            dialog.show()
        } catch (t: Throwable) {
            Logging.e(TAG, "Could not open screenshot ${item.file.name}", t)
            Toast.makeText(this, R.string.screenshots_gallery_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareScreenshot(item: ScreenshotItem) {
        try {
            val uri = uriFor(item)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.generic_share)))
        } catch (t: Throwable) {
            Logging.e(TAG, "Could not share screenshot ${item.file.name}", t)
            Toast.makeText(this, R.string.screenshots_gallery_share_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete(item: ScreenshotItem) {
        AlertDialog.Builder(this)
            .setTitle(R.string.generic_delete)
            .setMessage(getString(R.string.recordings_gallery_delete_confirm, item.file.name))
            .setPositiveButton(R.string.generic_delete) { _, _ -> deleteScreenshot(item) }
            .setNegativeButton(R.string.recordings_gallery_cancel, null)
            .show()
    }

    private fun deleteScreenshot(item: ScreenshotItem) {
        Task.runTask {
            ScreenshotsRepository.delete(item)
        }.ended(TaskExecutors.getAndroidUI()) { deleted: Boolean? ->
            if (deleted == true) {
                adapter.removeItem(item)
                updateEmptyState(adapter.itemCount == 0, adapter.itemCount)
            } else {
                Toast.makeText(this, R.string.screenshots_gallery_delete_failed, Toast.LENGTH_SHORT).show()
            }
        }.onThrowable {
            Logging.e(TAG, "Delete task threw")
        }.execute()
    }
}
