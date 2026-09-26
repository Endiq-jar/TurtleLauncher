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

package com.endiq.turtlelauncher.game.download.jvm_server

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.endiq.turtlelauncher.notification.NoticeProgress
import com.endiq.turtlelauncher.path.PathManager
import java.io.File

const val PROCESS_SERVICE_PORT = 53151 //random

/**
 * Log file of the install JVM process
 */
val LATEST_PROCESS_LOG_FILE: File get() = File(PathManager.DIR_FILES_EXTERNAL, "latest_process.log")

//Construction variables
const val SERVICE_JVM_ARGS = "service.jvm.args"
const val SERVICE_JRE_NAME = "service.jre.name"
const val SERVICE_USER_HOME = "service.user.home"
const val SERVICE_POST_SUMMARY = "service.post.summary"
const val SERVICE_POST_PROGRESS = "service.post.progress"

fun startJvmService(
    context: Context,
    jvmArgs: String,
    jreName: String? = null,
    userHome: String? = null,
    postSummary: String? = null,
    postProgress: NoticeProgress? = null,
) {
    val bundle = Bundle().apply {
        putString(SERVICE_JVM_ARGS, jvmArgs)
        putString(SERVICE_JRE_NAME, jreName)
        putString(SERVICE_USER_HOME, userHome)
        putString(SERVICE_POST_SUMMARY, postSummary)
        putParcelable(SERVICE_POST_PROGRESS, postProgress)
    }
    val intent = Intent(context, JvmService::class.java).apply {
        putExtras(bundle)
    }
    context.startForegroundService(intent)
}

/**
 * Checks whether the failure came from the system refusing to create the install process (:jvm)
 */
fun Throwable.isProcessStartRefused(): Boolean =
    this is SecurityException && message?.contains("process is bad") == true

/**
 * Suffixes of sub-process names that are mutex with the JVM install and must be cleared before running
 */
private val JVM_EXCLUSIVE_SUFFIXES = listOf(":jvm", ":game")

/** Checks whether a process name belongs to a mutex process that must vanish before the install JVM runs */
internal fun isJvmExclusiveProcess(processName: String, mainProcessName: String): Boolean =
    JVM_EXCLUSIVE_SUFFIXES.any { processName == mainProcessName + it }

/**
 * Lists still-running mutex sub-processes
 * @return an empty list means it's safe to run
 */
fun listBlockingProcesses(context: Context): List<String> {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mainProcessName = context.packageName
    val myPid = android.os.Process.myPid()

    return am.runningAppProcesses
        .filter { it.pid != myPid && isJvmExclusiveProcess(it.processName, mainProcessName) }
        .map { "${it.processName}(${it.pid})" }
}

/**
 * Stops all sub-processes mutex with the JVM install (:jvm, :game)
 */
fun stopAllNonMainProcesses(context: Context) {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mainPid = android.os.Process.myPid()
    val mainProcessName = context.packageName

    am.runningAppProcesses
        .filter { it.pid != mainPid && isJvmExclusiveProcess(it.processName, mainProcessName) }
        .forEach {
            try {
                android.os.Process.killProcess(it.pid)
            } catch (_: Exception) {
                //Ignored
            }
        }
}