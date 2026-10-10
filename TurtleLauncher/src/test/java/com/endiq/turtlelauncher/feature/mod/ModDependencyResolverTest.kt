package com.endiq.turtlelauncher.feature.mod

import com.endiq.turtlelauncher.feature.mod.parser.ModInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModDependencyResolverTest {
    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    @Test
    fun providedAliasSatisfiesRequiredDependency() {
        val consumer = ModInfo(
            "consumer", "1.0.0", "Consumer", "", emptyArray(),
            mapOf("old_library_id" to ">=1.0.0")
        )
        val provider = ModInfo(
            "new_library", "2.0.0", "Library", "", emptyArray(),
            emptyMap(), setOf("old_library_id")
        )

        assertEquals(emptyList<String>(), ModDependencyResolver.missingDependencyIds(listOf(consumer, provider)))
    }

    @Test
    fun disabledJarIsNotMistakenForAnActiveDependency() {
        val modsFolder = temporaryFolder.newFolder("mods")
        val disabled = modsFolder.resolve("example-library.jar.disabled").apply { writeText("disabled") }

        assertEquals(disabled, ModDependencyResolver.findMatchingDisabledJar(modsFolder, "example_library"))
        assertNull(ModDependencyResolver.findMatchingDisabledJar(modsFolder, "another_library"))
    }
}
