package com.endiq.turtlelauncher.feature.mod

import com.endiq.turtlelauncher.feature.download.enums.ModLoader
import com.endiq.turtlelauncher.feature.mod.parser.ModInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ModMetadataConflictDetectorTest {
    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    @Test
    fun fabricBreakRuleOnlyMatchesTargetVersionsInItsRange() {
        val sourceJar = createJar(
            "source.jar",
            "fabric.mod.json",
            """{"schemaVersion":1,"id":"source","version":"1.0.0","breaks":{"sodium":">=0.5.0 <0.6.0"}}"""
        )
        val targetJar = createJar("sodium.jar", "fabric.mod.json", """{"id":"sodium","version":"0.5.4"}""")

        val source = modInfo("source", "1.0.0", sourceJar)
        val matchingTarget = modInfo("sodium", "0.5.4", targetJar)
        val nonMatchingTarget = modInfo("sodium", "0.6.0", targetJar)

        assertEquals(1, ModMetadataConflictDetector.detect(listOf(source, matchingTarget), ModLoader.FABRIC).size)
        assertTrue(ModMetadataConflictDetector.detect(listOf(source, nonMatchingTarget), ModLoader.FABRIC).isEmpty())
    }

    @Test
    fun quiltBreakRuleHonorsUnlessAndReportsDuplicateProvidedIds() {
        val sourceJar = createJar(
            "source.jar",
            "quilt.mod.json",
            """{"quilt_loader":{"id":"source","version":"1.0.0","breaks":[{"id":"sodium","reason":"known issue","unless":"indium"}]}}"""
        )
        val sodiumJar = createJar("sodium.jar", "quilt.mod.json", """{"quilt_loader":{"id":"sodium","version":"0.5.0"}}""")
        val indiumJar = createJar("indium.jar", "quilt.mod.json", """{"quilt_loader":{"id":"indium","version":"1.0.0"}}""")
        val aliasJar = createJar("alias.jar", "fabric.mod.json", """{"id":"other","version":"1.0.0"}""")

        val source = modInfo("source", "1.0.0", sourceJar)
        val sodium = modInfo("sodium", "0.5.0", sodiumJar)
        val indium = modInfo("indium", "1.0.0", indiumJar)

        assertTrue(ModMetadataConflictDetector.detect(listOf(source, sodium, indium), ModLoader.QUILT).isEmpty())

        val aliasProvider = modInfo("other", "1.0.0", aliasJar, setOf("sodium"))
        val findings = ModMetadataConflictDetector.detect(listOf(sodium, aliasProvider), ModLoader.FABRIC)
        assertTrue(findings.any { it.kind == ModMetadataConflictDetector.Kind.DUPLICATE_ID && it.modId == "sodium" })
    }

    @Test
    fun unsupportedPredicateIsKeptAsAnAdvisoryFinding() {
        assertEquals(null, ModMetadataConflictDetector.matchesAnyPredicate("1.0.0", listOf(">=")))
    }

    private fun modInfo(id: String, version: String, file: File, providedIds: Set<String> = emptySet()) =
        ModInfo(id, version, id, "", emptyArray(), emptyMap(), providedIds).apply { this.file = file }

    private fun createJar(fileName: String, entryName: String, json: String): File {
        val file = File(temporaryFolder.root, fileName)
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(entryName))
            zip.write(json.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return file
    }
}
