package com.endiq.turtlelauncher.renderer.mobileglues

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class MGConfigCodecTest {
    @Test fun preservesUnknownKeysAndUsesNativeIntegerFlags() {
        val root = JsonParser.parseString("""{"foreignSetting":"keep","enableANGLE":3,"multidrawMode":2}""").asJsonObject
        val config = MGConfigCodec.decode(root).copy(angle = AngleConfig.ForceDisable, extComputeShader = true)
        val encoded = MGConfigCodec.encode(config, MGConfigCodec.foreignKeysOf(root))
        assertEquals("keep", encoded["foreignSetting"].asString)
        assertEquals(2, encoded["enableANGLE"].asInt)
        assertEquals(1, encoded["enableExtComputeShader"].asInt)
        assertFalse(encoded.has("multidrawMode"))
    }

    @Test fun normalizesIncompletePrioritiesAndRetainsNativeVersionClamping() {
        val root = JsonParser.parseString("""{"customGLVersion":38,"maxGlslCacheSize":0,"multidrawOrder":"compute,compute,unknown"}""").asJsonObject
        val config = MGConfigCodec.decode(root)
        assertEquals(GlVersion.Gl33, config.glVersion)
        assertEquals(GlslCacheSize.Disabled, config.glslCache)
        assertEquals(MultidrawOrderItem.Compute, config.multidraw.globalOrder.first())
        assertEquals(MultidrawOrderItem.entries.size, config.multidraw.globalOrder.size)
    }

    @Test fun handlesBadTypesWithoutDroppingTheRestOfTheConfig() {
        val root = JsonParser.parseString("""{"enableNoError":[],"customGLVersion":"46","enableExtTimerQuery":0}""").asJsonObject
        val config = MGConfigCodec.decode(root)
        assertEquals(NoErrorConfig.Auto, config.noError)
        assertEquals(GlVersion.Gl46, config.glVersion)
        assertFalse(config.extTimerQuery)
    }
}
