package com.endiq.turtlelauncher.ui.fragment.settings

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.databinding.SettingsFragmentDiscordBinding
import com.endiq.turtlelauncher.feature.discord.DiscordAuthManager
import com.endiq.turtlelauncher.feature.discord.DiscordRpcManager
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.ui.dialog.EditTextDialog
import com.endiq.turtlelauncher.ui.fragment.DiscordLoginFragment
import com.endiq.turtlelauncher.ui.fragment.FragmentWithAnim
import com.endiq.turtlelauncher.utils.ZHTools

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
        refreshRpcState()

        binding.discordLinkButton.setOnClickListener {
            if (DiscordAuthManager.isLinked()) {
                DiscordAuthManager.unlink()
                refreshLinkState()
            } else {
                ZHTools.swapFragmentWithAnim(this, DiscordLoginFragment::class.java, DiscordLoginFragment.TAG, null)
            }
        }

        binding.discordActivitySwitch.setOnCheckedChangeListener { _, checked ->
            if (checked && !DiscordRpcManager.isConfigured()) {
                binding.discordActivitySwitch.isChecked = false
                showRpcWarning()
            } else {
                AllSettings.discordRpcEnabled.put(checked).save()
                refreshRpcState()
            }
        }
        binding.discordRpcConfigureButton.setOnClickListener {
            if (DiscordRpcManager.isConfigured()) showConfiguredMenu() else showRpcWarning()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshLinkState()
        refreshRpcState()
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

    private fun refreshRpcState() {
        if (_binding == null) return
        val configured = DiscordRpcManager.isConfigured()
        binding.discordActivitySwitch.setOnCheckedChangeListener(null)
        binding.discordActivitySwitch.isEnabled = configured
        binding.discordActivitySwitch.isChecked = configured && AllSettings.discordRpcEnabled.getValue()
        binding.discordActivitySwitch.setOnCheckedChangeListener { _, checked ->
            AllSettings.discordRpcEnabled.put(checked).save()
            refreshRpcState()
        }
        binding.discordActivityDescription.setText(
            if (configured) R.string.discord_rpc_configured else R.string.discord_rpc_not_configured
        )
        binding.discordRpcConfigureButton.setText(
            if (configured) R.string.generic_edit else R.string.discord_rpc_configure
        )
    }

    private fun showRpcWarning() {
        AlertDialog.Builder(requireContext(), R.style.CustomAlertDialogTheme)
            .setTitle(R.string.generic_warning)
            .setMessage(R.string.discord_rpc_token_warning)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.generic_confirm) { _, _ -> showTokenEditor() }
            .show()
    }

    private fun showTokenEditor() {
        EditTextDialog.Builder(requireContext())
            .setTitle(R.string.discord_rpc_token_title)
            .setMessage(R.string.discord_rpc_token_warning)
            .setHintText(R.string.discord_rpc_token_hint)
            .setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
            .setAsRequired()
            .setConfirmText(R.string.generic_save)
            .setConfirmListener { editText, _ ->
                val token = editText.text.toString().trim()
                AllSettings.discordRpcToken.put(token).save()
                AllSettings.discordRpcEnabled.put(true).save()
                refreshRpcState()
                Toast.makeText(requireContext(), R.string.discord_rpc_token_saved, Toast.LENGTH_SHORT).show()
                true
            }
            .showDialog()
    }

    private fun showConfiguredMenu() {
        AlertDialog.Builder(requireContext(), R.style.CustomAlertDialogTheme)
            .setTitle(R.string.discord_rpc_configure)
            .setItems(arrayOf(getString(R.string.generic_edit), getString(R.string.discord_rpc_remove))) { _, which ->
                if (which == 0) {
                    showRpcWarning()
                } else {
                    AllSettings.discordRpcEnabled.put(false).save()
                    AllSettings.discordRpcToken.put("").save()
                    DiscordRpcManager.stop()
                    refreshRpcState()
                }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
