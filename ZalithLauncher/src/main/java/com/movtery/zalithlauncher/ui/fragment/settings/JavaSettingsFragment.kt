package com.movtery.zalithlauncher.ui.fragment.settings

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.movtery.anim.AnimPlayer
import com.movtery.anim.animations.Animations
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.databinding.SettingsFragmentJavaBinding
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.task.TaskExecutors
import com.movtery.zalithlauncher.ui.fragment.settings.wrapper.BaseSettingsWrapper
import com.movtery.zalithlauncher.ui.fragment.settings.wrapper.EditTextSettingsWrapper
import com.movtery.zalithlauncher.ui.fragment.settings.wrapper.ListSettingsWrapper
import com.movtery.zalithlauncher.ui.fragment.settings.wrapper.SeekBarSettingsWrapper
import com.movtery.zalithlauncher.ui.fragment.settings.wrapper.SwitchSettingsWrapper
import com.movtery.zalithlauncher.utils.ZHTools
import com.movtery.zalithlauncher.utils.file.FileTools.Companion.formatFileSize
import com.movtery.zalithlauncher.utils.platform.MemoryUtils.Companion.getFreeDeviceMemory
import com.movtery.zalithlauncher.utils.platform.MemoryUtils.Companion.getTotalDeviceMemory
import com.movtery.zalithlauncher.utils.platform.MemoryUtils.Companion.getUsedDeviceMemory
import com.movtery.zalithlauncher.utils.stringutils.StringUtils
import net.kdt.pojavlaunch.Architecture
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension
import net.kdt.pojavlaunch.multirt.MultiRTConfigDialog
import kotlin.math.min

/**
 * TurtleLauncher: everything Java-runtime-related (install/select a JRE, JVM args, RAM
 * allocation, the JRE sandbox toggle), split out of GameSettingsFragment into its own page
 * so "Java" isn't mixed in with language/HUD/preset settings anymore - same idea as Recording
 * and Phone already being their own dedicated, single-purpose pages.
 */
class JavaSettingsFragment : AbstractSettingsFragment(R.layout.settings_fragment_java, SettingCategory.JAVA) {
    companion object {
        const val TAG: String = "JavaSettingsFragment"
    }

    private lateinit var binding: SettingsFragmentJavaBinding
    private val mVmInstallLauncher = registerForActivityResult(
        OpenDocumentWithExtension("xz")
    ) { uris: List<Uri>? ->
        uris?.let { uriList ->
            uriList[0].let { data ->
                Tools.installRuntimeFromUri(context, data)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SettingsFragmentJavaBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()
        binding.subSettingsBackButton.setOnClickListener { ZHTools.onBackPressed(requireActivity()) }

        BaseSettingsWrapper(
            context,
            binding.installJreLayout
        ) {
            MultiRTConfigDialog().apply {
                prepare(context, mVmInstallLauncher)
            }.show()
        }

        ListSettingsWrapper(
            context,
            AllSettings.selectRuntimeMode,
            binding.selectRuntimeModeLayout,
            binding.selectRuntimeModeTitle,
            binding.selectRuntimeModeValue,
            R.array.select_java_runtime_names, R.array.select_java_runtime_values
        )

        EditTextSettingsWrapper(
            AllSettings.javaArgs,
            binding.javaArgsLayout,
            binding.javaArgsEdittext
        )

        val deviceRam = Tools.getTotalDeviceMemory(context)
        val maxRAM = if (Architecture.is32BitsDevice() || deviceRam < 2048) min(
            1024.0,
            deviceRam.toDouble()
        ).toInt()
        else deviceRam - (if (deviceRam < 3064) 800 else 1024) //To have a minimum for the device to breathe

        SeekBarSettingsWrapper(
            context,
            AllSettings.ramAllocation.value,
            binding.allocationLayout,
            binding.allocationTitle,
            binding.allocationSummary,
            binding.allocationValue,
            binding.allocation,
            "MB"
        ) { wrapper ->
            wrapper.seekbarView.max = maxRAM
            wrapper.seekbarView.progress = AllSettings.ramAllocation.value.getValue()
            wrapper.setSeekBarValueTextView()

            updateMemoryInfo(context, wrapper.seekbarView.progress.toLong())
        }.apply {
            setOnSeekBarProgressChangeListener {
                updateMemoryInfo(
                    requireContext(),
                    seekbarView.progress.toLong()
                )
            }
        }

        SwitchSettingsWrapper(
            context,
            AllSettings.javaSandbox,
            binding.javaSandboxLayout,
            binding.javaSandbox
        )
    }

    override fun slideIn(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.root, Animations.BounceInDown))
    }

    private fun updateMemoryInfo(context: Context, seekValue: Long) {
        val value = seekValue * 1024 * 1024
        val freeDeviceMemory = getFreeDeviceMemory(context)

        val isMemorySizeExceeded = value > freeDeviceMemory

        var summary = getMemoryInfoText(context, freeDeviceMemory)
        if (isMemorySizeExceeded) summary =
            StringUtils.insertNewline(summary, getString(R.string.setting_java_memory_exceeded))

        TaskExecutors.runInUIThread { binding.allocationMemory.text = summary }
    }

    private fun getMemoryInfoText(context: Context, freeDeviceMemory: Long): String {
        return getString(
            R.string.setting_java_memory_info,
            formatFileSize(getUsedDeviceMemory(context)),
            formatFileSize(getTotalDeviceMemory(context)),
            formatFileSize(freeDeviceMemory)
        )
    }
}
