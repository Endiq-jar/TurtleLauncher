package com.endiq.turtlelauncher.feature.engine

import android.app.Activity
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.task.TaskExecutors

/**
 * Activity-facing wrapper around [HorizonEngineInstaller], used when someone presses Play with
 * the Horizon engine selected but not installed - or installed at an older version than the
 * engine this launcher carries.
 *
 * Kept separate from the settings screen so the Play button can offer the same built-in install
 * without the user having to find a setting first. The dialog is the consent step: nothing is
 * downloaded or installed before the user taps Download, and Android still shows its own package
 * installer on top.
 */
object HorizonEngineInstallFlow {
    private const val TAG = "HorizonInstallFlow"

    /**
     * Asks whether to install the engine. It ships inside the launcher APK, so this is a consent
     * prompt for Android's installer, not a download. [onDeclined] runs on the main thread when
     * the user backs out (or the dialog is cancelled) so the caller can fall back to the on-device
     * engine.
     */
    @JvmStatic
    fun offerInstall(activity: Activity, onDeclined: Runnable) {
        if (activity.isFinishing || activity.isDestroyed) {
            onDeclined.run()
            return
        }
        val bundled = HorizonEngineInstaller.isBundled(activity.applicationContext)
        val label = HorizonEngineInstaller.bundledLabel(activity.applicationContext)
        val message = if (bundled) {
            activity.getString(R.string.engine_install_body_bundled, label.orEmpty())
        } else {
            activity.getString(R.string.engine_install_body, HorizonEngineInstaller.sourceRepo())
        }
        val dialog = AlertDialog.Builder(activity, R.style.CustomAlertDialogTheme)
            .setTitle(R.string.engine_install_title)
            .setMessage(message)
            .setPositiveButton(
                if (bundled) R.string.engine_install_confirm_bundled else R.string.engine_install_confirm
            ) { _, _ -> download(activity) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> onDeclined.run() }
            .setOnCancelListener { onDeclined.run() }
            .create()
        dialog.show()
    }

    /**
     * Offers to upgrade an already-installed Horizon app when the launcher's built-in engine is
     * newer. Returns true when a dialog was shown (the caller should stop; [onContinue] runs if
     * the user chooses to launch now, and the dismissal is remembered per bundled version so the
     * prompt never becomes a nag).
     */
    @JvmStatic
    fun offerUpdateIfNeeded(activity: Activity, onContinue: Runnable): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false
        val context = activity.applicationContext
        if (!HorizonEngineInstaller.isBundled(context)) return false
        val status = GameEngineManager.horizonStatus(activity)
        if (!status.updateAvailable) return false
        val bundledVersion = HorizonEngineInstaller.bundledVersion(context)
        if (AllSettings.horizonEngineUpdateSkipped.getValue() == bundledVersion) return false

        val installed = status.installation?.versionName.orEmpty().ifBlank { "unknown" }
        AlertDialog.Builder(activity, R.style.CustomAlertDialogTheme)
            .setTitle(R.string.engine_update_title)
            .setMessage(activity.getString(R.string.engine_update_body, installed, bundledVersion))
            .setPositiveButton(R.string.engine_update_confirm) { _, _ ->
                // Installing is Android's own flow; the game starts on the next Play press.
                download(activity)
            }
            .setNegativeButton(R.string.engine_update_later) { _, _ ->
                AllSettings.horizonEngineUpdateSkipped.put(bundledVersion).save()
                onContinue.run()
            }
            .setOnCancelListener {
                AllSettings.horizonEngineUpdateSkipped.put(bundledVersion).save()
                onContinue.run()
            }
            .show()
        return true
    }

    private fun download(activity: Activity) {
        val bundled = HorizonEngineInstaller.isBundled(activity.applicationContext)
        val startText = if (bundled) R.string.engine_prepare_starting else R.string.engine_download_starting
        val progressText = if (bundled) R.string.engine_unpack_progress else R.string.engine_download_progress

        val progress = AlertDialog.Builder(activity, R.style.CustomAlertDialogTheme)
            .setTitle(R.string.engine_install_title)
            .setMessage(activity.getString(startText))
            .setCancelable(false)
            .create()
        progress.show()

        TaskExecutors.getDefault().execute {
            val result = HorizonEngineInstaller.acquire(activity.applicationContext) { percent ->
                TaskExecutors.runInUIThread {
                    runCatching { progress.setMessage(activity.getString(progressText, percent)) }
                }
            }
            TaskExecutors.runInUIThread {
                runCatching { progress.dismiss() }
                result.onSuccess { file ->
                    HorizonEngineInstaller.install(activity, file)
                        .onSuccess {
                            toast(activity, activity.getString(R.string.engine_install_started), Toast.LENGTH_LONG)
                        }
                        .onFailure { error ->
                            Logging.w(TAG, "Could not start the installer", error)
                            toast(
                                activity,
                                activity.getString(R.string.engine_install_failed, error.message ?: ""),
                                Toast.LENGTH_LONG
                            )
                        }
                }.onFailure { error ->
                    Logging.w(TAG, "Engine download failed", error)
                    toast(
                        activity,
                        activity.getString(R.string.engine_install_failed, error.message ?: ""),
                        Toast.LENGTH_LONG
                    )
                }
            }
        }
    }

    private fun toast(activity: Activity, text: String, duration: Int) {
        if (activity.isFinishing || activity.isDestroyed) return
        runCatching { Toast.makeText(activity, text, duration).show() }
    }
}
