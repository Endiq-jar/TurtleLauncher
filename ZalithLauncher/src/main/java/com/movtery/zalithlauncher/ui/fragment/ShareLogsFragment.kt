package com.movtery.zalithlauncher.ui.fragment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.movtery.anim.AnimPlayer
import com.movtery.anim.animations.Animations
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.databinding.FragmentShareLogsBinding
import com.movtery.zalithlauncher.feature.log.CrashAnalyzer
import com.movtery.zalithlauncher.ui.dialog.TipDialog
import com.movtery.zalithlauncher.utils.ZHTools
import com.movtery.zalithlauncher.utils.file.FileTools
import com.movtery.zalithlauncher.utils.path.PathManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated Share Logs screen: shows the most recent launcher log file plus four actions
 * (View / Share / Copy / Clear), replacing the old single "Share Logs" quick action that
 * instantly zipped and opened the system share sheet with no way to preview or clear logs
 * first.
 */
class ShareLogsFragment : FragmentWithAnim(R.layout.fragment_share_logs) {
    companion object {
        const val TAG: String = "ShareLogsFragment"
    }

    private lateinit var binding: FragmentShareLogsBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentShareLogsBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.backButton.setOnClickListener { ZHTools.onBackPressed(requireActivity()) }

        refreshLatestLogInfo()

        binding.latestLogRow.setOnClickListener { showLogContent() }
        binding.viewLogRow.setOnClickListener { showLogContent() }
        binding.shareLogRow.setOnClickListener { ZHTools.shareLogs(requireActivity()) }
        binding.copyLogRow.setOnClickListener { copyLogToClipboard() }
        binding.clearLogRow.setOnClickListener { confirmClearLogs() }
    }

    /** The most recently modified launcher log file, or null if none exist yet. */
    private fun latestLogFile(): File? {
        val logDir = File(PathManager.DIR_LAUNCHER_LOG)
        return logDir.takeIf { it.isDirectory }
            ?.listFiles { f -> f.isFile }
            ?.maxByOrNull { it.lastModified() }
    }

    private fun refreshLatestLogInfo() {
        val file = latestLogFile()
        if (file == null) {
            binding.latestLogName.text = getString(R.string.share_logs_none_found)
            binding.latestLogSize.text = ""
            return
        }
        val dateText = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(file.lastModified()))
        binding.latestLogName.text = dateText
        binding.latestLogSize.text = getString(R.string.file_size_label, FileTools.formatFileSize(file.length()))
    }

    private fun showLogContent() {
        val file = latestLogFile()
        if (file == null) {
            Toast.makeText(requireContext(), R.string.share_logs_none_found, Toast.LENGTH_SHORT).show()
            return
        }
        val content = runCatching { CrashAnalyzer.tailOf(file, 32 * 1024) }.getOrDefault("")
        TipDialog.Builder(requireContext())
            .setTitle(file.name)
            .setMessage(content.ifBlank { getString(R.string.share_logs_none_found) })
            .setSelectable(true)
            .setShowCancel(false)
            .setConfirm(R.string.generic_ok)
            .buildDialog()
            .show()
    }

    private fun copyLogToClipboard() {
        val file = latestLogFile()
        if (file == null) {
            Toast.makeText(requireContext(), R.string.share_logs_none_found, Toast.LENGTH_SHORT).show()
            return
        }
        val content = runCatching { CrashAnalyzer.tailOf(file, 128 * 1024) }.getOrDefault("")
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(file.name, content))
        Toast.makeText(requireContext(), R.string.share_logs_copied, Toast.LENGTH_SHORT).show()
    }

    private fun confirmClearLogs() {
        TipDialog.Builder(requireContext())
            .setTitle(R.string.share_logs_clear_confirm_title)
            .setMessage(R.string.share_logs_clear_confirm_message)
            .setWarning()
            .setConfirm(R.string.generic_delete)
            .setConfirmClickListener {
                val logDir = File(PathManager.DIR_LAUNCHER_LOG)
                logDir.listFiles { f -> f.isFile }?.forEach { it.delete() }
                refreshLatestLogInfo()
                Toast.makeText(requireContext(), R.string.share_logs_cleared, Toast.LENGTH_SHORT).show()
            }
            .buildDialog()
            .show()
    }

    override fun slideIn(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.root, Animations.BounceInRight))
    }

    override fun slideOut(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.root, Animations.FadeOutLeft))
    }
}
