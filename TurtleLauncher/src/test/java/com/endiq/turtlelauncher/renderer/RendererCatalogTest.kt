package com.endiq.turtlelauncher.renderer

import com.endiq.turtlelauncher.renderer.renderers.AngleRenderer
import com.endiq.turtlelauncher.renderer.renderers.HolyGL4ESRenderer
import com.endiq.turtlelauncher.renderer.renderers.MobileGluesRenderer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererCatalogTest {
    @Test
    fun onlyExplicitlyDocumentedTranslationLayersReceiveThatClassification() {
        assertTrue(RendererCatalog.usesAndroidOpenGlTranslationLayer(HolyGL4ESRenderer.ID))
        assertTrue(RendererCatalog.usesAndroidOpenGlTranslationLayer(AngleRenderer.ID))
        assertFalse(RendererCatalog.usesAndroidOpenGlTranslationLayer(MobileGluesRenderer.ID))
        assertFalse(RendererCatalog.usesAndroidOpenGlTranslationLayer("third-party-renderer"))
    }
}
