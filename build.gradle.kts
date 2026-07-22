buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.2.0")
        // StringFog removed: the plugin is abandoned (last release mid-2024, no
        // maintainers) and its transform-API usage targets AGP 8.0.0 internals that
        // AGP 9's restructured transform pipeline is very likely to break on. There's
        // no newer version to upgrade to. This drops bytecode string obfuscation as
        // a feature; nothing else in the codebase depends on it (see grep in the
        // AGP9/OkHttp5 update notes).
    }
}