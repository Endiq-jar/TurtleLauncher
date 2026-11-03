package com.endiq.turtlelauncher.ui.fragment.settings

import com.endiq.turtlelauncher.utils.anim.TurtleTransitions
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.endiq.anim.AnimPlayer
import com.endiq.anim.animations.Animations
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.feature.engine.GameEngine
import com.endiq.turtlelauncher.feature.engine.GameEngineManager
import com.endiq.turtlelauncher.feature.engine.HorizonEngine
import com.endiq.turtlelauncher.feature.engine.HorizonEngineInstaller
import com.endiq.turtlelauncher.ui.dialog.EditTextDialog
import com.endiq.turtlelauncher.feature.version.VersionsManager
import com.endiq.turtlelauncher.databinding.SettingsFragmentPhoneBinding
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.task.TaskExecutors
import com.endiq.turtlelauncher.ui.fragment.settings.wrapper.ListSettingsWrapper
import com.endiq.turtlelauncher.ui.fragment.settings.wrapper.SeekBarSettingsWrapper
import com.endiq.turtlelauncher.ui.fragment.settings.wrapper.SwitchSettingsWrapper
import com.endiq.turtlelauncher.utils.file.FileTools.Companion.formatFileSize
import com.endiq.turtlelauncher.utils.platform.MemoryUtils.Companion.getFreeDeviceMemory
import com.endiq.turtlelauncher.utils.platform.MemoryUtils.Companion.getTotalDeviceMemory
import com.endiq.turtlelauncher.utils.ZHTools
import net.endiq.launcher.prefs.LauncherPreferences


class PhoneSettingsFragment : AbstractSettingsFragment(R.layout.settings_fragment_phone, SettingCategory.PHONE) {
    companion object {
        const val TAG: String = "PhoneSettingsFragment"
    }

    private lateinit var binding: SettingsFragmentPhoneBinding

    /**
     * Picks a Horizon build the user already downloaded. The system installer takes over from
     * there - the launcher only passes along the URI it was granted read access to.
     */
    private val horizonApkPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) installHorizonApk(uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SettingsFragmentPhoneBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()
        binding.subSettingsBackButton.setOnClickListener { ZHTools.onBackPressed(requireActivity()) }

        // ── Game engine ─────────────────────────────────────────────────────
        setupEngineSection()

        // ── CPU ──────────────────────────────────────────────────────────────
        SwitchSettingsWrapper(context, AllSettings.autoDetectCores, binding.autoDetectCoresLayout, binding.autoDetectCores)

        SwitchSettingsWrapper(context, AllSettings.manualCoreOverride, binding.manualCoreOverrideLayout, binding.manualCoreOverride)
            .setOnCheckedChangeListener { _, isChecked, save ->
                save.onSave()
                computeVisibility()
            }

        SeekBarSettingsWrapper(
            context,
            AllSettings.manualCoreCount,
            binding.manualCoreCountLayout,
            binding.manualCoreCountTitle,
            binding.manualCoreCountSummary,
            binding.manualCoreCountValue,
            binding.manualCoreCount,
            ""
        )

        SwitchSettingsWrapper(context, AllSettings.perInstanceCpuOverride, binding.perInstanceCpuOverrideLayout, binding.perInstanceCpuOverride)

        SwitchSettingsWrapper(context, AllSettings.advancedCpuTuning, binding.advancedCpuTuningLayout, binding.advancedCpuTuning)
            .setOnCheckedChangeListener { _, isChecked, save ->
                save.onSave()
                computeVisibility()
            }

        // Thread Affinity reuses the existing bigCoreAffinity setting (previously only
        // surfaced in Experimental Settings) - single source of truth, just exposed here too.
        SwitchSettingsWrapper(context, AllSettings.bigCoreAffinity, binding.bigCoreAffinityLayout, binding.bigCoreAffinity)

        SwitchSettingsWrapper(context, AllSettings.schedulerTuning, binding.schedulerTuningLayout, binding.schedulerTuning)

        // ── Memory Optimizer ────────────────────────────────────────────────
        SwitchSettingsWrapper(context, AllSettings.autoRamCalculator, binding.autoRamCalculatorLayout, binding.autoRamCalculator)
            .setOnCheckedChangeListener { _, isChecked, save ->
                save.onSave()
                if (isChecked) {
                    // Pin the RAM slider to this device's best pick right away, rather than
                    // waiting for the next launch to notice the toggle flipped.
                    val best = LauncherPreferences.findBestRAMAllocation(context)
                    AllSettings.ramAllocation.value.put(best).save()
                }
            }

        SwitchSettingsWrapper(context, AllSettings.equalHeapSizes, binding.equalHeapSizesLayout, binding.equalHeapSizes)

        ListSettingsWrapper(
            context,
            AllSettings.ramPreset,
            binding.ramPresetLayout,
            binding.ramPresetTitle,
            binding.ramPresetValue,
            R.array.all_ram_preset, R.array.all_ram_preset_value
        )

        updateDeviceRamInfo(context)

        SwitchSettingsWrapper(context, AllSettings.memoryPressureMonitor, binding.memoryPressureMonitorLayout, binding.memoryPressureMonitor)
            .setOnCheckedChangeListener { _, isChecked, save ->
                save.onSave()
                if (isChecked) {
                    com.endiq.turtlelauncher.feature.turtle.MemoryPressureMonitor.start(context.applicationContext)
                } else {
                    com.endiq.turtlelauncher.feature.turtle.MemoryPressureMonitor.stop()
                }
            }

        SwitchSettingsWrapper(context, AllSettings.gcStatistics, binding.gcStatisticsLayout, binding.gcStatistics)

        // ── JNI Optimization ────────────────────────────────────────────────
        SwitchSettingsWrapper(context, AllSettings.jniBatching, binding.jniBatchingLayout, binding.jniBatching)
        SwitchSettingsWrapper(context, AllSettings.jniCachedReferences, binding.jniCachedReferencesLayout, binding.jniCachedReferences)
        SwitchSettingsWrapper(context, AllSettings.nativeObjectPooling, binding.nativeObjectPoolingLayout, binding.nativeObjectPooling)
        SwitchSettingsWrapper(context, AllSettings.reducedJniCalls, binding.reducedJniCallsLayout, binding.reducedJniCalls)

        computeVisibility()
    }

    override fun onChange() {
        super.onChange()
        computeVisibility()
        renderEngineState()
    }

    override fun slideIn(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.root, TurtleTransitions.enter()))
    }

    // ── Game engine ─────────────────────────────────────────────────────────

    /**
     * Engine switch: the on-device (bundled JVM) engine or the Horizon engine. Horizon is a
     * separate installed app, so this section also carries the install/pick/open actions that
     * make the switch usable without leaving settings.
     */
    private fun setupEngineSection() {
        binding.engineDeviceLayout.setOnClickListener {
            GameEngineManager.select(GameEngine.DEVICE)
            renderEngineState()
        }
        binding.engineHorizonLayout.setOnClickListener {
            GameEngineManager.select(GameEngine.HORIZON)
            renderEngineState()
        }
        binding.engineHorizonPick.setOnClickListener { showEngineAppPicker() }
        // "Install engine" is the built-in downloader; a locally downloaded APK can still be
        // handed over with a long press, which keeps the manual path without another row.
        binding.engineHorizonInstall.setOnClickListener { downloadHorizonEngine() }
        binding.engineHorizonInstall.setOnLongClickListener {
            pickHorizonApk()
            true
        }
        binding.engineHorizonOpen.setOnClickListener { openHorizonEngine() }
        binding.engineHorizonSource.setOnClickListener { editEngineSource() }
        binding.engineHorizonUninstall.setOnClickListener { uninstallHorizonEngine() }
        renderEngineState()
    }

    private fun renderEngineState() {
        if (!::binding.isInitialized || !isAdded) return
        val selected = GameEngineManager.selected()
        val status = GameEngineManager.horizonStatus(requireContext())
        binding.engineDeviceRadio.isChecked = selected == GameEngine.DEVICE
        binding.engineHorizonRadio.isChecked = selected == GameEngine.HORIZON
        binding.engineHorizonStatus.text = status.summary
        // Keep the fix-it actions visible even when nothing is installed: picking an installed
        // build or installing a downloaded APK is exactly how the user gets out of that state.
        binding.engineHorizonActions.visibility = View.VISIBLE
        binding.engineHorizonOpen.visibility =
            if (status.installation != null) View.VISIBLE else View.GONE
        binding.engineHorizonUninstall.visibility =
            if (status.installation != null) View.VISIBLE else View.GONE
        val bundledLabel = HorizonEngineInstaller.bundledLabel(requireContext())
        binding.engineHorizonSource.text =
            if (bundledLabel != null) {
                getString(R.string.engine_bundled_label, bundledLabel)
            } else {
                getString(R.string.engine_source_label, HorizonEngineInstaller.sourceRepo())
            }
        // The engine ships inside the launcher, so this is a plain install/update, not a download.
        binding.engineHorizonInstall.text = getString(
            when {
                status.updateAvailable -> R.string.engine_install_update
                bundledLabel != null -> R.string.engine_install_bundled
                else -> R.string.engine_install_engine
            },
        )
    }

    /**
     * Built-in engine install. The engine is packed inside this launcher's APK, so this unpacks
     * the bundled file (with progress) and hands it to Android's installer - no download, and it
     * works offline. Only builds with the asset stripped fall back to the release feed.
     */
    private fun downloadHorizonEngine() {
        val appContext = requireContext().applicationContext
        val bundled = HorizonEngineInstaller.isBundled(appContext)
        binding.engineHorizonStatus.text = getString(
            if (bundled) R.string.engine_prepare_starting else R.string.engine_download_starting
        )
        binding.engineHorizonInstall.isEnabled = false
        TaskExecutors.getDefault().execute {
            val result = HorizonEngineInstaller.acquire(appContext) { percent ->
                TaskExecutors.runInUIThread {
                    if (isAdded) {
                        binding.engineHorizonStatus.text = getString(
                            if (bundled) R.string.engine_unpack_progress else R.string.engine_download_progress,
                            percent,
                        )
                    }
                }
            }
            TaskExecutors.runInUIThread {
                if (!isAdded) return@runInUIThread
                binding.engineHorizonInstall.isEnabled = true
                result.onSuccess { file ->
                    HorizonEngineInstaller.install(requireContext(), file)
                        .onSuccess { Toast.makeText(requireContext(), R.string.engine_install_started, Toast.LENGTH_LONG).show() }
                        .onFailure { error ->
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.engine_install_failed, error.message ?: ""),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }.onFailure { error ->
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.engine_install_failed, error.message ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                }
                renderEngineState()
            }
        }
    }

    private fun editEngineSource() {
        EditTextDialog.Builder(requireContext())
            .setTitle(R.string.engine_source_title)
            .setMessage(R.string.engine_source_hint)
            .setEditText(HorizonEngineInstaller.sourceRepo())
            .setConfirmListener { editText, _ ->
                HorizonEngineInstaller.setSourceRepo(editText.text.toString())
                renderEngineState()
                Toast.makeText(requireContext(), R.string.engine_source_saved, Toast.LENGTH_SHORT).show()
                true
            }
            .showDialog()
    }

    private fun uninstallHorizonEngine() {
        val context = requireContext()
        val installation = HorizonEngine.resolve(context)
        if (installation == null) {
            Toast.makeText(context, R.string.engine_pick_empty, Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { context.startActivity(HorizonEngineInstaller.uninstallIntent(installation.packageName)) }
            .onFailure {
                Toast.makeText(context, R.string.engine_install_unavailable, Toast.LENGTH_SHORT).show()
            }
    }

    private fun showEngineAppPicker() {
        val context = requireContext()
        val candidates = HorizonEngine.candidates(context)
        if (candidates.isEmpty()) {
            Toast.makeText(context, R.string.engine_pick_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = candidates.map { candidate ->
            candidate.versionName
                ?.takeIf { it.isNotBlank() }
                ?.let { context.getString(R.string.engine_status_ready_version, candidate.label, it) }
                ?: candidate.label
        }.toTypedArray()

        AlertDialog.Builder(context, R.style.CustomAlertDialogTheme)
            .setTitle(R.string.engine_pick_title)
            .setItems(labels) { _, which ->
                val picked = candidates[which]
                GameEngineManager.selectHorizonPackage(picked.packageName)
                GameEngineManager.select(GameEngine.HORIZON)
                renderEngineState()
                Toast.makeText(
                    context,
                    context.getString(R.string.engine_pick_selected, picked.label),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun pickHorizonApk() {
        runCatching { horizonApkPicker.launch(HorizonEngine.APK_MIME_TYPE) }
            .onFailure {
                Toast.makeText(requireContext(), R.string.engine_install_unavailable, Toast.LENGTH_SHORT).show()
            }
    }

    private fun installHorizonApk(uri: Uri) {
        runCatching { requireContext().startActivity(HorizonEngine.installIntent(uri)) }
            .onFailure {
                Toast.makeText(requireContext(), R.string.engine_install_unavailable, Toast.LENGTH_SHORT).show()
            }
    }

    private fun openHorizonEngine() {
        val context = requireContext()
        val installation = HorizonEngine.resolve(context)
        if (installation == null) {
            Toast.makeText(context, R.string.engine_pick_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val version = VersionsManager.getCurrentVersion()
        HorizonEngine.launch(
            context,
            installation,
            version?.getVersionName(),
            version?.getGameDir()?.absolutePath
        ).onFailure { error ->
            Toast.makeText(
                context,
                context.getString(R.string.engine_open_failed, error.message ?: installation.label),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun updateDeviceRamInfo(context: android.content.Context) {
        TaskExecutors.runInUIThread {
            binding.deviceRamDetectionValue.text = getString(
                R.string.setting_java_memory_info,
                formatFileSize(getTotalDeviceMemory(context) - getFreeDeviceMemory(context)),
                formatFileSize(getTotalDeviceMemory(context)),
                formatFileSize(getFreeDeviceMemory(context))
            )
        }
    }

    private fun computeVisibility() {
        binding.apply {
            setViewVisibility(manualCoreCountLayout, AllSettings.manualCoreOverride.getValue())
            val advanced = AllSettings.advancedCpuTuning.getValue()
            setViewVisibility(bigCoreAffinityLayout, advanced)
            setViewVisibility(schedulerTuningLayout, advanced)
        }
    }

    private fun setViewVisibility(view: View, visible: Boolean) {
        view.visibility = if (visible) View.VISIBLE else View.GONE
    }
}
