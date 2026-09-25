/*
 * Turtle Launcher
 * Copyright (C) 2025 Endiq and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.endiq.turtlelauncher

import android.app.Application
import android.content.res.Configuration
import android.os.Process
import android.util.Log
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.kyant.fishnet.Fishnet
import com.endiq.turtlelauncher.context.GlobalContext
import com.endiq.turtlelauncher.context.refreshContext
import com.endiq.turtlelauncher.coroutine.TaskSystem
import com.endiq.turtlelauncher.game.account.AccountsManager
import com.endiq.turtlelauncher.game.path.GamePathManager
import com.endiq.turtlelauncher.keepalive.TaskKeepAlive
import com.endiq.turtlelauncher.path.PathManager
import com.endiq.turtlelauncher.setting.loadAllSettings
import com.endiq.turtlelauncher.ui.activities.showFatalError
import com.endiq.turtlelauncher.ui.activities.showLauncherCrash
import com.endiq.turtlelauncher.utils.device.Architecture
import com.endiq.turtlelauncher.utils.logging.Logger
import com.endiq.turtlelauncher.utils.writeCrashFile
import com.tencent.mmkv.MMKV
import dagger.hilt.android.HiltAndroidApp
import okio.Path.Companion.toOkioPath
import kotlin.properties.Delegates

@HiltAndroidApp
class TLApplication : Application(), SingletonImageLoader.Factory {
    companion object {
        @JvmStatic
        var DEVICE_ARCHITECTURE by Delegates.notNull<Int>()
    }

    override fun onCreate() {
        GlobalContext = this
        refreshContext(this)
        //初始化任务保活控制器，需在任何任务开始前完成初始化
        TaskKeepAlive.initialize(this)

        Thread.setDefaultUncaughtExceptionHandler { _, th ->
            //停止所有任务
            TaskSystem.stopAll()

            val throwable = if (th is SplashException) th.cause!!
            else th

            Logger.error("Startup", "An exception occurred", throwable)

            writeCrashFile(
                file = PathManager.FILE_CRASH_REPORT,
                throwable = throwable
            ) { t ->
                Logger.error("AppCrash", "An exception occurred while saving the crash report", t)
            }

            showLauncherCrash(this@TLApplication, throwable, th !is SplashException)
            Process.killProcess(Process.myPid())
        }

        super.onCreate()
        runCatching {
            Fishnet.init(this, PathManager.DIR_NATIVE_LOGS.absolutePath)

            MMKV.initialize(this)
            loadAllSettings(this)

            Logger.initialize(this)

            initializeData()
            PathManager.DIR_FILES_PRIVATE = getDir("files", MODE_PRIVATE)
            DEVICE_ARCHITECTURE = Architecture.getDeviceArchitecture()
            //Force x86 lib directory for Asus x86 based zenfones
            if (Architecture.isx86Device() && Architecture.is32BitsDevice) {
                val originalJNIDirectory = applicationInfo.nativeLibraryDir
                applicationInfo.nativeLibraryDir = originalJNIDirectory.take(originalJNIDirectory.lastIndexOf("/")) + "/x86"
            }
        }.onFailure { launchTh ->
            writeCrashFile(
                file = PathManager.FILE_CRASH_REPORT,
                throwable = launchTh
            ) {
                Log.w("TLApplication", "An exception occurred while saving the crash report", it)
            }
            showFatalError(this, launchTh)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshContext(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(20L * 1024 * 1024) // 20MB
                    .weakReferencesEnabled(true) //弱引用
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .diskCache {
                DiskCache.Builder()
                    .maxSizeBytes(512L * 1024 * 1024) // 512MB
                    .directory(PathManager.DIR_IMAGE_CACHE.toOkioPath())
                    .build()
            }
            .components {
                add(GifDecoder.Factory())
                add(SvgDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }

    private fun initializeData() {
        AccountsManager.initialize(this)
        GamePathManager.initialize(this)
    }
}
