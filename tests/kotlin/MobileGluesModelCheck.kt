package com.endiq.turtlelauncher.renderer.mobileglues

/** Lightweight JVM regression check. Run with MGConfig.kt and the Android/annotation classpath. */
fun main() {
    check(AngleConfig.ForceDisable.wire == 2)
    check(NoErrorConfig.Auto.wire == 0)
    check(NoErrorConfig.IgnoreShaderProgramFramebuffer.wire == 3)
    check(GlVersion.fromWire(47) == GlVersion.Gl46)
    check(GlVersion.fromWire(38) == GlVersion.Gl33)
    check(GlVersion.fromWire(31) == GlVersion.Gl32)
    check(GlVersion.fromWire(null) == GlVersion.Default)
    check(GlslCacheSize.fromWire(0) == GlslCacheSize.Disabled)
    check(GlslCacheSize.ofMebibytes(32).wire == 32)
    check(MultidrawBackend.parse("MULTI-BASE_VERTEX") == MultidrawBackend.MultiBaseVertex)
    val order = MultidrawOrderItem.normalize(listOf(MultidrawOrderItem.Compute, MultidrawOrderItem.Compute))
    check(order.size == MultidrawOrderItem.entries.size)
    check(order.first() == MultidrawOrderItem.Compute)
    val settings = MultidrawSettings().withGlobalOrder(order)
    for (entry in MultidrawEntry.entries) {
        check(settings.globalOrderFor(entry).toSet() == entry.implemented.toSet())
        val custom = settings.withException(entry, true)
        check(custom.hasException(entry))
        check(!custom.withException(entry, false).hasException(entry))
    }
    println("MobileGlues wire-value and MultiDraw model checks passed")
}
