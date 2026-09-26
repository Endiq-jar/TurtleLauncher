package com.endiq.turtlelauncher.game.version.mod.reader

import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile as JDKZipFile

/**
 * Checks whether the package contains OptiFine's signature
 */
fun JDKZipFile.checkOptiFine(): Boolean {
    return checkOptiFine { path ->
        getEntry(path)
    }
}

/**
 * Checks whether the package contains OptiFine's signature
 */
fun ZipFile.checkOptiFine(): Boolean {
    return checkOptiFine { path ->
        getEntry(path)
    }
}

private fun checkOptiFine(
    checkEntry: (String) -> Any?,
): Boolean {
    return checkEntry("optifine/Installer.class") != null &&
           // Only checked 1.7.2, 1.12.2, 1.21.11
           checkEntry("optifine/OptiFineTweaker.class") != null
}


/**
 * Tries to get OptiFine's version number
 */
fun JDKZipFile.tryGetOptiFineVersion(): String? {
    return tryGetOptiFineVersion(
        getEntry = { path -> getEntry(path) },
        getInputStream = { entry -> getInputStream(entry) }
    )
}

/**
 * Tries to get OptiFine's version number
 */
fun ZipFile.tryGetOptiFineVersion(): String? {
    return tryGetOptiFineVersion(
        getEntry = { path -> getEntry(path) },
        getInputStream = { entry -> getInputStream(entry) }
    )
}

private fun <T> tryGetOptiFineVersion(
    getEntry: (String) -> T,
    getInputStream: (T) -> InputStream,
): String? {
    val entryNames = listOf(
        "net/optifine/Config.class",
        "notch/net/optifine/Config.class",
        "Config.class",
        "VersionThread.class"
    )

    val entry = entryNames.firstNotNullOfOrNull { getEntry(it) }
        ?: return null

    val fullVersion = getInputStream(entry).use { inputStream ->
        val bytes = inputStream.readBytes()
        val pattern = "OptiFine_".toByteArray(StandardCharsets.US_ASCII)

        var start = -1
        for (i in 0..bytes.size - pattern.size) {
            var matched = true
            for (j in pattern.indices) {
                if (bytes[i + j] != pattern[j]) {
                    matched = false
                    break
                }
            }
            if (matched) {
                start = i
                break
            }
        }

        if (start == -1) return@use null

        var end = start
        while (end < bytes.size && bytes[end] in 32..122) {
            end++
        }

        String(bytes, start, end - start, StandardCharsets.US_ASCII)
    } ?: return null

    return fullVersion.split("_").drop(2).joinToString(" ")
}