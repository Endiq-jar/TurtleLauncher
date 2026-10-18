package com.endiq.turtlelauncher.ui.subassembly.view

import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.View
import android.widget.TextView
import com.getkeepsafe.taptargetview.TapTargetView
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.feature.inputstats.InputStatsTracker
import com.endiq.turtlelauncher.feature.inputstats.SessionStatsTracker
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.utils.NewbieGuideUtils
import com.endiq.turtlelauncher.utils.file.FileTools.Companion.formatFileSize
import com.endiq.turtlelauncher.utils.platform.MemoryUtils
import com.petterp.floatingx.assist.FxGravity
import com.petterp.floatingx.assist.helper.FxScopeHelper
import com.petterp.floatingx.listener.IFxViewLifecycle
import com.petterp.floatingx.listener.control.IFxScopeControl
import com.petterp.floatingx.view.FxViewHolder
import net.endiq.launcher.LwjglGlfwKeycode
import org.lwjgl.glfw.CallbackBridge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.app.ActivityManager
import android.content.Context
import com.endiq.turtlelauncher.feature.turtle.HudRefreshPolicy

class GameMenuViewWrapper(
    private val activity: Activity,
    private val listener: View.OnClickListener,
    private val showInfo: Boolean
) {
    companion object {
        private const val TAG = "GameMenuViewWrapper"
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    }

    private val handler = Handler(Looper.getMainLooper())
    private var refreshTask: Runnable? = null
    private var paused = false
    private var disposed = false
    private val memoryText: String = AllSettings.gameMenuMemoryText.getValue()

    private var showMemory: Boolean = false
    private var showNativeMemory: Boolean = false
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
    private var showRecordButton: Boolean = false
    private var buttonVisible: Boolean = false

    private var buttonScopeFx: IFxScopeControl? = null
    private var hudScopeFx: IFxScopeControl? = null

    init {
        refreshState()
        thinkForButtonVisibility()
        if (showInfo && !paused && !disposed) thinkForHudVisibility()
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
        if (showInfo && !paused && !disposed) thinkForHudVisibility()
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
            showRamGraph || showPing || showScreenshotButton || showRecordButton

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
        showNativeMemory = AllSettings.gameMenuShowNativeMemory.getValue()
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
        showRecordButton = AllSettings.showRecordButtonHud.getValue()
    }

    private fun updateInfoText() {
        hudScopeFx?.getView()?.apply {
            updateInfoText(this)
        }
    }

    private fun updateInfoText(view: View) {
        cancelInfoTimer()

        val memoryText: TextView = view.findViewById(R.id.memory_text)
        val fpsText: TextView = view.findViewById(R.id.fps_text)
        val cpsText: TextView = view.findViewById(R.id.cps_text)
        val systemResourcesText: TextView = view.findViewById(R.id.system_resources_text)
        val timeText: TextView = view.findViewById(R.id.time_text)
        val stopwatchText: TextView = view.findViewById(R.id.stopwatch_text)
        val playtimeText: TextView = view.findViewById(R.id.playtime_text)
        val pingText: TextView = view.findViewById(R.id.ping_text)
        val ramGraphView: com.endiq.turtlelauncher.feature.turtle.RamGraphView = view.findViewById(R.id.ram_graph_view)
        val screenshotButton: TextView = view.findViewById(R.id.screenshot_button)
        val recordButton: TextView = view.findViewById(R.id.record_button)
        val recordTimerText: TextView = view.findViewById(R.id.record_timer_text)

        val keystrokesBlock: View = view.findViewById(R.id.keystrokes_block)
        val keyW: TextView = view.findViewById(R.id.key_w)
        val keyA: TextView = view.findViewById(R.id.key_a)
        val keyS: TextView = view.findViewById(R.id.key_s)
        val keyD: TextView = view.findViewById(R.id.key_d)
        val keySpace: TextView = view.findViewById(R.id.key_space)

        val mousestrokesBlock: View = view.findViewById(R.id.mousestrokes_block)
        val mouseLeft: TextView = view.findViewById(R.id.mouse_left)
        val mouseRight: TextView = view.findViewById(R.id.mouse_right)

        val heldColor = android.graphics.Color.parseColor("#9CCF93") // Status: Success, when held
        val idleColor = android.graphics.Color.parseColor("#33F2F5F0") // faint white when idle

        val keyStates = mutableMapOf<TextView, Boolean>()
        fun setKeyColor(key: TextView, held: Boolean) {
            if (keyStates.put(key, held) != held) {
                key.setBackgroundColor(if (held) heldColor else idleColor)
            }
        }
        val memoryInfo = ActivityManager.MemoryInfo()
        val activityManager = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        var lastSlowUpdate = -1L
        var lastBatteryUpdate = -1L
        var batteryPercent = -1

        fun updateInfoText() {
            val now = SystemClock.uptimeMillis()
            val updateSlow = lastSlowUpdate < 0 || now - lastSlowUpdate >= 1000L
            if (updateSlow) lastSlowUpdate = now
            if (updateSlow && (showMemory || showRamGraph)) activityManager.getMemoryInfo(memoryInfo)
            if (showSystemResources && (lastBatteryUpdate < 0 || now - lastBatteryUpdate >= 30_000L)) {
                lastBatteryUpdate = now
                batteryPercent = getBatteryPercent()
            }
            if (showMemory && updateSlow) {
                val nativeSuffix = if (showNativeMemory) {
                    val nativeMb = MemoryUtils.getNativeHeapAllocated() / (1024 * 1024)
                    " (N: ${nativeMb}M)"
                } else ""
                val memoryString = "${this@GameMenuViewWrapper.memoryText} ${formatFileSize(memoryInfo.totalMem - memoryInfo.availMem)}/${formatFileSize(memoryInfo.totalMem)}$nativeSuffix".let { string ->
                    if (string.length > 40) return@let string.take(40)
                    string
                }
                memoryText.text = memoryString
            }
            if (showFPS && updateSlow) {
                val fpsString = "FPS: ${CallbackBridge.getCurrentFps()}"
                fpsText.text = fpsString
            }
            if (showCps && updateSlow) {
                val cpsString = "CPS: ${InputStatsTracker.getCps()}"
                cpsText.text = cpsString
            }
            if (showSystemResources && updateSlow) {
                val batteryString = if (batteryPercent >= 0) "Battery: $batteryPercent%" else "Battery: --"
                systemResourcesText.text = batteryString
            }
            if (showTime && updateSlow) {
                val timeString = timeFormat.format(Date())
                timeText.text = timeString
            }
            if (showStopwatch && updateSlow) {
                val stopwatchString = "Session: ${SessionStatsTracker.formatDuration(SessionStatsTracker.getSessionElapsedMs())}"
                stopwatchText.text = stopwatchString
            }
            if (showPlaytime && updateSlow) {
                val playtimeString = "Playtime: ${SessionStatsTracker.formatDuration(SessionStatsTracker.getTotalPlaytimeMs())}"
                playtimeText.text = playtimeString
            }
            if (showKeystrokes) {
                val wHeld = InputStatsTracker.isKeyHeld(LwjglGlfwKeycode.GLFW_KEY_W.toInt())
                val aHeld = InputStatsTracker.isKeyHeld(LwjglGlfwKeycode.GLFW_KEY_A.toInt())
                val sHeld = InputStatsTracker.isKeyHeld(LwjglGlfwKeycode.GLFW_KEY_S.toInt())
                val dHeld = InputStatsTracker.isKeyHeld(LwjglGlfwKeycode.GLFW_KEY_D.toInt())
                val spaceHeld = InputStatsTracker.isKeyHeld(LwjglGlfwKeycode.GLFW_KEY_SPACE.toInt())
                run {
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
                run {
                    setKeyColor(mouseLeft, leftHeld)
                    setKeyColor(mouseRight, rightHeld)
                }
            }
            if (showPing && updateSlow) {
                val ms = com.endiq.turtlelauncher.feature.turtle.PingTracker.getPingMs()
                val pingString = when {
                    com.endiq.turtlelauncher.feature.turtle.PingTracker.getTargetAddress().isEmpty() ->
                        activity.getString(R.string.hud_ping_tap_to_set)
                    ms >= 0 -> "Ping: $ms ms"
                    else -> "Ping: -- ms"
                }
                pingText.text = pingString
            }
            if (showRamGraph && updateSlow) {
                val usedMb = (memoryInfo.totalMem - memoryInfo.availMem) / (1024 * 1024)
                val totalMb = memoryInfo.totalMem / (1024 * 1024)
                ramGraphView.pushSample(usedMb, totalMb)
            }
            if (showRecordButton && updateSlow) {
                val recording = com.endiq.turtlelauncher.feature.turtle.ScreenRecorder.isRecording()
                run {
                    recordButton.visibility = View.VISIBLE
                    recordButton.text = if (recording) "⏹" else "⏺"
                    if (recording && AllSettings.recordingShowTimer.getValue()) {
                        val elapsed = com.endiq.turtlelauncher.feature.turtle.ScreenRecorder.getElapsedSeconds()
                        recordTimerText.text = String.format(Locale.US, "%02d:%02d", elapsed / 60, elapsed % 60)
                        recordTimerText.visibility = View.VISIBLE
                    } else {
                        recordTimerText.visibility = View.GONE
                    }
                }
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
                com.endiq.turtlelauncher.feature.turtle.ScreenshotHelper.takeScreenshot(activity)
            }
            if (!showRecordButton) {
                recordButton.visibility = View.GONE
                recordTimerText.visibility = View.GONE
            }
            recordButton.setOnClickListener {
                com.endiq.turtlelauncher.feature.turtle.ScreenRecorder.toggle(activity)
            }

            if (showPing) {
                com.endiq.turtlelauncher.feature.turtle.PingTracker.start()
            } else {
                com.endiq.turtlelauncher.feature.turtle.PingTracker.stop()
            }
            pingText.contentDescription = activity.getString(R.string.hud_ping_target)
            pingText.setOnClickListener { showPingTargetDialog() }

            val needsTimer = showMemory || showFPS || showCps || showKeystrokes || showMousestrokes ||
                showStopwatch || showPlaytime || showSystemResources || showTime || showPing || showRamGraph ||
                showRecordButton
            if (needsTimer) {
                val interval = HudRefreshPolicy.intervalMs(
                    AllSettings.gameMenuInfoRefreshRate.getValue().toLong(),
                    showKeystrokes || showMousestrokes
                )
                refreshTask = object : Runnable {
                    override fun run() {
                        if (paused || disposed || !view.isAttachedToWindow) return
                        updateInfoText()
                        handler.postDelayed(this, interval)
                    }
                }.also { handler.postDelayed(it, interval) }
            }
        }
    }

    /** Lifecycle-owned polling: no HUD work or ping sockets while the game is hidden. */
    fun onResume() {
        if (disposed) return
        paused = false
        if (showInfo) thinkForHudVisibility()
    }

    fun onPause() {
        paused = true
        cancelInfoTimer()
    }

    fun dispose() {
        disposed = true
        onPause()
        hudScopeFx?.cancel()
        buttonScopeFx?.cancel()
        hudScopeFx = null
        buttonScopeFx = null
    }

    private fun showPingTargetDialog() {
        val input = android.widget.EditText(activity).apply {
            setSingleLine(true)
            hint = activity.getString(R.string.hud_ping_target_hint)
            setText(com.endiq.turtlelauncher.feature.turtle.PingTracker.getTargetAddress())
        }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(activity)
            .setTitle(R.string.hud_ping_target)
            .setMessage(R.string.hud_ping_target_description)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (com.endiq.turtlelauncher.feature.turtle.PingTracker.setTarget(input.text.toString())) {
                    dialog.dismiss()
                } else input.error = activity.getString(R.string.hud_ping_target_invalid)
            }
        }
        dialog.show()
    }

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
        refreshTask?.let { handler.removeCallbacks(it) }
        refreshTask = null
        com.endiq.turtlelauncher.feature.turtle.PingTracker.stop()
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

    private fun getHudGravity(): FxGravity {
        return when(AllSettings.gameMenuLocation.getValue()) {
            "left_or_top", "left_or_bottom" -> FxGravity.RIGHT_OR_TOP
            else -> FxGravity.LEFT_OR_TOP
        }
    }
}
