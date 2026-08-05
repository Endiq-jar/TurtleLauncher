package com.movtery.zalithlauncher.ui.fragment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.movtery.anim.AnimPlayer
import com.movtery.anim.animations.Animations
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.databinding.FragmentTerracottaBinding
import com.movtery.zalithlauncher.feature.accounts.AccountsManager
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.feature.terracotta.Terracotta
import com.movtery.zalithlauncher.feature.terracotta.TerracottaNodeList
import com.movtery.zalithlauncher.feature.terracotta.TerracottaState
import com.movtery.zalithlauncher.feature.terracotta.chat.TerracottaChat
import com.movtery.zalithlauncher.task.TaskExecutors
import com.movtery.zalithlauncher.ui.subassembly.aichat.ChatMessage
import com.movtery.zalithlauncher.ui.subassembly.aichat.ChatMessageAdapter
import com.movtery.zalithlauncher.utils.ZHTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.burningtnt.terracotta.TerracottaAndroidAPI

/**
 * Friends/LAN screen: host-a-room / join-by-code UI on top of [Terracotta], plus the
 * P2P chat ([TerracottaChat]) that piggybacks on that connection once it's live. The
 * Terracotta foundation (native bindings, orchestration, VPN service) predates this file -
 * this is the UI and chat layer on top of it.
 */
class TerracottaFragment : FragmentWithAnim(R.layout.fragment_terracotta) {
    companion object {
        const val TAG = "TerracottaFragment"
    }

    private enum class Group { WAITING, LOADING, CONNECTED, EXCEPTION }

    private lateinit var binding: FragmentTerracottaBinding
    private lateinit var chatAdapter: ChatMessageAdapter
    private val scope = CoroutineScope(Dispatchers.Main)

    private var currentGroup: Group? = null
    private var connectedValue: String = ""
    private var connectedIsHost: Boolean = false

    private val terracottaStateListener = Terracotta.StateListener { state ->
        // Already invoked on the UI thread - see Terracotta.java's poll daemon.
        if (isAdded && view != null) renderState(state)
    }

    private val chatListener = TerracottaChat.Listener { message ->
        TaskExecutors.runInUIThread {
            if (isAdded && view != null) {
                chatAdapter.addMessage(message)
                binding.chatMessageList.scrollToPosition(chatAdapter.itemCount - 1)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentTerracottaBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        chatAdapter = ChatMessageAdapter()
        binding.chatMessageList.layoutManager = LinearLayoutManager(requireContext())
        binding.chatMessageList.adapter = chatAdapter

        // Wired first and unconditionally: whatever else on this screen breaks, the user
        // must always be able to get back out without the app appearing to hang or crash.
        binding.backButton.setOnClickListener { ZHTools.onBackPressed(requireActivity()) }

        // Lazy init - only spin up Terracotta (and prompt for VPN permission) once someone
        // actually opens this screen, not at app startup. Catches Throwable, not just
        // Exception: a native-library load failure surfaces as UnsatisfiedLinkError/
        // ExceptionInInitializerError, which are Errors, not Exceptions - letting either
        // escape uncaught here previously took the whole app down instead of just this
        // screen, since nothing after the throwing line (including every other click
        // listener below) ever got the chance to run.
        val terracottaAvailable = try {
            Terracotta.initialize(requireActivity())
            TerracottaChat.attach()
            true
        } catch (t: Throwable) {
            Logging.e(TAG, "Terracotta failed to initialize - Friends/LAN unavailable on this device", t)
            false
        }

        if (!terracottaAvailable) {
            switchGroup(Group.EXCEPTION)
            binding.exceptionText.setText(R.string.terracotta_unavailable)
            binding.exceptionExportLogs.visibility = View.GONE
            return
        }

        binding.hostButton.setOnClickListener { onHostClicked() }
        binding.joinButton.setOnClickListener { toggleJoinCodeRow() }
        binding.joinCodeSubmit.setOnClickListener { onJoinSubmit() }
        binding.joinCodeInput.doAfterTextChanged { validateJoinCode() }
        binding.joinCodeInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) { onJoinSubmit(); true } else false
        }

        binding.loadingCancel.setOnClickListener { Terracotta.setWaiting(requireContext(), true) }
        binding.connectedLeave.setOnClickListener { Terracotta.setWaiting(requireContext(), true) }
        binding.connectedInfoCopy.setOnClickListener { copyConnectedValue() }
        binding.exceptionBack.setOnClickListener { Terracotta.setWaiting(requireContext(), true) }
        binding.exceptionExportLogs.setOnClickListener { exportLogs() }

        binding.chatSendButton.setOnClickListener { sendChat() }

        renderState(Terracotta.getState())
    }

    override fun onStart() {
        super.onStart()
        if (!this::chatAdapter.isInitialized) return // onViewCreated bailed out early - see above
        Terracotta.addStateListener(terracottaStateListener)
        TerracottaChat.addListener(chatListener)
        // Pick up anything that changed while this screen wasn't visible.
        renderState(Terracotta.getState())
    }

    override fun onStop() {
        super.onStop()
        Terracotta.removeStateListener(terracottaStateListener)
        TerracottaChat.removeListener(chatListener)
    }

    // FragmentWithAnim (SlideAnimation) leaves these two to every subclass - matches the
    // BounceInRight/FadeOutLeft pattern used by the other Quick-Actions screens
    // (AiChatFragment, ShareLogsFragment) that are also full-root ConstraintLayout fragments.
    override fun slideIn(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.root, Animations.BounceInRight))
    }

    override fun slideOut(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.root, Animations.FadeOutLeft))
    }

    // ============================== State rendering ==============================

    private fun renderState(state: TerracottaState.Ready?) {
        when (state) {
            null, is TerracottaState.Waiting -> showWaiting()
            is TerracottaState.HostScanning -> showLoading(getString(R.string.terracotta_status_host_scanning))
            is TerracottaState.HostStarting -> showLoading(getString(R.string.terracotta_status_host_starting))
            is TerracottaState.GuestConnecting -> showLoading(getString(R.string.terracotta_status_guest_connecting))
            is TerracottaState.GuestStarting -> showLoading(getString(R.string.terracotta_status_guest_starting))
            is TerracottaState.HostOK -> showConnected(
                title = getString(R.string.terracotta_host_ok_title),
                value = state.code,
                players = state.profiles,
                isHost = true
            )
            is TerracottaState.GuestOK -> showConnected(
                title = getString(R.string.terracotta_guest_ok_title),
                value = state.url ?: "",
                players = state.profiles,
                isHost = false
            )
            is TerracottaState.ExceptionState -> showException(state.type)
            else -> showWaiting()
        }
    }

    private fun switchGroup(group: Group) {
        if (currentGroup == group) return
        currentGroup = group

        binding.waitingGroup.visibility = if (group == Group.WAITING) View.VISIBLE else View.GONE
        binding.loadingGroup.visibility = if (group == Group.LOADING) View.VISIBLE else View.GONE
        binding.connectedGroup.visibility = if (group == Group.CONNECTED) View.VISIBLE else View.GONE
        binding.exceptionGroup.visibility = if (group == Group.EXCEPTION) View.VISIBLE else View.GONE

        when (group) {
            Group.WAITING -> {
                setButtonsEnabled(true)
                binding.joinCodeRow.visibility = View.GONE
                binding.joinCodeValidation.visibility = View.GONE
                binding.joinCodeInput.setText("")
            }
            // Fresh room - previous chat history (if any) belonged to a different session.
            Group.CONNECTED -> chatAdapter.clear()
            else -> Unit
        }
    }

    private fun showWaiting() = switchGroup(Group.WAITING)

    private fun showLoading(text: String) {
        switchGroup(Group.LOADING)
        binding.loadingText.text = text
    }

    private fun showConnected(title: String, value: String, players: List<TerracottaState.TerracottaProfile>?, isHost: Boolean) {
        switchGroup(Group.CONNECTED)
        setButtonsEnabled(true)
        connectedValue = value
        connectedIsHost = isHost

        binding.connectedInfoTitle.text = title
        binding.connectedInfoValue.text = value

        val names = players.orEmpty().mapNotNull { it.name.takeIf { name -> name.isNotBlank() } }
        binding.connectedPlayers.text = if (names.isEmpty())
            getString(R.string.terracotta_players_none)
        else
            getString(R.string.terracotta_players_label, names.joinToString(", "))
    }

    private fun showException(type: TerracottaState.ExceptionState.Type) {
        switchGroup(Group.EXCEPTION)
        setButtonsEnabled(true)
        binding.exceptionText.setText(exceptionMessageRes(type))
    }

    private fun exceptionMessageRes(type: TerracottaState.ExceptionState.Type): Int = when (type) {
        TerracottaState.ExceptionState.Type.PING_HOST_FAIL -> R.string.terracotta_exception_ping_host_fail
        TerracottaState.ExceptionState.Type.PING_HOST_RST -> R.string.terracotta_exception_ping_host_rst
        TerracottaState.ExceptionState.Type.GUEST_ET_CRASH -> R.string.terracotta_exception_guest_et_crash
        TerracottaState.ExceptionState.Type.HOST_ET_CRASH -> R.string.terracotta_exception_host_et_crash
        TerracottaState.ExceptionState.Type.PING_SERVER_RST -> R.string.terracotta_exception_ping_server_rst
        TerracottaState.ExceptionState.Type.SCAFFOLDING_INVALID_RESPONSE -> R.string.terracotta_exception_scaffolding_invalid_response
    }

    // ============================== Actions ==============================

    private fun setButtonsEnabled(enabled: Boolean) {
        binding.hostButton.isEnabled = enabled
        binding.joinButton.isEnabled = enabled
        binding.joinCodeSubmit.isEnabled = enabled
    }

    private fun onHostClicked() {
        setButtonsEnabled(false)
        showLoading(getString(R.string.terracotta_status_default))
        val player = AccountsManager.currentAccount?.username

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val nodes = TerracottaNodeList.fetch()
                    Terracotta.setScanning(null, player, nodes)
                }
            }
            result.onFailure { e ->
                Logging.w(TAG, "Failed to start hosting", e)
                setButtonsEnabled(true)
                renderState(Terracotta.getState())
            }
        }
    }

    private fun toggleJoinCodeRow() {
        val show = binding.joinCodeRow.visibility != View.VISIBLE
        binding.joinCodeRow.visibility = if (show) View.VISIBLE else View.GONE
        if (show) binding.joinCodeInput.requestFocus()
    }

    private fun validateJoinCode() {
        val code = binding.joinCodeInput.text?.toString().orEmpty()
        if (code.isEmpty()) {
            binding.joinCodeValidation.visibility = View.GONE
            return
        }
        binding.joinCodeValidation.visibility = View.VISIBLE
        val (textRes, colorRes) = when (Terracotta.parseRoomCode(code)) {
            null -> R.string.terracotta_join_code_invalid to R.color.turtle_error
            TerracottaAndroidAPI.RoomType.TERRACOTTA_LEGACY -> R.string.terracotta_join_code_legacy to R.color.turtle_warning
            TerracottaAndroidAPI.RoomType.PCL2CE -> R.string.terracotta_join_code_pcl2ce to R.color.turtle_warning
            TerracottaAndroidAPI.RoomType.SCAFFOLDING -> R.string.terracotta_join_code_scaffolding to R.color.turtle_success
        }
        binding.joinCodeValidation.setText(textRes)
        binding.joinCodeValidation.setTextColor(requireContext().getColor(colorRes))
    }

    private fun onJoinSubmit() {
        val code = binding.joinCodeInput.text?.toString()?.trim().orEmpty()
        if (code.isEmpty() || Terracotta.parseRoomCode(code) == null) {
            Toast.makeText(requireContext(), R.string.terracotta_join_code_invalid, Toast.LENGTH_SHORT).show()
            return
        }

        setButtonsEnabled(false)
        showLoading(getString(R.string.terracotta_status_default))
        val player = AccountsManager.currentAccount?.username

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val nodes = TerracottaNodeList.fetch()
                    Terracotta.setGuesting(code, player, nodes)
                }
            }
            result.onSuccess { accepted ->
                if (!accepted) {
                    Toast.makeText(requireContext(), R.string.terracotta_join_code_invalid, Toast.LENGTH_SHORT).show()
                    setButtonsEnabled(true)
                    renderState(Terracotta.getState())
                }
            }.onFailure { e ->
                Logging.w(TAG, "Failed to join room", e)
                setButtonsEnabled(true)
                renderState(Terracotta.getState())
            }
        }
    }

    private fun copyConnectedValue() {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("terracotta", connectedValue))
        val toastRes = if (connectedIsHost) R.string.terracotta_code_copied else R.string.terracotta_address_copied
        Toast.makeText(requireContext(), toastRes, Toast.LENGTH_SHORT).show()
    }

    private fun exportLogs() {
        scope.launch {
            val logs = withContext(Dispatchers.IO) { Terracotta.collectLogs() }
            if (!logs.isNullOrBlank()) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("terracotta_logs", logs))
                Toast.makeText(requireContext(), R.string.terracotta_export_logs, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendChat() {
        val text = binding.chatMessageInput.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        binding.chatMessageInput.setText("")
        TerracottaChat.sendMessage(text)
    }
}
