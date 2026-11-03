package com.endiq.turtlelauncher.ui.subassembly.friends

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.databinding.ItemSocialConversationBinding
import com.endiq.turtlelauncher.databinding.ItemSocialFriendBinding
import com.endiq.turtlelauncher.databinding.ItemSocialRequestBinding
import com.endiq.turtlelauncher.feature.social.ConversationKind
import com.endiq.turtlelauncher.feature.social.FriendRequest
import com.endiq.turtlelauncher.feature.social.LinkedChannel
import com.endiq.turtlelauncher.feature.social.LobbyMember
import com.endiq.turtlelauncher.feature.social.PartyLobby
import com.endiq.turtlelauncher.feature.social.PresenceStatus
import com.endiq.turtlelauncher.feature.social.SocialConversation
import com.endiq.turtlelauncher.feature.social.SocialFriend
import com.endiq.turtlelauncher.feature.social.SocialSource
import com.endiq.turtlelauncher.feature.social.VoiceParticipant

/** One row in the Friends screen. The tab decides which row types are produced. */
sealed class SocialRow {
    data class Friend(val friend: SocialFriend) : SocialRow()
    data class Request(val request: FriendRequest) : SocialRow()
    data class Conversation(val conversation: SocialConversation) : SocialRow()
    data class Party(val lobby: PartyLobby) : SocialRow()
    data class Channel(val channel: LinkedChannel?, val canUnlink: Boolean) : SocialRow()
    data class Participant(val participant: VoiceParticipant) : SocialRow()
}

/**
 * Renders every tab of the Friends screen.
 *
 * One adapter with three view types (person, request, thread) instead of seven adapters:
 * a lobby/channel row is visually a thread row, and a voice participant is visually a person
 * row, so splitting them further would only duplicate binding code. Rows carry click handling
 * for their whole surface; the request row additionally has Accept/Reject buttons.
 */
class SocialRowAdapter(private val listener: Listener) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface Listener {
        fun onFriendClicked(friend: SocialFriend)
        fun onRequestAccept(request: FriendRequest)
        fun onRequestReject(request: FriendRequest)
        fun onConversationClicked(conversation: SocialConversation)
        fun onPartyClicked(lobby: PartyLobby)
        fun onChannelClicked(channel: LinkedChannel?)
        fun onParticipantClicked(participant: VoiceParticipant)
    }

    private val rows = mutableListOf<SocialRow>()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(newRows: List<SocialRow>) {
        rows.clear()
        rows.addAll(newRows)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is SocialRow.Friend, is SocialRow.Participant -> TYPE_PERSON
        is SocialRow.Request -> TYPE_REQUEST
        is SocialRow.Conversation, is SocialRow.Party, is SocialRow.Channel -> TYPE_THREAD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_PERSON -> PersonHolder(ItemSocialFriendBinding.inflate(inflater, parent, false))
            TYPE_REQUEST -> RequestHolder(ItemSocialRequestBinding.inflate(inflater, parent, false))
            else -> ThreadHolder(ItemSocialConversationBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is SocialRow.Friend -> (holder as PersonHolder).bindFriend(row.friend, listener)
            is SocialRow.Participant -> (holder as PersonHolder).bindParticipant(row.participant, listener)
            is SocialRow.Request -> (holder as RequestHolder).bind(row.request, listener)
            is SocialRow.Conversation -> (holder as ThreadHolder).bindConversation(row.conversation, listener)
            is SocialRow.Party -> (holder as ThreadHolder).bindParty(row.lobby, listener)
            is SocialRow.Channel -> (holder as ThreadHolder).bindChannel(row.channel, listener)
        }
    }

    private class PersonHolder(private val binding: ItemSocialFriendBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bindFriend(friend: SocialFriend, listener: Listener) {
            val context = binding.root.context
            binding.friendName.text = friend.displayName
            binding.friendSource.text = context.getString(
                if (friend.source == SocialSource.DISCORD) R.string.social_source_discord
                else R.string.social_source_turtle
            )
            binding.presenceDot.backgroundTintList =
                ColorStateList.valueOf(SocialRowAdapter.presenceColor(context, friend.presence))

            val labels = mutableListOf<String>()
            labels.add(friend.presenceLabel ?: context.getString(SocialRowAdapter.presenceLabelRes(friend.presence)))
            if (friend.inParty) labels.add(context.getString(R.string.social_tab_play))
            if (friend.blocked) labels.add(context.getString(R.string.social_blocked))
            if (friend.ignored) labels.add(context.getString(R.string.social_ignored))
            binding.friendStatus.text = labels.joinToString(" · ")

            val activity = friend.activity
            if (activity == null) {
                binding.friendActivity.visibility = View.GONE
            } else {
                binding.friendActivity.visibility = View.VISIBLE
                binding.friendActivity.text = listOfNotNull(activity.label, activity.detail).joinToString(" · ")
            }

            binding.root.setOnClickListener { listener.onFriendClicked(friend) }
        }

        fun bindParticipant(participant: VoiceParticipant, listener: Listener) {
            val context = binding.root.context
            binding.friendName.text = participant.name
            binding.friendSource.text = context.getString(R.string.social_tab_voice)
            val speaking = participant.speaking
            binding.presenceDot.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    context,
                    when {
                        speaking -> R.color.turtle_success
                        participant.deafened || participant.muted -> R.color.turtle_warning
                        else -> R.color.accent_cyan
                    },
                )
            )
            binding.friendStatus.text = when {
                speaking -> context.getString(R.string.social_speaking)
                participant.deafened -> context.getString(R.string.social_voice_deafen)
                participant.muted -> context.getString(R.string.social_voice_mute)
                else -> context.getString(R.string.social_voice_joined, context.getString(R.string.social_tab_voice))
            }
            binding.friendActivity.visibility = View.GONE
            binding.root.setOnClickListener { listener.onParticipantClicked(participant) }
        }
    }

    private class RequestHolder(private val binding: ItemSocialRequestBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(request: FriendRequest, listener: Listener) {
            binding.requestName.text = request.displayName
            binding.requestDetail.text = request.detail.orEmpty()
            val incoming = request.direction == com.endiq.turtlelauncher.feature.social.FriendRequestDirection.INCOMING
            binding.requestActions.visibility = if (incoming) View.VISIBLE else View.GONE
            binding.root.setOnClickListener {
                if (!incoming) listener.onRequestReject(request)
            }
            binding.acceptButton.setOnClickListener { listener.onRequestAccept(request) }
            binding.rejectButton.setOnClickListener { listener.onRequestReject(request) }
        }
    }

    private class ThreadHolder(private val binding: ItemSocialConversationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bindConversation(conversation: SocialConversation, listener: Listener) {
            val context = binding.root.context
            binding.conversationTitle.text = when (conversation.kind) {
                ConversationKind.CHANNEL -> conversation.title
                ConversationKind.LOBBY -> context.getString(R.string.social_lobby_chat) + " · " + conversation.title
                ConversationKind.DIRECT -> conversation.title
            }
            binding.conversationSubtitle.text = conversation.lastMessage
                ?: conversation.subtitle
                ?: context.getString(R.string.social_empty_messages)
            bindUnread(conversation.unreadCount)
            binding.root.setOnClickListener { listener.onConversationClicked(conversation) }
        }

        fun bindParty(lobby: PartyLobby, listener: Listener) {
            val context = binding.root.context
            binding.conversationTitle.text = lobby.name
            val members = lobby.members.size
            binding.conversationSubtitle.text = buildString {
                append(context.getString(R.string.social_lobby_members, members))
                if (lobby.inviteCode.isNotBlank()) append(" · ").append(lobby.inviteCode)
                if (!lobby.isOwner && lobby.ownerName.isNotBlank()) append(" · ").append(lobby.ownerName)
            }
            bindUnread(0)
            binding.root.setOnClickListener { listener.onPartyClicked(lobby) }
        }

        fun bindChannel(channel: LinkedChannel?, listener: Listener) {
            val context = binding.root.context
            if (channel == null) {
                binding.conversationTitle.text = context.getString(R.string.social_empty_channels)
                binding.conversationSubtitle.text = context.getString(R.string.social_link_channel)
            } else {
                binding.conversationTitle.text = "#" + channel.channelName
                binding.conversationSubtitle.text = channel.guildName
            }
            bindUnread(0)
            binding.root.setOnClickListener { listener.onChannelClicked(channel) }
        }

        private fun bindUnread(count: Int) {
            if (count <= 0) {
                binding.unreadBadge.visibility = View.GONE
            } else {
                binding.unreadBadge.visibility = View.VISIBLE
                binding.unreadBadge.text = if (count > 99) "99+" else count.toString()
            }
        }
    }

    companion object {
        private const val TYPE_PERSON = 0
        private const val TYPE_REQUEST = 1
        private const val TYPE_THREAD = 2

        fun presenceLabelRes(presence: PresenceStatus): Int = when (presence) {
            PresenceStatus.ONLINE -> R.string.social_presence_online
            PresenceStatus.IDLE -> R.string.social_presence_idle
            PresenceStatus.DO_NOT_DISTURB -> R.string.social_presence_dnd
            PresenceStatus.OFFLINE -> R.string.social_presence_offline
            PresenceStatus.UNKNOWN -> R.string.social_presence_unknown
        }

        fun presenceColor(context: android.content.Context, presence: PresenceStatus): Int =
            ContextCompat.getColor(
                context,
                when (presence) {
                    PresenceStatus.ONLINE -> R.color.turtle_success
                    PresenceStatus.IDLE -> R.color.turtle_warning
                    PresenceStatus.DO_NOT_DISTURB -> R.color.accent_orange
                    PresenceStatus.OFFLINE, PresenceStatus.UNKNOWN -> R.color.turtle_text_disabled
                },
            )

        /** Convenience for the fragment: the member list of a lobby, shown in its details dialog. */
        fun memberNames(members: List<LobbyMember>): String =
            members.joinToString(", ") { if (it.isSelf) it.name + " (you)" else it.name }
    }
}
