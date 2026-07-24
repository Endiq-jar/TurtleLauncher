package com.movtery.zalithlauncher.ui.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.movtery.zalithlauncher.InfoCenter
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.databinding.ActivityErrorBinding
import com.movtery.zalithlauncher.feature.log.CrashAnalyzer
import com.movtery.zalithlauncher.utils.ZHTools
import com.movtery.zalithlauncher.utils.path.PathManager
import net.kdt.pojavlaunch.Tools
import java.io.File

/**
 * Shown as a small floating dialog (see CustomDialogStyle in the manifest) instead of a
 * full-screen takeover, so a crash never feels like the whole app exploded.
 *
 * Always tries to show a plain-language "how to fix this" tip via [CrashAnalyzer] first.
 * The raw stack trace / log is hidden by default behind "Advanced Log", with Copy and
 * Share actions, for when someone actually needs to read or send the real details.
 */
class ErrorActivity : BaseActivity() {
    private lateinit var binding: ActivityErrorBinding
    private var advancedLogContent: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityErrorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val extras = intent.extras
        extras ?: run {
            finish()
            return
        }

        binding.errorConfirm.setOnClickListener { finish() }
        binding.errorRestart.setOnClickListener {
            startActivity(Intent(this@ErrorActivity, SplashActivity::class.java))
        }
        binding.shareLog.setOnClickListener { ZHTools.shareLogs(this) }

        binding.toggleAdvancedLog.setOnClickListener {
            val showing = binding.advancedLogSection.visibility == View.VISIBLE
            binding.advancedLogSection.visibility = if (showing) View.GONE else View.VISIBLE
            binding.toggleAdvancedLog.setText(
                if (showing) R.string.crash_show_advanced_log else R.string.crash_hide_advanced_log
            )
        }
        binding.copyLog.setOnClickListener {
            val clipboard = getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText("crash_log", advancedLogContent))
            Toast.makeText(this, R.string.crash_log_copied, Toast.LENGTH_SHORT).show()
        }

        if (extras.getBoolean(BUNDLE_IS_LAUNCHER_CRASH, false)) {
            showLauncherCrash(extras)
            return
        }
        if (extras.getBoolean(BUNDLE_IS_GAME_CRASH, false)) {
            //如果不是应用崩溃，那么这个页面就不允许截图
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

    /** Populates the "How to fix this" section, or hides it if there's nothing useful to say. */
    private fun showFixTips(diagnosisText: String?) {
        binding.apply {
            if (diagnosisText.isNullOrBlank()) {
                fixTitle.visibility = View.VISIBLE
                fixText.visibility = View.VISIBLE
                fixText.text = getString(R.string.crash_no_diagnosis)
            } else {
                fixTitle.visibility = View.VISIBLE
                fixText.visibility = View.VISIBLE
                fixText.text = diagnosisText
            }
        }
    }

    /** Sets the raw text shown behind the "Advanced Log" toggle. */
    private fun setAdvancedLog(rawText: String) {
        advancedLogContent = rawText.ifBlank { "<no log available>" }
        binding.advancedLogText.text = advancedLogContent
    }

    private fun showLauncherCrash(extras: Bundle) {
        val context = this

        val throwable = extras.getSerializable(BUNDLE_THROWABLE) as Throwable?
        val stackTrace = if (throwable != null) Tools.printToString(throwable) else "<null>"
        val strSavePath = extras.getString(BUNDLE_SAVE_PATH)

        binding.apply {
            this.errorTitle.text = InfoCenter.replaceName(context, R.string.error_fatal)
            this.errorText.text = getString(R.string.crash_how_to_fix)

            this.topView.setBackgroundColor(ContextCompat.getColor(context, R.color.background_menu_top_error))
            this.background.setBackgroundColor(ContextCompat.getColor(context, R.color.background_app_error))
        }

        // Prefer the on-disk crash report (it has device/version info too); fall back to the raw stack trace.
        val savedReport = strSavePath?.let { runCatching { File(it).takeIf { f -> f.exists() }?.readText() }.getOrNull() }
        val fullLog = savedReport ?: stackTrace
        setAdvancedLog(fullLog)

        val diagnoses = runCatching { CrashAnalyzer.analyze(stackTrace) }.getOrDefault(emptyList())
        showFixTips(if (diagnoses.isEmpty()) null else CrashAnalyzer.formatForDisplay(diagnoses))
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
                text = getString(errorText, code)
                textSize = 13f
                setTextIsSelectable(true)
            }
            this.errorTip.visibility = View.VISIBLE
            this.errorNoScreenshot.visibility = View.VISIBLE

            this.topView.setBackgroundColor(ContextCompat.getColor(context, R.color.background_menu_top))
            this.background.setBackgroundColor(ContextCompat.getColor(context, R.color.background_app))
        }

        showFixTips(diagnosis)
        // The diagnosis already summarises the log; Advanced Log still gives access to the raw tail
        // for anyone (or anyone helping them) who needs the unfiltered details.
        val logTail = runCatching {
            CrashAnalyzer.tailOf(File(PathManager.DIR_GAME_HOME, "latestlog.txt"), 64 * 1024)
        }.getOrDefault("")
        setAdvancedLog(logTail.ifBlank { diagnosis ?: "" })
    }

    private fun showEasterEgg() {
        val context = this

        binding.apply {
            this.topView.visibility = View.GONE
            this.scrollView.visibility = View.GONE
            this.shareLog.visibility = View.GONE
            this.errorRestart.visibility = View.GONE
            this.errorConfirm.visibility = View.GONE
            this.centerText.visibility = View.VISIBLE

            this.centerText.text = InfoCenter.replaceName(context, R.string.error_fatal)

            this.topView.setBackgroundColor(ContextCompat.getColor(context, R.color.background_menu_top_error))
            this.background.setBackgroundResource(R.drawable.image_xibao)
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
         * @param diagnosis optional pre-formatted [com.movtery.zalithlauncher.feature.log.CrashAnalyzer]
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
