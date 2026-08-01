package com.movtery.zalithlauncher.ui.fragment.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.movtery.anim.AnimPlayer
import com.movtery.anim.animations.Animations
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.databinding.SettingsFragmentExperimentalBinding
import com.movtery.zalithlauncher.feature.pluginupdate.PluginUpdateManager
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.ui.fragment.settings.wrapper.BaseSettingsWrapper
import com.movtery.zalithlauncher.ui.fragment.settings.wrapper.SeekBarSettingsWrapper
import com.movtery.zalithlauncher.ui.fragment.settings.wrapper.SwitchSettingsWrapper

class ExperimentalSettingsFragment :
    AbstractSettingsFragment(R.layout.settings_fragment_experimental, SettingCategory.EXPERIMENTAL) {
    companion object {
        const val TAG: String = "ExperimentalSettingsFragment"
    }

    private lateinit var binding: SettingsFragmentExperimentalBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SettingsFragmentExperimentalBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()
        binding.subSettingsBackButton.setOnClickListener { com.movtery.zalithlauncher.utils.ZHTools.onBackPressed(requireActivity()) }

        SwitchSettingsWrapper(
            context,
            AllSettings.dumpShaders,
            binding.dumpShadersLayout,
            binding.dumpShaders
        )

        SwitchSettingsWrapper(
            context,
            AllSettings.bigCoreAffinity,
            binding.bigCoreAffinityLayout,
            binding.bigCoreAffinity
        )

        SwitchSettingsWrapper(
            context,
            AllSettings.fastBoot,
            binding.fastBootLayout,
            binding.fastBoot
        )

        SwitchSettingsWrapper(
            context,
            AllSettings.modConflictDetection,
            binding.modConflictDetectionLayout,
            binding.modConflictDetection
        )

        SeekBarSettingsWrapper(
            context,
            AllSettings.tcVibrateDuration,
            binding.tcVibrateDurationLayout,
            binding.tcVibrateDurationTitle,
            binding.tcVibrateDurationSummary,
            binding.tcVibrateDurationValue,
            binding.tcVibrateDuration,
            "ms"
        )

        // ── TurtleLauncher FPS Boost Settings ─────────────────────────────────
        SwitchSettingsWrapper(
            context,
            AllSettings.unlimitedFps,
            binding.unlimitedFpsLayout,
            binding.unlimitedFps
        )

        SwitchSettingsWrapper(
            context,
            AllSettings.lowLatencyRendering,
            binding.lowLatencyRenderingLayout,
            binding.lowLatencyRendering
        )

        SwitchSettingsWrapper(
            context,
            AllSettings.framePacing,
            binding.framePacingLayout,
            binding.framePacing
        )

        SwitchSettingsWrapper(
            context,
            AllSettings.frameSkipping,
            binding.frameSkippingLayout,
            binding.frameSkipping
        )

        SwitchSettingsWrapper(
            context,
            AllSettings.adaptiveFrameTiming,
            binding.adaptiveFrameTimingLayout,
            binding.adaptiveFrameTiming
        )

        // ── TurtleLauncher In-Game HUD Modules ────────────────────────────────
        SwitchSettingsWrapper(context, AllSettings.showCpsHud, binding.showCpsHudLayout, binding.showCpsHud)
        SwitchSettingsWrapper(context, AllSettings.showKeystrokesHud, binding.showKeystrokesHudLayout, binding.showKeystrokesHud)
        SwitchSettingsWrapper(context, AllSettings.showMousestrokesHud, binding.showMousestrokesHudLayout, binding.showMousestrokesHud)
        SwitchSettingsWrapper(context, AllSettings.showStopwatchHud, binding.showStopwatchHudLayout, binding.showStopwatchHud)
        SwitchSettingsWrapper(context, AllSettings.showPlaytimeHud, binding.showPlaytimeHudLayout, binding.showPlaytimeHud)
        SwitchSettingsWrapper(context, AllSettings.showSystemResourcesHud, binding.showSystemResourcesHudLayout, binding.showSystemResourcesHud)
        SwitchSettingsWrapper(context, AllSettings.showTimeHud, binding.showTimeHudLayout, binding.showTimeHud)
        SwitchSettingsWrapper(context, AllSettings.showRamGraphHud, binding.showRamGraphHudLayout, binding.showRamGraphHud)
        SwitchSettingsWrapper(context, AllSettings.showPingHud, binding.showPingHudLayout, binding.showPingHud)
        SwitchSettingsWrapper(context, AllSettings.showScreenshotButtonHud, binding.showScreenshotButtonHudLayout, binding.showScreenshotButtonHud)
        SwitchSettingsWrapper(context, AllSettings.hudModuleIndependentDrag, binding.hudModuleIndependentDragLayout, binding.hudModuleIndependentDrag)
        SeekBarSettingsWrapper(
            context,
            AllSettings.hudModuleScale,
            binding.hudModuleScaleLayout,
            binding.hudModuleScaleTitle,
            binding.hudModuleScaleSummary,
            binding.hudModuleScaleValue,
            binding.hudModuleScale,
            suffix = "%"
        )

        // NOTE (TurtleLauncher): a duplicate "Client Settings" section mirroring these same
        // ten HUD switches into a second location on this screen was attempted here, but only
        // the Kotlin wiring was written — the ~20 matching view IDs (clientShowCpsHudLayout,
        // clientShowCpsHud, etc.) were never added to settings_fragment_experimental.xml, so
        // this was failing to compile. Removed rather than guessed-at, since hand-authoring
        // that many new XML blocks without being able to see the actual screen risks a visual
        // mismatch with the rest of it. The HUD toggles themselves are fully intact and
        // functional via their original switches below (showCpsHud etc.) - only the duplicate
        // second copy is gone. Ask for the XML section to be built out properly if you still
        // want them reachable from two places.

        // PvP overlay preset: one toggle that flips CPS + Keystrokes + Mousestrokes together.
        // It mirrors its own state from those three on load (checked only if all three are
        // already on) and, when tapped, pushes the new state down to all three switches.
        binding.pvpOverlayPreset.isChecked = AllSettings.showCpsHud.getValue() &&
            AllSettings.showKeystrokesHud.getValue() && AllSettings.showMousestrokesHud.getValue()
        binding.pvpOverlayPresetLayout.setOnClickListener {
            binding.pvpOverlayPreset.isChecked = !binding.pvpOverlayPreset.isChecked
        }
        binding.pvpOverlayPreset.setOnCheckedChangeListener { _, isChecked ->
            AllSettings.pvpOverlayPreset.put(isChecked).save()
            AllSettings.showCpsHud.put(isChecked).save()
            AllSettings.showKeystrokesHud.put(isChecked).save()
            AllSettings.showMousestrokesHud.put(isChecked).save()
            binding.showCpsHud.isChecked = isChecked
            binding.showKeystrokesHud.isChecked = isChecked
            binding.showMousestrokesHud.isChecked = isChecked
        }

        SeekBarSettingsWrapper(
            context,
            AllSettings.hudAlpha,
            binding.hudAlphaLayout,
            binding.hudAlphaTitle,
            binding.hudAlphaSummary,
            binding.hudAlphaValue,
            binding.hudAlpha,
            "%"
        )

        // ── TurtleLauncher Renderer/Driver Plugin Updater ─────────────────────
        SwitchSettingsWrapper(
            context,
            AllSettings.autoCheckPluginUpdates,
            binding.autoCheckPluginUpdatesLayout,
            binding.autoCheckPluginUpdates
        )

        // ── Advanced Tools ──────────────────────────────────────────────────
        SwitchSettingsWrapper(context, AllSettings.offlineModeFallback, binding.offlineModeFallbackLayout, binding.offlineModeFallback)
        SwitchSettingsWrapper(context, AllSettings.anrDetectorEnabled, binding.anrDetectorEnabledLayout, binding.anrDetectorEnabled)

        BaseSettingsWrapper(context, binding.exportSettingsLayout) {
            val destFile = java.io.File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                "TurtleLauncher_settings_backup.json"
            )
            val ok = com.movtery.zalithlauncher.feature.turtle.SettingsBackupManager.exportToFile(destFile)
            Toast.makeText(
                context,
                getString(if (ok) R.string.export_settings_success else R.string.export_settings_failure),
                Toast.LENGTH_LONG
            ).show()
        }

        BaseSettingsWrapper(context, binding.importSettingsLayout) {
            val srcFile = java.io.File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                "TurtleLauncher_settings_backup.json"
            )
            val ok = com.movtery.zalithlauncher.feature.turtle.SettingsBackupManager.importFromFile(srcFile)
            Toast.makeText(
                context,
                getString(if (ok) R.string.import_settings_success else R.string.import_settings_failure),
                Toast.LENGTH_LONG
            ).show()
            if (ok) {
                // Settings were replaced under this fragment's feet — reload it so every
                // switch/seekbar reflects the freshly imported values instead of stale UI state.
                parentFragmentManager.beginTransaction().detach(this@ExperimentalSettingsFragment).attach(this@ExperimentalSettingsFragment).commit()
            }
        }

        BaseSettingsWrapper(context, binding.crashHistoryLayout) {
            val history = com.movtery.zalithlauncher.feature.log.CrashAnalyzer.getCrashHistory()
            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            val message = if (history.isEmpty()) {
                getString(R.string.crash_diagnostics_dialog_empty)
            } else {
                history.joinToString("\n\n") { entry ->
                    "${formatter.format(java.util.Date(entry.timestampMs))} (exit ${entry.exitCode})\n${entry.summary}"
                }
            }
            com.movtery.zalithlauncher.ui.dialog.TipDialog.Builder(context)
                .setTitle(R.string.setting_crash_history_title)
                .setMessage(message)
                .setCenterMessage(false)
                .setSelectable(true)
                .setShowCancel(history.isNotEmpty())
                .setCancel(R.string.generic_clear)
                .setCancelClickListener { com.movtery.zalithlauncher.feature.log.CrashAnalyzer.clearCrashHistory() }
                .setConfirm(R.string.generic_ok)
                .showDialog()
        }

        BaseSettingsWrapper(context, binding.crashRuleEditorLayout) {
            showCrashRuleEditor(context)
        }

        SwitchSettingsWrapper(
            context,
            com.movtery.zalithlauncher.setting.AllSettings.aiCrashHelpEnabled,
            binding.aiCrashHelpLayout,
            binding.aiCrashHelp
        )

        BaseSettingsWrapper(context, binding.aiCrashHelpApiKeyLayout) {
            promptSetAiCrashHelpApiKey(context)
        }

        BaseSettingsWrapper(context, binding.dependencyGraphLayout) {
            val version = com.movtery.zalithlauncher.feature.version.VersionsManager.getCurrentVersion()
            if (version == null) {
                Toast.makeText(context, R.string.dependency_graph_no_version, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, R.string.setting_dependency_graph_title, Toast.LENGTH_SHORT).show()
                Thread {
                    val graphText = com.movtery.zalithlauncher.feature.turtle.DependencyGraphExporter.buildTextTree(version)
                    com.movtery.zalithlauncher.task.TaskExecutors.runInUIThread {
                        com.movtery.zalithlauncher.ui.dialog.TipDialog.Builder(context)
                            .setTitle(R.string.setting_dependency_graph_title)
                            .setMessage(graphText)
                            .setCenterMessage(false)
                            .setSelectable(true)
                            .setShowCancel(false)
                            .setConfirm(R.string.generic_ok)
                            .showDialog()
                    }
                }.start()
            }
        }

        BaseSettingsWrapper(context, binding.configEditorLayout) {
            val version = com.movtery.zalithlauncher.feature.version.VersionsManager.getCurrentVersion()
            if (version == null) {
                Toast.makeText(context, R.string.dependency_graph_no_version, Toast.LENGTH_SHORT).show()
            } else {
                val intent = android.content.Intent(context, com.movtery.zalithlauncher.ui.activity.ConfigEditorActivity::class.java)
                intent.putExtra(com.movtery.zalithlauncher.ui.activity.ConfigEditorActivity.EXTRA_VERSION_NAME, version.getVersionName())
                startActivity(intent)
            }
        }

        BaseSettingsWrapper(
            context,
            binding.checkPluginUpdatesLayout
        ) {
            Toast.makeText(context, getString(R.string.setting_plugin_update_checking), Toast.LENGTH_SHORT).show()
            PluginUpdateManager.checkForUpdates(context, force = true) { updates, error ->
                when {
                    error != null -> Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                    updates.isEmpty() -> Toast.makeText(context, getString(R.string.setting_plugin_update_none), Toast.LENGTH_SHORT).show()
                    else -> {
                        // Install everything that changed; each plugin is independent so a
                        // partial failure on one shouldn't block the others.
                        updates.forEach { asset ->
                            PluginUpdateManager.downloadAndInstall(context, asset) { success, message ->
                                Toast.makeText(context, message, if (success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }

        BaseSettingsWrapper(
            context,
            binding.crashDiagnosticsLayout
        ) {
            val snapshot = com.movtery.zalithlauncher.feature.log.CrashAnalyzer.getTelemetrySnapshot(context)
            val message = if (snapshot.isEmpty()) {
                getString(R.string.crash_diagnostics_dialog_empty)
            } else {
                snapshot.joinToString("\n") { (title, count) -> "$title: $count" }
            }
            com.movtery.zalithlauncher.ui.dialog.TipDialog.Builder(context)
                .setTitle(R.string.crash_diagnostics_dialog_title)
                .setMessage(message)
                .setCenterMessage(false)
                .setSelectable(true)
                .setShowCancel(false)
                .setConfirm(R.string.generic_ok)
                .showDialog()
        }
    }

    private fun promptSetAiCrashHelpApiKey(context: android.content.Context) {
        val current = runCatching { com.movtery.zalithlauncher.setting.AllSettings.aiCrashHelpApiKey.getValue() }.getOrDefault("")
        com.movtery.zalithlauncher.ui.dialog.EditTextDialog.Builder(context)
            .setTitle(R.string.setting_ai_crash_help_key_title)
            .setHintText(R.string.setting_ai_crash_help_key_desc)
            .setEditText(current)
            .setInputType(android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)
            .setConfirmListener { editText, _ ->
                com.movtery.zalithlauncher.setting.AllSettings.aiCrashHelpApiKey.put(editText.text.toString().trim()).save()
                Toast.makeText(context, R.string.generic_ok, Toast.LENGTH_SHORT).show()
                true
            }
            .showDialog()
    }

    private fun showCrashRuleEditor(context: android.content.Context) {
        val rules = com.movtery.zalithlauncher.feature.log.CrashAnalyzer.getCustomRules()
        val message = if (rules.isEmpty()) {
            getString(R.string.crash_rule_editor_empty)
        } else {
            rules.mapIndexed { i, r -> "${i + 1}. ${r.pattern} → ${r.tip}" }.joinToString("\n")
        }
        com.movtery.zalithlauncher.ui.dialog.TipDialog.Builder(context)
            .setTitle(R.string.setting_crash_rule_editor_title)
            .setMessage(message)
            .setCenterMessage(false)
            .setSelectable(true)
            .setShowCancel(true)
            .setCancel(R.string.generic_add)
            .setCancelClickListener { promptAddCrashRule(context) }
            .setConfirm(R.string.generic_ok)
            .showDialog()
    }

    private fun promptAddCrashRule(context: android.content.Context) {
        com.movtery.zalithlauncher.ui.dialog.EditTextDialog.Builder(context)
            .setTitle(R.string.setting_crash_rule_editor_title)
            .setHintText(R.string.crash_rule_editor_pattern_hint)
            .setAsRequired()
            .setConfirmListener { editText, _ ->
                promptAddCrashRuleTip(context, editText.text.toString())
                true
            }
            .showDialog()
    }

    private fun promptAddCrashRuleTip(context: android.content.Context, pattern: String) {
        com.movtery.zalithlauncher.ui.dialog.EditTextDialog.Builder(context)
            .setTitle(R.string.setting_crash_rule_editor_title)
            .setHintText(R.string.crash_rule_editor_tip_hint)
            .setAsRequired()
            .setConfirmListener { editText, _ ->
                com.movtery.zalithlauncher.feature.log.CrashAnalyzer.addCustomRule(pattern, editText.text.toString())
                Toast.makeText(context, R.string.generic_ok, Toast.LENGTH_SHORT).show()
                showCrashRuleEditor(context)
                true
            }
            .showDialog()
    }

    override fun slideIn(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.root, Animations.BounceInDown))
    }
}
