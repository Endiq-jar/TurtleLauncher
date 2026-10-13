package com.endiq.turtlelauncher.renderer.angle

/**
 * Static facts about MojoLauncher's ANGLE plugin (`git.mojo.angle`), the companion app published at
 * https://github.com/MojoLauncher/AnglePlugin that ships a current ANGLE build as one pair of native
 * libraries: `libEGL_angle.so` + `libGLESv2_angle.so`.
 *
 * Why a launcher that already bundles ANGLE would care: ANGLE renders OpenGL ES on top of Vulkan, so
 * it *replaces the device's own GLES/EGL driver*. On PowerVR / Imagination GPUs that vendor driver is
 * the piece that hangs, black-screens or refuses to hand out a context for Minecraft 26.x's SDL3
 * windowing path (see CrashAnalyzer's "SDL3 backend" and "no GL context" rules) - pointing the launch
 * at ANGLE is what unblocks 26.3 there. The plugin is preferred over the ANGLE copy inside the APK
 * because it can be updated independently of the launcher (and is a newer build than the bundled one).
 *
 * Nothing here downloads or installs anything by itself - [AnglePluginManager] owns discovery, the
 * plugin APK install/update and the runtime state; [AngleLaunchPolicy] owns the launch decision.
 */
object AnglePlugin {
    /** Android package name of the plugin app. Also its manifest `namespace` (git.mojo.angle). */
    const val PACKAGE_NAME: String = "git.mojo.angle"

    /** Release the launcher tracks. Mojo re-uploads the same `rel` tag on every build, so the asset's
     *  size/updated-at (not the tag name) is what tells us an update is available. */
    const val RELEASE_TAG: String = "rel"
    const val RELEASE_NAME: String = "Mojo ANGLE Plugin"
    const val ASSET_NAME: String = "app-debug.apk"

    const val RELEASE_API_URL: String =
        "https://api.github.com/repos/MojoLauncher/AnglePlugin/releases/tags/$RELEASE_TAG"
    const val RELEASE_PAGE_URL: String =
        "https://github.com/MojoLauncher/AnglePlugin/releases/tag/$RELEASE_TAG"
    const val DOWNLOAD_URL: String =
        "https://github.com/MojoLauncher/AnglePlugin/releases/download/$RELEASE_TAG/$ASSET_NAME"

    /** The two files the plugin's ABI folders contain and ANGLE needs as a pair. */
    const val EGL_LIBRARY: String = "libEGL_angle.so"
    const val GLES_LIBRARY: String = "libGLESv2_angle.so"
    val LIBRARIES: List<String> = listOf(EGL_LIBRARY, GLES_LIBRARY)

    /** Size of the asset the launcher's docs/logs were last verified against. Used only for a
     *  "newer build upstream" log line when the plugin is not installed yet - never as a gate. */
    const val KNOWN_ASSET_SIZE: Long = 10_784_345L
}

/** Where a resolved ANGLE pair came from. */
enum class AngleSource {
    /** MojoLauncher's ANGLE plugin (`git.mojo.angle`) - preferred, updatable on its own. */
    PLUGIN,

    /** The ANGLE libraries shipped inside the launcher APK (`jniLibs`). */
    BUNDLED
}

/**
 * One usable ANGLE pair for this launch.
 *
 * Both paths are absolute on purpose: the *file* is what gets pre-loaded, and using one single path
 * per library everywhere (pre-load, `POJAVEXEC_EGL`, `SDL_EGL_LIBRARY`) is what guarantees every
 * consumer of ANGLE in the game process ends up in the same instance instead of a second copy of the
 * same library under a different linker namespace.
 */
data class AngleLibraries(
    val eglPath: String,
    val glesPath: String,
    val source: AngleSource,
    /** Plugin version code when [source] is [AngleSource.PLUGIN], else 0. */
    val versionCode: Int = 0,
    /** True when the files were unpacked out of the plugin APK by the launcher rather than read
     *  from the plugin's own `nativeLibraryDir` (see AnglePluginManager.ensureExtracted). */
    val extracted: Boolean = false
) {
    val eglName: String get() = eglPath.substringAfterLast('/')
    val glesName: String get() = glesPath.substringAfterLast('/')
}

/**
 * The launcher's answer to "should this launch run on ANGLE, and out of which copy?".
 *
 * @param force            whether ANGLE replaces the platform GLES/EGL for this launch
 * @param source           which copy won, when [force] is true
 * @param reason           short, log/UI-safe explanation of [force]
 * @param requestedByUser  true when Settings → Game → "Use ANGLE" asked for it (rather than the
 *                         automatic PowerVR hook)
 * @param rendererCanUseAngle false when the selected renderer does not ride the platform GLES/EGL at
 *                         all (Zink/VirGL/OSMesa - they bring their own EGL), so forcing ANGLE under
 *                         it would be meaningless
 */
data class AngleLaunchDecision(
    val force: Boolean,
    val source: AngleSource? = null,
    val reason: String = "",
    val requestedByUser: Boolean = false,
    val rendererCanUseAngle: Boolean = true
)
