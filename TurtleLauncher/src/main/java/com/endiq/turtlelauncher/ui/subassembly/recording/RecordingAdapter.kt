package com.endiq.turtlelauncher.ui.subassembly.recording

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.task.Task
import com.endiq.turtlelauncher.task.TaskExecutors
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class RecordingAdapter(
    private val items: MutableList<RecordingItem>,
    private val onPlay: (RecordingItem) -> Unit,
    private val onShare: (RecordingItem) -> Unit,
    private val onDelete: (RecordingItem) -> Unit
) : RecyclerView.Adapter<RecordingAdapter.ViewHolder>() {

    companion object {
        private const val TAG = "RecordingAdapter"
    }

    private val dateFormat = SimpleDateFormat("MMM d, yyyy  HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recording, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun removeItem(item: RecordingItem) {
        val index = items.indexOf(item)
        if (index >= 0) {
            items.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    fun replaceAll(newItems: List<RecordingItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnail: ImageView = itemView.findViewById(R.id.recording_thumbnail)
        private val duration: TextView = itemView.findViewById(R.id.recording_duration)
        private val name: TextView = itemView.findViewById(R.id.recording_name)
        private val meta: TextView = itemView.findViewById(R.id.recording_meta)
        private val shareButton: ImageView = itemView.findViewById(R.id.recording_share)
        private val deleteButton: ImageView = itemView.findViewById(R.id.recording_delete)

        fun bind(item: RecordingItem) {
            thumbnail.setImageDrawable(null)
            name.text = item.file.name
            meta.text = itemView.context.getString(
                R.string.recordings_gallery_item_meta,
                formatSize(item.sizeBytes),
                dateFormat.format(item.lastModified)
            )
            duration.text = if (item.durationMs > 0) formatDuration(item.durationMs) else ""
            duration.visibility = if (item.durationMs > 0) android.view.View.VISIBLE else android.view.View.GONE

            itemView.setOnClickListener { onPlay(item) }
            shareButton.setOnClickListener { onShare(item) }
            deleteButton.setOnClickListener { onDelete(item) }

            loadThumbnail(item)
        }

        private fun loadThumbnail(item: RecordingItem) {
            Task.runTask {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(item.file.absolutePath)
                    retriever.frameAtTime
                } catch (t: Throwable) {
                    Logging.e(TAG, "Thumbnail extraction failed for ${item.file.name}", t)
                    null
                } finally {
                    try { retriever.release() } catch (t: Throwable) { Logging.e(TAG, "retriever.release() failed", t) }
                }
            }.ended(TaskExecutors.getAndroidUI()) { bitmap: Bitmap? ->
                // This holder may have been recycled for a different item by the time the decode
                // finishes - only paint the frame if it's still showing the same recording.
                val stillSameItem = items.getOrNull(bindingAdapterPosition) === item
                if (!stillSameItem) return@ended
                if (bitmap != null) thumbnail.setImageBitmap(bitmap)
                else thumbnail.setImageResource(R.drawable.ic_play_circle)
            }.onThrowable {
                Logging.e(TAG, "Thumbnail task threw")
            }.execute()
        }

        private fun formatDuration(ms: Long): String {
            val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.US, "%d:%02d", minutes, seconds)
        }

        private fun formatSize(bytes: Long): String {
            val mb = bytes / (1024.0 * 1024.0)
            return if (mb >= 1024) String.format(Locale.US, "%.2f GB", mb / 1024.0)
            else String.format(Locale.US, "%.1f MB", mb)
        }
    }
}
