package com.movtery.zalithlauncher.launch

import android.app.Activity
import android.graphics.SurfaceTexture
import android.view.Surface
import org.libsdl.app.SDL
import org.libsdl.app.SDLActivity

/**
 * TurtleLauncher CRASH FIX (MC 26.3+ SDL native crash) - SUPERSEDED Aug 2026 by the real
 * fix in sdl_hook.c (native SDL_InitSubSystem bytehook + CallbackBridge.notifyLauncher).
 *
 * ── Why this class's approach could never fully work ──
 *
 * Minecraft's SDL_Init() runs inside a *separate embedded JVM* (see VMLauncher /
 * JNI_CreateJavaVM), not the launcher's own real Activity/JVM. This class calls
 * SDL.setupJNI() ahead of time, here, on the real JVM - but each JVM instance has its
 * own independent static state, even for identically-named/loaded classes. So this
 * priming was never actually visible to whichever JVM instance runs Minecraft's own
 * SDL_Init() - confirmed by porting AngelAuraMC/Amethyst-Android's real, shipping fix
 * for the exact same crash and seeing how they solve it: a native (C-level) bytehook on
 * SDL_InitSubSystem that fires in-process regardless of which JVM's thread called it,
 * and can freely attach to the real JavaVM* (a native global, not a Java static field -
 * the crucial difference) to run the equivalent of this class's setup() from the
 * correct side of the VM boundary, every time SDL actually initializes. See sdl_hook.c
 * for the full explanation and the real fix.
 *
 * This class is kept only as a defensive no-op-if-redundant fallback (SDL.setupJNI() is
 * safe to call twice) in case the native hook fails to install on a given device/build
 * (e.g. bytehook_init() itself fails) - it is NOT relied upon as the actual fix anymore.
 * The `isActive`/token-surface logic below predates the real fix and its actual
 * effectiveness was always unconfirmed; left in place only because removing it isn't
 * needed to ship the real fix and doing so hasn't been verified safe on its own.
 */
object SdlAndroidJniPrep {
    /**
     * True once [setup] has run far enough that handing the render Surface to
     * [org.libsdl.app.SDLActivity.setDroidBridgeNativeSurface] is worthwhile - lets
     * MinecraftGLSurface's surfaceCreated/onSurfaceTextureAvailable callbacks know
     * whether to bother, without needing their own copy of the NEW_SDL version check.
     * Deliberately not reset to false anywhere - once true for a given process, stays
     * true (matches setup() itself only ever being called once per launch).
     */
    @JvmStatic
    @Volatile
    var isActive: Boolean = false
        private set

    @JvmStatic
    fun setup(activity: Activity) {
        try {
            System.loadLibrary("SDL3")
            SDL.setContext(activity)
            SDL.setupJNI()
            SDL.initialize()
            SDLActivity.setDroidBridgeHostActivity(activity)
            // This launcher renders through its own MinecraftGLSurface/EGL bridge
            // (see the POJAVEXEC_EGL fix in JREUtils), not SDL's own SDLSurface, so
            // the Surface itself is handed over from MinecraftGLSurface's existing
            // surfaceCreated/onSurfaceTextureAvailable callbacks - see those call
            // sites for setDroidBridgeNativeSurface(), not here (the real render
            // Surface doesn't exist yet at this point in the launch sequence).
            SDLActivity.setDroidBridgeExternalSurfaceMode(true)

            // TurtleLauncher CRASH FIX (MC 26.3+ SDL native crash): provide SDL_Init
            // with a non-null token native Surface so its Android JNI backend
            // doesn't dereference a null pointer during init (see SDLActivity's
            // getNativeSurface() and CrashAnalyzer rule 22). Best-effort only,
            // since the real Surface comes from MinecraftGLSurface later.
            try {
                val tokenTexture = SurfaceTexture(0)
                val tokenSurface = Surface(tokenTexture)
                SDLActivity.setDroidBridgeNativeSurface(tokenSurface)
            } catch (ignored: Throwable) {
                // If token creation fails, fall through - the original crash may
                // still occur, which is exactly the pre-existing behavior.
            }

            isActive = true
        } catch (t: Throwable) {
            // Best effort - see class doc. Deliberately not logged as an error: a
            // failure here should look like "the original crash still happens", not
            // like a new problem.
        }
    }
}
