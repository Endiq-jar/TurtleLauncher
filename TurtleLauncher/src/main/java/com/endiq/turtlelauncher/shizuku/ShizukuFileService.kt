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

import android.os.ParcelFileDescriptor
import java.io.File
import kotlin.system.exitProcess

/**
 * Implementation of [IShizukuFileService].
 *
 * This class is instantiated by the Shizuku runtime inside a dedicated
 * process running as the ADB shell user (uid 2000), which is what grants it
 * access to locations the app itself cannot reach.
 *
 * The class must stay public with a public no-arg constructor,
 * otherwise Shizuku cannot spawn the user service.
 */
class ShizukuFileService : IShizukuFileService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun listFiles(path: String): Array<String> {
        val dir = File(path)
        if (!dir.isDirectory) return emptyArray()
        return dir.listFiles()
            ?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            ?.map { file ->
                //pipe-delimited record; names containing '|' are sanitized
                val safeName = file.name.replace('|', '_')
                "$safeName|${file.isDirectory}|${file.length()}|${file.lastModified()}"
            }
            ?.toTypedArray()
            ?: emptyArray()
    }

    override fun exists(path: String): Boolean = File(path).exists()

    override fun isDirectory(path: String): Boolean = File(path).isDirectory

    override fun length(path: String): Long = File(path).length()

    override fun lastModified(path: String): Long = File(path).lastModified()

    override fun deleteRecursively(path: String): Boolean = runCatching {
        File(path).deleteRecursively()
    }.getOrDefault(false)

    override fun mkdirs(path: String): Boolean = File(path).mkdirs()

    override fun renameTo(from: String, to: String): Boolean =
        File(from).renameTo(File(to))

    override fun openRead(path: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)

    override fun openWrite(path: String): ParcelFileDescriptor {
        val file = File(path)
        file.parentFile?.mkdirs()
        return ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_CREATE or
                    ParcelFileDescriptor.MODE_WRITE_ONLY or
                    ParcelFileDescriptor.MODE_TRUNCATE
        )
    }

    override fun exec(command: String): String {
        val process = ProcessBuilder("sh", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        runCatching { process.waitFor() }
        return output.trim()
    }
}
