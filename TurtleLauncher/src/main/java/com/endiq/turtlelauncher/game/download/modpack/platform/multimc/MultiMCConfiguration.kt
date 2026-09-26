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
package com.endiq.turtlelauncher.game.download.modpack.platform.multimc

import java.io.File
import java.util.Optional
import java.util.Properties

/**
 * Instance configuration of a MultiMC modpack
 * Data structure reference: [HMCL](https://github.com/HMCL-dev/HMCL/blob/bb3d03f/HMCLCore/src/main/java/org/jackhuang/hmcl/mod/multimc/MultiMCInstanceConfiguration.java)
 * @param name instance name
 * @param gameVersion the instance's game version
 * @param permGen JVM PermGen memory size
 * @param wrapperCommand command used to launch the JVM
 * @param preLaunchCommand command run before the game starts
 * @param postExitCommand command run after the game exits
 * @param notes instance description
 * @param javaPath JVM install path
 * @param jvmArgs JVM launch arguments
 * @param isFullscreen whether to launch Minecraft fullscreen
 * @param width the game window's initial width
 * @param height the game window's initial height
 * @param maxMemory maximum allocatable JVM memory
 * @param minMemory minimum allocatable JVM memory
 * @param joinServerOnLaunch automatically joins a server when the game launches
 * @param isShowConsole whether to show the console window at launch
 * @param isShowConsoleOnError whether to show the console window on crash
 * @param isAutoCloseConsole whether to auto-close the console window when the game stops
 * @param isOverrideMemory whether to force-apply the [maxMemory], [minMemory], [permGen] memory settings
 * @param isOverrideJavaLocation whether to force-apply the [javaPath] Java path setting
 * @param isOverrideJavaArgs whether to force-apply the [jvmArgs] JVM argument setting
 * @param isOverrideConsole whether to force-apply the [isShowConsole], [isShowConsoleOnError], [isAutoCloseConsole] console settings
 * @param isOverrideCommands whether to force-apply the [preLaunchCommand], [postExitCommand], [wrapperCommand] settings
 * @param isOverrideWindow whether to force-apply the [height], [width], [isFullscreen] window settings
 */
data class MultiMCConfiguration(
    val instanceType: String?, // InstanceType
    val name: String?, // name
    val gameVersion: String?, // IntendedVersion
    val permGen: Int?, // PermGen
    val wrapperCommand: String?, // WrapperCommand
    val preLaunchCommand: String?, // PreLaunchCommand
    val postExitCommand: String?, // PostExitCommand
    val notes: String?, // notes
    val javaPath: String?, // JavaPath
    val jvmArgs: String?, // JvmArgs
    val isFullscreen: Boolean, // LaunchMaximized
    val width: Int?, // MinecraftWinWidth
    val height: Int?, // MinecraftWinHeight
    val maxMemory: Int?, // MaxMemAlloc
    val minMemory: Int?, // MinMemAlloc
    val joinServerOnLaunch: String?, // JoinServerOnLaunchAddress
    val isShowConsole: Boolean, // ShowConsole
    val isShowConsoleOnError: Boolean, // ShowConsoleOnError
    val isAutoCloseConsole: Boolean, // AutoCloseConsole
    val isOverrideMemory: Boolean, // OverrideMemory
    val isOverrideJavaLocation: Boolean, // OverrideJavaLocation
    val isOverrideJavaArgs: Boolean, // OverrideJavaArgs
    val isOverrideConsole: Boolean, // OverrideConsole
    val isOverrideCommands: Boolean, // OverrideCommands
    val isOverrideWindow: Boolean, // OverrideWindow
    val iconKey: String?
) {
    /**
     * @param instanceName the instance's name
     * @param gameVersion Minecraft game version
     */
    constructor(
        properties: Properties,
        instanceName: String? = null,
        gameVersion: String? = null
    ): this(
        instanceType = readValue(properties, "InstanceType"),
        isAutoCloseConsole = readValue(properties, "AutoCloseConsole").toBoolean(),
        gameVersion = gameVersion ?: readValue(properties, "IntendedVersion"),
        javaPath = readValue(properties, "JavaPath"),
        jvmArgs = readValue(properties, "JvmArgs"),
        isFullscreen = readValue(properties, "LaunchMaximized").toBoolean(),
        maxMemory = readValue(properties, "MaxMemAlloc")?.toIntOrNull(),
        minMemory = readValue(properties, "MinMemAlloc")?.toIntOrNull(),
        joinServerOnLaunch = readValue(properties, "JoinServerOnLaunchAddress"),
        height = readValue(properties, "MinecraftWinHeight")?.toIntOrNull(),
        width = readValue(properties, "MinecraftWinWidth")?.toIntOrNull(),
        isOverrideCommands = readValue(properties, "OverrideCommands").toBoolean(),
        isOverrideConsole = readValue(properties, "OverrideConsole").toBoolean(),
        isOverrideJavaArgs = readValue(properties, "OverrideJavaArgs").toBoolean(),
        isOverrideJavaLocation = readValue(properties, "OverrideJavaLocation").toBoolean(),
        isOverrideMemory = readValue(properties, "OverrideMemory").toBoolean(),
        isOverrideWindow = readValue(properties, "OverrideWindow").toBoolean(),
        permGen = readValue(properties, "PermGen")?.toIntOrNull(),
        postExitCommand = readValue(properties, "PostExitCommand"),
        preLaunchCommand = readValue(properties, "PreLaunchCommand"),
        isShowConsole = readValue(properties, "ShowConsole").toBoolean(),
        isShowConsoleOnError = readValue(properties, "ShowConsoleOnError").toBoolean(),
        wrapperCommand = readValue(properties, "WrapperCommand"),
        name = instanceName ?: readValue(properties, "name"),
        notes = Optional.ofNullable<String?>(readValue(properties, "notes")).orElse(""),
        iconKey = readValue(properties, "iconKey")
    )

    fun toProperties(): Properties {
        val p = Properties()
        if (instanceType != null) p.setProperty("InstanceType", instanceType)
        p.setProperty("AutoCloseConsole", isAutoCloseConsole.toString())
        if (gameVersion != null) p.setProperty("IntendedVersion", gameVersion)
        if (javaPath != null) p.setProperty("JavaPath", javaPath)
        if (jvmArgs != null) p.setProperty("JvmArgs", jvmArgs)
        p.setProperty("LaunchMaximized", isFullscreen.toString())
        if (maxMemory != null) p.setProperty("MaxMemAlloc", maxMemory.toString())
        if (minMemory != null) p.setProperty("MinMemAlloc", minMemory.toString())
        if (height != null) p.setProperty("MinecraftWinHeight", height.toString())
        if (width != null) p.setProperty("MinecraftWinWidth", width.toString())
        p.setProperty("OverrideCommands", isOverrideCommands.toString())
        p.setProperty("OverrideConsole", isOverrideConsole.toString())
        p.setProperty("OverrideJavaArgs", isOverrideJavaArgs.toString())
        p.setProperty("OverrideJavaLocation", isOverrideJavaLocation.toString())
        p.setProperty("OverrideMemory", isOverrideMemory.toString())
        p.setProperty("OverrideWindow", isOverrideWindow.toString())
        if (permGen != null) p.setProperty("PermGen", permGen.toString())
        if (postExitCommand != null) p.setProperty("PostExitCommand", postExitCommand)
        if (preLaunchCommand != null) p.setProperty("PreLaunchCommand", preLaunchCommand)
        p.setProperty("ShowConsole", isShowConsole.toString())
        p.setProperty("ShowConsoleOnError", isShowConsoleOnError.toString())
        if (wrapperCommand != null) p.setProperty("WrapperCommand", wrapperCommand)
        if (name != null) p.setProperty("name", name)
        if (notes != null) p.setProperty("notes", notes)
        if (iconKey != null) p.setProperty("iconKey", iconKey)
        return p
    }
}

private fun readValue(properties: Properties, key: String?): String? {
    val value = properties.getProperty(key) ?: return null

    val l = value.length
    if (l >= 2 && value[0] == '"' && value[l - 1] == ':') {
        return value.take(l - 1)
    }
    return value
}

/**
 * Finds the instance config file in the modpack and tries parsing it
 */
fun loadMMCConfigFromPack(root: File): MultiMCConfiguration? {
    //Configuration file
    val configuration = File(root, "instance.cfg")
    return if (configuration.exists() && configuration.isFile) {
        val properties = Properties()
        configuration.reader(Charsets.UTF_8).use { isr ->
            properties.load(isr)
        }
        MultiMCConfiguration(properties = properties)
    } else {
        null
    }
}