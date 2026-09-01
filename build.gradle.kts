buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.2.0")
        // StringFog: tried again per request, removed again. Concrete bug this time (not just
        // the general AGP9/abandoned-plugin risk flagged before): StringFog's own docs use the
        // legacy `classpath(...)` + `apply(plugin = ...)` mechanism, which I mixed with the
        // modern `plugins { id("stringfog") }` DSL block in the module's build.gradle.kts.
        // Those are two different Gradle plugin-resolution paths - `plugins {}` resolves via
        // the Plugin Portal/pluginManagement, NOT this buildscript classpath - so `id("stringfog")`
        // was never actually resolvable the way it was wired. Combined with the still-real AGP9
        // compatibility risk noted before, not worth re-attempting without a specific need.
    }
}
