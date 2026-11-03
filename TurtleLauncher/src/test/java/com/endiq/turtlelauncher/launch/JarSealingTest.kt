package com.endiq.turtlelauncher.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipFile

/**
 * Sealed LWJGL 2 jars plus unsealed LWJGL 3 bridge jars on the same classpath is what produces
 * `SecurityException: sealing violation: package org.lwjgl is sealed` on Forge 1.8.9 launches.
 * These tests pin the rewrite that avoids it.
 */
class JarSealingTest {
    @get:Rule val directory = TemporaryFolder()

    private fun writeJar(
        file: File,
        sealed: Boolean,
        signed: Boolean = false,
    ): File {
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes.putValue("Built-By", "JarSealingTest")
            if (sealed) mainAttributes[Attributes.Name.SEALED] = "true"
        }
        JarOutputStream(file.outputStream(), manifest).use { out ->
            out.putNextEntry(JarEntry("org/lwjgl/opengl/GL11.class"))
            out.write(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()))
            out.closeEntry()

            out.putNextEntry(JarEntry("org/lwjgl/LWJGLException.class"))
            out.write("lwjgl".toByteArray())
            out.closeEntry()

            out.putNextEntry(JarEntry("META-INF/natives/libjni.so"))
            out.write("native".toByteArray())
            out.closeEntry()

            if (signed) {
                out.putNextEntry(JarEntry("META-INF/LWJGL.SF"))
                out.write("Signature-Version: 1.0".toByteArray())
                out.closeEntry()
            }
        }
        return file
    }

    private fun entries(jar: File): Map<String, ByteArray> =
        ZipFile(jar).use { zip ->
            zip.entries().toList()
                .filter { !it.isDirectory }
                .associate { entry -> entry.name to zip.getInputStream(entry).use { it.readBytes() } }
        }

    @Test fun detectsSealingDeclaredOnlyByManifest() {
        val sealed = writeJar(File(directory.root, "lwjgl-sealed.jar"), sealed = true)
        val plain = writeJar(File(directory.root, "lwjgl-plain.jar"), sealed = false)

        assertTrue(JarSealing.declaresSealing(sealed))
        assertFalse(JarSealing.declaresSealing(plain))
    }

    @Test fun unsealedCopyKeepsContentAndDropsTheAttribute() {
        val sealed = writeJar(File(directory.root, "lwjgl.jar"), sealed = true)
        val cache = File(directory.root, "cache")
        val originalBytes = entries(sealed)

        val replacement = File(JarSealing.unsealJarEntry(sealed.absolutePath, cache))

        assertNotEquals(sealed.absolutePath, replacement.absolutePath)
        assertTrue(replacement.isFile)
        assertFalse("the copy must no longer declare sealing", JarSealing.declaresSealing(replacement))
        assertTrue("the source jar must not be modified", JarSealing.declaresSealing(sealed))
        assertEquals(originalBytes.keys, entries(replacement).keys)
        assertEquals(
            "org/lwjgl classes have to survive the rewrite",
            originalBytes["org/lwjgl/opengl/GL11.class"]?.toList(),
            entries(replacement)["org/lwjgl/opengl/GL11.class"]?.toList(),
        )
        assertEquals(
            "bundled natives have to survive the rewrite",
            originalBytes["META-INF/natives/libjni.so"]?.toList(),
            entries(replacement)["META-INF/natives/libjni.so"]?.toList(),
        )
    }

    @Test fun signaturesAndJarIndexAreDropped() {
        val sealed = writeJar(File(directory.root, "lwjgl-signed.jar"), sealed = true, signed = true)
        val replacement = File(JarSealing.unsealJarEntry(sealed.absolutePath, File(directory.root, "cache")))

        assertFalse(entries(replacement).containsKey("META-INF/LWJGL.SF"))
    }

    @Test fun unsealedJarsAndDirectoriesAreLeftAlone() {
        val plain = writeJar(File(directory.root, "bridge.jar"), sealed = false)
        val cache = File(directory.root, "cache")
        assertEquals(plain.absolutePath, JarSealing.unsealJarEntry(plain.absolutePath, cache))
        assertEquals("/nowhere/classes", JarSealing.unsealJarEntry("/nowhere/classes", cache))
        assertFalse("nothing to rewrite means nothing is written", cache.exists())
    }

    @Test fun classpathRewriteTouchesOnlySealedEntries() {
        val sealed = writeJar(File(directory.root, "lwjgl.jar"), sealed = true)
        val plain = writeJar(File(directory.root, "bridge.jar"), sealed = false)
        val cache = File(directory.root, "cache")
        val separator = File.pathSeparator
        val classpath = listOf("/game/lwjgl3", sealed.absolutePath, plain.absolutePath).joinToString(separator)

        val rewritten = JarSealing.unsealClasspath(classpath, cache)
        val parts = rewritten.split(separator)

        assertEquals(3, parts.size)
        assertEquals("/game/lwjgl3", parts[0])
        assertNotEquals(sealed.absolutePath, parts[1])
        assertEquals(plain.absolutePath, parts[2])
        assertFalse(JarSealing.declaresSealing(File(parts[1])))
    }

    @Test fun copiesAreCachedPerSourceVersion() {
        val sealed = writeJar(File(directory.root, "lwjgl.jar"), sealed = true)
        val cache = File(directory.root, "cache")

        val first = JarSealing.unsealJarEntry(sealed.absolutePath, cache)
        val second = JarSealing.unsealJarEntry(sealed.absolutePath, cache)

        assertEquals("the cached copy must be reused", first, second)
        assertTrue("only one copy may exist per source version", cache.listFiles()!!.size == 1)
    }
}
