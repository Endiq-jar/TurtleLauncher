package com.endiq.turtlelauncher.launch

import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.utils.path.PathManager
import java.io.File
import java.security.MessageDigest
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Strips jar *sealing* from classpath jars.
 *
 * LWJGL 2 ships sealed jars: the manifest declares `Sealed: true`, which pins every `org.lwjgl.*`
 * package to that single jar. This launcher deliberately puts a second source of `org.lwjgl`
 * classes on the classpath (`lwjgl-glfw-classes.jar`, the Android-patched bridge, plus the ABI
 * override jars), so the moment the game touches a class from the other source the JVM aborts
 * with the classic Minecraft-on-Android crash:
 *
 * ```
 * Caused by: java.lang.SecurityException: sealing violation: package org.lwjgl is sealed
 *     at java.net.URLClassLoader.getAndVerifyPackage(...)
 * ```
 *
 * Sealing is only a manifest attribute, so the fix is to hand the JVM copies of the affected jars
 * with `Sealed` removed. The libraries on disk are never modified - copies live under the cache
 * directory and are reused until the source jar changes (path + size + mtime key).
 *
 * Notes on the rewrite:
 *  - signature files under META-INF (`.SF`, `.RSA`, `.DSA`, `.EC`) are dropped, because editing
 *    the manifest invalidates them and a broken signature is worse than no signature;
 *  - `META-INF/INDEX.LIST` is dropped, since it indexes the old jar;
 *  - every other entry is copied byte-for-byte, so classes and bundled natives are untouched.
 */
object JarSealing {
    private const val TAG = "JarSealing"
    private const val CACHE_DIR = "unsealed-jars"

    private val SIGNATURE_SUFFIXES = listOf(".SF", ".RSA", ".DSA", ".EC")
    private const val INDEX_LIST = "META-INF/INDEX.LIST"

    /** Rewrites every sealed jar in [classpath], using the launcher cache for the copies. */
    fun unsealClasspath(classpath: String): String =
        unsealClasspath(classpath, File(PathManager.DIR_CACHE, CACHE_DIR))

    /** Same as [unsealClasspath], with an explicit cache directory (tests, alternate storage). */
    fun unsealClasspath(classpath: String, cacheDir: File): String {
        if (classpath.isEmpty()) return classpath
        val separator = File.pathSeparator
        var changed = false
        val rewritten = classpath.split(separator).map { entry ->
            val replacement = unsealJarEntry(entry, cacheDir)
            if (replacement != entry) changed = true
            replacement
        }
        return if (changed) rewritten.joinToString(separator) else classpath
    }

    /**
     * Returns the path to use for one classpath entry: the unsealed copy when [entry] is a sealed
     * jar, otherwise the entry itself. Never throws - a jar that cannot be rewritten is used as-is
     * so the launch still has a chance to work.
     */
    fun unsealJarEntry(entry: String, cacheDir: File): String {
        if (!entry.endsWith(".jar", ignoreCase = true)) return entry
        val jar = File(entry)
        if (!jar.isFile || !declaresSealing(jar)) return entry
        return runCatching { unsealedCopy(jar, cacheDir).absolutePath }
            .onFailure { warn("Could not unseal ${jar.name}; using it as-is", it) }
            .getOrDefault(entry)
    }

    /** True when the jar's manifest seals the whole jar or any of its packages. */
    fun declaresSealing(jar: File): Boolean = runCatching {
        ZipFile(jar).use { zip ->
            val manifest = readManifest(zip) ?: return false
            val wholeJarSealed =
                manifest.mainAttributes.getValue(Attributes.Name.SEALED)?.equals("true", ignoreCase = true) == true
            wholeJarSealed || manifest.entries.values.any { attributes ->
                attributes.getValue(Attributes.Name.SEALED)?.equals("true", ignoreCase = true) == true
            }
        }
    }.onFailure { warn("Could not read ${jar.name}", it) }.getOrDefault(false)

    /** Cached unsealed copy of [jar] (written once per source version). */
    fun unsealedCopy(jar: File, cacheDir: File): File {
        cacheDir.mkdirs()
        val target = File(cacheDir, "${jar.nameWithoutExtension}-${cacheKey(jar)}.jar")
        if (target.isFile && target.length() > 0L) return target

        val temp = File(cacheDir, "${target.name}.${System.nanoTime()}.tmp")
        try {
            ZipFile(jar).use { zip ->
                val manifest = readManifest(zip)?.also(::stripSealing)
                ZipOutputStream(temp.outputStream().buffered()).use { out ->
                    if (manifest != null) {
                        out.putNextEntry(ZipEntry(JarFile.MANIFEST_NAME))
                        manifest.write(out)
                        out.closeEntry()
                    }
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory) {
                            out.putNextEntry(ZipEntry(entry.name))
                            out.closeEntry()
                            continue
                        }
                        if (!copyable(entry.name)) continue
                        val copy = ZipEntry(entry.name)
                        if (entry.time >= 0L) copy.time = entry.time
                        out.putNextEntry(copy)
                        zip.getInputStream(entry).use { input -> input.copyTo(out) }
                        out.closeEntry()
                    }
                }
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        } finally {
            runCatching { if (temp.exists()) temp.delete() }
        }
        info("Unsealed ${jar.name} -> ${target.name}")
        return target
    }

    // Logging is best-effort: this object also runs under plain JVM unit tests, where the
    // Android logging backend is unavailable.
    private fun info(message: String) {
        runCatching { Logging.i(TAG, message) }
    }

    private fun warn(message: String, error: Throwable) {
        runCatching { Logging.w(TAG, message, error) }
    }

    private fun readManifest(zip: ZipFile): Manifest? =
        zip.getEntry(JarFile.MANIFEST_NAME)?.let { entry ->
            zip.getInputStream(entry).use { input -> Manifest(input) }
        }

    private fun stripSealing(manifest: Manifest) {
        manifest.mainAttributes.remove(Attributes.Name.SEALED)
        manifest.entries.values.forEach { attributes ->
            attributes.remove(Attributes.Name.SEALED)
        }
    }

    private fun copyable(name: String): Boolean {
        val upper = name.uppercase()
        if (upper == JarFile.MANIFEST_NAME.uppercase()) return false
        if (upper == INDEX_LIST) return false
        if (!upper.startsWith("META-INF/")) return true
        return SIGNATURE_SUFFIXES.none { upper.endsWith(it) }
    }

    private fun cacheKey(jar: File): String {
        val stamp = "${jar.absolutePath}|${jar.length()}|${jar.lastModified()}"
        val digest = MessageDigest.getInstance("SHA-1").digest(stamp.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }
}
