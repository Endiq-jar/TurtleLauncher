package com.endiq.turtlelauncher.ui.fragment.friends

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.databinding.FragmentFriendsBinding
import com.endiq.turtlelauncher.feature.social.DiscordOAuthClient
import com.endiq.turtlelauncher.feature.social.FriendRequest
import com.endiq.turtlelauncher.feature.social.LinkedChannel
import com.endiq.turtlelauncher.feature.social.LobbyManager
import com.endiq.turtlelauncher.feature.social.PartyLobby
import com.endiq.turtlelauncher.feature.social.PresenceStatus
import com.endiq.turtlelauncher.feature.social.SocialConversation
import com.endiq.turtlelauncher.feature.social.SocialFriend
import com.endiq.turtlelauncher.feature.social.SocialRepository
import com.endiq.turtlelauncher.feature.social.SocialSnapshot
import com.endiq.turtlelauncher.feature.social.VoiceConnectionState
import com.endiq.turtlelauncher.feature.social.VoiceManager
import com.endiq.turtlelauncher.feature.social.VoiceParticipant
import com.endiq.turtlelauncher.feature.social.VoiceState
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.ui.dialog.EditTextDialog
import com.endiq.turtlelauncher.ui.fragment.FragmentWithAnim
import com.endiq.turtlelauncher.ui.fragment.TerracottaFragment
import com.endiq.turtlelauncher.ui.subassembly.friends.SocialRow
import com.endiq.turtlelauncher.ui.subassembly.friends.SocialRowAdapter
import com.endiq.turtlelauncher.utils.ZHTools
import java.text.DateFormat
import java.util.Date

/**
 * The Friends screen, opened from the Friends button in the home screen's quick actions rail
 * (next to Share Logs), and from social notifications / the Discord OAuth redirect.
 *
 * Seven tabs over one snapshot from [SocialRepository]:
 *  - Friends      unified Discord + Turtle list with online/idle/offline + activity
 *  - Requests     incoming/outgoing, accept/reject
 *  - Messages     DMs, lobby chats and the linked community channel, with unread counts
 *  - Voice        party voice session: participants, mute, deafen, push-to-talk
 *  - Play Together parties: create/join, invite friends, share the invite, open Friends/LAN
 *  - Activity     who is online and what they are playing
 *  - Communities  the linked Discord text channel
 *
 * Every action goes through the repository (background thread); this class only renders and
 * asks questions. Account linking/unlinking lives behind the Account chip in the header.
 */
class FriendsFragment : FragmentWithAnim(R.layout.fragment_friends) {
    companion object {
        const val TAG: String = "FriendsFragment"
    }

    private enum class Tab(val labelRes: Int, val emptyRes: Int) {
        FRIENDS(R.string.social_tab_friends, R.string.social_empty_friends),
        REQUESTS(R.string.social_tab_requests, R.string.social_empty_requests),
        MESSAGES(R.string.social_tab_messages, R.string.social_empty_messages),
        VOICE(R.string.social_tab_voice, R.string.social_empty_voice),
        PLAY(R.string.social_tab_play, R.string.social_empty_play),
        ACTIVITY(R.string.social_tab_activity, R.string.social_empty_activity),
        CHANNELS(R.string.social_tab_channels, R.string.social_empty_channels),
    }

    private var _binding: FragmentFriendsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: SocialRowAdapter
    private var currentTab: Tab = Tab.FRIENDS
    private var latest: SocialSnapshot = SocialSnapshot()

    private val repositoryListener: (SocialSnapshot) -> Unit = { snapshot -> render(snapshot) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentFriendsBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = SocialRowAdapter(object : SocialRowAdapter.Listener {
            override fun onFriendClicked(friend: SocialFriend) = showFriendActions(friend)
            override fun onRequestAccept(request: FriendRequest) {
                SocialRepository.acceptRequest(request)
            }
            override fun onRequestReject(request: FriendRequest) {
                SocialRepository.rejectRequest(request)
            }
            override fun onConversationClicked(conversation: SocialConversation) =
                openConversation(conversation.id, conversation.title, conversation.subtitle.orEmpty())
            override fun onPartyClicked(lobby: PartyLobby) = showPartyActions(lobby)
            override fun onChannelClicked(channel: LinkedChannel?) {
                if (channel == null) {
                    promptLinkChannel()
                } else {
                    SocialRepository.openChannelConversation { conversation ->
                        conversation?.let { openConversation(it.id, it.title, it.subtitle.orEmpty()) }
                    }
                }
            }
            override fun onParticipantClicked(participant: VoiceParticipant) = showVolumeDialog(participant)
        })

        binding.friendsList.layoutManager = LinearLayoutManager(requireContext())
        binding.friendsList.adapter = adapter
        binding.backButton.setOnClickListener { ZHTools.onBackPressed(requireActivity()) }
        binding.accountButton.setOnClickListener { showAccountDialog() }

        setupTabs()
        setupActions()
        selectTab(Tab.FRIENDS)
    }

    override fun onStart() {
        super.onStart()
        SocialRepository.addListener(repositoryListener)
        SocialRepository.refresh()
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the Discord browser redirect, or from the Friends/LAN screen.
        SocialRepository.refresh()
    }

    override fun onStop() {
        SocialRepository.removeListener(repositoryListener)
        super.onStop()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    // ── Wiring ─────────────────────────────────────────────────────────────────────

    private fun setupTabs() {
        binding.friendsTab.setOnClickListener { selectTab(Tab.FRIENDS) }
        binding.requestsTab.setOnClickListener { selectTab(Tab.REQUESTS) }
        binding.messagesTab.setOnClickListener { selectTab(Tab.MESSAGES) }
        binding.voiceTab.setOnClickListener { selectTab(Tab.VOICE) }
        binding.playTab.setOnClickListener { selectTab(Tab.PLAY) }
        binding.activityTab.setOnClickListener { selectTab(Tab.ACTIVITY) }
        binding.channelsTab.setOnClickListener { selectTab(Tab.CHANNELS) }
    }

    private fun setupActions() {
        binding.addFriendButton.setOnClickListener {
            val name = binding.addFriendInput.text?.toString().orEmpty()
            if (name.isBlank()) {
                toast(getString(R.string.social_add_friend_hint))
                return@setOnClickListener
            }
            SocialRepository.addGameFriend(name)
            binding.addFriendInput.setText("")
        }
        binding.refreshButton.setOnClickListener { SocialRepository.refresh() }

        binding.createPartyButton.setOnClickListener {
            EditTextDialog.Builder(requireContext())
                .setTitle(R.string.social_create_party)
                .setHintText(R.string.social_party_name_hint)
                .setAsRequired()
                .setConfirmListener { editText, _ ->
                    SocialRepository.createLobby(editText.text.toString())
                    true
                }
                .showDialog()
        }
        binding.joinPartyButton.setOnClickListener {
            val code = binding.partyCodeInput.text?.toString().orEmpty().trim()
            if (code.isBlank()) {
                toast(getString(R.string.social_party_code_hint))
                return@setOnClickListener
            }
            SocialRepository.joinLobby(code)
            binding.partyCodeInput.setText("")
            selectTab(Tab.PLAY)
        }

        binding.voiceJoinButton.setOnClickListener {
            val lobby = latest.lobbies.firstOrNull()
            if (lobby == null) {
                toast(getString(R.string.social_empty_play))
                return@setOnClickListener
            }
            VoiceManager.join(lobby.id, lobby.name, lobby.members.map { it.name })
        }
        binding.leaveVoiceButton.setOnClickListener { VoiceManager.leave() }
        binding.muteButton.setOnClickListener { VoiceManager.setMuted(!latest.voice.muted) }
        binding.deafenButton.setOnClickListener { VoiceManager.setDeafened(!latest.voice.deafened) }
        binding.pttButton.setOnClickListener { VoiceManager.setPushToTalk(!latest.voice.pushToTalkEnabled) }
        binding.noiseButton.setOnClickListener {
            VoiceManager.setNoiseSuppression(!latest.voice.noiseSuppression)
            if (!latest.voice.capabilities.noiseSuppression) {
                toast(getString(R.string.social_voice_no_audio))
            }
        }

        binding.linkChannelButton.setOnClickListener { promptLinkChannel() }
        binding.unlinkChannelButton.setOnClickListener { SocialRepository.unlinkChannel() }
    }

    private fun selectTab(tab: Tab) {
        currentTab = tab
        if (_binding == null) return
        val context = context ?: return
        val chips = mapOf(
            Tab.FRIENDS to binding.friendsTab,
            Tab.REQUESTS to binding.requestsTab,
            Tab.MESSAGES to binding.messagesTab,
            Tab.VOICE to binding.voiceTab,
            Tab.PLAY to binding.playTab,
            Tab.ACTIVITY to binding.activityTab,
            Tab.CHANNELS to binding.channelsTab,
        )
        for ((chipTab, chip) in chips) {
            val selected = chipTab == tab
            chip.setBackgroundResource(
                if (selected) R.drawable.background_pill_filled else R.drawable.background_pill_outline
            )
            chip.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (selected) R.color.turtle_text_on_accent else R.color.turtle_text_secondary,
                )
            )
        }

        binding.addFriendRow.visibility = if (tab == Tab.FRIENDS || tab == Tab.REQUESTS) View.VISIBLE else View.GONE
        binding.playActions.visibility = if (tab == Tab.PLAY) View.VISIBLE else View.GONE
        binding.voiceActions.visibility = if (tab == Tab.VOICE) View.VISIBLE else View.GONE
        binding.channelActions.visibility = if (tab == Tab.CHANNELS) View.VISIBLE else View.GONE

        render(latest)
    }

    // ── Rendering ──────────────────────────────────────────────────────────────────

    private fun render(snapshot: SocialSnapshot) {
        if (_binding == null || !isAdded) return
        latest = snapshot

        binding.accountButton.text = snapshot.identity.effectiveName.ifBlank {
            getString(R.string.social_account_button)
        }
        binding.messagesTab.text = if (snapshot.unreadTotal > 0) {
            getString(R.string.social_tab_messages) + " (" + snapshot.unreadTotal + ")"
        } else {
            getString(R.string.social_tab_messages)
        }

        val rows = rowsFor(snapshot)
        adapter.submit(rows)
        renderStatus(snapshot, rows.isEmpty())
        updateVoiceButtons(snapshot.voice)
        binding.unlinkChannelButton.isEnabled = snapshot.linkedChannel != null
    }

    private fun rowsFor(snapshot: SocialSnapshot): List<SocialRow> = when (currentTab) {
        Tab.FRIENDS -> snapshot.friends.map { SocialRow.Friend(it) }
        Tab.REQUESTS -> snapshot.requests.map { SocialRow.Request(it) }
        Tab.MESSAGES -> snapshot.conversations.map { SocialRow.Conversation(it) }
        Tab.VOICE -> snapshot.voice.participants.map { SocialRow.Participant(it) }
        Tab.PLAY -> snapshot.lobbies.map { SocialRow.Party(it) }
        Tab.ACTIVITY -> snapshot.friends
            .filter { it.activity != null || it.presence == PresenceStatus.ONLINE || it.presence == PresenceStatus.IDLE }
            .map { SocialRow.Friend(it) }
        Tab.CHANNELS -> listOf(SocialRow.Channel(snapshot.linkedChannel, snapshot.linkedChannel != null))
    }

    private fun renderStatus(snapshot: SocialSnapshot, empty: Boolean) {
        val explicit = snapshot.statusMessage
        val summary = when (currentTab) {
            Tab.FRIENDS -> getString(
                R.string.social_summary_friends,
                snapshot.friends.count { !it.isMuted },
                snapshot.unreadTotal,
            )
            Tab.VOICE -> voiceStatus(snapshot.voice)
            else -> null
        }
        val text = when {
            explicit != null -> explicit
            empty -> getString(currentTab.emptyRes)
            else -> summary.orEmpty()
        }
        binding.statusText.text = text
        binding.statusText.visibility = if (text.isBlank()) View.GONE else View.VISIBLE
    }

    private fun voiceStatus(voice: VoiceState): String = when (voice.connection) {
        VoiceConnectionState.DISCONNECTED -> getString(R.string.social_voice_status_idle)
        VoiceConnectionState.CONNECTING -> getString(R.string.social_loading)
        VoiceConnectionState.CONNECTED -> buildString {
            append(getString(R.string.social_voice_joined, voice.channelName.orEmpty()))
            append(" · ")
            append(getString(R.string.social_voice_members, voice.participants.size))
            if (!voice.capabilities.sdkVoiceAvailable) {
                append("\n")
                append(getString(R.string.social_voice_no_audio))
            }
        }
        VoiceConnectionState.RECONNECTING -> getString(R.string.social_loading)
        VoiceConnectionState.FAILED -> getString(R.string.social_voice_failed)
    }

    private fun updateVoiceButtons(voice: VoiceState) {
        binding.muteButton.setText(if (voice.muted) R.string.social_voice_unmute else R.string.social_voice_mute)
        binding.deafenButton.setText(if (voice.deafened) R.string.social_voice_undeafen else R.string.social_voice_deafen)
        binding.voiceJoinButton.isEnabled = voice.connection != VoiceConnectionState.CONNECTED
        binding.leaveVoiceButton.isEnabled = voice.connection == VoiceConnectionState.CONNECTED
        binding.noiseButton.alpha = if (voice.capabilities.noiseSuppression) 1f else 0.6f
        binding.pttButton.alpha = if (voice.pushToTalkEnabled) 1f else 0.7f
    }

    // ── Conversations ──────────────────────────────────────────────────────────────

    private fun openConversation(conversationId: String, title: String, subtitle: String) {
        // Repository callbacks and dialog actions can land after this fragment was detached.
        if (!isAdded || _binding == null) return
        val args = Bundle().apply {
            putString(ConversationFragment.ARG_CONVERSATION_ID, conversationId)
            putString(ConversationFragment.ARG_TITLE, title)
            putString(ConversationFragment.ARG_SUBTITLE, subtitle)
        }
        ZHTools.swapFragmentWithAnim(this, ConversationFragment::class.java, ConversationFragment.TAG, args)
    }

    // ── Friend actions ─────────────────────────────────────────────────────────────

    private fun showFriendActions(friend: SocialFriend) {
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        actions.add(getString(R.string.social_action_message) to {
            SocialRepository.openConversationForFriend(friend.key) { conversation ->
                conversation?.let { openConversation(it.id, it.title, it.subtitle.orEmpty()) }
            }
        })
        actions.add(getString(R.string.social_action_invite_session) to {
            SocialRepository.inviteToSession(friend.key, null)
        })
        latest.lobbies.firstOrNull()?.let { lobby ->
            actions.add(getString(R.string.social_action_invite_party) to {
                SocialRepository.inviteToLobby(lobby, friend.key)
            })
        }
        actions.add(getString(R.string.social_action_info) to { showFriendInfo(friend) })
        actions.add(
            getString(if (friend.blocked) R.string.social_action_unblock else R.string.social_action_block) to {
                SocialRepository.setBlocked(friend.key, !friend.blocked)
            }
        )
        actions.add(
            getString(if (friend.ignored) R.string.social_action_unignore else R.string.social_action_ignore) to {
                SocialRepository.setIgnored(friend.key, !friend.ignored)
            }
        )
        actions.add(getString(R.string.social_action_remove) to { confirmRemove(friend) })
        showChoiceList(getString(R.string.social_friend_actions, friend.displayName), actions)
    }

    private fun showFriendInfo(friend: SocialFriend) {
        val source = getString(
            if (friend.source == com.endiq.turtlelauncher.feature.social.SocialSource.DISCORD) {
                R.string.social_source_discord
            } else {
                R.string.social_source_turtle
            }
        )
        val lastSeen = if (friend.lastSeenMs <= 0L) {
            getString(R.string.social_never_seen)
        } else {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(friend.lastSeenMs))
        }
        val status = friend.presenceLabel ?: getString(SocialRowAdapter.presenceLabelRes(friend.presence))
        AlertDialog.Builder(requireContext(), R.style.CustomAlertDialogTheme)
            .setTitle(friend.displayName)
            .setMessage(getString(R.string.social_friend_info, friend.subtitle.orEmpty(), status, source, lastSeen))
            .setPositiveButton(R.string.social_action_close, null)
            .show()
    }

    private fun confirmRemove(friend: SocialFriend) {
        AlertDialog.Builder(requireContext(), R.style.CustomAlertDialogTheme)
            .setTitle(R.string.social_action_remove)
            .setMessage(getString(R.string.social_confirm_remove, friend.displayName))
            .setPositiveButton(R.string.social_action_remove) { _, _ -> SocialRepository.removeFriend(friend.key) }
            .setNegativeButton(R.string.social_action_close, null)
            .show()
    }

    private fun showVolumeDialog(participant: VoiceParticipant) {
        val volumes = arrayOf("0%", "25%", "50%", "75%", "100%", "125%", "150%")
        val values = intArrayOf(0, 25, 50, 75, 100, 125, 150)
        AlertDialog.Builder(requireContext(), R.style.CustomAlertDialogTheme)
            .setTitle(participant.name)
            .setItems(volumes) { _, which -> VoiceManager.setParticipantVolume(participant.name, values[which]) }
            .setNegativeButton(R.string.social_action_close, null)
            .show()
    }

    // ── Parties ────────────────────────────────────────────────────────────────────

    private fun showPartyActions(lobby: PartyLobby) {
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        actions.add(getString(R.string.social_lobby_chat) to {
            SocialRepository.openLobbyConversation(lobby.id) { conversation ->
                conversation?.let { openConversation(it.id, it.title, it.subtitle.orEmpty()) }
            }
        })
        actions.add(getString(R.string.social_lobby_invite) to { chooseFriendToInvite(lobby) })
        actions.add(getString(R.string.social_lobby_share) to { shareInvite(lobby) })
        if (lobby.inviteCode.isNotBlank()) {
            actions.add(getString(R.string.social_lobby_join_session) to { openFriendsLan(lobby.inviteCode) })
        }
        actions.add(getString(R.string.social_lobby_leave) to { SocialRepository.leaveLobby(lobby.id) })
        showChoiceList(lobby.name, actions)
    }

    private fun chooseFriendToInvite(lobby: PartyLobby) {
        val friends = latest.friends.filterNot { it.isMuted }
        if (friends.isEmpty()) {
            toast(getString(R.string.social_empty_friends))
            return
        }
        AlertDialog.Builder(requireContext(), R.style.CustomAlertDialogTheme)
            .setTitle(R.string.social_choose_friend)
            .setItems(friends.map { it.displayName }.toTypedArray()) { _, which ->
                SocialRepository.inviteToLobby(lobby, friends[which].key)
            }
            .setNegativeButton(R.string.social_action_close, null)
            .show()
    }

    private fun shareInvite(lobby: PartyLobby) {
        val text = LobbyManager.inviteText(lobby)
        runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.social_share_invite_via)))
        }.onFailure { error -> toast(error.message ?: getString(R.string.generic_error)) }
    }

    private fun openFriendsLan(code: String) {
        val args = Bundle().apply { putString(TerracottaFragment.BUNDLE_JOIN_CODE, code) }
        ZHTools.swapFragmentWithAnim(this, TerracottaFragment::class.java, TerracottaFragment.TAG, args)
    }

    // ── Communities ────────────────────────────────────────────────────────────────

    private fun promptLinkChannel() {
        SocialRepository.listChannelChoices { result ->
            if (!isAdded || _binding == null) return@listChannelChoices
            result.onSuccess { guilds ->
                val entries = guilds.flatMap { guild ->
                    guild.channels.map { channel -> guild.guildName + " / #" + channel.name to channel.id }
                }
                if (entries.isEmpty()) {
                    toast(getString(R.string.social_channel_load_failed))
                    return@onSuccess
                }
                AlertDialog.Builder(requireContext(), R.style.CustomAlertDialogTheme)
                    .setTitle(R.string.social_choose_channel)
                    .setItems(entries.map { it.first }.toTypedArray()) { _, which ->
                        SocialRepository.linkChannel(entries[which].second) { linkResult ->
                            linkResult.onFailure { error ->
                                toast(error.message ?: getString(R.string.social_channel_load_failed))
                            }
                        }
                    }
                    .setNegativeButton(R.string.social_action_close, null)
                    .show()
            }.onFailure { error ->
                toast(error.message ?: getString(R.string.social_channel_load_failed))
            }
        }
    }

    // ── Account ────────────────────────────────────────────────────────────────────

    private fun showAccountDialog() {
        val snapshot = latest
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (!snapshot.discordLinked) {
            actions.add(getString(R.string.social_link_discord) to { startDiscordLink() })
        } else {
            actions.add(getString(R.string.social_unlink_discord) to { SocialRepository.unlinkDiscord() })
        }
        actions.add(getString(R.string.social_rename_identity) to {
            EditTextDialog.Builder(requireContext())
                .setTitle(R.string.social_rename_identity)
                .setEditText(snapshot.identity.displayName)
                .setConfirmListener { editText, _ ->
                    SocialRepository.renameIdentity(editText.text.toString())
                    true
                }
                .showDialog()
        })
        val notificationsOn = AllSettings.socialNotificationsEnabled.getValue()
        actions.add(
            getString(R.string.social_notifications_toggle) + ": " +
                getString(if (notificationsOn) R.string.discord_rpc_on else R.string.discord_rpc_off) to {
                AllSettings.socialNotificationsEnabled.put(!notificationsOn).save()
                toast(getString(R.string.social_setting_saved))
            }
        )
        val previewOn = AllSettings.socialNotificationPreview.getValue()
        actions.add(
            getString(R.string.social_preview_toggle) + ": " +
                getString(if (previewOn) R.string.discord_rpc_on else R.string.discord_rpc_off) to {
                AllSettings.socialNotificationPreview.put(!previewOn).save()
                toast(getString(R.string.social_setting_saved))
            }
        )
        actions.add(getString(R.string.social_redirect_uri_title) to { editRedirectUri() })
        actions.add(getString(R.string.social_scopes_edit_title) to { editScopes() })
        actions.add(getString(R.string.social_scope_title) to { showScopeDialog() })
        actions.add(getString(R.string.social_sdk_help_title) to { showSdkHelp() })
        showChoiceList(getString(R.string.social_identity_title), actions)
    }

    private fun editRedirectUri() {
        val current = AllSettings.discordOAuthRedirectUri.getValue()
            .ifBlank { DiscordOAuthClient.redirectUri() }
        EditTextDialog.Builder(requireContext())
            .setTitle(R.string.social_redirect_uri_title)
            .setMessage(R.string.social_redirect_hint)
            .setEditText(current)
            .setConfirmListener { editText, _ ->
                AllSettings.discordOAuthRedirectUri.put(editText.text.toString().trim()).save()
                toast(getString(R.string.social_setting_saved))
                true
            }
            .showDialog()
    }

    private fun editScopes() {
        val current = AllSettings.discordOAuthScopes.getValue().ifBlank { DiscordOAuthClient.scopes() }
        EditTextDialog.Builder(requireContext())
            .setTitle(R.string.social_scopes_edit_title)
            .setMessage(R.string.social_scopes_hint)
            .setEditText(current)
            .setConfirmListener { editText, _ ->
                AllSettings.discordOAuthScopes.put(editText.text.toString().trim()).save()
                toast(getString(R.string.social_setting_saved))
                true
            }
            .showDialog()
    }

    private fun startDiscordLink() {
        DiscordOAuthClient.beginAuth(requireContext())
            .onSuccess { toast(getString(R.string.social_link_opened)) }
            .onFailure { error ->
                toast(getString(R.string.social_link_failed, error.message ?: ""))
            }
    }

    private fun showScopeDialog() {
        val redirect = DiscordOAuthClient.redirectUri()
        val scopes = DiscordOAuthClient.scopes()
        AlertDialog.Builder(requireContext(), R.style.CustomAlertDialogTheme)
            .setTitle(R.string.social_scope_title)
            .setMessage(getString(R.string.social_scope_body, redirect, scopes))
            .setPositiveButton(R.string.social_copy_redirect) { _, _ ->
                copyToClipboard(redirect)
            }
            .setNegativeButton(R.string.social_action_close, null)
            .show()
    }

    private fun showSdkHelp() {
        AlertDialog.Builder(requireContext(), R.style.CustomAlertDialogTheme)
            .setTitle(R.string.social_sdk_help_title)
            .setMessage(R.string.social_sdk_help_body)
            .setPositiveButton(R.string.social_action_close, null)
            .show()
    }

    private fun copyToClipboard(text: String) {
        runCatching {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Turtle Launcher", text))
            toast(getString(R.string.social_copied))
        }.onFailure { error -> toast(error.message ?: "") }
    }

    // ── Small helpers ──────────────────────────────────────────────────────────────

    private fun showChoiceList(title: String, actions: List<Pair<String, () -> Unit>>) {
        if (actions.isEmpty() || !isAdded) return
        AlertDialog.Builder(requireContext(), R.style.CustomAlertDialogTheme)
            .setTitle(title)
            .setItems(actions.map { it.first }.toTypedArray()) { dialog, which ->
                dialog.dismiss()
                actions[which].second()
            }
            .setNegativeButton(R.string.social_action_close, null)
            .show()
    }

    private fun toast(message: String) {
        if (!isAdded || message.isBlank()) return
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
