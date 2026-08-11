package com.movtery.zalithlauncher.ui.fragment

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.movtery.anim.AnimPlayer
import com.movtery.anim.animations.Animations
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.databinding.FragmentLogViewerBinding
import com.movtery.zalithlauncher.feature.log.CrashAnalyzer
import com.movtery.zalithlauncher.utils.ZHTools
import com.movtery.zalithlauncher.utils.path.PathManager
import java.io.File

/**
 * Built-in log viewer: shows a launcher log file with a live text search (highlights matches)
 * and an "Errors only" filter that narrows the view down to lines carrying
 * [com.movtery.zalithlauncher.feature.log.Logging]'s own `(ERROR)` tag - the same tag every
 * `Logging.e(...)` call writes, so this needs no separate error-detection heuristic. Opened
 * from ShareLogsFragment's "View Log" row instead of the old single-shot TipDialog preview.
 */
class LogViewerFragment : FragmentWithAnim(R.layout.fragment_log_viewer) {
    companion object {
        const val TAG: String = "LogViewerFragment"
        private const val ARG_FILE_PATH = "file_path"
        private const val MAX_READ_BYTES = 512 * 1024

        fun createArgs(file: File): Bundle = Bundle().apply {
            putString(ARG_FILE_PATH, file.absolutePath)
        }
    }

    private lateinit var binding: FragmentLogViewerBinding
    private var allLines: List<String> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLogViewerBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.backButton.setOnClickListener { ZHTools.onBackPressed(requireActivity()) }

        val path = arguments?.getString(ARG_FILE_PATH)
        val file = path?.let { File(it) }
            ?: File(PathManager.DIR_LAUNCHER_LOG).takeIf { it.isDirectory }
                ?.listFiles { f -> f.isFile }
                ?.maxByOrNull { it.lastModified() }

        if (file == null || !file.isFile) {
            binding.logViewerTitle.text = getString(R.string.log_viewer_title)
            binding.logMatchCount.text = getString(R.string.share_logs_none_found)
            return
        }

        binding.logViewerTitle.text = file.name
        val content = runCatching { CrashAnalyzer.tailOf(file, MAX_READ_BYTES) }.getOrDefault("")
        allLines = content.split("\n")

        binding.logErrorsOnlyCheckbox.setOnCheckedChangeListener { _, _ -> applyFilter() }
        binding.logSearchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = applyFilter()
        })

        applyFilter()
    }

    private fun applyFilter() {
        val query = binding.logSearchEdit.text?.toString()?.trim().orEmpty()
        val errorsOnly = binding.logErrorsOnlyCheckbox.isChecked

        val filtered = allLines.filter { line ->
            (!errorsOnly || line.contains("(ERROR)")) &&
                (query.isEmpty() || line.contains(query, ignoreCase = true))
        }

        binding.logMatchCount.text = if (allLines.isEmpty() || allLines.all { it.isBlank() }) {
            getString(R.string.share_logs_none_found)
        } else {
            getString(R.string.log_viewer_match_count, filtered.size)
        }

        if (filtered.isEmpty()) {
            binding.logContentText.text = getString(R.string.log_viewer_no_matches)
            return
        }

        val errorColor = ContextCompat.getColor(requireContext(), R.color.turtle_error)
        val warnColor = ContextCompat.getColor(requireContext(), R.color.turtle_warning)
        val highlightColor = ContextCompat.getColor(requireContext(), R.color.accent_primary)

        val builder = SpannableStringBuilder()
        filtered.forEachIndexed { index, line ->
            val start = builder.length
            builder.append(line)
            val end = builder.length

            when {
                line.contains("(ERROR)") -> builder.setSpan(ForegroundColorSpan(errorColor), start, end, 0)
                line.contains("(WARN)") -> builder.setSpan(ForegroundColorSpan(warnColor), start, end, 0)
            }

            if (query.isNotEmpty()) {
                var searchFrom = 0
                val lowerLine = line.lowercase()
                val lowerQuery = query.lowercase()
                while (true) {
                    val matchIndex = lowerLine.indexOf(lowerQuery, searchFrom)
                    if (matchIndex == -1) break
                    builder.setSpan(
                        BackgroundColorSpan(Color.argb(120, Color.red(highlightColor), Color.green(highlightColor), Color.blue(highlightColor))),
                        start + matchIndex,
                        start + matchIndex + query.length,
                        0
                    )
                    searchFrom = matchIndex + query.length
                }
            }

            if (index != filtered.lastIndex) builder.append("\n")
        }
        binding.logContentText.text = builder
    }

    override fun slideIn(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.root, Animations.BounceInRight))
    }

    override fun slideOut(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.root, Animations.FadeOutLeft))
    }
}
