package com.movtery.zalithlauncher.ui.subassembly.view

import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.View
import android.widget.TextView
import com.getkeepsafe.taptargetview.TapTargetView
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.feature.inputstats.InputStatsTracker
import com.movtery.zalithlauncher.feature.inputstats.SessionStatsTracker
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.task.TaskExecutors
import com.movtery.zalithlauncher.utils.NewbieGuideUtils
import com.movtery.zalithlauncher.utils.file.FileTools.Companion.formatFileSize
import com.movtery.zalithlauncher.utils.platform.MemoryUtils
import com.petterp.floatingx.assist.FxGravity
import com.petterp.floatingx.assist.helper.FxScopeHelper
import com.petterp.floatingx.listener.IFxViewLifecycle
import com.petterp.floatingx.listener.control.IFxScopeControl
import com.petterp.floatingx.view.FxViewHolder
import net.kdt.pojavlaunch.LwjglGlfwKeycode
import org.lwjgl.glfw.CallbackBridge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

/**
 * TurtleLauncher: the in-game menu button and the info HUD (FPS, memory,
 * CPS, keystrokes, etc.) used to share one draggable floating bubble.
 * They're now two independent floating windows so the player can
 * position/drag them separately — tapping the gear opens the menu, the
 * info panel is purely informational and never intercepts taps.
 */
class GameMenuViewWrapper(
    private val activity: Activity,
    private val listener: View.OnClickListener,
    private val showInfo: Boolean
) {
    companion object {
        private const val TAG = "GameMenuViewWrapper"
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    }

    private var timer: Timer? = null
    private val memoryText: String = AllSettings.gameMenuMemoryText.getValue()

    private var showMemory: Boolean = false
    private var showFPS: Boolean = false
    private var showCps: Boolean = false
    private var showKeystrokes: Boolean = false
    private var showMousestrokes: Boolean = false
    private var showStopwatch: Boolean = false
    private var showPlaytime: Boolean = false
    private var showSystemResources: Boolean = false
    private var showTime: Boolean = false
    private var showRamGraph: Boolean = false
    private var showPing: Boolean = false
    private var showScreenshotButton: Boolean = false
    private var buttonVisible: Boolean = false

    private var buttonScopeFx: IFxScopeControl? = null
    private var hudScopeFx: IFxScopeControl? = null
    // TurtleLauncher: used only when hudModuleIndependentDrag is enabled — one
    // separate draggable FloatingX window per active module instead of one
    // combined block, keyed by module name.
    private val moduleScopes = mutableMapOf<String, IFxScopeControl>()
    private var independentDragActive: Boolean = false

    init {
        refreshState()
        thinkForButtonVisibility()
        if (showInfo) thinkForHudVisibility()
    }

    private fun getButtonWindow(): IFxScopeControl {
        return FxScopeHelper.Builder().apply {
            setLayout(R.layout.view_game_menu_window)
            setOnClickListener(0L, listener)
            setEnableEdgeAdsorption(false)
            addViewLifecycle(object : IFxViewLifecycle {
                override fun initView(holder: FxViewHolder) {
                    holder.view.alpha = AllSettings.gameMenuAlpha.getValue().toFloat() / 100f
                }

                override fun detached(view: View) {}
            })
            setGravity(getCurrentGravity())
        }.build().toControl(activity)
    }

    private fun getHudWindow(): IFxScopeControl {
        return FxScopeHelper.Builder().apply {
            setLayout(R.layout.view_game_info_hud)
            setEnableEdgeAdsorption(false)
            addViewLifecycle(object : IFxViewLifecycle {
                override fun initView(holder: FxViewHolder) {
                    holder.view.alpha = AllSettings.hudAlpha.getValue().toFloat() / 100f
                    updateInfoText(holder.view)
                }

                override fun detached(view: View) {
                    cancelInfoTimer()
                }
            })
            setGravity(getHudGravity())
        }.build().toControl(activity)
    }

    /** (module key, its root view id in view_game_info_hud.xml, a staggered starting gravity) */
    private val independentModules: List<Triple<String, Int, FxGravity>> = listOf(
        Triple("memory", R.id.memory_text, FxGravity.LEFT_OR_TOP),
        Triple("fps", R.id.fps_text, FxGravity.RIGHT_OR_TOP),
        Triple("cps", R.id.cps_text, FxGravity.LEFT_OR_TOP),
        Triple("systemResources", R.id.system_resources_text, FxGravity.RIGHT_OR_TOP),
        Triple("time", R.id.time_text, FxGravity.LEFT_OR_BOTTOM),
        Triple("stopwatch", R.id.stopwatch_text, FxGravity.RIGHT_OR_BOTTOM),
        Triple("playtime", R.id.playtime_text, FxGravity.LEFT_OR_BOTTOM),
        Triple("keystrokes", R.id.keystrokes_block, FxGravity.RIGHT_OR_BOTTOM),
        Triple("mousestrokes", R.id.mousestrokes_block, FxGravity.LEFT_OR_TOP),
        Triple("ping", R.id.ping_text, FxGravity.RIGHT_OR_TOP),
        Triple("ramGraph", R.id.ram_graph_view, FxGravity.LEFT_OR_BOTTOM),
        Triple("screenshot", R.id.screenshot_button, FxGravity.RIGHT_OR_BOTTOM)
    )

    private fun isModuleEnabled(key: String): Boolean = when (key) {
        "memory" -> showMemory
        "fps" -> showFPS
        "cps" -> showCps
        "systemResources" -> showSystemResources
        "time" -> showTime
        "stopwatch" -> showStopwatch
        "playtime" -> showPlaytime
        "keystrokes" -> showKeystrokes
        "mousestrokes" -> showMousestrokes
        "ping" -> showPing
        "ramGraph" -> showRamGraph
        "screenshot" -> showScreenshotButton
        else -> false
    }

    /**
     * TurtleLauncher: one independently-draggable FloatingX window per active
     * module. Each window reuses view_game_info_hud.xml (so the update logic
     * below stays shared with the combined-window mode) but hides every module
     * except the one it was created for, via [restrictToViewId].
     */
    private fun getModuleWindow(viewId: Int, gravity: FxGravity): IFxScopeControl {
        return FxScopeHelper.Builder().apply {
            setLayout(R.layout.view_game_info_hud)
            setEnableEdgeAdsorption(false)
            addViewLifecycle(object : IFxViewLifecycle {
                override fun initView(holder: FxViewHolder) {
                    holder.view.alpha = AllSettings.hudAlpha.getValue().toFloat() / 100f
                    updateInfoText(holder.view, restrictToViewId = viewId)
                }

                override fun detached(view: View) {}
            })
            setGravity(gravity)
        }.build().toControl(activity)
    }

    private fun thinkForIndependentModules() {
        independentDragActive = true
        hudScopeFx?.cancel()
        hudScopeFx = null

        independentModules.forEach { (key, viewId, gravity) ->
            val shouldShow = isModuleEnabled(key)
            val existing = moduleScopes[key]
            if (shouldShow) {
                if (existing == null) {
                    moduleScopes[key] = getModuleWindow(viewId, gravity).apply { show() }
                }
            } else {
                existing?.cancel()
                moduleScopes.remove(key)
            }
        }

        if (moduleScopes.isNotEmpty()) {
            cancelInfoTimer()
            timer = Timer().apply {
                schedule(object : TimerTask() {
                    override fun run() {
                        moduleScopes.forEach { (key, scope) ->
                            val viewId = independentModules.first { it.first == key }.second
                            scope.getView()?.let { updateInfoText(it, restrictToViewId = viewId) }
                        }
                        if (isModuleEnabled("ping")) com.movtery.zalithlauncher.feature.turtle.PingTracker.start()
                    }
                }, 0, AllSettings.gameMenuInfoRefreshRate.getValue().toLong())
            }
        } else {
            cancelInfoTimer()
        }
    }

    private fun cancelIndependentModules() {
        independentDragActive = false
        moduleScopes.values.forEach { it.cancel() }
        moduleScopes.clear()
    }

    private fun startNewbieGuide(mainView: View) {
        if (NewbieGuideUtils.showOnlyOne(TAG)) return
        TapTargetView.showFor(
            activity,
            NewbieGuideUtils.getSimpleTarget(activity, mainView,
                activity.getString(R.string.setting_category_game_menu),
                activity.getString(R.string.newbie_guide_game_menu)
            )
        )
    }

    /** Controls only the menu button's visibility (e.g. hidden when the control layout already has its own menu button). */
    fun setVisibility(visible: Boolean) {
        this.buttonVisible = visible
        thinkForButtonVisibility()
    }

    /** Called when relevant settings (HUD module toggles, alpha, position, etc.) change. */
    fun refreshSettingsState() {
        refreshState()
        thinkForButtonVisibility()
        if (showInfo) thinkForHudVisibility()
    }

    private fun thinkForButtonVisibility() {
        if (buttonVisible) {
            if (buttonScopeFx == null) {
                buttonScopeFx = getButtonWindow().apply {
                    show()
                    getView()?.let { startNewbieGuide(it) }
                }
            }
        } else {
            buttonScopeFx?.cancel()
            buttonScopeFx = null
        }
    }

    private fun thinkForHudVisibility() {
        val shouldShow = showMemory || showFPS || showCps || showKeystrokes || showMousestrokes ||
            showStopwatch || showPlaytime || showSystemResources || showTime ||
            showRamGraph || showPing || showScreenshotButton

        if (AllSettings.hudModuleIndependentDrag.getValue()) {
            if (shouldShow) {
                thinkForIndependentModules()
            } else {
                cancelIndependentModules()
                cancelInfoTimer()
            }
            return
        }

        // Coming back from independent-drag mode — tear down the per-module windows first.
        if (independentDragActive) cancelIndependentModules()

        if (shouldShow) {
            if (hudScopeFx != null) {
                updateInfoText()
            } else {
                hudScopeFx = getHudWindow().apply {
                    updateInfoText()
                    show()
                }
            }
        } else {
            hudScopeFx?.cancel()
            hudScopeFx = null
            cancelInfoTimer()
        }
    }

    private fun refreshState() {
        showMemory = AllSettings.gameMenuShowMemory.getValue()
        showFPS = AllSettings.gameMenuShowFPS.getValue()
        showCps = AllSettings.showCpsHud.getValue()
        showKeystrokes = AllSettings.showKeystrokesHud.getValue()
        showMousestrokes = AllSettings.showMousestrokesHud.getValue()
        showStopwatch = AllSettings.showStopwatchHud.getValue()
        showPlaytime = AllSettings.showPlaytimeHud.getValue()
        showSystemResources = AllSettings.showSystemResourcesHud.getValue()
        showTime = AllSettings.showTimeHud.getValue()
        showRamGraph = AllSettings.showRamGraphHud.getValue()
        showPing = AllSettings.showPingHud.getValue()
        showScreenshotButton = AllSettings.showScreenshotButtonHud.getValue()
    }

    private fun updateInfoText() {
        hudScopeFx?.getView()?.apply {
            updateInfoText(this)
        }
    }

    private fun updateInfoText(view: View, restrictToViewId: Int? = null) {
        if (restrictToViewId == null) cancelInfoTimer()

        val memoryText: TextView = view.findViewById(R.id.memory_text)
        val fpsText: TextView = view.findViewById(R.id.fps_text)
        val cpsText: TextView = view.findViewById(R.id.cps_text)
        val systemResourcesText: TextView = view.findViewById(R.id.system_resources_text)
        val timeText: TextView = view.findViewById(R.id.time_text)
        val stopwatchText: TextView = view.findViewById(R.id.stopwatch_text)
        val playtimeText: TextView = view.findViewById(R.id.playtime_text)
        val pingText: TextView = view.findViewById(R.id.ping_text)
        val ramGraphView: com.movtery.zalithlauncher.feature.turtle.RamGraphView = view.findViewById(R.id.ram_graph_view)
        val screenshotButton: TextView = view.findViewById(R.id.screenshot_button)

        val keystrokesBlock: View = view.findViewById(R.id.keystrokes_block)
        val keyW: TextView = view.findViewById(R.id.key_w)
        val keyA: TextView = view.findViewById(R.id.key_a)
        val keyS: TextView = view.findViewById(R.id.key_s)
        val keyD: TextView = view.findViewById(R.id.key_d)
        val keySpace: TextView = view.findViewById(R.id.key_space)

        val mousestrokesBlock: View = view.findViewById(R.id.mousestrokes_block)
        val mouseLeft: TextView = view.findViewById(R.id.mouse_left)
        val mouseRight: TextView = view.findViewById(R.id.mouse_right)

        val heldColor = android.graphics.Color.parseColor("#4CAF50") // accent green when held
        val idleColor = android.graphics.Color.parseColor("#33FFFFFF") // faint white when idle

        fun setKeyColor(key: TextView, held: Boolean) {
            key.setBackgroundColor(if (held) heldColor else idleColor)
        }

        fun updateInfoText() {
            if (showMemory) {
                val memoryString = "${this@GameMenuViewWrapper.memoryText} ${getUsedDeviceMemory()}/${getTotalDeviceMemory()}".let { string ->
                    if (string.length > 40) return@let string.take(40)
                    string
                }
                TaskExecutors.runInUIThread { memoryText.text = memoryString }
            }
            if (showFPS) {
                val fpsString = "FPS: ${CallbackBridge.getCurrentFps()}"
                TaskExecutors.runInUIThread { fpsText.text = fpsString }
            }
            if (showCps) {
                val cpsString = "CPS: ${InputStatsTracker.getCps()}"
                TaskExecutors.runInUIThread { cpsText.text = cpsString }
            }
            if (showSystemResources) {
                val batteryPercent = getBatteryPercent()
                val batteryString = if (batteryPercent >= 0) "Battery: $batteryPercent%" else "Battery: --"
                TaskExecutors.runInUIThread { systemResourcesText.text = batteryString }
            }
            if (showTime) {
                val timeString = timeFormat.format(Date())
                TaskExecutors.runInUIThread { timeText.text = timeString }
            }
            if (showStopwatch) {
                val stopwatchString = "Session: ${SessionStatsTracker.formatDuration(SessionStatsTracker.getSessionElapsedMs())}"
                TaskExecutors.runInUIThread { stopwatchText.text = stopwatchString }
            }
            if (showPlaytime) {
                val playtimeString = "Playtime: ${SessionStatsTracker.formatDuration(SessionStatsTracker.getTotalPlaytimeMs())}"
                TaskExecutors.runInUIThread { playtimeText.text = playtimeString }
            }
            if (showKeystrokes) {
                val wHeld = InputStatsTracker.isKeyHeld(LwjglGlfwKeycode.GLFW_KEY_W.toInt())
                val aHeld = InputStatsTracker.isKeyHeld(LwjglGlfwKeycode.GLFW_KEY_A.toInt())
                val sHeld = InputStatsTracker.isKeyHeld(LwjglGlfwKeycode.GLFW_KEY_S.toInt())
                val dHeld = InputStatsTracker.isKeyHeld(LwjglGlfwKeycode.GLFW_KEY_D.toInt())
                val spaceHeld = InputStatsTracker.isKeyHeld(LwjglGlfwKeycode.GLFW_KEY_SPACE.toInt())
                TaskExecutors.runInUIThread {
                    setKeyColor(keyW, wHeld)
                    setKeyColor(keyA, aHeld)
                    setKeyColor(keyS, sHeld)
                    setKeyColor(keyD, dHeld)
                    setKeyColor(keySpace, spaceHeld)
                }
            }
            if (showMousestrokes) {
                val leftHeld = InputStatsTracker.isLeftMouseHeld()
                val rightHeld = InputStatsTracker.isRightMouseHeld()
                TaskExecutors.runInUIThread {
                    setKeyColor(mouseLeft, leftHeld)
                    setKeyColor(mouseRight, rightHeld)
                }
            }
            if (showPing) {
                val ms = com.movtery.zalithlauncher.feature.turtle.PingTracker.getPingMs()
                val pingString = if (ms >= 0) "Ping: $ms ms" else "Ping: -- ms"
                TaskExecutors.runInUIThread { pingText.text = pingString }
            }
            if (showRamGraph) {
                val usedMb = MemoryUtils.getUsedDeviceMemory(activity) / (1024 * 1024)
                val totalMb = MemoryUtils.getTotalDeviceMemory(activity) / (1024 * 1024)
                TaskExecutors.runInUIThread { ramGraphView.pushSample(usedMb, totalMb) }
            }
        }

        updateInfoText()

        if (showInfo) {
            memoryText.visibility = if (showMemory) View.VISIBLE else View.GONE
            fpsText.visibility = if (showFPS) View.VISIBLE else View.GONE
            cpsText.visibility = if (showCps) View.VISIBLE else View.GONE
            systemResourcesText.visibility = if (showSystemResources) View.VISIBLE else View.GONE
            timeText.visibility = if (showTime) View.VISIBLE else View.GONE
            stopwatchText.visibility = if (showStopwatch) View.VISIBLE else View.GONE
            playtimeText.visibility = if (showPlaytime) View.VISIBLE else View.GONE
            keystrokesBlock.visibility = if (showKeystrokes) View.VISIBLE else View.GONE
            mousestrokesBlock.visibility = if (showMousestrokes) View.VISIBLE else View.GONE
            pingText.visibility = if (showPing) View.VISIBLE else View.GONE
            ramGraphView.visibility = if (showRamGraph) View.VISIBLE else View.GONE
            screenshotButton.visibility = if (showScreenshotButton) View.VISIBLE else View.GONE
            screenshotButton.setOnClickListener {
                com.movtery.zalithlauncher.feature.turtle.ScreenshotHelper.takeScreenshot(activity)
            }

            // TurtleLauncher: independent-drag mode — this window exists for exactly one
            // module, so force every other module's view GONE regardless of its global
            // toggle (those are shown in their own separate windows instead).
            if (restrictToViewId != null) {
                listOf(
                    memoryText, fpsText, cpsText, systemResourcesText, timeText, stopwatchText,
                    playtimeText, keystrokesBlock, mousestrokesBlock, pingText, ramGraphView, screenshotButton
                ).forEach { moduleView ->
                    if (moduleView.id != restrictToViewId) moduleView.visibility = View.GONE
                }
                // Timing and the ping tracker are owned by thinkForIndependentModules's own
                // Timer in this mode — don't spawn a second one here.
                return
            }

            if (showPing) {
                com.movtery.zalithlauncher.feature.turtle.PingTracker.start()
            }

            val needsTimer = showMemory || showFPS || showCps || showKeystrokes || showMousestrokes ||
                showStopwatch || showPlaytime || showSystemResources || showTime || showPing || showRamGraph
            if (needsTimer) {
                timer = Timer().apply {
                    schedule(object : TimerTask() {
                        override fun run() {
                            updateInfoText()
                        }
                    }, 0, AllSettings.gameMenuInfoRefreshRate.getValue().toLong())
                }
            }
        }
    }

    private fun getUsedDeviceMemory(): String = formatFileSize(MemoryUtils.getUsedDeviceMemory(activity))

    private fun getTotalDeviceMemory(): String = formatFileSize(MemoryUtils.getTotalDeviceMemory(activity))

    /** Reads the current battery intent synchronously (sticky intent, no receiver actually registered). */
    private fun getBatteryPercent(): Int {
        return try {
            val intent = activity.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) (level * 100) / scale else -1
        } catch (e: Exception) {
            -1
        }
    }

    private fun cancelInfoTimer() {
        timer?.cancel()
        timer = null
    }

    private fun getCurrentGravity(): FxGravity {
        return when(AllSettings.gameMenuLocation.getValue()) {
            "left_or_top" -> FxGravity.LEFT_OR_TOP
            "left_or_bottom" -> FxGravity.LEFT_OR_BOTTOM
            "right_or_top" -> FxGravity.RIGHT_OR_TOP
            "right_or_bottom" -> FxGravity.RIGHT_OR_BOTTOM
            "top_or_center" -> FxGravity.TOP_OR_CENTER
            "bottom_or_center" -> FxGravity.BOTTOM_OR_CENTER
            "center" -> FxGravity.CENTER
            else -> FxGravity.CENTER
        }
    }

    /**
     * The info HUD defaults to the opposite top corner from the menu button
     * so the two never spawn stacked on top of each other. Both windows are
     * user-draggable (FloatingX), so this is just a sane starting position,
     * not a fixed one.
     */
    private fun getHudGravity(): FxGravity {
        return when(AllSettings.gameMenuLocation.getValue()) {
            "left_or_top", "left_or_bottom" -> FxGravity.RIGHT_OR_TOP
            else -> FxGravity.LEFT_OR_TOP
        }
    }
}
