pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
    // TurtleLauncher fix: AGP's version used to come from a `buildscript { dependencies {
    // classpath("com.android.tools.build:gradle:9.2.0") } }` block in the root
    // build.gradle.kts. That block was dropped when build.gradle.kts was rewritten to use
    // the modern `plugins { id("com.android.application") }` DSL, but nothing replaced it -
    // the plugins{} DSL resolves via pluginManagement (Plugin Portal/google()), not the old
    // buildscript classpath, and needs its own version pin. With no version anywhere in the
    // build, Gradle failed with "Plugin [id: 'com.android.application'] was not found in any
    // of the following sources". Pinning it here fixes that for every project applying it.
    // Also pinning the Kotlin plugins here: build.gradle.kts's comment claims AGP 9's
    // built-in-Kotlin machinery supplies a Kotlin Gradle Plugin version automatically, so
    // org.jetbrains.kotlin.android/kapt were left unversioned on purpose - but gradle.properties
    // explicitly sets android.builtInKotlin=false, so that machinery is off and those plugins
    // would hit the exact same "not found" failure right after this one. 2.3.20 is Gradle
    // 9.4.1-compatible (Gradle 9's Kotlin DSL embeds 2.2.0; Gradle is tested through 2.4.20).
    plugins {
        id("com.android.application") version "9.2.0"
        id("org.jetbrains.kotlin.android") version "2.3.20"
        id("org.jetbrains.kotlin.kapt") version "2.3.20"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "Turtle Launcher"
include(":jre_lwjgl3glfw")
include(":ZalithLauncher")
