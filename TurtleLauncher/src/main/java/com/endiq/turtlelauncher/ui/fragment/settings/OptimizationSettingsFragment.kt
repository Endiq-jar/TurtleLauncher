package com.endiq.turtlelauncher.ui.fragment.settings

import com.endiq.turtlelauncher.utils.anim.TurtleTransitions
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.endiq.anim.AnimPlayer
import com.endiq.anim.animations.Animations
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.databinding.SettingsFragmentOptimizationBinding
import com.endiq.turtlelauncher.feature.benchmark.GraphicsBenchmark
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.launch.AutoSettingsOptimizer
import com.endiq.turtlelauncher.launch.GraphicsTuning
import com.endiq.turtlelauncher.renderer.CurrentRenderer
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.task.Task
import com.endiq.turtlelauncher.task.TaskExecutors
import com.endiq.turtlelauncher.ui.dialog.TipDialog
import com.endiq.turtlelauncher.ui.fragment.settings.wrapper.BaseSettingsWrapper
import com.endiq.turtlelauncher.ui.fragment.settings.wrapper.SwitchSettingsWrapper
import com.endiq.turtlelauncher.utils.ZHTools


class OptimizationSettingsFragment : AbstractSettingsFragment(R.layout.settings_fragment_optimization, SettingCategory.OPTIMIZATION) {
    companion object {
        const val TAG: String = "OptimizationSettingsFragment"
    }

    private lateinit var binding: SettingsFragmentOptimizationBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SettingsFragmentOptimizationBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()
        binding.subSettingsBackButton.setOnClickListener { ZHTools.onBackPressed(requireActivity()) }

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

        SwitchSettingsWrapper(context, AllSettings.autoMemoryCleanup,
            binding.autoMemoryCleanupLayout, binding.autoMemoryCleanup)

        SwitchSettingsWrapper(context, AllSettings.rendererShaderCacheEnabled,
            binding.rendererShaderCacheLayout, binding.rendererShaderCache)

        SwitchSettingsWrapper(context, AllSettings.rendererDebugLogging,
            binding.rendererDebugLoggingLayout, binding.rendererDebugLogging)

        initGraphicsBenchmark()
    }

    /**
     * Runs [GraphicsBenchmark] on a background thread, stores the score for
     * AutoSettingsOptimizer, and offers to apply the resulting recommendation
     * straight away. The row also shows the last score, so the effect of a
     * renderer/driver change can be seen without launching the game.
     */
    private fun initGraphicsBenchmark() {
        val context = requireContext()
        updateBenchmarkValue()
        BaseSettingsWrapper(context, binding.graphicsBenchmarkLayout) {
            binding.graphicsBenchmarkValue.setText(R.string.setting_graphics_benchmark_running)
            val layout = binding.graphicsBenchmarkLayout
            layout.isEnabled = false
            Task.runTask {
                GraphicsBenchmark.run()
            }.onThrowable(TaskExecutors.getAndroidUI()) { error ->
                Logging.w("OptimizationSettings", "Benchmark failed: ${error.message}")
                TaskExecutors.runInUIThread {
                    layout.isEnabled = true
                    binding.graphicsBenchmarkValue.setText(R.string.setting_graphics_benchmark_not_run)
                    TipDialog.Builder(context)
                        .setTitle(R.string.setting_graphics_benchmark_title)
                        .setMessage(context.getString(
                            R.string.setting_graphics_benchmark_failed,
                            error.message ?: error.javaClass.simpleName
                        ))
                        .setShowCancel(false)
                        .showDialog()
                }
            }.finallyTask(TaskExecutors.getAndroidUI()) {
                layout.isEnabled = true
            }.ended(TaskExecutors.getAndroidUI()) { result ->
                if (result == null) return@ended
                AllSettings.lastGraphicsBenchmark
                    .put("${result.score}|${System.currentTimeMillis()}")
                    .save()
                updateBenchmarkValue()
                if (result.failed) {
                    TipDialog.Builder(context)
                        .setTitle(R.string.setting_graphics_benchmark_title)
                        .setMessage(context.getString(
                            R.string.setting_graphics_benchmark_failed,
                            result.failureReason ?: context.getString(R.string.generic_unknown)
                        ))
                        .setShowCancel(false)
                        .showDialog()
                    return@ended
                }
                showBenchmarkResult(result)
            }.execute()
        }
    }

    private fun updateBenchmarkValue() {
        val stored = AllSettings.lastGraphicsBenchmark.getValue().substringBefore('|').toIntOrNull()
        binding.graphicsBenchmarkValue.apply {
            text = if (stored == null || stored <= 0) {
                context.getString(R.string.setting_graphics_benchmark_not_run)
            } else {
                context.getString(
                    R.string.setting_graphics_benchmark_current,
                    AutoSettingsOptimizer.detectGpu(),
                    CurrentRenderer.name(requireContext()) + " - " +
                        GraphicsTuning.benchTier(stored).name.lowercase()
                )
            }
        }
    }

    private fun showBenchmarkResult(result: GraphicsBenchmark.Result) {
        val context = requireContext()
        val profile = AutoSettingsOptimizer.buildProfile(context, "")
        val resolution = GraphicsTuning.resolutionPercent(profile)
        val message = context.getString(
            R.string.setting_graphics_benchmark_result,
            result.score,
            result.tier,
            result.megapixelsPerSecond,
            result.durationMs.toInt(),
            result.glRenderer ?: ""
        ) +
            "\n" + context.getString(
                R.string.setting_graphics_benchmark_current,
                profile.gpuName,
                CurrentRenderer.name(context)
            ) +
            "\n" + context.getString(R.string.setting_graphics_benchmark_resolution, resolution)
        TipDialog.Builder(context)
            .setTitle(R.string.setting_graphics_benchmark_title)
            .setMessage(message)
            .setConfirm(R.string.setting_graphics_benchmark_apply)
            .setCancel(R.string.generic_close)
            .setConfirmClickListener {
                // Re-run the optimizer with the fresh score. It still respects any
                // renderer/driver the user chose by hand.
                AutoSettingsOptimizer.apply(context)
                android.widget.Toast.makeText(
                    context,
                    R.string.setting_graphics_benchmark_applied,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                updateBenchmarkValue()
            }
            .showDialog()
    }

    override fun slideIn(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.root, TurtleTransitions.enter()))
    }
}
