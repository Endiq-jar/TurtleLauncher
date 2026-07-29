package com.movtery.zalithlauncher.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.movtery.anim.AnimPlayer
import com.movtery.anim.animations.Animations
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.databinding.FragmentAiChatBinding
import com.movtery.zalithlauncher.ui.subassembly.aichat.AiChatAdvisor
import com.movtery.zalithlauncher.ui.subassembly.aichat.ChatMessage
import com.movtery.zalithlauncher.ui.subassembly.aichat.ChatMessageAdapter
import com.movtery.zalithlauncher.ui.subassembly.aichat.GithubChatSync
import com.movtery.zalithlauncher.utils.ZHTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quick Actions "AI Chat" screen: a simple chat UI backed by [AiChatAdvisor] (Gemini).
 * Conversation history lives only in memory for this fragment's lifetime - nothing is
 * persisted to disk, so it resets each time the screen is reopened.
 */
class AiChatFragment : FragmentWithAnim(R.layout.fragment_ai_chat) {
    companion object {
        const val TAG: String = "AiChatFragment"
    }

    private lateinit var binding: FragmentAiChatBinding
    private val adapter = ChatMessageAdapter()
    private val history = mutableListOf<ChatMessage>()
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAiChatBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.backButton.setOnClickListener { ZHTools.onBackPressed(requireActivity()) }
        binding.clearChatButton.setOnClickListener {
            history.clear()
            adapter.clear()
        }

        binding.messageList.layoutManager = LinearLayoutManager(requireContext())
        binding.messageList.adapter = adapter

        binding.noApiKeyNotice.visibility = if (AiChatAdvisor.hasApiKey()) View.GONE else View.VISIBLE

        binding.sendButton.setOnClickListener { sendCurrentMessage() }
    }

    private fun sendCurrentMessage() {
        val text = binding.messageInput.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        if (!AiChatAdvisor.hasApiKey()) {
            Toast.makeText(requireContext(), R.string.ai_chat_no_api_key, Toast.LENGTH_SHORT).show()
            return
        }

        val userMessage = ChatMessage(text, isUser = true)
        history.add(userMessage)
        adapter.addMessage(userMessage)
        binding.messageInput.text?.clear()
        binding.messageList.scrollToPosition(adapter.itemCount - 1)

        binding.thinkingIndicator.visibility = View.VISIBLE
        binding.sendButton.isEnabled = false

        scope.launch {
            val reply = withContext(Dispatchers.IO) { AiChatAdvisor.sendMessage(history) }
            binding.thinkingIndicator.visibility = View.GONE
            binding.sendButton.isEnabled = true

            val replyMessage = ChatMessage(
                text = reply ?: getString(R.string.ai_chat_request_failed),
                isUser = false
            )
            if (reply != null) {
                history.add(replyMessage)
                // Fire-and-forget save - never blocks or interrupts the chat itself.
                scope.launch(Dispatchers.IO) {
                    GithubChatSync.saveConversation(requireContext().applicationContext, history)
                }
            }
            adapter.addMessage(replyMessage)
            binding.messageList.scrollToPosition(adapter.itemCount - 1)
        }
    }

    override fun slideIn(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.root, Animations.BounceInRight))
    }

    override fun slideOut(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.root, Animations.FadeOutLeft))
    }
}
