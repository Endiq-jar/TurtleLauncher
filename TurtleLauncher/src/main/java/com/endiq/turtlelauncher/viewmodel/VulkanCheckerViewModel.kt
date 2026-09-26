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

package com.endiq.turtlelauncher.viewmodel

import androidx.annotation.Keep
import androidx.lifecycle.ViewModel
import com.endiq.turtlelauncher.game.plugin.driver.Driver
import com.endiq.turtlelauncher.game.plugin.driver.DriverPluginManager
import com.endiq.turtlelauncher.game.version.installed.Version
import com.endiq.turtlelauncher.game.version.installed.utils.isLowerVer
import com.endiq.turtlelauncher.path.PathManager
import com.endiq.turtlelauncher.setting.launcherMMKV
import com.endiq.turtlelauncher.ui.vulkan_checker.VCOperation
import com.endiq.turtlelauncher.utils.GSON
import com.endiq.turtlelauncher.utils.device.VulkanCapabilities
import com.endiq.turtlelauncher.utils.device.VulkanChecker
import com.endiq.turtlelauncher.utils.device.VulkanRequirements
import com.endiq.turtlelauncher.utils.device.normalizeMcVersion
import com.endiq.turtlelauncher.utils.device.profileSupport
import com.endiq.turtlelauncher.utils.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

private const val TAG = "VulkanCheckerViewModel"
private const val KEY_VULKAN_CHECK_RECORD = "vulkanCheckRecord"

@Keep
data class VulkanCheckRecord(
    val useTurnip: Boolean,
    val driverPath: String,
    /** Device-supported Minecraft version range: [Range.until] as exclusive upper bound, null = no upper bound */
    val supportedRanges: List<Range> = emptyList(),
    val version: Int = 0,
) {
    @Keep
    data class Range(
        val since: String,
        val until: String?
    )

    /** Checks whether a given Minecraft version falls inside the supported range */
    fun isSupported(mcVersion: String): Boolean {
        return supportedRanges.any { range ->
            !mcVersion.isLowerVer(range.since) &&
                    (range.until == null || mcVersion.isLowerVer(range.until))
        }
    }
}

class VulkanCheckerViewModel: ViewModel() {
    private val _vcOperation = MutableStateFlow<VCOperation>(VCOperation.None)
    val vcOperation = _vcOperation.asStateFlow()

    private val mutex = Mutex()

    var vulkanCheckerCont: (Continuation<Unit>)? = null
        private set

    fun changeOperation(operation: VCOperation) {
        _vcOperation.update { operation }
    }

    suspend fun waitForVulkanChecker(version: Version) {
        suspendCancellableCoroutine { cont ->
            vulkanCheckerCont = cont
            changeOperation(VCOperation.Tip(version))

            cont.invokeOnCancellation {
                vulkanCheckerCont = null
            }
        }
    }

    fun resumeCont() {
        vulkanCheckerCont?.resume(Unit)
        vulkanCheckerCont = null
    }

    suspend fun check(version: Version): Pair<VulkanCapabilities?, Boolean> {
        return mutex.withLock {
            val driver = DriverPluginManager.getDriver(version.getDriver())
            val useTurnip = !driver.isLauncher
            val capabilities = doCheck(useTurnip, driver)
            saveRecord(
                VulkanCheckRecord(
                    useTurnip = useTurnip,
                    driverPath = driverPath(useTurnip, driver),
                    supportedRanges = capabilities?.profileSupport()
                        ?.filter { it.supported }
                        ?.map { VulkanCheckRecord.Range(since = it.since, until = it.until) }
                        ?: emptyList(),
                    version = VulkanRequirements.VULKAN_REQUIREMENTS_VERSION
                )
            )
            capabilities to useTurnip
        }
    }

    suspend fun ensureSupported(version: Version): Boolean {
        val driver = DriverPluginManager.getDriver(version.getDriver())
        val useTurnip = !driver.isLauncher
        val mcVersion = version.getVersionInfo()?.minecraftVersion?.let(::normalizeMcVersion)
        val path = driverPath(useTurnip, driver)

        loadRecord()?.takeIf { last ->
            //Check with the current version detector; mismatch invalidates cached data
            last.version == VulkanRequirements.VULKAN_REQUIREMENTS_VERSION &&
            //Check Turnip environment consistency; mismatch invalidates cached data
            last.useTurnip == useTurnip && last.driverPath == path
        }?.let {
            //Same driver state: device capability unchanged; judge by cached supported range
            return mcVersion != null && it.isSupported(mcVersion)
        }

        //No record or driver mismatch: run the full detection and persist the result
        waitForVulkanChecker(version)
        val record = loadRecord() ?: return false
        return mcVersion != null && record.isSupported(mcVersion)
    }

    private fun driverPath(useTurnip: Boolean, driver: Driver): String {
        return if (useTurnip) driver.path else ""
    }

    private suspend fun doCheck(useTurnip: Boolean, driver: Driver): VulkanCapabilities? {
        return withContext(Dispatchers.IO) {
            if (useTurnip) {
                val tempDir = File(PathManager.DIR_CACHE, "vulkan_temp")
                VulkanChecker.checkCapabilities(null, driver.path, tempDir.absolutePath)
            } else {
                VulkanChecker.checkCapabilities(null, null, null)
            }
        }
    }

    private fun loadRecord(): VulkanCheckRecord? {
        val json = launcherMMKV().getString(KEY_VULKAN_CHECK_RECORD, null) ?: return null
        return runCatching {
            GSON.fromJson(json, VulkanCheckRecord::class.java)
        }.onFailure { e ->
            Logger.warning(TAG, "Failed to read vulkan check record", e)
        }.getOrNull()
    }

    private fun saveRecord(record: VulkanCheckRecord) {
        runCatching {
            launcherMMKV()
                .putString(KEY_VULKAN_CHECK_RECORD, GSON.toJson(record))
                .apply()
        }.onFailure { e ->
            Logger.warning(TAG, "Failed to save vulkan check record", e)
        }
    }
}