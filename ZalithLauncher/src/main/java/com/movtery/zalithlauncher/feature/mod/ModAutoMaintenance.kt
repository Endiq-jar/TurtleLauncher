package com.movtery.zalithlauncher.feature.mod

import android.content.Context
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.feature.download.utils.ModLoaderUtils
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.feature.mod.parser.ModInfo
import com.movtery.zalithlauncher.feature.version.Version
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.task.Task
import com.movtery.zalithlauncher.task.TaskExecutors
import com.movtery.zalithlauncher.ui.dialog.TipDialog
import java.io.File

/**
 * Runs TurtleLauncher's automatic mod maintenance (dependency installer + update
 * checker) once per launch, right after mods have been parsed and before the
 * existing [com.movtery.zalithlauncher.feature.mod.parser.ModChecker] step.
 *
 * Both features are best-effort and network-bound, so all work happens on a
 * background task; [onComplete] is invoked back on whatever the caller needs
 * (always called exactly once, never throws). Any dialogs this shows are purely
 * informational/optional and do NOT block [onComplete] — launch is never held up
 * waiting for the player to tap a button.
 */
object ModAutoMaintenance {

    @JvmStatic
    fun runForVersion(
        context: Context,
        version: Version,
        modInfoList: List<ModInfo>,
        onComplete: Runnable
    ) {
        val dependencyEnabled = runCatching { AllSettings.autoDependencyInstall.getValue() }.getOrDefault(true) &&
                !runCatching { AllSettings.fastBoot.getValue() }.getOrDefault(false)
        val updateCheckEnabled = runCatching { AllSettings.autoModUpdateCheck.getValue() }.getOrDefault(true) &&
                !runCatching { AllSettings.fastBoot.getValue() }.getOrDefault(false)
        // Conflict detection is cheap (static jar/bytecode scan, no network) so it still runs
        // under Fast Boot — it's exactly the kind of check that helps when something's wrong.
        val conflictCheckEnabled = runCatching { AllSettings.modConflictDetection.getValue() }.getOrDefault(true)

        if (!dependencyEnabled && !updateCheckEnabled && !conflictCheckEnabled) {
            onComplete.run()
            return
        }

        Task.runTask {
            var dependencyResult: ModDependencyResolver.ResolveResult? = null
            var updates: List<ModUpdateChecker.UpdateInfo> = emptyList()
            var conflicts: List<ModConflictDetector.Conflict> = emptyList()

            runCatching {
                val versionInfo = version.getVersionInfo()
                val mcVersion = versionInfo?.minecraftVersion
                val loader = versionInfo?.loaderInfo?.firstNotNullOfOrNull { ModLoaderUtils.getModLoader(it.name) }
                val modsFolder = File(version.getGameDir(), "mods")

                if (mcVersion != null && loader != null && modsFolder.isDirectory) {
                    if (dependencyEnabled) {
                        dependencyResult = ModDependencyResolver.resolveMissingDependencies(
                            modsFolder, modInfoList, mcVersion, loader
                        )
                    }
                    if (updateCheckEnabled) {
                        updates = ModUpdateChecker.checkForUpdates(modInfoList, mcVersion, loader)
                    }
                }
                if (conflictCheckEnabled) {
                    val modsFolderForConflicts = File(version.getGameDir(), "mods")
                    if (modsFolderForConflicts.isDirectory) {
                        conflicts = ModConflictDetector.detectConflicts(modsFolderForConflicts)
                    }
                }
            }.onFailure { e -> Logging.e("ModAutoMaintenance", "Mod auto-maintenance failed", e) }

            Triple(dependencyResult, updates, conflicts)
        }.ended { result ->
            val safeResult: Triple<ModDependencyResolver.ResolveResult?, List<ModUpdateChecker.UpdateInfo>, List<ModConflictDetector.Conflict>> =
                result ?: Triple(null, emptyList(), emptyList())
            val dependencyResult = safeResult.first
            val updates = safeResult.second
            val conflicts = safeResult.third

            if ((dependencyResult != null && !dependencyResult.isEmpty) || updates.isNotEmpty() || conflicts.isNotEmpty()) {
                TaskExecutors.getAndroidUI().execute {
                    if (dependencyResult != null && !dependencyResult.isEmpty) {
                        showDependencyResultDialog(context, dependencyResult)
                    }
                    if (updates.isNotEmpty()) {
                        showUpdateAvailableDialog(context, updates)
                    }
                    if (conflicts.isNotEmpty()) {
                        showConflictWarningDialog(context, conflicts)
                    }
                }
            }

            // Dialogs above are fire-and-forget/informational — don't block the launch on them.
            // Keep onComplete on this same (background) executor, matching the threading the
            // pre-existing ModChecker step already ran on.
            onComplete.run()
        }.onThrowable {
            onComplete.run()
        }.execute()
    }

    private fun showDependencyResultDialog(context: Context, result: ModDependencyResolver.ResolveResult) {
        val message = buildString {
            if (result.installed.isNotEmpty()) {
                append(context.getString(R.string.dependency_installer_installed_header, result.installed.size))
                result.installed.forEach { append("\n • ").append(it) }
            }
            if (result.failed.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append(context.getString(R.string.dependency_installer_failed_header))
                result.failed.forEach { append("\n • ").append(it) }
            }
        }
        if (message.isBlank()) return

        TipDialog.Builder(context)
            .setTitle(R.string.dependency_installer_dialog_title)
            .setMessage(message)
            .setCenterMessage(false)
            .setSelectable(true)
            .setShowCancel(false)
            .setConfirm(R.string.generic_ok)
            .showDialog()
    }

    private fun showUpdateAvailableDialog(context: Context, updates: List<ModUpdateChecker.UpdateInfo>) {
        val message = android.text.SpannableStringBuilder().apply {
            append(context.getString(R.string.mod_update_dialog_header, updates.size))
            updates.forEach { update ->
                append("\n • ")
                // Highlight each "modName: current → new" entry in green so available
                // updates stand out clearly against the rest of the dialog text.
                val entryStart = length
                append(update.modName).append(": ")
                    .append(update.currentVersion).append(" → ").append(update.newVersionNumber)
                setSpan(
                    android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#4CAF50")),
                    entryStart, length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        TipDialog.Builder(context)
            .setTitle(R.string.mod_update_dialog_title)
            .setMessage(message)
            .setCenterMessage(false)
            .setSelectable(true)
            .setConfirm(R.string.mod_update_dialog_update_now)
            .setCancel(R.string.mod_update_dialog_later)
            .setConfirmClickListener { _ -> applyUpdatesInBackground(context, updates) }
            .showDialog()
    }

    private fun showConflictWarningDialog(context: Context, conflicts: List<ModConflictDetector.Conflict>) {
        val message = android.text.SpannableStringBuilder().apply {
            append(context.getString(R.string.mod_conflict_dialog_header, conflicts.size))
            conflicts.forEach { conflict ->
                append("\n • ")
                val entryStart = length
                append(conflict.targetClass)
                setSpan(
                    android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#FF5252")),
                    entryStart, length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                append(" — ").append(conflict.modNames.joinToString(", "))
            }
        }

        TipDialog.Builder(context)
            .setTitle(R.string.mod_conflict_dialog_title)
            .setMessage(message)
            .setCenterMessage(false)
            .setSelectable(true)
            .setShowCancel(false)
            .setConfirm(R.string.generic_ok)
            .showDialog()
    }

    private fun applyUpdatesInBackground(context: Context, updates: List<ModUpdateChecker.UpdateInfo>) {
        Task.runTask {
            ModUpdateChecker.applyUpdates(updates)
        }.ended(TaskExecutors.getAndroidUI()) { result ->
            val (success, failed) = result ?: Pair(0, updates.size)
            TipDialog.Builder(context)
                .setTitle(R.string.mod_update_dialog_title)
                .setMessage(context.getString(R.string.mod_update_result_message, success, failed))
                .setShowCancel(false)
                .setConfirm(R.string.generic_ok)
                .showDialog()
        }.onThrowable {
            Logging.e("ModAutoMaintenance", "Failed to apply mod updates")
        }.execute()
    }
}
