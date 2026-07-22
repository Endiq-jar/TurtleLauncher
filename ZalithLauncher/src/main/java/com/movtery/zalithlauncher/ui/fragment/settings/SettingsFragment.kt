package com.movtery.zalithlauncher.ui.fragment.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.movtery.anim.AnimPlayer
import com.movtery.anim.animations.Animations
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.databinding.FragmentSettingsBinding
import com.movtery.zalithlauncher.setting.Settings
import com.movtery.zalithlauncher.ui.fragment.AboutFragment
import com.movtery.zalithlauncher.ui.fragment.AccountFragment
import com.movtery.zalithlauncher.ui.fragment.FragmentWithAnim
import com.movtery.zalithlauncher.utils.ZHTools

/**
 * TurtleLauncher: rebuilt as a single scrollable, grouped list (General / Performance / Other)
 * that drills into each settings category as its own full-screen destination, replacing the
 * previous vertical-tab + ViewPager2 layout. Every existing settings category fragment is kept
 * and reused unchanged — this only replaces how you get to them, not what's inside them.
 */
class SettingsFragment : FragmentWithAnim(R.layout.fragment_settings) {
    companion object {
        const val TAG: String = "SettingsFragment"
    }

    private lateinit var binding: FragmentSettingsBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSettingsBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.backButton.setOnClickListener { ZHTools.onBackPressed(requireActivity()) }

        binding.launcherSettingsRow.setOnClickListener {
            ZHTools.swapFragmentWithAnim(this, LauncherSettingsFragment::class.java, LauncherSettingsFragment.TAG, null)
        }
        binding.accountSettingsRow.setOnClickListener {
            ZHTools.swapFragmentWithAnim(this, AccountFragment::class.java, AccountFragment.TAG, null)
        }
        binding.videoSettingsRow.setOnClickListener {
            ZHTools.swapFragmentWithAnim(this, VideoSettingsFragment::class.java, VideoSettingsFragment.TAG, null)
        }
        binding.gameSettingsRow.setOnClickListener {
            ZHTools.swapFragmentWithAnim(this, GameSettingsFragment::class.java, GameSettingsFragment.TAG, null)
        }
        binding.controlsSettingsRow.setOnClickListener {
            ZHTools.swapFragmentWithAnim(this, ControlSettingsFragment::class.java, ControlSettingsFragment.TAG, null)
        }
        binding.experimentalSettingsRow.setOnClickListener {
            ZHTools.swapFragmentWithAnim(this, ExperimentalSettingsFragment::class.java, ExperimentalSettingsFragment.TAG, null)
        }
        binding.aboutSettingsRow.setOnClickListener {
            ZHTools.swapFragmentWithAnim(this, AboutFragment::class.java, AboutFragment.TAG, null)
        }
    }

    override fun onResume() {
        super.onResume()
        Settings.refreshSettings()
    }

    override fun slideIn(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.settingsLayout, Animations.BounceInRight))
    }

    override fun slideOut(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.settingsLayout, Animations.FadeOutLeft))
    }
}
