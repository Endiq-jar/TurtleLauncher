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

package com.endiq.turtlelauncher.game.version.installed

import android.content.Context
import android.os.Parcelable
import androidx.annotation.Keep
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonObject
import com.endiq.turtlelauncher.BuildConfig
import com.endiq.turtlelauncher.BuildKeys
import com.endiq.turtlelauncher.context.GlobalContext
import com.endiq.turtlelauncher.game.launch.LogName
import com.endiq.turtlelauncher.game.path.getVersionsHome
import com.endiq.turtlelauncher.game.support.touch_controller.VibrationHandler
import com.endiq.turtlelauncher.path.PathManager
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.setting.unit.getOrMin
import com.endiq.turtlelauncher.ui.screens.content.elements.QuickPlay
import com.endiq.turtlelauncher.utils.GSON
import com.endiq.turtlelauncher.utils.file.readText
import com.endiq.turtlelauncher.utils.logging.Logger
import com.endiq.turtlelauncher.utils.platform.getMaxMemoryForSettings
import com.endiq.turtlelauncher.utils.string.isNotEmptyOrBlank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.min

private const val TAG = "Version"

/**
 * A Minecraft version, distinguished by its version name
 * @param versionName Version name
 * @param gameHome the game directory holding the version (.minecraft)
 * @param versionConfig the per-version configuration
 * @param versionInfo version info
 * @param isValid the version's validity
 * @param versionType the version's type
 */
@Keep
@Parcelize
class Version(
    private val versionName: String,
    private val gameHome: String,
    private val versionConfig: VersionConfig,
    private val versionInfo: VersionInfo?,
    private val isValid: Boolean,
    val versionType: VersionType,
    /**
     * Controls whether the current account launches the game as an offline account
     */
    var offlineAccountLogin: Boolean = false,
    /**
     * Quick launch
     */
    var quickPlaySingle: QuickPlay? = null,
    /**
     * Enables the control proxy
     */
    var enableTouchProxy: Boolean = false
): Parcelable {
    /**
     * Whether the current version is pinned
     */
    @IgnoredOnParcel
    var pinnedState by mutableStateOf(versionConfig.pinned)
        private set

    /**
     * Sets and saves the version's pinned state
     */
    fun setPinnedAndSave(value: Boolean) {
        this.versionConfig.setPinnedAndSave(value) { state ->
            this.pinnedState = state
        }
    }

    /**
     * @return the version's game directory (.minecraft)
     */
    fun getGameHome(): String = gameHome

    /**
     * @return the folder the version belongs to
     */
    fun getVersionsFolder(): String = getVersionsHome(gameHome)

    /**
     * @return the version folder
     */
    fun getVersionPath(): File = File(getVersionsFolder(), versionName)

    /**
     * @return the version name
     */
    fun getVersionName(): String = versionName

    /**
     * @return the launcher version marker folder
     */
    fun getTurtleVersionPath(): File = File(getVersionPath(), BuildKeys.LAUNCHER_IDENTIFIER)

    /**
     * @return the game's last run log
     */
    fun getLatestLog(): File = File(getTurtleVersionPath(), LogName.GAME.fileName)

    /**
     * @return the icon set for the version
     */
    fun getVersionIconFile(): File = File(getTurtleVersionPath(), "VersionIcon.png")

    /**
     * Returns the client jar of the inherited (inheritsFrom) version
     * @param inheritsFrom the version's declared inheritance target
     * @return null when undeclared or the file doesn't exist
     */
    fun getInheritedClientJar(inheritsFrom: String?): File? =
        inheritsFrom?.let { File(File(getVersionsFolder(), it), "$it.jar") }
            ?.takeIf { jar -> jar.exists() }

    /**
     * @return the client jar file
     */
    fun getClientJar(): File = File(getVersionPath(), "$versionName.jar")

    /**
     * @return the version isolation config
     */
    fun getVersionConfig() = versionConfig

    /**
     * @return the version info
     */
    fun getVersionInfo() = versionInfo

    /**
     * @return whether the version description is usable
     */
    fun isSummaryValid(): Boolean {
        val summary = versionConfig.versionSummary
        return summary.isNotEmptyOrBlank()
    }

    /**
     * @return the version description
     */
    fun getVersionSummary(): String {
        if (!isValid()) throw IllegalStateException("The version is invalid!")
        return if (isSummaryValid()) versionConfig.versionSummary else versionInfo!!.getInfoString()
    }

    /**
     * @return version validity: whether the version JSON file and the version folder exist
     */
    fun isValid() = isValid && getVersionPath().exists()

    /**
     * @return whether version isolation is enabled
     */
    fun isIsolation() = versionConfig.isIsolation()

    /**
     * @return whether the game integrity check is skipped
     */
    fun skipGameIntegrityCheck() = versionConfig.skipGameIntegrityCheck()

    /**
     * @return the version's game folder path (the version folder when isolation is enabled)
     */
    fun getGameDir(): File {
        return if (versionConfig.isIsolation()) getVersionPath()
        //Without isolation a custom path may be used; when it's empty (unset), the default game path (.minecraft/) is returned
        else if (versionConfig.customPath.isNotEmpty()) File(versionConfig.customPath)
        else File(gameHome)
    }

    private fun String.getValueOrDefault(default: String): String = this.takeIf { it.isNotEmpty() } ?: default

    fun getRenderer(): String = versionConfig.renderer.getValueOrDefault(AllSettings.renderer.getValue())

    fun getDriver(): String = versionConfig.driver.getValueOrDefault(AllSettings.vulkanDriver.getValue())

    fun getGraphicsApi(): GraphicsApi = versionConfig.graphicsApi ?: AllSettings.graphicsApi.getValue()

    fun getControlPath(): File? = versionConfig.control
        .getValueOrDefault(AllSettings.controlLayout.getValue())
        .takeIf { it.isNotEmpty() }
        ?.let { fileName -> File(PathManager.DIR_CONTROL_LAYOUTS, fileName) }

    fun getJavaRuntime(): String = versionConfig.javaRuntime

    fun getJvmArgs(): String = versionConfig.jvmArgs

    fun getGameArgs(): String = versionConfig.gameArgs

    fun getCustomInfo(): String = versionConfig.customInfo.getValueOrDefault(AllSettings.versionCustomInfo.getValue())
        .replace("[zl_version]", BuildConfig.VERSION_NAME)

    fun getServerIp(): String? = versionConfig.serverIp.takeIf { it.isNotEmptyOrBlank() }

    fun getRamAllocation(context: Context = GlobalContext): Int = versionConfig.ramAllocation.takeIf { it >= 256 }?.let {
        min(it, getMaxMemoryForSettings(context))
    } ?: AllSettings.ramAllocation.getOrMin()

    fun getTouchVibrateDuration(): Int? = versionConfig.touchVibrateDuration.takeIf { it >= 80 }

    fun getTouchVibrateKind(): VibrationHandler.VibrateKind = versionConfig.touchVibrateKind ?: VibrationHandler.VibrateKind.default
}

/** Returns the launcher version marker folder for a version folder */
fun getTurtleVersionPath(versionFolder: File): File = File(versionFolder, BuildKeys.LAUNCHER_IDENTIFIER)

/** Returns the version icon file for a version folder */
fun getVersionIconFile(versionFolder: File): File = File(getTurtleVersionPath(versionFolder), "VersionIcon.png")

/** 26.2-snapshot-1 */
private const val VULKAN_RUNTIME_WORLD_VERSION = 4883

/**
 * Whether the game carries a Vulkan backend
 */
suspend fun Version.hasVulkanBackend(): Boolean {
    return withContext(Dispatchers.IO) {
        val clientJar = getClientJar()
        if (!clientJar.exists()) return@withContext false
        runCatching {
            //Read the data version inside the client
            ZipFile(clientJar).use { zip ->
                val worldVersion = zip.getEntry("version.json")
                    ?.readText(zip)
                    ?.let { GSON.fromJson(it, JsonObject::class.java) }
                    //https://zh.minecraft.wiki/w/%E7%89%88%E6%9C%AC%E4%BF%A1%E6%81%AF%E6%96%87%E4%BB%B6%E6%A0%BC%E5%BC%8F
                    ?.get("world_version")?.asInt
                worldVersion != null && worldVersion >= VULKAN_RUNTIME_WORLD_VERSION
            }
        }.onFailure { e ->
            Logger.warning(TAG, "Unable to determine the data version of this client Jar, possibly due to an outdated version.", e)
        }.getOrDefault(false)
    }
}