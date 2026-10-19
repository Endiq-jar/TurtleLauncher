package com.endiq.turtlelauncher.ui.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiuixThemeAndSpringTest {

    @Test
    fun darkColorSchemeMatchesMiuixSpecs() {
        val dark = MiuixTheme.DarkColorScheme

        assertEquals(0xFF8FD9A8.toInt(), dark.primary)
        assertEquals(0xFF0B1F12.toInt(), dark.onPrimary)
        assertEquals(0xFF000000.toInt(), dark.surface)
        assertEquals(0xFF1A221C.toInt(), dark.surfaceContainer)
        assertEquals(0xFF434343.toInt(), dark.secondaryContainer)
        assertEquals(0xFF1B3A28.toInt(), dark.tertiaryContainer)
        assertEquals(0xFFB8EFCB.toInt(), dark.onTertiaryContainer)
        assertEquals(0xFF8A9C90.toInt(), dark.onBackgroundVariant)

        // Dark-only launcher: the scheme is dark regardless of the device configuration,
        // so programmatically drawn surfaces can never fall back to a light palette.
        assertTrue(MiuixTheme.isDarkMode(null))
        assertEquals(dark, MiuixTheme.colors(null))
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
