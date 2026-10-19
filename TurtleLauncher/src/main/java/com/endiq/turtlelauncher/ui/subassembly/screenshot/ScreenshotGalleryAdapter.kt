package com.endiq.turtlelauncher.ui.subassembly.screenshot

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.task.Task
import com.endiq.turtlelauncher.task.TaskExecutors

class ScreenshotGalleryAdapter(
    private val items: MutableList<ScreenshotItem>,
    private val thumbnailSizePx: Int,
    private val onOpen: (ScreenshotItem) -> Unit,
    private val onDelete: (ScreenshotItem) -> Unit
) : RecyclerView.Adapter<ScreenshotGalleryAdapter.ViewHolder>() {

    companion object {
        private const val TAG = "ScreenshotGalleryAdapter"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_screenshot, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun removeItem(item: ScreenshotItem) {
        val index = items.indexOf(item)
        if (index >= 0) {
            items.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    fun replaceAll(newItems: List<ScreenshotItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnail: ImageView = itemView.findViewById(R.id.screenshot_thumbnail)

        fun bind(item: ScreenshotItem) {
            thumbnail.setImageDrawable(null)
            itemView.setOnClickListener { onOpen(item) }
            itemView.setOnLongClickListener { onDelete(item); true }
            loadThumbnail(item)
        }

        private fun loadThumbnail(item: ScreenshotItem) {
            Task.runTask {
                runCatching { decodeSampledBitmap(item.file.absolutePath, thumbnailSizePx) }
                    .onFailure { e -> Logging.e(TAG, "Thumbnail decode failed for ${item.file.name}", e) }
                    .getOrNull()
            }.ended(TaskExecutors.getAndroidUI()) { bitmap: Bitmap? ->
                // The holder may already be showing a different item by the time this decode
                // finishes (fast scroll during a background task) - don't paint a stale frame.
                val stillSameItem = items.getOrNull(bindingAdapterPosition) === item
                if (stillSameItem && bitmap != null) thumbnail.setImageBitmap(bitmap)
            }.onThrowable {
                Logging.e(TAG, "Thumbnail task threw")
            }.execute()
        }

        private fun decodeSampledBitmap(path: String, targetSizePx: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sampleSize = 1
            while ((bounds.outWidth / sampleSize) > targetSizePx * 2 || (bounds.outHeight / sampleSize) > targetSizePx * 2) {
                sampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            return BitmapFactory.decodeFile(path, opts)
        }
    }
}
