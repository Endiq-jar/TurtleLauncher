package com.endiq.turtlelauncher.ui.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.endiq.turtlelauncher.InfoCenter
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.databinding.ActivityErrorBinding
import com.endiq.turtlelauncher.feature.log.AiCrashAdvisor
import com.endiq.turtlelauncher.feature.log.CrashAnalyzer
import com.endiq.turtlelauncher.feature.log.GameLogcat
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.feature.log.SelfHealingManager
import com.endiq.turtlelauncher.feature.shizuku.ShizukuActions
import com.endiq.turtlelauncher.task.TaskExecutors
import com.endiq.turtlelauncher.utils.ZHTools
import com.endiq.turtlelauncher.utils.file.FileTools
import com.endiq.turtlelauncher.utils.path.PathManager
import net.endiq.launcher.Tools
import java.io.File

class ErrorActivity : BaseActivity() {
    private lateinit var binding: ActivityErrorBinding
    private var advancedLogContent: String = ""
    private var aiAnalysisRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityErrorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val extras = intent.extras
        extras ?: run {
            finish()
            return
        }

        binding.errorConfirm.setOnClickListener { dismissOrReturnToLauncher() }
        binding.errorRestart.setOnClickListener {
            startActivity(Intent(this@ErrorActivity, SplashActivity::class.java))
        }
        binding.shareLog.setOnClickListener { ZHTools.shareLogs(this) }
        binding.toggleAdvancedLog.setOnClickListener {
            val clipboard = getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText("crash_log", advancedLogContent))
            Toast.makeText(this, R.string.crash_log_copied, Toast.LENGTH_SHORT).show()
        }

        binding.tabGameLogs.setOnClickListener { selectTab(isGameLogs = true) }
        binding.tabAiAnalysis.setOnClickListener { selectTab(isGameLogs = false) }
        binding.aiInspectButton.setOnClickListener {
            selectTab(isGameLogs = false)
            requestAiAnalysis()
        }
        selectTab(isGameLogs = true)

        if (extras.getBoolean(BUNDLE_IS_LAUNCHER_CRASH, false)) {
            showLauncherCrash(extras)
            return
        }
        if (extras.getBoolean(BUNDLE_IS_GAME_CRASH, false)) {
            // Unless the app crashed, this page may not be captured in screenshots.
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            showGameCrash(extras)
            return
        }
        if (extras.getBoolean(BUNDLE_EASTER_EGG, false)) {
            showEasterEgg()
            return
        }

        finish()
    }

    private fun dismissOrReturnToLauncher() {
        if (!isTaskRoot) {
            finish()
            return
        }
        runCatching {
            startActivity(
                Intent(this, SplashActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }.onFailure {
            Logging.e("ErrorActivity", "Could not return to the launcher from the crash screen", it)
        }
        finish()
    }

    /** Switches between the "Game Logs" and "AI Analysis" tabs, sliding the underline indicator. */
    private fun selectTab(isGameLogs: Boolean) {
        binding.apply {
            logTabContent.visibility = if (isGameLogs) View.VISIBLE else View.GONE
            aiTabContent.visibility = if (isGameLogs) View.GONE else View.VISIBLE
            tabGameLogs.alpha = if (isGameLogs) 1f else 0.5f
            tabAiAnalysis.alpha = if (isGameLogs) 0.5f else 1f

            val indicatorTarget = if (isGameLogs) tabGameLogs else tabAiAnalysis
            crashTabIndicator.post {
                val params = crashTabIndicator.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                params.startToStart = indicatorTarget.id
                params.endToEnd = indicatorTarget.id
                crashTabIndicator.layoutParams = params
            }
        }
    }

    /** Populates the "AI Analysis" tab, or hides it if there's nothing useful to say yet. */
    private fun showFixTips(diagnosisText: String?) {
        binding.apply {
            if (diagnosisText.isNullOrBlank()) {
                fixText.visibility = View.GONE
                aiAnalysisPlaceholder.visibility = View.VISIBLE
            } else {
                fixText.visibility = View.VISIBLE
                fixText.text = diagnosisText
                aiAnalysisPlaceholder.visibility = View.GONE
            }
        }
    }

    /** Sets the raw text shown under the "Game Logs" tab. */
    private fun setAdvancedLog(rawText: String) {
        advancedLogContent = rawText.ifBlank { "<no log available>" }
        binding.advancedLogText.text = advancedLogContent
    }

    /**
     * Calls the (separate, opt-in) AI crash advisor on the current log tail and shows the
     * result in place of the local rule-based diagnosis, once it comes back. A no-op if AI
     * crash help isn't configured or a request is already in flight — [AiCrashAdvisor] itself
     * returns null in those cases, so the local diagnosis text is simply left as-is.
     */
    private fun requestAiAnalysis() {
        if (aiAnalysisRequested) return
        aiAnalysisRequested = true
        binding.aiAnalysisPlaceholder.visibility = View.GONE
        binding.fixText.visibility = View.VISIBLE
        val previousText = binding.fixText.text.toString()
        binding.fixText.text = getString(R.string.crash_ai_analysis_loading)

        TaskExecutors.getDefault().execute {
            val suggestion = runCatching { AiCrashAdvisor.getSuggestion(advancedLogContent) }.getOrNull()
            TaskExecutors.runInUIThread {
                binding.fixText.text = suggestion ?: previousText.ifBlank {
                    getString(R.string.crash_ai_analysis_unavailable)
                }
            }
        }
    }

    private fun showDiagnosisActions() {
        val diagnoses = runCatching { CrashAnalyzer.getLastDiagnoses() }.getOrDefault(emptyList())
        val gameVersion = runCatching { CrashAnalyzer.getLastGameVersion() }.getOrNull()
        val topDiagnosis = diagnoses.firstOrNull()
        val repairAction = diagnoses.firstOrNull { it.repairActions.isNotEmpty() }?.repairActions?.firstOrNull()

        binding.apply {
            if (topDiagnosis != null) {
                crashExportButton.visibility = View.VISIBLE
                crashExportButton.setOnClickListener { exportDiagnostics(diagnoses, gameVersion) }
            } else {
                crashExportButton.visibility = View.GONE
            }

            // "Repair" and "Search online" are secondary actions - tucked behind the overflow
            // (⋯) icon rather than taking a dedicated pill in the rail, same actions as before.
            val hasOverflowActions = repairAction != null || topDiagnosis != null
            crashMoreButton.visibility = if (hasOverflowActions) View.VISIBLE else View.GONE
            crashMoreButton.setOnClickListener {
                showOverflowMenu(it, repairAction, topDiagnosis, gameVersion)
            }
        }

        runSelfHeal(diagnoses, gameVersion)
    }

    private fun showOverflowMenu(
        anchor: View,
        repairAction: CrashAnalyzer.RepairAction?,
        topDiagnosis: CrashAnalyzer.Diagnosis?,
        gameVersion: com.endiq.turtlelauncher.feature.version.Version?
    ) {
        val popup = PopupMenu(this, anchor)
        if (repairAction != null) {
            popup.menu.add(repairAction.label).setOnMenuItemClickListener {
                runRepair(repairAction, gameVersion); true
            }
        }
        if (topDiagnosis != null) {
            popup.menu.add(getString(R.string.crash_search_online_button)).setOnMenuItemClickListener {
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CrashAnalyzer.onlineSearchUrl(topDiagnosis))))
                }
                true
            }
        }
        popup.show()
    }

    private fun runSelfHeal(diagnoses: List<CrashAnalyzer.Diagnosis>, gameVersion: com.endiq.turtlelauncher.feature.version.Version?) {
        if (diagnoses.none { it.repairActions.isNotEmpty() }) {
            binding.selfHealStatus.visibility = View.GONE
            return
        }

        binding.selfHealStatus.visibility = View.VISIBLE
        binding.selfHealStatus.text = getString(R.string.self_heal_repairing)

        TaskExecutors.getDefault().execute {
            val outcome = runCatching { SelfHealingManager.autoHeal(diagnoses, gameVersion) }.getOrNull()
            TaskExecutors.runInUIThread {
                if (outcome == null || !outcome.triggered) {
                    binding.selfHealStatus.visibility = View.GONE
                } else {
                    binding.selfHealStatus.text = outcome.summary
                }
            }
        }
    }

    /** Runs [action] off the UI thread, then reports the result and refreshes the fix text. */
    private fun runRepair(action: CrashAnalyzer.RepairAction, gameVersion: com.endiq.turtlelauncher.feature.version.Version?) {
        TaskExecutors.getDefault().execute {
            val result = runCatching { CrashAnalyzer.executeRepair(action, gameVersion) }
                .getOrElse { e -> CrashAnalyzer.RepairResult(false, e.message ?: "Repair failed") }
            TaskExecutors.runInUIThread {
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Writes a diagnostics export file off the UI thread, then offers to share it. */
    private fun exportDiagnostics(diagnoses: List<CrashAnalyzer.Diagnosis>, gameVersion: com.endiq.turtlelauncher.feature.version.Version?) {
        binding.crashExportButton.isEnabled = false
        TaskExecutors.getDefault().execute {
            val file = runCatching {
                CrashAnalyzer.exportDiagnostics(diagnoses, logText = advancedLogContent, gameVersion = gameVersion)
            }.getOrNull()
            TaskExecutors.runInUIThread {
                binding.crashExportButton.isEnabled = true
                if (file == null) {
                    Toast.makeText(this, R.string.crash_export_failed, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.crash_export_saved, file.name), Toast.LENGTH_LONG).show()
                    FileTools.shareFile(this, file)
                }
            }
        }
    }

    private fun showLauncherCrash(extras: Bundle) {
        val context = this

        val throwable = extras.getSerializable(BUNDLE_THROWABLE) as Throwable?
        val stackTrace = if (throwable != null) Tools.printToString(throwable) else "<null>"
        val strSavePath = extras.getString(BUNDLE_SAVE_PATH)

        binding.apply {
            this.errorTitle.text = InfoCenter.replaceName(context, R.string.error_fatal)
            this.background.setBackgroundColor(ContextCompat.getColor(context, R.color.background_app_error))
        }

        // Prefer the on-disk crash report (it has device/version info too); fall back to the raw stack trace.
        val savedReport = strSavePath?.let { runCatching { File(it).takeIf { f -> f.exists() }?.readText() }.getOrNull() }
        val fullLog = savedReport ?: stackTrace
        setAdvancedLog(fullLog)

        val diagnoses = runCatching { CrashAnalyzer.analyze(stackTrace) }.getOrDefault(emptyList())
        showFixTips(if (diagnoses.isEmpty()) null else CrashAnalyzer.formatForDisplay(diagnoses))
        showDiagnosisActions()
    }

    private fun showGameCrash(extras: Bundle) {
        val code = extras.getInt(BUNDLE_CODE, 0)
        if (code == 0) {
            finish()
            return
        }
        val errorText = if (extras.getBoolean(BUNDLE_IS_SIGNAL)) R.string.game_singnal_message else R.string.game_exit_message
        val diagnosis = extras.getString(BUNDLE_DIAGNOSIS)

        val context = this

        binding.apply {
            this.errorTitle.setText(R.string.generic_wrong_tip)
            this.errorText.apply {
                visibility = View.VISIBLE
                text = getString(errorText, code)
                setTextIsSelectable(true)
            }
            this.errorTip.visibility = View.VISIBLE
            this.errorNoScreenshot.visibility = View.VISIBLE

            this.background.setBackgroundColor(ContextCompat.getColor(context, R.color.turtle_surface))
        }

        showFixTips(diagnosis)
        // The diagnosis already summarises the log; Game Logs tab still gives access to the raw
        // tail for anyone (or anyone helping them) who needs the unfiltered details.
        applyGameCrashAdvancedLog(diagnosis)
        // Structured diagnoses for this exact crash were already stashed by CrashAnalyzer.analyzeGameExit()
        // (called from JREUtils right before this activity was launched) — see getLastDiagnoses().
        showDiagnosisActions()
    }

    private fun applyGameCrashAdvancedLog(diagnosis: String?) {
        val nativeLog = runCatching {
            CrashAnalyzer.tailOf(File(PathManager.DIR_GAME_HOME, "latestlog.txt"), 64 * 1024)
        }.getOrDefault("")
        if (nativeLog.isNotBlank()) {
            setAdvancedLog(nativeLog)
            return
        }

        val sessionLogcat = GameLogcat.readSessionTail()
        if (sessionLogcat.isNotBlank()) {
            setAdvancedLog(GameLogcat.formatForReport(sessionLogcat))
            return
        }

        setAdvancedLog("")
        TaskExecutors.getDefault().execute {
            val fallback = ShizukuActions.dumpLogcat(4000)
                .takeIf { it.isNotBlank() }
                ?: GameLogcat.dumpNow()
            val text = if (fallback.isNotBlank()) GameLogcat.formatForReport(fallback)
            else diagnosis ?: ""
            TaskExecutors.runInUIThread { setAdvancedLog(text) }
        }
    }

    private fun showEasterEgg() {
        val context = this

        binding.apply {
            this.crashCard.visibility = View.GONE
            this.actionRail.visibility = View.GONE
            this.centerText.visibility = View.VISIBLE

            this.centerText.text = InfoCenter.replaceName(context, R.string.error_fatal)

            this.background.setBackgroundResource(R.drawable.image_error_background)
        }
    }

    companion object {
        private const val BUNDLE_IS_LAUNCHER_CRASH = "is_launcher_crash"
        private const val BUNDLE_IS_GAME_CRASH = "is_game_crash"
        private const val BUNDLE_IS_SIGNAL = "is_signal"
        private const val BUNDLE_CODE = "code"
        private const val BUNDLE_THROWABLE = "throwable"
        private const val BUNDLE_SAVE_PATH = "save_path"
        private const val BUNDLE_EASTER_EGG = "easter_egg"
        private const val BUNDLE_DIAGNOSIS = "crash_diagnosis"

        @JvmStatic
        fun showLauncherCrash(ctx: Context, savePath: String?, th: Throwable?) {
            val intent = Intent(ctx, ErrorActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra(BUNDLE_THROWABLE, th)
            intent.putExtra(BUNDLE_SAVE_PATH, savePath)
            intent.putExtra(BUNDLE_IS_LAUNCHER_CRASH, true)
            ctx.startActivity(intent)
        }

        /**
         * @param diagnosis optional pre-formatted [com.endiq.turtlelauncher.feature.log.CrashAnalyzer]
         * output to display alongside the generic exit message. Pass null/blank for none.
         */
        @JvmOverloads
        @JvmStatic
        fun showExitMessage(
            ctx: Context,
            code: Int,
            isSignal: Boolean,
            diagnosis: String? = null
        ) {
            val intent = Intent(ctx, ErrorActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra(BUNDLE_CODE, code)
            intent.putExtra(BUNDLE_IS_LAUNCHER_CRASH, false)
            intent.putExtra(BUNDLE_IS_SIGNAL, isSignal)
            intent.putExtra(BUNDLE_IS_GAME_CRASH, true)
            if (!diagnosis.isNullOrBlank()) intent.putExtra(BUNDLE_DIAGNOSIS, diagnosis)
            ctx.startActivity(intent)
        }

        @JvmStatic
        fun showEasterEgg(ctx: Context) {
            val intent = Intent(ctx, ErrorActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra(BUNDLE_EASTER_EGG, true)
            ctx.startActivity(intent)
        }
    }
}
