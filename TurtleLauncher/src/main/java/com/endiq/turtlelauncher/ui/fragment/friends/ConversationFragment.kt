package com.endiq.turtlelauncher.ui.fragment.friends

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.databinding.FragmentSocialConversationBinding
import com.endiq.turtlelauncher.feature.social.SocialConversation
import com.endiq.turtlelauncher.feature.social.SocialMessage
import com.endiq.turtlelauncher.feature.social.SocialRepository
import com.endiq.turtlelauncher.feature.social.SocialSnapshot
import com.endiq.turtlelauncher.ui.fragment.FragmentWithAnim
import com.endiq.turtlelauncher.ui.subassembly.friends.SocialMessageAdapter
import com.endiq.turtlelauncher.utils.ZHTools

/**
 * One social thread: a Discord DM, the linked Discord channel, a Turtle direct message, or a
 * lobby chat. Pushed from the Friends screen (Messages tab, a friend row, a party, or the
 * Communities tab) with the conversation id already resolved by [SocialRepository].
 *
 * History is loaded from the local store first and then topped up from Discord (see
 * [SocialRepository.loadHistory]); queued-but-unsent messages stay in place. The input row is
 * disabled rather than silently failing when the conversation reports `canSend = false`.
 */
class ConversationFragment : FragmentWithAnim(R.layout.fragment_social_conversation) {

    companion object {
        const val TAG: String = "ConversationFragment"
        const val ARG_CONVERSATION_ID: String = "conversation_id"
        const val ARG_TITLE: String = "conversation_title"
        const val ARG_SUBTITLE: String = "conversation_subtitle"
    }

    private var _binding: FragmentSocialConversationBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: SocialMessageAdapter
    private var conversationId: String? = null
    private var conversation: SocialConversation? = null
    private var lastMessageCount: Int = -1
    private var lastMessageId: String? = null

    private val repositoryListener: (SocialSnapshot) -> Unit = { snapshot -> renderConversation(snapshot) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSocialConversationBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val id = arguments?.getString(ARG_CONVERSATION_ID)
        if (id.isNullOrBlank()) {
            toast(getString(R.string.social_empty_messages))
            ZHTools.onBackPressed(requireActivity())
            return
        }
        conversationId = id
        binding.conversationTitle.text = arguments?.getString(ARG_TITLE).orEmpty()
        binding.conversationSubtitle.text = arguments?.getString(ARG_SUBTITLE).orEmpty()

        adapter = SocialMessageAdapter { message -> confirmDelete(message) }
        val layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        binding.messageList.layoutManager = layoutManager
        binding.messageList.adapter = adapter

        binding.backButton.setOnClickListener { ZHTools.onBackPressed(requireActivity()) }
        binding.sendButton.setOnClickListener { send() }
        binding.messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                send()
                true
            } else {
                false
            }
        }
    }

    override fun onStart() {
        super.onStart()
        SocialRepository.addListener(repositoryListener)
        val id = conversationId ?: return
        SocialRepository.markRead(id)
        // The callback is delivered on the main thread, but the view may be gone by then.
        SocialRepository.loadHistory(id) {
            if (_binding == null) return@loadHistory
            refreshMessages()
        }
        refreshMessages()
    }

    override fun onStop() {
        SocialRepository.removeListener(repositoryListener)
        conversationId?.let { SocialRepository.closeConversation(it) }
        super.onStop()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    // ── Rendering ──────────────────────────────────────────────────────────────────

    private fun renderConversation(snapshot: SocialSnapshot) {
        if (_binding == null) return
        val id = conversationId ?: return
        conversation = snapshot.conversations.firstOrNull { it.id == id }
        val current = conversation
        if (current != null) {
            if (binding.conversationTitle.text.isNullOrBlank()) binding.conversationTitle.text = current.title
            val subtitle = current.subtitle.orEmpty().trim()
            binding.conversationSubtitle.text = subtitle
            binding.conversationSubtitle.visibility = if (subtitle.isEmpty()) View.GONE else View.VISIBLE
            binding.sendButton.isEnabled = current.canSend
            binding.messageInput.isEnabled = current.canSend
            binding.messageInput.hint = if (current.canSend) {
                getString(R.string.social_message_hint)
            } else {
                getString(R.string.social_conversation_read_only, current.subtitle.orEmpty())
            }
        }
        renderStatus()
        refreshMessages()
    }

    private fun renderStatus() {
        // Discord connection problems are handled in the background (see SocialRepository's
        // retry loop). Printing that global status here used to drop a red paragraph into the
        // chat every time the session hiccuped - a conversation only ever shows its own state.
        val queued = SocialRepository.messages(conversationId.orEmpty()).any { it.pending }
        val text = when {
            conversation?.canSend == false -> getString(
                R.string.social_conversation_read_only,
                conversation?.subtitle.orEmpty(),
            )
            queued -> getString(R.string.social_messages_queued)
            else -> null
        }
        binding.conversationStatus.text = text.orEmpty()
        binding.conversationStatus.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    private fun refreshMessages() {
        val id = conversationId ?: return
        val messages = SocialRepository.messages(id)
        adapter.submit(messages)
        // Track the newest id, not the count: a queued message that Discord accepts is replaced
        // in place, so the count stays the same and the view would otherwise not follow it.
        val newest = messages.lastOrNull()?.id
        if (newest != lastMessageId) {
            lastMessageId = newest
            lastMessageCount = messages.size
            if (messages.isEmpty()) return
            // Post it: the list has not measured the new row yet, so an immediate scroll lands
            // short and the last bubble ends up half-hidden behind the composer.
            binding.messageList.post {
                if (_binding != null) binding.messageList.scrollToPosition(messages.size - 1)
            }
        }
    }

    // ── Sending and deleting ───────────────────────────────────────────────────────

    private fun send() {
        val id = conversationId ?: return
        if (conversation?.canSend == false) {
            toast(getString(R.string.social_conversation_read_only, conversation?.subtitle.orEmpty()))
            return
        }
        val text = binding.messageInput.text?.toString().orEmpty()
        if (text.isBlank()) return
        binding.messageInput.setText("")
        val root = binding.root
        SocialRepository.sendMessage(id, text) { message ->
            root.post {
                if (_binding == null) return@post
                refreshMessages()
                if (message.failed) {
                    toast(
                        getString(
                            R.string.social_message_failed,
                            message.failureReason ?: getString(R.string.social_send_failed),
                        )
                    )
                }
            }
        }
        hideKeyboard()
    }

    private fun confirmDelete(message: SocialMessage) {
        val id = conversationId ?: return
        AlertDialog.Builder(requireContext(), R.style.CustomAlertDialogTheme)
            .setTitle(R.string.social_delete_message)
            .setMessage(R.string.social_confirm_delete)
            .setPositiveButton(R.string.social_delete) { _, _ ->
                SocialRepository.deleteMessage(id, message.id)
                binding.root.postDelayed({ if (_binding != null) refreshMessages() }, 250L)
            }
            .setNegativeButton(R.string.social_action_close, null)
            .show()
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────

    private fun hideKeyboard() {
        runCatching {
            val manager = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            manager.hideSoftInputFromWindow(binding.messageInput.windowToken, 0)
        }
    }

    private fun toast(message: String) {
        if (!isAdded || message.isBlank()) return
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
