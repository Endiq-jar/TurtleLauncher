package com.movtery.zalithlauncher.ui.fragment.settings

import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.widget.Toast
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import com.movtery.anim.AnimPlayer
import com.movtery.anim.animations.Animations
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.databinding.SettingsFragmentVideoBinding
import com.movtery.zalithlauncher.event.single.LauncherIgnoreNotchEvent
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.plugins.driver.DriverPluginManager
import com.movtery.zalithlauncher.plugins.renderer.RendererPluginManager
import com.movtery.zalithlauncher.renderer.Renderers
import com.movtery.zalithlauncher.renderer.renderers.HolyGL4ESRenderer
import com.movtery.zalithlauncher.renderer.renderers.ZinkRenderer
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.setting.AllStaticSettings
import com.movtery.zalithlauncher.task.Task
import com.movtery.zalithlauncher.task.TaskExecutors
import com.movtery.zalithlauncher.ui.dialog.LocalRendererPluginDialog
import com.movtery.zalithlauncher.ui.dialog.TipDialog
import com.movtery.zalithlauncher.ui.fragment.settings.wrapper.BaseSettingsWrapper
import com.movtery.zalithlauncher.ui.fragment.settings.wrapper.ListSettingsWrapper
import com.movtery.zalithlauncher.ui.fragment.settings.wrapper.SeekBarSettingsWrapper
import com.movtery.zalithlauncher.ui.fragment.settings.wrapper.SwitchSettingsWrapper
import com.movtery.zalithlauncher.utils.ZHTools
import com.movtery.zalithlauncher.utils.file.FileTools
import com.movtery.zalithlauncher.utils.path.PathManager
import com.movtery.zalithlauncher.utils.path.UrlManager
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension
import org.apache.commons.io.FileUtils
import org.greenrobot.eventbus.EventBus
import java.io.File

class VideoSettingsFragment : AbstractSettingsFragment(R.layout.settings_fragment_video, SettingCategory.VIDEO) {
    private lateinit var binding: SettingsFragmentVideoBinding
    private lateinit var openDocumentLauncher: ActivityResultLauncher<Any>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openDocumentLauncher = registerForActivityResult(OpenDocumentWithExtension("zip", true)) { uris: List<Uri>? ->
            uris?.let { uriList ->
                val dialog = ZHTools.showTaskRunningDialog(requireActivity())
                Task.runTask {
                    val pluginFiles = mutableListOf<File>()
                    uriList.forEach { uri ->
                        val file = FileTools.copyFileInBackground(requireActivity(), uri, PathManager.DIR_CACHE.absolutePath)
                        pluginFiles.add(file)
                    }
                    pluginFiles.takeIf { it.isNotEmpty() }
                }.beforeStart(TaskExecutors.getAndroidUI()) {
                    dialog.show()
                }.ended { pluginFiles ->
                    pluginFiles?.let { files ->
                        var requiresRestart = false
                        files.forEach { pluginFile ->
                            val info = if (RendererPluginManager.importLocalRendererPlugin(pluginFile)) {
                                requiresRestart = true
                                "The renderer plugin has been successfully imported!"
                            } else {
                                "The renderer plugin import failed!"
                            }
                            Logging.i("VideoSettings", info)
                            FileUtils.deleteQuietly(pluginFile)
                        }
                        TaskExecutors.runInUIThread {
                            if (requiresRestart) {
                                TipDialog.Builder(requireActivity())
                                    .setTitle(R.string.generic_warning)
                                    .setMessage(R.string.setting_renderer_local_import_restart)
                                    .setWarning()
                                    .setConfirmClickListener { ZHTools.killProcess() }
                                    .showDialog()
                            } else {
                                TipDialog.Builder(requireActivity())
                                    .setTitle(R.string.generic_tip)
                                    .setMessage(R.string.setting_renderer_local_import_failed)
                                    .showDialog()
                            }
                        }
                    }
                }.onThrowable { e ->
                    Tools.showErrorRemote(e)
                }.finallyTask(TaskExecutors.getAndroidUI()) {
                    dialog.dismiss()
                }.execute()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SettingsFragmentVideoBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireActivity()
        binding.subSettingsBackButton.setOnClickListener { com.movtery.zalithlauncher.utils.ZHTools.onBackPressed(requireActivity()) }

        // ── Renderer & Driver ─────────────────────────────────────────────────
        val compatibleRenderers = Renderers.getCompatibleRenderers(context)
        val renderers = compatibleRenderers.first
        val rendererInstances = compatibleRenderers.second
        ListSettingsWrapper(
            context, AllSettings.renderer,
            binding.rendererLayout, binding.rendererTitle, binding.rendererValue,
            renderers.rendererNames.toTypedArray(),
            renderers.rendererIdentifier.toTypedArray()
        )
        binding.rendererDownload.setOnClickListener { ZHTools.openLink(context, UrlManager.URL_FCL_RENDERER_PLUGIN) }
        binding.rendererManagerButton.setOnClickListener {
            ZHTools.swapFragmentWithAnim(this, com.movtery.zalithlauncher.ui.fragment.settings.RendererManagerFragment::class.java, com.movtery.zalithlauncher.ui.fragment.settings.RendererManagerFragment.TAG, null)
        }

        // ── Graphics Backend quick-select (Vulkan / OpenGL) ─────────────────────
        // Simple shortcut over the same AllSettings.renderer used above: OpenGL maps to
        // HolyGL4ESRenderer's UUID, Vulkan maps to ZinkRenderer's UUID (Zink translates
        // Minecraft's GL calls to Vulkan). Matched by renderer id, not display name, so
        // this doesn't break if a renderer's display name ever changes. Falls back to
        // whichever of the two is actually compatible/present in the device's renderer
        // list, in case one isn't available.
        val openglRendererId = rendererInstances.find { it.getRendererId() == HolyGL4ESRenderer.ID }?.getUniqueIdentifier()
        val vulkanRendererId = rendererInstances.find { it.getRendererId() == ZinkRenderer.ID }?.getUniqueIdentifier()
        fun applyBackend(rendererId: String?) {
            if (rendererId == null) return
            AllSettings.renderer.put(rendererId).save()
            val index = renderers.rendererIdentifier.indexOf(rendererId)
            if (index >= 0) binding.rendererValue.text = renderers.rendererNames[index]
        }
        binding.graphicsBackendOpengl.setOnClickListener { applyBackend(openglRendererId) }
        binding.graphicsBackendVulkan.setOnClickListener { applyBackend(vulkanRendererId) }
        if (vulkanRendererId == null) {
            // Device has no Vulkan support at all - disable the Vulkan shortcut rather than
            // silently doing nothing when tapped.
            binding.graphicsBackendVulkan.isEnabled = false
            binding.graphicsBackendVulkan.alpha = 0.5f
        }

        BaseSettingsWrapper(context, binding.rendererLocalImportLayout) {
            openDocumentLauncher.launch("zip")
        }
        binding.rendererLocalImportManage.setOnClickListener {
            if (RendererPluginManager.getAllLocalRendererList().isNotEmpty()) {
                LocalRendererPluginDialog(requireActivity()).show()
            }
        }

        val driverNames = DriverPluginManager.getDriverNameList().toTypedArray()
        ListSettingsWrapper(
            context, AllSettings.driver,
            binding.driverLayout, binding.driverTitle, binding.driverValue,
            driverNames, driverNames
        )
        binding.driverDownload.setOnClickListener { ZHTools.openLink(context, UrlManager.URL_FCL_DRIVER_PLUGIN) }

        // ── Notch ─────────────────────────────────────────────────────────────
        val ignoreNotch = SwitchSettingsWrapper(
            context, AllSettings.ignoreNotch,
            binding.ignoreNotchLayout, binding.ignoreNotch
        )
        val ignoreNotchLauncher = SwitchSettingsWrapper(
            context, AllSettings.ignoreNotchLauncher,
            binding.ignoreNotchLauncherLayout, binding.ignoreNotchLauncher
        ).setOnCheckedChangeListener { _, _, listener ->
            listener.onSave()
            EventBus.getDefault().post(LauncherIgnoreNotchEvent())
        }
        if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && AllStaticSettings.notchSize > 0)) {
            ignoreNotch.setGone()
            ignoreNotchLauncher.setGone()
        }

        // ── Resolution ────────────────────────────────────────────────────────
        SeekBarSettingsWrapper(
            context, AllSettings.resolutionRatio,
            binding.resolutionRatioLayout,
            binding.resolutionRatioTitle, binding.resolutionRatioSummary,
            binding.resolutionRatioValue, binding.resolutionRatio, "%"
        ).setOnSeekBarProgressChangeListener { progress ->
            changeResolutionRatioPreview(progress)
        }

        // TurtleLauncher v10: quick resolution presets + auto-detect
        fun applyResolutionPreset(percent: Int) {
            AllSettings.resolutionRatio.put(percent).save()
            binding.resolutionRatio.progress = percent
            changeResolutionRatioPreview(percent)
        }
        binding.resolutionPreset100.setOnClickListener { applyResolutionPreset(100) }
        binding.resolutionPreset75.setOnClickListener { applyResolutionPreset(75) }
        binding.resolutionPreset50.setOnClickListener { applyResolutionPreset(50) }
        binding.resolutionAutoDetectButton.setOnClickListener {
            com.movtery.zalithlauncher.launch.AutoSettingsOptimizer.apply(context)
            binding.resolutionRatio.progress = AllSettings.resolutionRatio.getValue()
            changeResolutionRatioPreview(AllSettings.resolutionRatio.getValue())
            Toast.makeText(context, getString(R.string.setting_resolution_auto_detect), Toast.LENGTH_SHORT).show()
        }

        // ── Video Switches ────────────────────────────────────────────────────
        SwitchSettingsWrapper(context, AllSettings.sustainedPerformance,
            binding.sustainedPerformanceLayout, binding.sustainedPerformance)

        SwitchSettingsWrapper(context, AllSettings.alternateSurface,
            binding.alternateSurfaceLayout, binding.alternateSurface)

        SwitchSettingsWrapper(context, AllSettings.forceVsync,
            binding.forceVsyncLayout, binding.forceVsync)

        SwitchSettingsWrapper(context, AllSettings.vsyncInZink,
            binding.vsyncInZinkLayout, binding.vsyncInZink)

        val zinkPreferSystemDriver = SwitchSettingsWrapper(
            context, AllSettings.zinkPreferSystemDriver,
            binding.zinkPreferSystemDriverLayout, binding.zinkPreferSystemDriver
        )
        if (!Tools.checkVulkanSupport(context.packageManager)) {
            zinkPreferSystemDriver.setGone()
        } else {
            zinkPreferSystemDriver.setOnCheckedChangeListener { buttonView, isChecked, listener ->
                if (isChecked && ZHTools.isAdrenoGPU()) {
                    TipDialog.Builder(requireActivity())
                        .setTitle(R.string.generic_warning)
                        .setMessage(R.string.setting_zink_driver_adreno)
                        .setWarning()
                        .setCancelable(false)
                        .setConfirmClickListener { listener.onSave() }
                        .setCancelClickListener { buttonView.isChecked = false }
                        .showDialog()
                } else {
                    listener.onSave()
                }
            }
        }

        // ── Auto Settings Optimizer ──────────────────────────────────────────
        SwitchSettingsWrapper(context, AllSettings.autoSettingsOptimizer,
            binding.autoSettingsOptimizerLayout, binding.autoSettingsOptimizer)

        // ── FPS Boost Toggles ─────────────────────────────────────────────────
        SwitchSettingsWrapper(context, AllSettings.unlimitedFps,
            binding.unlimitedFpsLayout, binding.unlimitedFps)

        SwitchSettingsWrapper(context, AllSettings.lowLatencyRendering,
            binding.lowLatencyRenderingLayout, binding.lowLatencyRendering)

        SwitchSettingsWrapper(context, AllSettings.framePacing,
            binding.framePacingLayout, binding.framePacing)

        SwitchSettingsWrapper(context, AllSettings.frameSkipping,
            binding.frameSkippingLayout, binding.frameSkipping)

        SwitchSettingsWrapper(context, AllSettings.adaptiveFrameTiming,
            binding.adaptiveFrameTimingLayout, binding.adaptiveFrameTiming)

        // ── Advanced Renderer Settings ───────────────────────────────────────────
        SwitchSettingsWrapper(context, AllSettings.rendererShaderCacheEnabled,
            binding.rendererShaderCacheLayout, binding.rendererShaderCache)

        SwitchSettingsWrapper(context, AllSettings.rendererDebugLogging,
            binding.rendererDebugLoggingLayout, binding.rendererDebugLogging)

        changeResolutionRatioPreview(AllSettings.resolutionRatio.getValue())
        computeVisibility()
    }

    private fun changeResolutionRatioPreview(progress: Int) {
        binding.resolutionRatioPreview.text = getResolutionRatioPreview(resources, progress)
    }

    override fun onChange() {
        super.onChange()
        computeVisibility()
    }

    private fun computeVisibility() {
        binding.forceVsyncLayout.visibility =
            if (AllSettings.alternateSurface.getValue()) View.VISIBLE else View.GONE
    }

    override fun slideIn(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.root, Animations.BounceInDown))
    }

    companion object {
        const val TAG: String = "VideoSettingsFragment"

        @JvmStatic
        fun getResolutionRatioPreview(resources: Resources, progress: Int): String {
            val metrics = Tools.currentDisplayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || width > height
            val progressFloat = progress.toFloat() / 100F
            val previewWidth  = Tools.getDisplayFriendlyRes(if (isLandscape) width  else height, progressFloat)
            val previewHeight = Tools.getDisplayFriendlyRes(if (isLandscape) height else width,  progressFloat)
            return "$previewWidth x $previewHeight"
        }
    }
}
