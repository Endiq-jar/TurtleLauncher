package com.movtery.zalithlauncher.launch

import android.app.Activity
import org.libsdl.app.SDL

/**
 * TurtleLauncher CRASH FIX ATTEMPT (MC 26.3+ SDL native crash):
 *
 * Minecraft 26.3+'s SDL_Init() eventually SIGSEGVs inside libSDL3.so itself
 * (not a Java exception) right after basic system-info logging. Root cause:
 * SDL's Android backend expects a real Android JNIEnv/Activity, normally
 * supplied via SDL's own Java SDLActivity glue layer - which this launcher
 * doesn't use, since Minecraft's SDL calls happen inside LWJGL's own
 * org.lwjgl.sdl bindings running in the guest JVM (see VMLauncher - this
 * guest JVM shares the same OS process as the app, created via
 * JNI_CreateJavaVM, but is a separate JVM instance with no SDL Java glue of
 * its own).
 *
 * This is a genuine hypothesis, not a confirmed fix: SDL 3.4.12 added
 * SDL.setupJNI() (org.libsdl.app.SDL, from SDL's own official Android AAR -
 * see libs/sdl3-android-classes.jar) as part of upstream work to decouple
 * Android JNI setup from needing the full SDLActivity lifecycle. Since the
 * loaded libSDL3.so is the SAME native image in memory regardless of which
 * JVM instance calls into it (same OS process), calling this from the app's
 * own real Activity/JNIEnv - before the guest JVM starts and before
 * Minecraft's own SDL_Init() runs - might pre-populate libSDL3.so's Android
 * JNI state so that LWJGL's later calls into the same library find it
 * already there instead of crashing on nothing. It's untested whether this
 * state is actually process-global in the way this assumes, or whether
 * LWJGL's SDL code path even looks at it.
 *
 * Call setup(activity) once, on the real app UI thread, before
 * VMLauncher.launchJVM() runs - but only for SDL versions (see
 * Tools.versionUsesLwjglSdl); calling it for GLFW versions would be a no-op
 * at best. Best-effort throughout: any failure here is swallowed so a
 * problem with this priming step can't block the game from launching at
 * all - worst case, the original SIGSEGV this was meant to prevent still
 * happens, exactly as it did before this fix existed.
 */
object SdlAndroidJniPrep {
    @JvmStatic
    fun setup(activity: Activity) {
        try {
            System.loadLibrary("SDL3")
            SDL.setContext(activity)
            SDL.setupJNI()
        } catch (t: Throwable) {
            // Best effort - see class doc. Deliberately not logged as an
            // error: this is a speculative fix, and a failure here should
            // look like "the original crash still happens", not like a new
            // problem, until this hypothesis is actually confirmed.
        }
    }
}
