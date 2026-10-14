package com.endiq.turtlelauncher.renderer.angle

import android.content.Context
import com.endiq.turtlelauncher.context.ContextExecutor
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.renderer.renderers.MobileGluesRenderer
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.utils.path.PathManager
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/**
 * The single decision point for "does this launch run on ANGLE, and out of which copy?" - and the
 * only place that writes ANGLE into a game launch's environment.
 *
 * It exists because ANGLE is not a renderer in this launcher's model: it is the **GLES/EGL provider
 * underneath** whichever renderer is selected. MojoLauncher models it the same way (its GLESProvider
 * hands the ANGLE libraries to the GLES-based render specs), and that is what makes the plugin useful
 * on PowerVR: the desktop-GL wrapper stays whatever the user picked, and the vendor GLES driver that
 * was failing is replaced.
 *
 * Two ways ANGLE gets switched on:
 *
 * * **Asked for** - Settings → Game → "Use ANGLE" (works on any GPU; previously limited to renderers
 *   that ride `libEGL.so`).
 * * **Forced for this device** - Settings → Game → "Force ANGLE on PowerVR" (on by default), which
 *   covers PowerVR/Imagination GPUs and any device whose last launch ended without a GL context
 *   (see [AnglePluginManager.hasRecentGlContextFailure]). That is the "always active" path for the
 *   PowerVR + Minecraft 26.x case.
 *
 * Renderers that bring their own EGL (Zink, VirGL, Freedreno, VGPU - Mesa/OSMesa based) are left
 * alone: ANGLE cannot stand in for their driver, and pretending otherwise would only produce
 * confusing crashes. MobileGlues is a special case - it keeps its own EGL, and ANGLE is enabled
 * *inside* it through its `enableAngle` config, so the chain is GL → MobileGlues → ANGLE → Vulkan.
 */
object AngleLaunchPolicy {
    private const val TAG = "AngleLaunchPolicy"

    /** Bare name, valid only because the pair was pre-loaded by absolute path first - see
     *  [AnglePluginManager.preload]. This is what the wrappers (LTW/GL4ES/NW/VGPU) and the native EGL
     *  bridge look up; the bundled copy lives in the launcher's own native lib dir, which is on their
     *  search path, and the plugin's copy is already loaded under the same soname. */
    private const val ANGLE_EGL_NAME = AnglePlugin.EGL_LIBRARY

    private val mobileGluesConfigFile: File
        get() = File(PathManager.DIR_FILE, "mobileglues/config.json")

    /**
     * @param eglReplaced true when ANGLE took over the EGL for this launch, which means the caller
     *                    must NOT publish the renderer's own EGL library on top of it
     * @param summary     the applied configuration, for logs/UI; null when nothing was applied
     */
    data class AngleApplyResult(val eglReplaced: Boolean, val summary: String?)

    /** The decision for the current launch, kept for logs/UI after the fact. */
    @Volatile
    var lastDecision: AngleLaunchDecision = AngleLaunchDecision(force = false, reason = "not evaluated")
        private set

    /**
     * Applies ANGLE to a game launch environment.
     *
     * Must be called before the caller publishes the renderer's own EGL into `POJAVEXEC_EGL`.
     *
     * @param envMap          the launch's environment map, mutated in place
     * @param rendererId      the selected renderer's id (e.g. `LTW`, `MOBILEGLUES`)
     * @param rendererEgl     the renderer's EGL library name/path, if it declares one
     * @param mcVersionName   Minecraft version being launched, for logs/state only
     */
    @JvmStatic
    fun applyToGameEnvironment(
        envMap: MutableMap<String, String>,
        rendererId: String,
        rendererEgl: String?,
        mcVersionName: String?
    ): AngleApplyResult {
        // The Application is set from TurtleApplication.onCreate in every process that matters
        // (:launcher for the UI, :game for the actual launch), but a plugin/mis-ordered call must not
        // take a launch down - without it the platform driver simply stays in place.
        val context = runCatching { ContextExecutor.getApplication() }.getOrNull() ?: run {
            Logging.w(TAG, "No application context - leaving the platform GLES driver in place")
            return AngleApplyResult(false, null)
        }

        val autoWanted = AnglePluginManager.isAutoForceWanted(context)
        val userWanted = AllSettings.useAngle.getValue()
        if (!autoWanted && !userWanted) {
            lastDecision = AngleLaunchDecision(force = false, reason = "ANGLE was not requested")
            return AngleApplyResult(false, null)
        }

        // A renderer that brings its own driver config must not be overridden - silently replacing a
        // working ANGLE/GLES setup with ours is how you get a mystery black screen.
        val declaredGles = envMap["LIBGL_GLES"]
        val declaredEgl = envMap["LIBGL_EGL"]
        if (declaredGles != null || declaredEgl != null) {
            val reason = "the renderer's own configuration already selects its GLES/EGL (LIBGL_EGL=$declaredEgl, LIBGL_GLES=$declaredGles)"
            lastDecision = AngleLaunchDecision(force = false, reason = reason, requestedByUser = userWanted)
            Logging.i(TAG, "ANGLE not applied: $reason")
            return AngleApplyResult(false, null)
        }

        val ridesPlatformGles = rendererEgl == null || rendererEgl == "libEGL.so" || rendererEgl.endsWith(AnglePlugin.EGL_LIBRARY)
        val isMobileGlues = rendererId == MobileGluesRenderer.ID || rendererEgl?.endsWith("libmobileglues.so") == true

        if (!ridesPlatformGles && !isMobileGlues) {
            val reason = "$rendererId renders through its own EGL ($rendererEgl), so ANGLE cannot replace the GLES driver"
            lastDecision = AngleLaunchDecision(
                force = false,
                reason = reason,
                requestedByUser = userWanted,
                rendererCanUseAngle = false
            )
            Logging.w(TAG, "ANGLE not applied: $reason")
            return AngleApplyResult(false, null)
        }

        // MobileGlues only goes on ANGLE through its own enableAngle switch, and that is flipped by the
        // device hook (PowerVR / after a GL-context failure) - not by "Use ANGLE", whose behaviour for
        // MobileGlues has always been "this renderer brings its own EGL, nothing to swap".
        if (isMobileGlues && !autoWanted) {
            val reason = "MobileGlues brings its own EGL; turn on \"Force ANGLE on PowerVR\" to run it on ANGLE instead"
            lastDecision = AngleLaunchDecision(force = false, reason = reason, requestedByUser = true, rendererCanUseAngle = false)
            Logging.i(TAG, "ANGLE not applied: $reason")
            return AngleApplyResult(false, null)
        }

        val libraries = AnglePluginManager.resolveLibraries(context)
        if (libraries == null) {
            val reason = "no ANGLE libraries are available (plugin not installed and none bundled for this ABI)"
            lastDecision = AngleLaunchDecision(force = false, reason = reason, requestedByUser = userWanted)
            Logging.w(TAG, "ANGLE not applied: $reason")
            return AngleApplyResult(false, null)
        }

        val preloaded = AnglePluginManager.preloadWithFallback(
            libraries,
            AnglePluginManager.bundledLibraries()?.takeIf { it != libraries }
        ) ?: run {
            val reason = "the ANGLE libraries could not be loaded (${libraries.eglPath})"
            lastDecision = AngleLaunchDecision(force = false, reason = reason, requestedByUser = userWanted)
            Logging.e(TAG, "ANGLE not applied: $reason")
            return AngleApplyResult(false, null)
        }

        val source = preloaded.source
        val reason = when {
            autoWanted && AnglePluginManager.isPowerVrHost(context) ->
                "PowerVR GPU: ANGLE replaces the vendor GLES driver, which is what hangs MC 26.x"
            autoWanted -> "the last launch ended without a GL context: forcing ANGLE"
            else -> "Use ANGLE is enabled in Settings"
        }
        lastDecision = AngleLaunchDecision(
            force = true,
            source = source,
            reason = reason,
            requestedByUser = userWanted && !autoWanted
        )
        AnglePluginManager.noteForcedLaunch(mcVersionName, preloaded)

        if (isMobileGlues) {
            // MobileGlues keeps its own EGL and engine; ANGLE goes underneath it, which is exactly
            // what its enableAngle switch does. The pre-load above decides which ANGLE copy it gets,
            // since its internal lookup is by bare name.
            applyMobileGluesAngle(enable = true)
            val summary = "ANGLE enabled inside MobileGlues (source=$source, $reason)"
            Logging.i(TAG, summary)
            return AngleApplyResult(eglReplaced = false, summary = summary)
        }

        // The plugin's EGL by absolute path (identical to the pre-loaded file, so it cannot become a
        // second instance); the bundled one stays a bare name, which is what the existing ANGLE path
        // has always used.
        val bridgeEgl = if (source == AngleSource.PLUGIN) preloaded.eglPath else ANGLE_EGL_NAME
        envMap["POJAVEXEC_EGL"] = bridgeEgl
        // gl4es-family wrappers (LTW, NW, VGPU, Holy GL4ES) take their EGL/GLES from these two.
        envMap["LIBGL_EGL"] = bridgeEgl
        // Deliberately the bare name, not the plugin's path: native/ctxbridges/egl_loader.c treats
        // any LIBGL_GLES value that isn't the bare ANGLE name as the EGL library to open.
        envMap["LIBGL_GLES"] = AnglePlugin.GLES_LIBRARY
        // A previous launch may have gone through MobileGlues with ANGLE enabled inside it; when the
        // user has since switched to a wrapper renderer, that inner switch would nest the two.
        applyMobileGluesAngle(enable = false)

        val summary = "ANGLE active as the GLES/EGL driver (source=$source, egl=$bridgeEgl, $reason)"
        Logging.i(TAG, summary)
        return AngleApplyResult(eglReplaced = true, summary = summary)
    }

    /**
     * Turns MobileGlues' own `enableAngle` on (or off) by merging its config file, keeping every
     * other key the user or MobileGlues has in there. Written for the same `MG_DIR_PATH` the renderer
     * class hands to the game (`files/mobileglues`).
     */
    private fun applyMobileGluesAngle(enable: Boolean) {
        runCatching {
            val file = mobileGluesConfigFile
            // Nothing to switch off when MobileGlues was never configured on this device.
            if (!enable && !file.isFile) return@runCatching
            file.parentFile?.mkdirs()
            val freshFile = !file.isFile
            // Tree model, not a Map: round-tripping parsed JSON through Gson's Map path turns every
            // integer in the user's config into "2.0".
            val root = if (freshFile) {
                JsonObject()
            } else {
                runCatching { JsonParser.parseString(file.readText()).asJsonObject }.getOrElse { JsonObject() }
            }
            // Same default SdlAndroidJniPrep writes for MC 26.3+; only when creating the file.
            if (freshFile && !root.has("enableNoError")) root.addProperty("enableNoError", 2)
            if (root.get("enableAngle")?.asBoolean == enable) return@runCatching
            root.addProperty("enableAngle", enable)
            file.writeText(root.toString())
            Logging.i(TAG, "MobileGlues config.json: enableAngle=$enable (${file.absolutePath})")
        }.onFailure { e -> Logging.w(TAG, "Could not update the MobileGlues config: ${e.message}") }
    }

    /**
     * Launch-path helper: on a device the hook applies to, make sure the ANGLE plugin is present and
     * current (install prompt through the package installer when it isn't). Never blocks or fails the
     * launch - without the plugin the ANGLE pair bundled in the launcher APK is used instead.
     */
    @JvmStatic
    fun ensurePluginReady(context: Context, onMessage: (String) -> Unit = {}) {
        if (!AnglePluginManager.isAutoForceWanted(context) && !AllSettings.useAngle.getValue()) return
        val plugin = AnglePluginManager.installedPlugin(context)
        if (plugin == null) {
            Logging.w(
                TAG,
                "ANGLE is going to be used for this device, but the ANGLE plugin (" + AnglePlugin.PACKAGE_NAME +
                    ") is not installed - using the copy bundled in the launcher for now, and offering the plugin install"
            )
        } else {
            Logging.i(
                TAG,
                "ANGLE plugin ${plugin.versionName} (v${plugin.versionCode}) is installed; libs at ${plugin.nativeLibraryDir}"
            )
        }
        AnglePluginManager.maybeInstallOrUpdate(context, onMessage)
    }

    /** One-line status for the settings screen, the crash screen and log banners. */
    @JvmStatic
    fun describeStatus(context: Context): String = AnglePluginManager.describe(context)
}
