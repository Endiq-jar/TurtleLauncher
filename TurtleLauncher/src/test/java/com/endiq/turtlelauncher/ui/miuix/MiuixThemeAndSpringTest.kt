package com.endiq.turtlelauncher.ui.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiuixThemeAndSpringTest {

    @Test
    fun darkAndLightColorSchemesMatchMiuixSpecs() {
        val dark = MiuixTheme.DarkColorScheme
        val light = MiuixTheme.LightColorScheme

        assertEquals(0xFF277AF7.toInt(), dark.primary)
        assertEquals(0xFF000000.toInt(), dark.surface)
        assertEquals(0xFF242424.toInt(), dark.surfaceContainer)
        assertEquals(0xFF434343.toInt(), dark.secondaryContainer)
        assertEquals(0xFF2B3B54.toInt(), dark.tertiaryContainer)
        assertEquals(0xFF4788FF.toInt(), dark.onTertiaryContainer)
        assertEquals(0xFF787E96.toInt(), dark.onBackgroundVariant)

        assertEquals(0xFF3482FF.toInt(), light.primary)
        assertEquals(0xFFF7F7F7.toInt(), light.surface)
        assertEquals(0xFFFFFFFF.toInt(), light.surfaceContainer)
        assertEquals(0xFFF0F0F0.toInt(), light.secondaryContainer)
        assertEquals(0xFFEAF2FF.toInt(), light.tertiaryContainer)
        assertEquals(0xFF8C93B0.toInt(), light.onBackgroundVariant)
    }

    @Test
    fun folmeSpringPhysicsConvergesToTarget() {
        val stiffness = MiuixSpring.stiffnessFromResponse(0.3f)
        // (2 * PI / 0.3)^2 ~= 438.65
        assertEquals(438.65f, stiffness, 0.5f)

        assertEquals(0f, MiuixSpring.evaluateSpring(0f, 0.85f, stiffness), 0.0001f)
        val mid = MiuixSpring.evaluateSpring(0.12f, 0.85f, stiffness)
        assertTrue(mid > 0.5f)
        val settled = MiuixSpring.evaluateSpring(0.5f, 0.85f, stiffness)
        assertEquals(1f, settled, 0.02f)
    }

    @Test
    fun squircleAndIndicationConstantsMatchMiuixSpecs() {
        assertEquals(0.643f, MiuixSquircle.SQUIRCLE_CONTROL, 0.0001f)
        assertEquals(1.1f, MiuixSquircle.SQUIRCLE_EXTENSION, 0.0001f)
        assertEquals(0.06f, MiuixTheme.IndicationAlpha.HOVER, 0.0001f)
        assertEquals(0.08f, MiuixTheme.IndicationAlpha.FOCUS, 0.0001f)
        assertEquals(0.10f, MiuixTheme.IndicationAlpha.PRESS, 0.0001f)
    }
}
