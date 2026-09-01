package com.movtery.zalithlauncher.utils.path

import java.io.File

/**
 * The "common native library standard" shared by the Pojav-family launchers
 * (PojavLauncher, MojoLauncher, ZalithLauncher, FoldCraftLauncher and their forks).
 *
 * Every one of those launchers ships the same small set of `.so` files in the APK's
 * native-lib directory and exposes them to the game through the same two mechanisms:
 *
 *  1. `-Djava.library.path=<per-version natives cache>:<APK nativeLibraryDir>` — the
 *     writable per-version cache dir first (LWJGL/JNA/Netty extract and scratch there),
 *     then the app's read-only APK lib dir where the standard libraries actually live.
 *  2. `-Dorg.lwjgl.libname=<name>` — when a single shared `liblwjgl.so` is shared across
 *     Minecraft versions, this property re-points LWJGL at the ABI-correct build for the
 *     version being launched (see LaunchArgs' lwjgl-legacy override).
 *
 * Keeping this list and the path-composition rule in one place means a renderer plugin
 * (MobileGlues, FCL/Zalith renderer plugins) or a rebuilt native lib can be validated
 * against the same standard the game's own launch args are built from, instead of the
 * standard living as scattered string literals across LaunchArgs/CrashAnalyzer/Tools.
 */
object CommonNativeLibraries {

    /**
     * One entry of the standard native set.
     *
     * @param baseName     file name as it appears in the APK's native-lib dir (with `lib` prefix).
     * @param lwjglModule  the `org.lwjgl.*` module that loads it, or null when it isn't loaded
     *                     by LWJGL's own module system (e.g. gl4es is dlopen'd by the GL shim).
     * @param purpose      short human description for diagnostics/logs.
     */
    data class StandardLibrary(
        val baseName: String,
        val lwjglModule: String?,
        val purpose: String
    )

    /**
     * The canonical standard set. This matches the libraries every Pojav-family launcher
     * ships and that this project's own jniLibs/<abi>/ bundles (and its launch/crash code
     * references by name — see LaunchArgs and CrashAnalyzer).
     */
    val STANDARD_LIBRARIES: List<StandardLibrary> = listOf(
        StandardLibrary("liblwjgl.so", "org.lwjgl", "LWJGL core (window/system/GLFW)"),
        StandardLibrary("liblwjgl_opengl.so", "org.lwjgl.opengl", "LWJGL OpenGL binding"),
        StandardLibrary("libpojavexec.so", "org.lwjgl.glfw", "Pojav GLFW/exec shim (input + renderer dispatch)"),
        StandardLibrary("libgl4es_114.so", null, "GL4ES 1.1.4 GL→GLES translator"),
        StandardLibrary("libgl4es_115.so", null, "GL4ES 1.1.5 GL→GLES translator"),
        StandardLibrary("libopenal.so", "org.lwjgl.openal", "OpenAL audio")
    )

    /**
     * The standard `java.library.path` value: the writable per-version natives cache dir
     * first, then the APK's native-lib dir. Both must be present — the cache dir is where
     * LWJGL/JNA/Netty extract and scratch their own natives, and the APK dir is where the
     * [STANDARD_LIBRARIES] above are installed on disk.
     */
    @JvmStatic
    fun nativeLibraryPath(perVersionNativesDir: File, appNativeLibDir: String): String =
        "${perVersionNativesDir.absolutePath}:$appNativeLibDir"

    /**
     * Returns the [StandardLibrary.baseName]s from [STANDARD_LIBRARIES] that are missing from
     * [appNativeLibDir]. Empty when every standard library is present. Purely diagnostic —
     * used to put an actionable warning in the launch log when a rebuild drops one of the
     * standard libraries, rather than failing later with a bare UnsatisfiedLinkError.
     */
    @JvmStatic
    fun missingStandardLibraries(appNativeLibDir: String): List<String> {
        val dir = File(appNativeLibDir)
        return STANDARD_LIBRARIES
            .filter { !File(dir, it.baseName).isFile }
            .map { it.baseName }
    }
}
