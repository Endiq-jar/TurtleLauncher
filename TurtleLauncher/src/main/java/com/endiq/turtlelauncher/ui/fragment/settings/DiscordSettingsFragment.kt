package com.endiq.turtlelauncher.ui.fragment.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.databinding.SettingsFragmentDiscordBinding
import com.endiq.turtlelauncher.feature.discord.DiscordAuthManager
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.ui.fragment.DiscordLoginFragment
import com.endiq.turtlelauncher.ui.fragment.FragmentWithAnim
import com.endiq.turtlelauncher.utils.ZHTools

/**
 * See DiscordAuthManager for why "game activity" below is shown but disabled rather
 * than wired to anything - it isn't something a third-party Android app can actually
 * do for a real Discord account through any public, ToS-compliant API.
 */
class DiscordSettingsFragment : FragmentWithAnim(R.layout.settings_fragment_discord) {
    companion object {
        const val TAG: String = "DiscordSettingsFragment"
    }

    private var _binding: SettingsFragmentDiscordBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SettingsFragmentDiscordBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.subSettingsBackButton.setOnClickListener { ZHTools.onBackPressed(requireActivity()) }
        refreshLinkState()

        binding.discordLinkButton.setOnClickListener {
            if (DiscordAuthManager.isLinked()) {
                DiscordAuthManager.unlink()
                refreshLinkState()
            } else {
                ZHTools.swapFragmentWithAnim(this, DiscordLoginFragment::class.java, DiscordLoginFragment.TAG, null)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshLinkState()
    }

    private fun refreshLinkState() {
        if (DiscordAuthManager.isLinked()) {
            binding.discordStatusTitle.text = getString(R.string.discord_connected_as, AllSettings.discordUsername.getValue())
            binding.discordStatusDesc.text = ""
            binding.discordLinkButton.text = getString(R.string.discord_disconnect)
        } else {
            binding.discordStatusTitle.text = getString(R.string.discord_link_account)
            binding.discordStatusDesc.text = getString(R.string.discord_link_account_desc)
            binding.discordLinkButton.text = getString(R.string.discord_link_account)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
