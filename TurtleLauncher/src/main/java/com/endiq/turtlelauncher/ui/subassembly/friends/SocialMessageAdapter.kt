package com.endiq.turtlelauncher.ui.subassembly.friends

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.databinding.ItemSocialMessageBinding
import com.endiq.turtlelauncher.feature.social.SocialMessage
import java.text.DateFormat
import java.util.Date

/**
 * Rows for the conversation screen.
 *
 * One layout for both sides of the conversation: the launcher's social threads are 1:1 DMs,
 * lobby chatter and a single community channel, so a full bubble side-swap is not worth a
 * second layout. Self messages are right-aligned with the filled background; everyone else is
 * left-aligned on the grid card. Queued ([SocialMessage.pending]) and rejected
 * ([SocialMessage.failed]) messages stay visible and are labelled - the store never drops a
 * message the user typed.
 */
class SocialMessageAdapter(
    private val onDelete: (SocialMessage) -> Unit,
) : RecyclerView.Adapter<SocialMessageAdapter.Holder>() {

    private val rows = mutableListOf<SocialMessage>()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(messages: List<SocialMessage>) {
        rows.clear()
        rows.addAll(messages)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemSocialMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(rows[position], onDelete)
    }

    class Holder(private val binding: ItemSocialMessageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: SocialMessage, onDelete: (SocialMessage) -> Unit) {
            val context = binding.root.context
            val self = message.fromSelf

            val author = if (self) {
                context.getString(R.string.social_message_from, message.authorName)
            } else {
                message.authorName
            }
            binding.messageAuthor.text = author
            binding.messageAuthor.gravity = if (self) Gravity.END else Gravity.START

            binding.messageBody.text = message.text.ifBlank { context.getString(R.string.social_message_deleted) }
            binding.messageBody.gravity = if (self) Gravity.END else Gravity.START
            binding.messageBody.setBackgroundResource(
                if (self) R.drawable.background_edit_box else R.drawable.background_grid_card
            )
            val bodyLayoutParams = binding.messageBody.layoutParams as LinearLayout.LayoutParams
            bodyLayoutParams.gravity = if (self) Gravity.END else Gravity.START
            binding.messageBody.layoutParams = bodyLayoutParams

            val attachment = message.attachments.firstOrNull()
            if (attachment != null) {
                binding.messageAttachment.visibility = View.VISIBLE
                binding.messageAttachment.text = context.getString(
                    R.string.social_attachment,
                    attachment.fileName ?: attachment.url,
                )
            } else {
                binding.messageAttachment.visibility = View.GONE
            }

            val meta = when {
                message.failed -> context.getString(
                    R.string.social_message_failed,
                    message.failureReason ?: context.getString(R.string.social_send_failed),
                )
                message.pending -> context.getString(R.string.social_message_pending)
                else -> DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.sentAtMs))
            }
            binding.messageMeta.text = meta
            binding.messageMeta.gravity = if (self) Gravity.END else Gravity.START
            binding.messageMeta.setTextColor(
                ContextCompat.getColor(
                    context,
                    when {
                        message.failed -> R.color.accent_orange
                        message.pending -> R.color.turtle_text_secondary
                        else -> R.color.turtle_text_disabled
                    },
                )
            )

            binding.root.setOnLongClickListener {
                if (message.deletable) {
                    onDelete(message)
                    true
                } else {
                    false
                }
            }
        }
    }
}
