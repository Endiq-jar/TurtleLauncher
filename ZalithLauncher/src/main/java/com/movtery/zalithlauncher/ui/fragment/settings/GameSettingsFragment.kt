package com.movtery.zalithlauncher.ui.fragment.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.movtery.anim.AnimPlayer
import com.movtery.anim.animations.Animations
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.databinding.SettingsFragmentGameBinding
import com.movtery.zalithlauncher.feature.preset.PresetManager
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.ui.dialog.TipDialog
import com.movtery.zalithlauncher.ui.fragment.settings.wrapper.EditTextSettingsWrapper
import com.movtery.zalithlauncher.ui.fragment.settings.wrapper.ListSettingsWrapper
import com.movtery.zalithlauncher.ui.fragment.settings.wrapper.SeekBarSettingsWrapper
import com.movtery.zalithlauncher.ui.fragment.settings.wrapper.SwitchSettingsWrapper

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
        binding.subSettingsBackButton.setOnClickListener { com.movtery.zalithlauncher.utils.ZHTools.onBackPressed(requireActivity()) }

        // ── Quick Presets ─────────────────────────────────────────────────────
        fun applyPresetWithConfirm(nameRes: Int, assetFile: String) {
            val presetConfig = PresetManager.loadPreset(context, assetFile)
            val presetName = presetConfig?.name ?: getString(nameRes)
            TipDialog.Builder(context)
                .setTitle(nameRes)
                .setMessage(getString(R.string.preset_apply_confirm, presetName))
                .setConfirm(android.R.string.ok)
                .setConfirmClickListener {
                    if (PresetManager.applyPreset(context, assetFile)) {
                        Toast.makeText(context, R.string.preset_applied, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, R.string.preset_failed, Toast.LENGTH_SHORT).show()
                    }
                }.showDialog()
        }

        binding.presetPvpButton.setOnClickListener {
            applyPresetWithConfirm(R.string.preset_pvp_title, "pvp.json")
        }
        binding.presetSurvivalButton.setOnClickListener {
            applyPresetWithConfirm(R.string.preset_survival_title, "survival.json")
        }

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
        animPlayer.apply(AnimPlayer.Entry(binding.root, Animations.BounceInDown))
    }
}