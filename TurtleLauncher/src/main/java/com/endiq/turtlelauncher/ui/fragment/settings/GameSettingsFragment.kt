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
import com.endiq.turtlelauncher.databinding.SettingsFragmentGameBinding
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.ui.fragment.settings.wrapper.BaseSettingsWrapper
import com.endiq.turtlelauncher.ui.fragment.settings.wrapper.EditTextSettingsWrapper
import com.endiq.turtlelauncher.ui.fragment.settings.wrapper.ListSettingsWrapper
import com.endiq.turtlelauncher.ui.fragment.settings.wrapper.SeekBarSettingsWrapper
import com.endiq.turtlelauncher.ui.fragment.settings.wrapper.SwitchSettingsWrapper


class GameSettingsFragment : AbstractSettingsFragment(R.layout.settings_fragment_game, SettingCategory.GAME) {
    companion object {
        const val TAG: String = "GameSettingsFragment"
    }

    private lateinit var binding: SettingsFragmentGameBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SettingsFragmentGameBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()
        binding.subSettingsBackButton.setOnClickListener { com.endiq.turtlelauncher.utils.ZHTools.onBackPressed(requireActivity()) }

        // Quick Presets (PvP/Survival) removed per explicit request - was here.

        SwitchSettingsWrapper(context, AllSettings.useAngle, binding.useAngleLayout, binding.useAngle)
        com.endiq.turtlelauncher.utils.WarningTooltip.attach(binding.useAngleWarning, getString(R.string.setting_warning_angle))

        // ── ANGLE (always-active hook + plugin) ───────────────────────────────
        // The switch flips the PowerVR hook; the row below it reports which ANGLE copy is in play and
        // installs/updates the plugin. Both are wired straight through AnglePluginManager, which is
        // also what the launch path (AngleLaunchPolicy) reads.
        SwitchSettingsWrapper(context, AllSettings.angleForceOnPowerVr, binding.angleForceLayout, binding.angleForce)
        com.endiq.turtlelauncher.utils.WarningTooltip.attach(
            binding.angleForceWarning, getString(R.string.setting_warning_angle_force)
        )
        setupAnglePluginRow()

        SwitchSettingsWrapper(
            context,
            AllSettings.versionIsolation,
            binding.versionIsolationLayout,
            binding.versionIsolation
        )

        EditTextSettingsWrapper(
            AllSettings.versionCustomInfo,
            binding.versionCustomInfoLayout,
            binding.versionCustomInfoEdittext
        )

        EditTextSettingsWrapper(
            AllSettings.curseForgeApiKey,
            binding.curseForgeApiKeyLayout,
            binding.curseForgeApiKeyEdittext
        )

        SwitchSettingsWrapper(
            context,
            AllSettings.autoSetGameLanguage,
            binding.autoSetGameLanguageLayout,
            binding.autoSetGameLanguage
        )

        SwitchSettingsWrapper(
            context,
            AllSettings.gameLanguageOverridden,
            binding.gameLanguageOverriddenLayout,
            binding.gameLanguageOverridden
        )

        ListSettingsWrapper(
            context,
            AllSettings.setGameLanguage,
            binding.setGameLanguageLayout,
            binding.setGameLanguageTitle,
            binding.setGameLanguageValue,
            R.array.all_game_language, R.array.all_game_language_value
        )

    }

    override fun slideIn(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.root, TurtleTransitions.enter()))
    }

    /**
     * The "ANGLE plugin" row: shows where ANGLE is coming from right now (plugin or the copy built
     * into the launcher) and, on tap, downloads+offers the plugin install - the same flow the launch
     * path uses on a device the hook applies to. Long-press opens the release page instead.
     */
    private fun setupAnglePluginRow() {
        val context = requireContext()
        refreshAnglePluginRow()

        BaseSettingsWrapper(context, binding.anglePluginLayout) {
            if (!com.endiq.turtlelauncher.renderer.angle.AnglePluginManager.isNetworkAvailable(context)) {
                Toast.makeText(context, R.string.setting_angle_plugin_no_network, Toast.LENGTH_LONG).show()
            } else {
                binding.anglePluginAction.text = getString(R.string.setting_angle_plugin_downloading)
                Toast.makeText(context, R.string.setting_angle_plugin_downloading, Toast.LENGTH_SHORT).show()
                com.endiq.turtlelauncher.renderer.angle.AnglePluginManager.downloadAndInstall(context) { success, message ->
                    if (isAdded) {
                        binding.anglePluginAction.text = getString(R.string.setting_angle_plugin_action_install)
                        val text = if (success) getString(R.string.setting_angle_plugin_installed_toast)
                        else getString(R.string.setting_angle_plugin_failed, message)
                        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                        refreshAnglePluginRow()
                    }
                }
            }
        }
        binding.anglePluginLayout.setOnLongClickListener {
            com.endiq.turtlelauncher.renderer.angle.AnglePluginManager.openReleasePage(context)
            true
        }
    }

    private fun refreshAnglePluginRow() {
        val context = requireContext()
        val manager = com.endiq.turtlelauncher.renderer.angle.AnglePluginManager
        val plugin = manager.installedPlugin(context)
        if (plugin != null) {
            val version = plugin.versionName ?: "v${plugin.versionCode}"
            binding.anglePluginSummary.text = getString(
                R.string.setting_angle_plugin_summary_installed, version
            ) + "\n" + getString(R.string.setting_angle_plugin_status_source, manager.sourceLabelCheap(context)) +
                "\n" + getString(R.string.setting_angle_plugin_restart_hint)
            binding.anglePluginAction.text = getString(R.string.setting_angle_plugin_action_update)
        } else {
            binding.anglePluginSummary.text = getString(R.string.setting_angle_plugin_summary_missing)
            binding.anglePluginAction.text = getString(R.string.setting_angle_plugin_action_install)
        }
    }
}