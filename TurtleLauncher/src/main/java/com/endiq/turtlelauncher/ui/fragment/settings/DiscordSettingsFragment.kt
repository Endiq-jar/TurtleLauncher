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
        binding.discordRpcCustomizeButton.setOnClickListener { showCustomizationMenu() }
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
        val status = AllSettings.discordRpcStatus.getValue().replaceFirstChar { it.uppercaseChar() }
        val timer = getString(
            if (AllSettings.discordRpcShowElapsed.getValue()) R.string.discord_rpc_on else R.string.discord_rpc_off
        )
        binding.discordRpcCustomizationSummary.text = getString(
            R.string.discord_rpc_customization_summary,
            status,
            timer
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

    private fun showCustomizationMenu() {
        val elapsedLabel = getString(
            if (AllSettings.discordRpcShowElapsed.getValue()) R.string.discord_rpc_on else R.string.discord_rpc_off
        )
        val items = arrayOf(
            getString(R.string.discord_rpc_name),
            getString(R.string.discord_rpc_details),
            getString(R.string.discord_rpc_state),
            getString(R.string.discord_rpc_status),
            getString(R.string.discord_rpc_elapsed) + ": " + elapsedLabel,
            getString(R.string.discord_rpc_application_id),
            getString(R.string.discord_rpc_large_image),
            getString(R.string.discord_rpc_large_text),
            getString(R.string.discord_rpc_reset_customization)
        )
        AlertDialog.Builder(requireContext(), R.style.CustomAlertDialogTheme)
            .setTitle(R.string.discord_rpc_customization_title)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showCustomizationTextEditor(
                        R.string.discord_rpc_name,
                        R.string.discord_rpc_name_hint,
                        AllSettings.discordRpcName.getValue(),
                        required = true
                    ) { AllSettings.discordRpcName.put(it).save() }
                    1 -> showCustomizationTextEditor(
                        R.string.discord_rpc_details,
                        R.string.discord_rpc_details_hint,
                        AllSettings.discordRpcDetails.getValue()
                    ) { AllSettings.discordRpcDetails.put(it).save() }
                    2 -> showCustomizationTextEditor(
                        R.string.discord_rpc_state,
                        R.string.discord_rpc_state_hint,
                        AllSettings.discordRpcState.getValue()
                    ) { AllSettings.discordRpcState.put(it).save() }
                    3 -> showStatusPicker()
                    4 -> {
                        AllSettings.discordRpcShowElapsed
                            .put(!AllSettings.discordRpcShowElapsed.getValue()).save()
                        refreshRpcState()
                    }
                    5 -> showCustomizationTextEditor(
                        R.string.discord_rpc_application_id,
                        R.string.discord_rpc_application_id_hint,
                        AllSettings.discordRpcApplicationId.getValue(),
                        inputType = InputType.TYPE_CLASS_NUMBER,
                        showTemplateHelp = false
                    ) { AllSettings.discordRpcApplicationId.put(it).save() }
                    6 -> showCustomizationTextEditor(
                        R.string.discord_rpc_large_image,
                        R.string.discord_rpc_large_image_hint,
                        AllSettings.discordRpcLargeImage.getValue(),
                        showTemplateHelp = false
                    ) { AllSettings.discordRpcLargeImage.put(it).save() }
                    7 -> showCustomizationTextEditor(
                        R.string.discord_rpc_large_text,
                        R.string.discord_rpc_large_text_hint,
                        AllSettings.discordRpcLargeText.getValue()
                    ) { AllSettings.discordRpcLargeText.put(it).save() }
                    8 -> resetCustomization()
                }
            }
            .show()
    }

    private fun showCustomizationTextEditor(
        title: Int,
        hint: Int,
        current: String,
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        required: Boolean = false,
        showTemplateHelp: Boolean = true,
        onSave: (String) -> Unit
    ) {
        val builder = EditTextDialog.Builder(requireContext())
            .setTitle(title)
            .setHintText(hint)
            .setEditText(current)
            .setInputType(inputType)
            .setConfirmText(R.string.generic_save)
            .setConfirmListener { editText, _ ->
                onSave(editText.text.toString().trim())
                refreshRpcState()
                true
            }
        if (required) builder.setAsRequired()
        if (showTemplateHelp) builder.setMessage(R.string.discord_rpc_template_help)
        builder.showDialog()
    }

    private fun showStatusPicker() {
        val values = arrayOf("online", "idle", "dnd", "invisible")
        val labels = arrayOf("Online", "Idle", "Do not disturb", "Invisible")
        val selected = values.indexOf(AllSettings.discordRpcStatus.getValue()).coerceAtLeast(0)
        AlertDialog.Builder(requireContext(), R.style.CustomAlertDialogTheme)
            .setTitle(R.string.discord_rpc_status)
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                AllSettings.discordRpcStatus.put(values[which]).save()
                dialog.dismiss()
                refreshRpcState()
            }
            .show()
    }

    private fun resetCustomization() {
        AllSettings.discordRpcName.reset()
        AllSettings.discordRpcDetails.reset()
        AllSettings.discordRpcState.reset()
        AllSettings.discordRpcStatus.reset()
        AllSettings.discordRpcShowElapsed.reset()
        AllSettings.discordRpcApplicationId.reset()
        AllSettings.discordRpcLargeImage.reset()
        AllSettings.discordRpcLargeText.reset()
        refreshRpcState()
        Toast.makeText(requireContext(), R.string.discord_rpc_reset_done, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
