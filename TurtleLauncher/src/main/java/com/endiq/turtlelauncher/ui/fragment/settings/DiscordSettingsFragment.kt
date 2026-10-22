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
import com.endiq.turtlelauncher.feature.discord.DiscordRpcManager
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.ui.dialog.EditTextDialog
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
            val intent = requireContext().packageManager.getLaunchIntentForPackage("com.discord")
                ?: android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://discord.com/download"))
            runCatching { startActivity(intent) }
                .onFailure { Toast.makeText(requireContext(), R.string.discord_open_failed, Toast.LENGTH_SHORT).show() }
        }

        binding.discordRpcCustomizeButton.setOnClickListener { showCustomizationMenu() }
        binding.discordSdkNoticesButton.setOnClickListener {
            val notices = requireContext().assets.open("notices/discord/License-Notices.txt")
                .bufferedReader().use { it.readText() }
            val dialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialogTheme)
                .setTitle(R.string.discord_sdk_notices)
                .setMessage(getString(R.string.discord_sdk_notice_description) + "\n\n" + notices)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            dialog.findViewById<android.widget.TextView>(android.R.id.message)?.let {
                it.setTextIsSelectable(true)
                android.text.util.Linkify.addLinks(it, android.text.util.Linkify.WEB_URLS)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshLinkState()
        refreshRpcState()
    }

    private fun refreshLinkState() {
        binding.discordStatusTitle.setText(R.string.discord_native_title)
        binding.discordStatusDesc.setText(R.string.discord_native_description)
        binding.discordLinkButton.setText(R.string.discord_open_app)
    }

    private fun refreshRpcState() {
        if (_binding == null) return
        binding.discordActivityDescription.setText(
            if (DiscordRpcManager.isConfigured()) R.string.discord_rpc_automatic
            else R.string.discord_rpc_auth_unavailable
        )
        com.endiq.turtlelauncher.feature.discord.DiscordPresenceLifecycle.refresh()
        val timer = getString(
            if (AllSettings.discordRpcShowElapsed.getValue()) R.string.discord_rpc_on else R.string.discord_rpc_off
        )
        binding.discordRpcCustomizationSummary.text = getString(R.string.discord_rpc_customization_summary, timer)
    }

    private fun showCustomizationMenu() {
        val enabledLabel = getString(
            if (AllSettings.discordRpcPresenceEnabled.getValue()) R.string.discord_rpc_on else R.string.discord_rpc_off
        )
        val elapsedLabel = getString(
            if (AllSettings.discordRpcShowElapsed.getValue()) R.string.discord_rpc_on else R.string.discord_rpc_off
        )
        val typeLabel = typeLabel(AllSettings.discordRpcType.getValue())
        val items = arrayOf(
            getString(R.string.discord_rpc_presence_enabled) + ": " + enabledLabel,
            getString(R.string.discord_rpc_details),
            getString(R.string.discord_rpc_state),
            getString(R.string.discord_rpc_elapsed) + ": " + elapsedLabel,
            getString(R.string.discord_rpc_type) + ": " + typeLabel,
            getString(R.string.discord_rpc_application_id),
            getString(R.string.discord_rpc_large_image),
            getString(R.string.discord_rpc_large_text),
            getString(R.string.discord_rpc_small_image),
            getString(R.string.discord_rpc_small_text),
            getString(R.string.discord_rpc_button1),
            getString(R.string.discord_rpc_button1_url),
            getString(R.string.discord_rpc_button2),
            getString(R.string.discord_rpc_button2_url),
            getString(R.string.discord_rpc_preview),
            getString(R.string.discord_rpc_reset_customization)
        )
        AlertDialog.Builder(requireContext(), R.style.CustomAlertDialogTheme)
            .setTitle(R.string.discord_rpc_customization_title)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        AllSettings.discordRpcPresenceEnabled
                            .put(!AllSettings.discordRpcPresenceEnabled.getValue()).save()
                        refreshRpcState()
                    }
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
                    3 -> {
                        AllSettings.discordRpcShowElapsed
                            .put(!AllSettings.discordRpcShowElapsed.getValue()).save()
                        refreshRpcState()
                    }
                    4 -> showTypePicker()
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
                    8 -> showCustomizationTextEditor(
                        R.string.discord_rpc_small_image,
                        R.string.discord_rpc_small_image_hint,
                        AllSettings.discordRpcSmallImage.getValue(),
                        showTemplateHelp = false
                    ) { AllSettings.discordRpcSmallImage.put(it).save() }
                    9 -> showCustomizationTextEditor(
                        R.string.discord_rpc_small_text,
                        R.string.discord_rpc_small_text_hint,
                        AllSettings.discordRpcSmallText.getValue()
                    ) { AllSettings.discordRpcSmallText.put(it).save() }
                    10 -> showCustomizationTextEditor(
                        R.string.discord_rpc_button1,
                        R.string.discord_rpc_button1_hint,
                        AllSettings.discordRpcButton1.getValue(),
                        showTemplateHelp = false
                    ) { AllSettings.discordRpcButton1.put(it).save(); warnIfButtonIncomplete(1) }
                    11 -> showCustomizationTextEditor(
                        R.string.discord_rpc_button1_url,
                        R.string.discord_rpc_button1_url_hint,
                        AllSettings.discordRpcButton1Url.getValue(),
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
                        showTemplateHelp = false,
                        urlField = true
                    ) { AllSettings.discordRpcButton1Url.put(it).save(); warnIfButtonIncomplete(1) }
                    12 -> showCustomizationTextEditor(
                        R.string.discord_rpc_button2,
                        R.string.discord_rpc_button2_hint,
                        AllSettings.discordRpcButton2.getValue(),
                        showTemplateHelp = false
                    ) { AllSettings.discordRpcButton2.put(it).save(); warnIfButtonIncomplete(2) }
                    13 -> showCustomizationTextEditor(
                        R.string.discord_rpc_button2_url,
                        R.string.discord_rpc_button2_url_hint,
                        AllSettings.discordRpcButton2Url.getValue(),
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
                        showTemplateHelp = false,
                        urlField = true
                    ) { AllSettings.discordRpcButton2Url.put(it).save(); warnIfButtonIncomplete(2) }
                    14 -> showPreview()
                    15 -> resetCustomization()
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
        urlField: Boolean = false,
        onSave: (String) -> Unit
    ) {
        val builder = EditTextDialog.Builder(requireContext())
            .setTitle(title)
            .setHintText(hint)
            .setEditText(current)
            .setInputType(inputType)
            .setConfirmText(R.string.generic_save)
            .setConfirmListener { editText, _ ->
                val value = editText.text.toString().trim()
                if (urlField && value.isNotEmpty()) {
                    // Normalize (assume https:// when no scheme was typed - the #1 cause of a
                    // "saved but the button never shows up" report) and reject anything that
                    // still isn't a usable http(s) link instead of silently storing it.
                    val normalized = com.endiq.turtlelauncher.feature.discord.DiscordPresencePayload.buttonUrl(value)
                    if (normalized == null) {
                        editText.error = getString(R.string.discord_rpc_invalid_url)
                        return@setConfirmListener false
                    }
                    onSave(normalized)
                } else {
                    onSave(value)
                }
                refreshRpcState()
                true
            }
        if (required) builder.setAsRequired()
        if (showTemplateHelp) builder.setMessage(R.string.discord_rpc_template_help)
        builder.showDialog()
    }

    /** Discord only shows a button when it has both a label and a link - tell the user
     * when they've only filled in one half, instead of leaving them to wonder why nothing
     * shows up in their Discord profile. */
    private fun warnIfButtonIncomplete(button: Int) {
        val label = if (button == 1) AllSettings.discordRpcButton1.getValue() else AllSettings.discordRpcButton2.getValue()
        val url = if (button == 1) AllSettings.discordRpcButton1Url.getValue() else AllSettings.discordRpcButton2Url.getValue()
        if (label.isNotBlank() != url.isNotBlank()) {
            Toast.makeText(
                requireContext(),
                getString(R.string.discord_rpc_button_incomplete, button),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showPreview() {
        val version = "1.21.1"
        fun render(template: String, fallback: String) = template.ifBlank { fallback }
            .replace("{version}", version)
            .replace("{activity}", "Playing Minecraft")
        val lines = mutableListOf<String>()
        lines += render(AllSettings.discordRpcDetails.getValue(), "")
        lines += render(AllSettings.discordRpcState.getValue(), "")
        if (AllSettings.discordRpcShowElapsed.getValue()) lines += getString(R.string.discord_rpc_preview_elapsed)
        listOf(
            AllSettings.discordRpcButton1.getValue() to AllSettings.discordRpcButton1Url.getValue(),
            AllSettings.discordRpcButton2.getValue() to AllSettings.discordRpcButton2Url.getValue(),
        ).forEachIndexed { index, (label, url) ->
            val shown = label.isNotBlank() && com.endiq.turtlelauncher.feature.discord.DiscordPresencePayload.buttonUrl(url) != null
            lines += "[" + getString(if (index == 0) R.string.discord_rpc_button1 else R.string.discord_rpc_button2) +
                (if (shown) ": $label -> $url" else ": " + getString(R.string.discord_rpc_preview_button_hidden))
        }
        AlertDialog.Builder(requireContext(), R.style.CustomAlertDialogTheme)
            .setTitle(R.string.discord_rpc_preview_title)
            .setMessage(lines.filter { it.isNotBlank() }.joinToString("\n"))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showTypePicker() {
        val values = arrayOf("0", "1", "2", "3", "5")
        val labels = arrayOf(
            getString(R.string.discord_rpc_type_playing),
            getString(R.string.discord_rpc_type_streaming),
            getString(R.string.discord_rpc_type_listening),
            getString(R.string.discord_rpc_type_watching),
            getString(R.string.discord_rpc_type_competing)
        )
        val selected = values.indexOf(AllSettings.discordRpcType.getValue()).coerceAtLeast(0)
        AlertDialog.Builder(requireContext(), R.style.CustomAlertDialogTheme)
            .setTitle(R.string.discord_rpc_type)
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                AllSettings.discordRpcType.put(values[which]).save()
                dialog.dismiss()
                refreshRpcState()
            }
            .show()
    }

    private fun typeLabel(value: String): String = getString(
        when (value.trim()) {
            "1" -> R.string.discord_rpc_type_streaming
            "2" -> R.string.discord_rpc_type_listening
            "3" -> R.string.discord_rpc_type_watching
            "5" -> R.string.discord_rpc_type_competing
            else -> R.string.discord_rpc_type_playing
        }
    )

    private fun resetCustomization() {
        AllSettings.discordRpcPresenceEnabled.reset()
        AllSettings.discordRpcName.reset()
        AllSettings.discordRpcDetails.reset()
        AllSettings.discordRpcState.reset()
        AllSettings.discordRpcShowElapsed.reset()
        AllSettings.discordRpcType.reset()
        AllSettings.discordRpcApplicationId.reset()
        AllSettings.discordRpcLargeImage.reset()
        AllSettings.discordRpcLargeText.reset()
        AllSettings.discordRpcSmallImage.reset()
        AllSettings.discordRpcSmallText.reset()
        AllSettings.discordRpcButton1.reset()
        AllSettings.discordRpcButton1Url.reset()
        AllSettings.discordRpcButton2.reset()
        AllSettings.discordRpcButton2Url.reset()
        refreshRpcState()
        Toast.makeText(requireContext(), R.string.discord_rpc_reset_done, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
