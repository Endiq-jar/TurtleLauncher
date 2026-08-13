package com.movtery.zalithlauncher.ui.fragment.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.movtery.anim.AnimPlayer
import com.movtery.anim.animations.Animations
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.databinding.SettingsFragmentAccessibilityBinding
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.ui.fragment.settings.wrapper.ListSettingsWrapper
import com.movtery.zalithlauncher.ui.fragment.settings.wrapper.SeekBarSettingsWrapper
import com.movtery.zalithlauncher.ui.fragment.settings.wrapper.SwitchSettingsWrapper
import com.movtery.zalithlauncher.utils.ZHTools

/**
 * TurtleLauncher Accessibility Settings (roadmap item 22): larger UI scaling, high-contrast
 * mode, and a font family picker.
 *
 * Scope note: these only affect the launcher's OWN screens (settings, menus, dialogs) - they
 * cannot touch Minecraft's in-game text/GUI, which is drawn natively by the game itself and
 * has no dependency on Android Resources/Configuration at all. That's an inherent boundary of
 * how this launcher (and every Pojav/Zalith-based launcher) works, not something any Android-
 * side setting can reach past.
 *
 * All three settings below change how the launcher's OWN UI is rendered (Configuration
 * fontScale + a theme overlay, both applied in BaseActivity.attachBaseContext()/onCreate() -
 * see AccessibilityHelper). That only takes effect when an Activity is (re)created, so rather
 * than a fragile manual recreate() call, these reuse the app's existing "requires reboot" flow
 * (setRequiresReboot() -> the wrapper's own checkShowRebootDialog() prompts to relaunch) - the
 * same mechanism already used for other settings that need a fresh process (e.g. renderer
 * changes), so the relaunch is guaranteed clean instead of half-applied.
 */
class AccessibilitySettingsFragment : AbstractSettingsFragment(R.layout.settings_fragment_accessibility, SettingCategory.ACCESSIBILITY) {
    companion object {
        const val TAG: String = "AccessibilitySettingsFragment"
    }

    private lateinit var binding: SettingsFragmentAccessibilityBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SettingsFragmentAccessibilityBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()
        binding.subSettingsBackButton.setOnClickListener { ZHTools.onBackPressed(requireActivity()) }

        SwitchSettingsWrapper(context, AllSettings.highContrastMode, binding.highContrastModeLayout, binding.highContrastMode)
            .setRequiresReboot()

        SeekBarSettingsWrapper(
            context,
            AllSettings.fontScale,
            binding.fontScaleLayout,
            binding.fontScaleTitle,
            binding.fontScaleSummary,
            binding.fontScaleValue,
            binding.fontScale,
            "%"
        ).setRequiresReboot()

        ListSettingsWrapper(
            context,
            AllSettings.fontFamily,
            binding.fontFamilyLayout,
            binding.fontFamilyTitle,
            binding.fontFamilyValue,
            R.array.all_font_family, R.array.all_font_family_value
        ).setRequiresReboot()
    }

    override fun slideIn(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.root, Animations.BounceInDown))
    }
}
