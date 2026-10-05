package com.endiq.turtlelauncher.ui.fragment.settings

import com.endiq.turtlelauncher.utils.anim.TurtleTransitions
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.endiq.anim.AnimPlayer
import com.endiq.anim.animations.Animations
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.databinding.SettingsFragmentExperimentalBinding
import com.endiq.turtlelauncher.feature.pluginupdate.PluginUpdateManager
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.ui.fragment.settings.wrapper.BaseSettingsWrapper
import com.endiq.turtlelauncher.ui.fragment.settings.wrapper.ListSettingsWrapper
import com.endiq.turtlelauncher.ui.fragment.settings.wrapper.SeekBarSettingsWrapper
import com.endiq.turtlelauncher.ui.fragment.settings.wrapper.SwitchSettingsWrapper


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
        binding.subSettingsBackButton.setOnClickListener { com.endiq.turtlelauncher.utils.ZHTools.onBackPressed(requireActivity()) }

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

        ListSettingsWrapper(
            context, AllSettings.lwjglCompatMode,
            binding.lwjglCompatModeLayout, binding.lwjglCompatModeTitle, binding.lwjglCompatModeValue,
            R.array.lwjgl_compat_mode_names, R.array.lwjgl_compat_mode_values
        )

        SwitchSettingsWrapper(
            context,
            AllSettings.backgroundServiceOptimization,
            binding.backgroundServiceOptimizationLayout,
            binding.backgroundServiceOptimization
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
            val ok = com.endiq.turtlelauncher.feature.turtle.SettingsBackupManager.exportToFile(destFile)
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
            val ok = com.endiq.turtlelauncher.feature.turtle.SettingsBackupManager.importFromFile(srcFile)
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
            val history = com.endiq.turtlelauncher.feature.log.CrashAnalyzer.getCrashHistory()
            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            val message = if (history.isEmpty()) {
                getString(R.string.crash_diagnostics_dialog_empty)
            } else {
                history.joinToString("\n\n") { entry ->
                    "${formatter.format(java.util.Date(entry.timestampMs))} (exit ${entry.exitCode})\n${entry.summary}"
                }
            }
            com.endiq.turtlelauncher.ui.dialog.TipDialog.Builder(context)
                .setTitle(R.string.setting_crash_history_title)
                .setMessage(message)
                .setCenterMessage(false)
                .setSelectable(true)
                .setShowCancel(history.isNotEmpty())
                .setCancel(R.string.generic_clear)
                .setCancelClickListener { com.endiq.turtlelauncher.feature.log.CrashAnalyzer.clearCrashHistory() }
                .setConfirm(R.string.generic_ok)
                .showDialog()
        }

        BaseSettingsWrapper(context, binding.crashRuleEditorLayout) {
            showCrashRuleEditor(context)
        }

        SwitchSettingsWrapper(
            context,
            com.endiq.turtlelauncher.setting.AllSettings.aiCrashHelpEnabled,
            binding.aiCrashHelpLayout,
            binding.aiCrashHelp
        )

        BaseSettingsWrapper(context, binding.aiCrashHelpApiKeyLayout) {
            promptSetAiCrashHelpApiKey(context)
        }

        SwitchSettingsWrapper(
            context,
            AllSettings.aiSkinFilterEnabled,
            binding.aiSkinFilterLayout,
            binding.aiSkinFilter
        )

        // Turtle AI: language, optional cloud brain, optional web search. See
        // feature/ai/TurtleAiLanguage.kt, TurtleAiGemini.kt and TurtleAiWebSearch.kt.
        BaseSettingsWrapper(context, binding.aiLanguageLayout) {
            promptSetAssistantLanguage(context)
        }

        SwitchSettingsWrapper(
            context,
            AllSettings.aiAssistantCloudBrain,
            binding.aiBrainLayout,
            binding.aiBrain
        )

        BaseSettingsWrapper(context, binding.aiModelLayout) {
            promptSetAiModels(context)
        }

        BaseSettingsWrapper(context, binding.aiVoiceLayout) {
            promptSetAiVoice(context)
        }

        SwitchSettingsWrapper(
            context,
            AllSettings.aiWebSearchEnabled,
            binding.aiWebSearchLayout,
            binding.aiWebSearch
        )

        BaseSettingsWrapper(context, binding.aiSearchProviderLayout) {
            promptSetSearchProvider(context)
        }

        BaseSettingsWrapper(context, binding.aiSearchUrlLayout) {
            promptSetSearchUrl(context)
        }

        BaseSettingsWrapper(context, binding.aiSearchKeyLayout) {
            promptSetSearchApiKey(context)
        }

        BaseSettingsWrapper(context, binding.dependencyGraphLayout) {
            val version = com.endiq.turtlelauncher.feature.version.VersionsManager.getCurrentVersion()
            if (version == null) {
                Toast.makeText(context, R.string.dependency_graph_no_version, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, R.string.setting_dependency_graph_title, Toast.LENGTH_SHORT).show()
                Thread {
                    val graphText = com.endiq.turtlelauncher.feature.turtle.DependencyGraphExporter.buildTextTree(version)
                    com.endiq.turtlelauncher.task.TaskExecutors.runInUIThread {
                        com.endiq.turtlelauncher.ui.dialog.TipDialog.Builder(context)
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
            val version = com.endiq.turtlelauncher.feature.version.VersionsManager.getCurrentVersion()
            if (version == null) {
                Toast.makeText(context, R.string.dependency_graph_no_version, Toast.LENGTH_SHORT).show()
            } else {
                val intent = android.content.Intent(context, com.endiq.turtlelauncher.ui.activity.ConfigEditorActivity::class.java)
                intent.putExtra(com.endiq.turtlelauncher.ui.activity.ConfigEditorActivity.EXTRA_VERSION_NAME, version.getVersionName())
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
            val snapshot = com.endiq.turtlelauncher.feature.log.CrashAnalyzer.getTelemetrySnapshot(context)
            val message = if (snapshot.isEmpty()) {
                getString(R.string.crash_diagnostics_dialog_empty)
            } else {
                snapshot.joinToString("\n") { (title, count) -> "$title: $count" }
            }
            com.endiq.turtlelauncher.ui.dialog.TipDialog.Builder(context)
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
        val current = runCatching { AllSettings.aiGeminiApiKey.getValue() }.getOrDefault("")
        val builtIn = com.endiq.turtlelauncher.feature.ai.TurtleAiGemini.hasBuildKey()
        com.endiq.turtlelauncher.ui.dialog.EditTextDialog.Builder(context)
            .setTitle(R.string.setting_ai_key_title)
            .setHintText(
                if (builtIn) R.string.setting_ai_key_desc_builtin
                else R.string.setting_ai_key_desc
            )
            .setEditText(current)
            .setInputType(
                android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            )
            .setConfirmListener { editText, _ ->
                AllSettings.aiGeminiApiKey.put(editText.text.toString().trim()).save()
                Toast.makeText(context, R.string.generic_ok, Toast.LENGTH_SHORT).show()
                true
            }
            .showDialog()
    }

    /**
     * First screen of the model settings: one row per job, each showing what it uses now.
     * Ten separate rows in the Settings list would be a wall of text, so the jobs live here
     * instead.
     */
    private fun promptSetAiModels(context: android.content.Context) {
        val tasks = com.endiq.turtlelauncher.feature.ai.TurtleAiModels.Task.values()
        val labels = tasks.map { task ->
            task.settingLabel + "\n" + com.endiq.turtlelauncher.feature.ai.TurtleAiModels.summary(task)
        }.toTypedArray()
        android.app.AlertDialog.Builder(context)
            .setTitle(R.string.setting_ai_model_title)
            .setItems(labels) { _, which -> promptSetAiTaskModel(context, tasks[which]) }
            .setNeutralButton(R.string.setting_ai_models_help) { _, _ ->
                com.endiq.turtlelauncher.ui.dialog.TipDialog.Builder(context)
                    .setTitle(R.string.setting_ai_model_title)
                    .setMessage(R.string.setting_ai_models_help_text)
                    .setCenterMessage(false)
                    .setSelectable(true)
                    .setShowCancel(false)
                    .setConfirm(R.string.generic_ok)
                    .showDialog()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * The model picker for one job. The list is the TurtleAI catalogue (the names the user
     * knows), with the live list from the API appended for the configured key - model names
     * change far faster than app releases, and a hand-typed id is still possible.
     */
    private fun promptSetAiTaskModel(
        context: android.content.Context,
        task: com.endiq.turtlelauncher.feature.ai.TurtleAiModels.Task
    ) {
        val auto = com.endiq.turtlelauncher.feature.ai.TurtleAiModels.AUTO
        val current = runCatching {
            com.endiq.turtlelauncher.feature.ai.TurtleAiModels.settingFor(task).getValue()
        }.getOrDefault(auto).ifBlank { auto }

        val entries = mutableListOf<Pair<String, String>>()
        entries.addAll(com.endiq.turtlelauncher.feature.ai.TurtleAiModels.pickerEntries(task))
        val fetched = runCatching { com.endiq.turtlelauncher.feature.ai.TurtleAiGemini.listModels() }
            .getOrDefault(emptyList())
        fetched.filter { id -> entries.none { it.first == id } }
            .forEach { id -> entries.add(id to id + " (from Google)") }
        if (entries.none { it.first == current }) {
            entries.add(current to current + " (current)")
        }

        val labels = entries.map { it.second }.toTypedArray()
        val checked = entries.indexOfFirst { it.first == current }.coerceAtLeast(0)
        android.app.AlertDialog.Builder(context)
            .setTitle(task.settingLabel)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                com.endiq.turtlelauncher.feature.ai.TurtleAiModels.settingFor(task)
                    .put(entries[which].first).save()
                dialog.dismiss()
            }
            .setNeutralButton(R.string.setting_ai_model_type) { _, _ ->
                promptSetAiTaskModelManual(context, task, current)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Free-text entry, for a model id the catalogue and the API listing do not know about. */
    private fun promptSetAiTaskModelManual(
        context: android.content.Context,
        task: com.endiq.turtlelauncher.feature.ai.TurtleAiModels.Task,
        current: String
    ) {
        com.endiq.turtlelauncher.ui.dialog.EditTextDialog.Builder(context)
            .setTitle(task.settingLabel)
            .setHintText(R.string.setting_ai_model_manual_hint)
            .setEditText(current)
            .setInputType(android.text.InputType.TYPE_CLASS_TEXT)
            .setConfirmListener { editText, _ ->
                com.endiq.turtlelauncher.feature.ai.TurtleAiModels.settingFor(task)
                    .put(editText.text.toString().trim()).save()
                Toast.makeText(context, R.string.generic_ok, Toast.LENGTH_SHORT).show()
                true
            }
            .showDialog()
    }

    /** The voice used by /speak and the live conversation. */
    private fun promptSetAiVoice(context: android.content.Context) {
        val voices = com.endiq.turtlelauncher.feature.ai.TurtleAiGemini.VOICES
        val current = runCatching { AllSettings.aiVoice.getValue() }
            .getOrDefault(com.endiq.turtlelauncher.feature.ai.TurtleAiGemini.DEFAULT_VOICE)
            .ifBlank { com.endiq.turtlelauncher.feature.ai.TurtleAiGemini.DEFAULT_VOICE }
        val entries = voices.toMutableList()
        if (entries.none { it == current }) entries.add(current)
        val labels = entries.toTypedArray()
        val checked = entries.indexOfFirst { it == current }.coerceAtLeast(0)
        android.app.AlertDialog.Builder(context)
            .setTitle(R.string.setting_ai_voice_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                AllSettings.aiVoice.put(entries[which]).save()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Language picker for the Assistant's answers (see TurtleAiLanguage). */
    private fun promptSetAssistantLanguage(context: android.content.Context) {
        val entries = com.endiq.turtlelauncher.feature.ai.TurtleAiLanguage.pickerEntries()
        val labels = entries.map { it.second }.toTypedArray()
        val current = runCatching { AllSettings.aiLanguage.getValue() }
            .getOrDefault(com.endiq.turtlelauncher.feature.ai.TurtleAiLanguage.AUTO)
        val checked = entries.indexOfFirst { it.first == current }.coerceAtLeast(0)
        android.app.AlertDialog.Builder(context)
            .setTitle(R.string.setting_ai_language_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                AllSettings.aiLanguage.put(entries[which].first).save()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Search provider picker; "custom" is what makes the URL/key rows below matter. */
    private fun promptSetSearchProvider(context: android.content.Context) {
        val entries = com.endiq.turtlelauncher.feature.ai.TurtleAiWebSearch.PICKER
        val labels = entries.map { it.second }.toTypedArray()
        val current = runCatching { AllSettings.aiSearchProvider.getValue() }
            .getOrDefault(com.endiq.turtlelauncher.feature.ai.TurtleAiWebSearch.PROVIDER_AUTO)
        val checked = entries.indexOfFirst { it.first == current }.coerceAtLeast(0)
        android.app.AlertDialog.Builder(context)
            .setTitle(R.string.setting_ai_search_provider_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                AllSettings.aiSearchProvider.put(entries[which].first).save()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun promptSetSearchUrl(context: android.content.Context) {
        val current = runCatching { AllSettings.aiSearchUrl.getValue() }.getOrDefault("")
        com.endiq.turtlelauncher.ui.dialog.EditTextDialog.Builder(context)
            .setTitle(R.string.setting_ai_search_url_title)
            .setHintText(R.string.setting_ai_search_url_desc)
            .setEditText(current)
            .setInputType(android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI)
            .setConfirmListener { editText, _ ->
                AllSettings.aiSearchUrl.put(editText.text.toString().trim()).save()
                Toast.makeText(context, R.string.generic_ok, Toast.LENGTH_SHORT).show()
                true
            }
            .showDialog()
    }

    private fun promptSetSearchApiKey(context: android.content.Context) {
        val current = runCatching { AllSettings.aiSearchApiKey.getValue() }.getOrDefault("")
        com.endiq.turtlelauncher.ui.dialog.EditTextDialog.Builder(context)
            .setTitle(R.string.setting_ai_search_key_title)
            .setHintText(R.string.setting_ai_search_key_desc)
            .setEditText(current)
            .setInputType(android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)
            .setConfirmListener { editText, _ ->
                AllSettings.aiSearchApiKey.put(editText.text.toString().trim()).save()
                Toast.makeText(context, R.string.generic_ok, Toast.LENGTH_SHORT).show()
                true
            }
            .showDialog()
    }

    private fun showCrashRuleEditor(context: android.content.Context) {
        val rules = com.endiq.turtlelauncher.feature.log.CrashAnalyzer.getCustomRules()
        val message = if (rules.isEmpty()) {
            getString(R.string.crash_rule_editor_empty)
        } else {
            rules.mapIndexed { i, r -> "${i + 1}. ${r.pattern} → ${r.tip}" }.joinToString("\n")
        }
        com.endiq.turtlelauncher.ui.dialog.TipDialog.Builder(context)
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
        com.endiq.turtlelauncher.ui.dialog.EditTextDialog.Builder(context)
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
        com.endiq.turtlelauncher.ui.dialog.EditTextDialog.Builder(context)
            .setTitle(R.string.setting_crash_rule_editor_title)
            .setHintText(R.string.crash_rule_editor_tip_hint)
            .setAsRequired()
            .setConfirmListener { editText, _ ->
                com.endiq.turtlelauncher.feature.log.CrashAnalyzer.addCustomRule(pattern, editText.text.toString())
                Toast.makeText(context, R.string.generic_ok, Toast.LENGTH_SHORT).show()
                showCrashRuleEditor(context)
                true
            }
            .showDialog()
    }

    override fun slideIn(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.root, TurtleTransitions.enter()))
    }
}
