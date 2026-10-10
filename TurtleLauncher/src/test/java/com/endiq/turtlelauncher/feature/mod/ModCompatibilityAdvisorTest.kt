package com.endiq.turtlelauncher.feature.mod

import com.endiq.turtlelauncher.feature.mod.parser.ModInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModCompatibilityAdvisorTest {
    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    @Test
    fun sodiumGetsTranslationLayerCaveatOnlyForKnownGl4esAndAngleRenderers() {
        val sodium = mod("sodium", "0.6.0")
        val mods = listOf(sodium)
        val folder = temporaryFolder.newFolder("sodium-mods")

        val gl4es = ModCompatibilityAdvisor.inspect(mods, folder, environment("GL4ES"))
        assertTrue(gl4es.any { it.key == "sodium-translation-layer" })

        val angle = ModCompatibilityAdvisor.inspect(mods, folder, environment("ANGLE"))
        assertTrue(angle.any { it.key == "sodium-translation-layer" })

        val zink = ModCompatibilityAdvisor.inspect(mods, folder, environment("ZINK"))
        assertFalse(zink.any { it.key == "sodium-translation-layer" })
    }

    @Test
    fun shaderAdviceUsesRendererCapabilityRatherThanAssumingEveryPackWorks() {
        val iris = mod("iris", "1.7.0")
        val folder = temporaryFolder.newFolder("shader-mods")

        val unsupported = ModCompatibilityAdvisor.inspect(listOf(iris), folder, environment("LTW"))
        assertTrue(unsupported.any { it.key == "shader-renderer" })

        val listedRenderer = ModCompatibilityAdvisor.inspect(
            listOf(iris), folder, environment("MOBILEGLUES", shaderSupport = true)
        )
        assertFalse(listedRenderer.any { it.key == "shader-renderer" })
    }

    @Test
    fun optifineIsDetectedFromJarNameWhenItsMetadataIsNotParsed() {
        val modsFolder = temporaryFolder.newFolder("optifine-mods")
        modsFolder.resolve("OptiFine_1.20.1_HD_U_I6.jar").writeBytes(byteArrayOf(1))

        val findings = ModCompatibilityAdvisor.inspect(
            emptyList(), modsFolder, environment("GL4ES")
        )

        assertTrue(findings.any { it.key == "shader-renderer" && "OptiFine" in it.affectedMods })
    }

    @Test
    fun voxyNoticeExplainsItsLauncherSpecificStackWithoutBlockingLaunch() {
        val voxy = mod("voxy", "0.2.18-beta")
        val folder = temporaryFolder.newFolder("voxy-mods")

        val findings = ModCompatibilityAdvisor.inspect(listOf(voxy), folder, environment("ZINK"))
        val voxyFinding = findings.single { it.key == "voxy-special-stack" }

        assertTrue(voxyFinding.message.contains("Zalith Launcher 2"))
        assertTrue(voxyFinding.message.contains("RocksDB"))
        assertTrue(voxyFinding.message.contains("not validated or bundled"))
    }

    @Test
    fun historicalPojavReportsAreExplicitlyLabeledAsHistorical() {
        val create = mod("create", "0.5.1")
        val folder = temporaryFolder.newFolder("create-mods")

        val finding = ModCompatibilityAdvisor.inspect(listOf(create), folder, environment("MOBILEGLUES"))
            .single { it.key == "historical-pojav-reports" }

        assertTrue(finding.message.contains("2021"))
        assertTrue(finding.message.contains("not a current compatibility verdict"))
    }

    @Test
    fun webDisplaysAndControllerModsReceivePlatformSpecificGuidance() {
        val webDisplays = mod("webdisplays", "1.0.0")
        val controller = mod("midnightcontrols", "1.9.0")
        val folder = temporaryFolder.newFolder("platform-mods")

        val findings = ModCompatibilityAdvisor.inspect(
            listOf(webDisplays, controller), folder, environment("MOBILEGLUES")
        )

        assertTrue(findings.any { it.key == "web-browser-native-runtime" && it.message.contains("Chromium") })
        assertTrue(findings.any { it.key == "controller-api" && it.message.contains("Gamepad Mapper") })
    }

    @Test
    fun ordinaryContentModsAreNotLabeledUnsupportedByDefault() {
        val mekanism = mod("mekanism", "10.4.0")
        val appliedEnergistics = mod("ae2", "15.0.0")
        val folder = temporaryFolder.newFolder("content-mods")

        assertEquals(
            emptyList<ModCompatibilityAdvisor.Finding>(),
            ModCompatibilityAdvisor.inspect(listOf(mekanism, appliedEnergistics), folder, environment("ZINK"))
        )
    }

    private fun environment(rendererId: String, shaderSupport: Boolean = false) =
        ModCompatibilityAdvisor.Environment(
            minecraftVersion = "1.20.1",
            rendererId = rendererId,
            rendererName = rendererId,
            rendererSupportsShaderPacks = shaderSupport,
            rendererUsesAndroidOpenGlTranslationLayer = rendererId in setOf("GL4ES", "ANGLE")
        )

    private fun mod(id: String, version: String) =
        ModInfo(id, version, id, "", emptyArray())
}
