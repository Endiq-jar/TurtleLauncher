package com.endiq.turtlelauncher.ui.subassembly.aichat

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.Gravity
import android.view.ViewGroup
import android.view.View
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.databinding.ItemChatMessageBinding
import com.endiq.turtlelauncher.feature.ai.TurtleAiFiles
import java.io.File

class ChatMessageAdapter(
    private val mData: MutableList<ChatMessage> = mutableListOf()
) : RecyclerView.Adapter<ChatMessageAdapter.InnerHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InnerHolder {
        return InnerHolder(ItemChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: InnerHolder, position: Int) {
        holder.setData(mData[position])
    }

    override fun getItemCount(): Int = mData.size

    fun addMessage(message: ChatMessage) {
        mData.add(message)
        notifyItemInserted(mData.size - 1)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clear() {
        mData.clear()
        notifyDataSetChanged()
    }

    class InnerHolder(private val binding: ItemChatMessageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun setData(message: ChatMessage) {
            if (message.isUser) {
                binding.messageBubble.text = message.text
            } else {
                // Assistant replies are Markdown (Gemini answers with **bold**, lists, code...).
                val rendered = ChatMarkdown.render(message.text, binding.root.context)
                binding.messageBubble.text = rendered
                // Links need LinkMovementMethod, which drops text selection - only swap it in
                // for bubbles that actually contain a link.
                if (rendered is android.text.Spanned &&
                    rendered.getSpans(0, rendered.length, android.text.style.URLSpan::class.java).isNotEmpty()
                ) {
                    binding.messageBubble.movementMethod = android.text.method.LinkMovementMethod.getInstance()
                }
            }
            // A turn can be an image with a caption, or (when the model returned no text) an
            // image alone - an empty bubble next to a picture looks like a bug.
            binding.messageBubble.visibility =
                if (message.text.isBlank()) View.GONE else View.VISIBLE

            val imagePath = message.imagePath
            if (imagePath.isNullOrBlank()) {
                // The holder is recycled: without clearing, a previous row's picture can
                // reappear under an unrelated message.
                Glide.with(binding.messageImage).clear(binding.messageImage)
                binding.messageImage.visibility = View.GONE
            } else {
                binding.messageImage.visibility = View.VISIBLE
                Glide.with(binding.messageImage)
                    .load(File(imagePath))
                    .into(binding.messageImage)
            }

            val context = binding.root.context

            // Generated video and audio can't be shown inline, so they get a row that opens
            // the file in whatever app can play it (the launcher's DocumentsProvider hands
            // out the URI - see TurtleAiFiles).
            val mediaPath = message.mediaPath
            if (mediaPath.isNullOrBlank()) {
                binding.messageFile.visibility = View.GONE
                binding.messageFile.setOnClickListener(null)
            } else {
                val file = File(mediaPath)
                binding.messageFile.visibility = View.VISIBLE
                binding.messageFile.text =
                    message.mediaLabel ?: context.getString(R.string.ai_media_open_file)
                binding.messageFile.setOnClickListener {
                    TurtleAiFiles.openFile(it.context, file)
                }
            }
            val params = binding.root.layoutParams as? LinearLayout.LayoutParams
            if (message.isUser) {
                binding.root.gravity = Gravity.END
                binding.messageBubble.setBackgroundResource(R.drawable.background_chat_bubble_user)
                binding.messageBubble.setTextColor(context.getColor(R.color.background_app))
            } else {
                binding.root.gravity = Gravity.START
                binding.messageBubble.setBackgroundResource(R.drawable.background_chat_bubble_ai)
                binding.messageBubble.setTextColor(context.getColor(R.color.turtle_text_primary))
            }
            params?.let { binding.root.layoutParams = it }
        }
    }
}
