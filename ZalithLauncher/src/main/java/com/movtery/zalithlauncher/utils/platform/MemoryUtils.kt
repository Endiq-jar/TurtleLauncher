package com.movtery.zalithlauncher.utils.platform

import android.app.ActivityManager
import android.content.Context

class MemoryUtils {
    companion object {
        private var activityManager: ActivityManager? = null

        // TurtleLauncher perf: totalMem is a fixed property of the device - it never changes
        // for the lifetime of the process - but this getter was doing a full getMemoryInfo()
        // Binder call into system_server on every single invocation. The in-game HUD calls it
        // 4x/second by default (gameMenuInfoRefreshRate = 250ms), and the RAM graph calls it
        // again on top of the used-memory query, so it was ~8 pointless IPCs/second while a
        // game is running. Read it once and cache; 0 means "not read successfully yet".
        @Volatile
        private var cachedTotalMem: Long = 0L

        private fun init(context: Context) {
            activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        }

        @JvmStatic
        fun getTotalDeviceMemory(context: Context): Long {
            cachedTotalMem.let { if (it > 0L) return it }

            activityManager ?: run { init(context) }

            val memInfo = ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memInfo)
            if (memInfo.totalMem > 0L) cachedTotalMem = memInfo.totalMem
            return memInfo.totalMem
        }

        @JvmStatic
        fun getUsedDeviceMemory(context: Context): Long {
            activityManager ?: run { init(context) }

            val memInfo = ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memInfo)
            return memInfo.totalMem - memInfo.availMem
        }

        @JvmStatic
        fun getFreeDeviceMemory(context: Context): Long {
            activityManager ?: run { init(context) }

            val memInfo = ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memInfo)
            return memInfo.availMem
        }

        /** TurtleLauncher: process-wide native heap currently allocated (android.os.Debug),
         *  not device-wide like the other stats here. Since this launcher runs the JVM in
         *  the same process via JNI rather than a separate one, this reflects native memory
         *  from both the launcher's own UI and, once running, the game/renderer libraries
         *  combined - there's no way to separate the two from this API. */
        @JvmStatic
        fun getNativeHeapAllocated(): Long = android.os.Debug.getNativeHeapAllocatedSize()
    }
}