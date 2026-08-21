package com.movtery.zalithlauncher.feature.log

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.movtery.zalithlauncher.ui.activity.ErrorActivity
import com.movtery.zalithlauncher.utils.path.PathManager
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * TurtleLauncher Native Crash Capture.
 *
 * Root problem this exists for: MC 26.3+'s SDL3 SIGSEGV (and any other native-level crash -
 * a bad renderer .so, a bad JNI call, etc) kills the whole OS process instantly via a signal.
 * Since VMLauncher.launchJVM() runs the guest JVM IN-PROCESS (JNI_CreateJavaVM, same process
 * as the launcher itself, not a separate exec'd process), a signal like this doesn't just end
 * "the game" - it kills the launcher too, with no chance for ANY Java code to run: no
 * uncaught-exception handler (that's Java-level only, PojavApplication's own handler in
 * onCreate() can't see this), no JREUtils.launchJavaVM()'s post-launchJVM() exit-code handling
 * (that line of code is simply never reached - the process is already dead), no
 * CrashAnalyzer.analyzeGameExit(). This is exactly why "no crash logs" was a real, structural
 * gap and not a logging bug to patch around - there was no code path left alive to do any
 * logging at the moment of the crash.
 *
 * The fix: Android itself keeps a short history of *why* a process died, independent of
 * whether that process is still alive to report it, via ActivityManager.
 * getHistoricalProcessExitReasons() (API 30+). For a native crash it can even hand back the
 * actual native trace (tombstone-style backtrace) via ApplicationExitInfo.getTraceInputStream().
 * Nothing here needs the native source this launcher doesn't have - it's reading a record the
 * OS already kept, on the *next* cold start, since obviously nothing can run at the instant of
 * the kill itself.
 *
 * Call [checkAndReport] once, early in app startup, after PathManager.DIR_DATA/DIR_LAUNCHER_LOG
 * are resolvable (see TurtleStartupInitializer, which already documents that exact ordering
 * constraint for the same reason).
 */
object NativeCrashCapture {
    private const val TAG = "NativeCrashCapture"
    private const val PREFS_NAME = "native_crash_capture"
    private const val KEY_LAST_REPORTED_TIMESTAMP = "last_reported_timestamp"

    /** Real file, not "latestcrash.txt" - that name is already owned by the Java-uncaught-
     *  exception handler in PojavApplication and has its own (different) format. Keeping
     *  these separate avoids one silently overwriting the other if both happen to fire across
     *  two different app runs before the user opens Share Logs. */
    private const val CRASH_FILE_NAME = "latest_native_crash.txt"

    @JvmStatic
    fun checkAndReport(context: Context) {
        // ApplicationExitInfo doesn't exist before API 30. Below that, this is simply a no-op -
        // there is no equivalent facility on older Android to fall back to without root.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
            // null package = this app's own package; maxRecords 5 is plenty, we only ever
            // care about the single newest one.
            val reasons = am.getHistoricalProcessExitReasons(null, 0, 5)
            if (reasons.isEmpty()) return

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastReported = prefs.getLong(KEY_LAST_REPORTED_TIMESTAMP, 0L)

            val newest = reasons.maxByOrNull { it.timestamp } ?: return
            // Already reported this exact exit on a previous cold start - don't show it again
            // every single time the app opens.
            if (newest.timestamp <= lastReported) return

            val isRelevant = newest.reason == ApplicationExitInfo.REASON_CRASH_NATIVE ||
                newest.reason == ApplicationExitInfo.REASON_ANR
            if (!isRelevant) {
                // A normal exit (user swiped away, REASON_USER_REQUESTED, etc) - not a crash,
                // nothing to report, but still worth remembering so an old boring exit doesn't
                // get compared against forever. Only advance the marker on exits we recognize,
                // so a reason we don't have a name for yet doesn't get silently swallowed if a
                // future Android version adds one worth surfacing.
                prefs.edit().putLong(KEY_LAST_REPORTED_TIMESTAMP, newest.timestamp).apply()
                return
            }

            val traceText = runCatching {
                newest.traceInputStream?.use { it.bufferedReader().readText() }
            }.getOrNull()

            val reportText = buildString {
                appendLine("TurtleLauncher native crash report")
                appendLine(" - Time: ${DateFormat.getDateTimeInstance().format(Date(newest.timestamp))}")
                appendLine(" - Reason: ${reasonName(newest.reason)} (status ${newest.status})")
                appendLine(" - System description: ${newest.description ?: "<none given>"}")
                appendLine(" - Process importance at death: ${newest.importance}")
                appendLine(" - Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
                appendLine()
                if (traceText.isNullOrBlank()) {
                    appendLine("<no native trace was attached to this exit by the system>")
                } else {
                    appendLine("── Native trace ──")
                    append(traceText)
                }
            }

            val crashFile = File(PathManager.DIR_LAUNCHER_LOG, CRASH_FILE_NAME)
            runCatching {
                crashFile.parentFile?.takeIf { !it.exists() }?.mkdirs()
                crashFile.writeText(reportText)
            }.onFailure { Logging.e(TAG, "Failed to write native crash report", it) }

            prefs.edit().putLong(KEY_LAST_REPORTED_TIMESTAMP, newest.timestamp).apply()

            // Reuses the exact same "launcher crash" display ErrorActivity already has (Advanced
            // Log, Copy, Share, CrashAnalyzer pattern matching) rather than building a second UI
            // for this. CrashAnalyzer.analyze() runs against the Throwable's own message text,
            // so a trimmed slice of the real trace is embedded in it (not just the reason name)
            // to give the existing rule engine an actual shot at recognizing it - e.g. a known
            // "libSDL3.so"/"libEGL" signature, if one gets added to CrashAnalyzer's knowledge
            // base later.
            val summary = "Native crash: ${reasonName(newest.reason)} - ${newest.description ?: "no system description"}\n\n" +
                (traceText?.take(4000) ?: "")
            ErrorActivity.showLauncherCrash(context, crashFile.absolutePath, RuntimeException(summary))
        }.onFailure { Logging.e(TAG, "checkAndReport failed", it) }
    }

    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_CRASH_NATIVE ->
            "Native crash (a signal like SIGSEGV/SIGABRT killed the whole process outside the JVM - e.g. in a renderer .so, SDL, or another native library)"
        ApplicationExitInfo.REASON_ANR -> "App Not Responding (the app froze long enough for the system to kill it)"
        ApplicationExitInfo.REASON_CRASH -> "Java crash"
        else -> "Other (reason code $reason)"
    }
}
