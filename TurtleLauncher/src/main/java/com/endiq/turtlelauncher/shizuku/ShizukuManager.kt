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

package com.endiq.turtlelauncher.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.endiq.turtlelauncher.BuildConfig
import com.endiq.turtlelauncher.utils.hasStoragePermissions
import com.endiq.turtlelauncher.utils.logging.Logger
import rikka.shizuku.Shizuku

/**
 * Central manager for everything Shizuku.
 *
 * - Tracks binder availability and permission state (observable from Compose)
 * - Runs the permission request flow
 * - Owns the connection to [ShizukuFileService], an ADB-level file service
 *   used to reach restricted locations such as Android/data
 * - Offers small convenience helpers ([exec], [grantAllFilesAccess]) that
 *   consume the service for the "replace storage permission flows" use-case
 */
object ShizukuManager {
    private const val TAG = "ShizukuManager"

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE = 0x51AC

    /** True when the Shizuku binder is alive (Shizuku installed & running). */
    var binderAlive by mutableStateOf(false)
        private set

    /** True when the user granted this app permission to use Shizuku. */
    var permissionGranted by mutableStateOf(false)
        private set

    /** True once [ShizukuFileService] is connected and usable. */
    var serviceReady by mutableStateOf(false)
        private set

    private var appContext: Context? = null

    @Volatile
    private var fileService: IShizukuFileService? = null

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Logger.info(TAG, "Shizuku binder received")
        refreshState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Logger.info(TAG, "Shizuku binder died")
        fileService = null
        serviceReady = false
        refreshState()
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_CODE) {
                Logger.info(TAG, "Shizuku permission result: $grantResult")
                refreshState()
            }
        }

    /**
     * Registers the global Shizuku listeners. Call once from the Application.
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        runCatching {
            Shizuku.addBinderReceivedListener(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
            refreshState()
        }.onFailure { throwable ->
            Logger.warning(TAG, "Shizuku API not available in this process", throwable)
        }
    }

    /** Reads the current binder/permission state into the observable fields. */
    fun refreshState() {
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        binderAlive = alive
        permissionGranted = alive && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (permissionGranted) ensureService()
    }

    /** True when the Shizuku app is installed on this device. */
    fun isShizukuInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    }.getOrDefault(false)

    /** Whether a permission request can be shown right now. */
    fun canRequestPermission(): Boolean =
        binderAlive && !permissionGranted && runCatching { !Shizuku.isPreV11() }.getOrDefault(false)

    /** Shows the Shizuku permission dialog if possible. */
    fun requestPermission() {
        if (!binderAlive) return
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
            .onFailure { Logger.error(TAG, "Failed to request Shizuku permission", it) }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                fileService = IShizukuFileService.Stub.asInterface(binder)
                serviceReady = true
                Logger.info(TAG, "Shizuku file service connected")
            } else {
                serviceReady = false
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            fileService = null
            serviceReady = false
            Logger.info(TAG, "Shizuku file service disconnected")
        }
    }

    /** Binds the ADB-level file service (no-op unless permission is granted). */
    fun ensureService() {
        val context = appContext ?: return
        if (!permissionGranted || fileService != null) return
        val args = Shizuku.newUserServiceArgs(
            ComponentName(context.packageName, ShizukuFileService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("shizuku_service")
            .debuggable(BuildConfig.DEBUG)
            .version(1)
        runCatching { Shizuku.bindUserService(args, serviceConnection) }
            .onFailure { Logger.error(TAG, "Failed to bind Shizuku user service", it) }
    }

    /** Direct access to the bound file service, when connected. */
    fun fileService(): IShizukuFileService? = fileService

    /**
     * Runs a shell command with ADB-level privileges.
     * @return command output, or null when the service is unavailable/failed
     */
    fun exec(command: String): String? {
        val service = fileService ?: run {
            Logger.warning(TAG, "exec() called but Shizuku service is not bound")
            return null
        }
        return runCatching { service.exec(command) }
            .onFailure { Logger.error(TAG, "exec($command) failed", it) }
            .getOrNull()
    }

    /**
     * Uses Shizuku to flip this app's MANAGE_EXTERNAL_STORAGE op to "allow",
     * replacing the manual "All files access" permission flow.
     * @return true if all-files access is effective after the attempt
     */
    fun grantAllFilesAccess(context: Context): Boolean {
        exec("appops set ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
        return hasStoragePermissions(context)
    }
}
