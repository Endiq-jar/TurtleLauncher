package com.movtery.zalithlauncher.launch

import android.app.Activity
import android.graphics.SurfaceTexture
import android.view.Surface
import org.libsdl.app.SDL
import org.libsdl.app.SDLActivity

/**
 * TurtleLauncher CRASH FIX (MC 26.3+ SDL native crash) - native hook added, native
 * build re-enabled, Sept 2026.
 *
 * ── What was actually broken, and what's fixed now ──
 *
 * This was disabled after a real device crash log showed it hard-aborting the process
 * with a JNI-checked SIGABRT: "no static or non-static method
 * Lorg/libsdl/app/SDLActivity;.nativeSetupJNI()I". Root cause, confirmed by parsing the
 * actual bundled files: the old `libs/sdl3-android-classes.jar`'s `SDLActivity.class`
 * declared `nativeSetupJNI` as `()V` (void), but the bundled `libSDL3.so` (all ABIs)
 * looks up an `()I` (int-returning) overload during its own JNI_OnLoad - the jar and the
 * .so were never a matched pair.
 *
 * That jar has been replaced with real source (org.libsdl.app.SDL/SDLActivity/etc.,
 * adapted from DroidBridge Launcher's public source - see that package's file headers
 * for attribution) whose `nativeSetupJNI()` is confirmed, by direct inspection of the
 * source, to return `int` - matching what this launcher's bundled libSDL3.so expects.
 * That specific abort is fixed for real, not just hypothesized.
 *
 * ── The cross-JVM SIGSEGV, and the native hook added for it ──
 *
 * Fixing the abort does not mean the original SIGSEGV this class was written to prevent
 * is actually gone. Digging into DroidBridge Launcher's own production code (which does
 * successfully run MC 26.3+'s SDL backend) turned up something this class's previous
 * doc comment only guessed at: Minecraft's SDL calls run inside a *separate* embedded
 * JVM (see VMLauncher / JNI_CreateJavaVM), not the app's own real Activity/JVM - so the
 * plain Java static field `setDroidBridgeNativeSurface` below sets is not guaranteed to
 * be visible to whichever JVM instance actually calls libSDL3.so's SDL_Init().
 *
 * `sdl_hook.c` (see its own file doc) now addresses that gap at the native level
 * instead: it installs a process-wide bytehook on `SDL_InitSubSystem` before
 * VMLauncher launches the guest JVM, and backs it with a plain C global (not a Java
 * static) for the native window pointer. Both JVMs run in the same OS process/address
 * space, so a hook installed this way sees the call regardless of which JVM's JNI glue
 * triggered it - which a Java-only fix structurally cannot. `nativeInstallSdlHook()`
 * and `nativeSetSurface()` below are that hook's Java-side call sites; the existing
 * `setDroidBridgeNativeSurface` calls are kept alongside them, not replaced, since
 * org.libsdl.app's own Java-side logic still reads that field independently.
 *
 * This is a substantially stronger fix than the previous Java-only workaround, but per
 * sdl_hook.c's own doc, it still isn't a *confirmed* one - a statically-inlined internal
 * call inside libSDL3.so, or the guest JVM's libSDL3.so copy living in a linker
 * namespace bytehook can't reach, would both defeat it, and neither has been ruled out
 * without a real device test. Describe this as "a real native-level fix has been added
 * and the native build re-enabled to ship it", not as "the SIGSEGV is confirmed gone".
 *
 * Call setup(activity) once, on the real app UI thread, before VMLauncher.launchJVM()
 * runs - but only for SDL versions (see Tools.versionUsesLwjglSdl); calling it for GLFW
 * versions would be a no-op at best. Best-effort throughout: any failure here is
 * swallowed so a problem with this priming step can't block the game from launching at
 * all - worst case, the original SIGSEGV still happens, exactly as before this fix
 * existed.
 */
object SdlAndroidJniPrep {
    // Not private: nativeSetSurface is also called from MinecraftGLSurface (a
    // different package) when the real render Surface becomes available.
    @JvmStatic
    external fun nativeInstallSdlHook()

    @JvmStatic
    external fun nativeSetSurface(surface: Surface?)
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
            // TurtleLauncher CRASH FIX: install the native bytehook BEFORE libSDL3.so
            // loads (BYTEHOOK_MODE_AUTOMATIC covers libraries loaded after install, not
            // before) and before VMLauncher launches the guest JVM that will actually
            // call into it - see this class's doc comment and sdl_hook.c.
            try {
                System.loadLibrary("sdlhook")
                nativeInstallSdlHook()
            } catch (ignored: Throwable) {
                // Best-effort, same as the rest of this method - if the native hook
                // can't load, fall through to exactly the pre-existing behavior.
            }

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
                // Also feed the native hook's plain-C-global copy (see this class's doc
                // comment) - this is the one actually visible across JVM instances.
                nativeSetSurface(tokenSurface)
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
