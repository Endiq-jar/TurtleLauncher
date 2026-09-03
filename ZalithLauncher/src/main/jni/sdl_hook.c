//
// TurtleLauncher CRASH FIX (MC 26.3+ SDL3 SIGSEGV) - the "real" native-level fix
// referenced in SdlAndroidJniPrep.kt's class doc.
//
// ── Why this exists on top of the Java-side token-Surface workaround ──
//
// SdlAndroidJniPrep already hands SDLActivity a token Surface via a Java static
// field (setDroidBridgeNativeSurface). That only helps if whichever JVM actually
// calls into libSDL3.so's SDL_Init() reads that same static field - and per that
// class's doc comment, Minecraft's SDL calls run inside a *separate* embedded
// JVM (VMLauncher's JNI_CreateJavaVM), so plain Java statics set from the host
// app's JVM are not guaranteed to be visible there at all.
//
// bytehook works at a different layer: it patches the GOT/PLT entry for a given
// symbol name process-wide (BYTEHOOK_MODE_AUTOMATIC also covers libraries loaded
// *after* the hook is installed), not per-JVM. Both the host app and the
// embedded guest JVM run in the same OS process and the same address space, so
// installing this hook once - early, from the host JVM, before VMLauncher
// launches the guest JVM - means any later call to SDL_InitSubSystem, no matter
// which JVM's JNI glue triggers it, passes through this function first, and
// reads the plain C global below instead of a Java static field. That closes
// the specific cross-JVM visibility gap the old doc comment flagged as unsolved.
//
// ── What this does and does not guarantee ──
//
// This makes sure a valid (non-NULL) ANativeWindow* is available to whichever
// code path inside libSDL3.so consults it during SDL_INIT_VIDEO, and logs
// loudly (via zl_log, see logger/zl_log.c) if none has been registered yet so a
// future crash log actually says why. It does NOT guarantee libSDL3.so's
// internal video-init code calls SDL_InitSubSystem through the PLT (a
// statically-inlined or otherwise-optimized internal call site would not be
// caught by this), and it does NOT guarantee both JVMs' copies of libSDL3.so
// are loaded from the same file/into linker-visible-to-bytehook state if the
// guest JVM uses a private linker namespace - that would need a real device
// test (logcat showing "sdl_hook: hook installed" AND the eventual crash/no
// crash) to confirm either way. Treat this as a substantially stronger attempt
// than the Java-only workaround, not as a confirmed fix.
//

#include <jni.h>
#include <stdint.h>
#include <stdbool.h>
#include <dlfcn.h>
#include <bytehook.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include "logger/logger.h"

// SDL3's public SDL_init.h flag (stable, documented part of the SDL3 API) -
// not redefined from a bundled header because none ships in this tree; only
// this one flag is needed here.
#define SDL_INIT_VIDEO 0x00000020u

typedef int (*sdl_init_subsystem_func)(uint32_t flags);

// Deliberately a plain process-wide C global, NOT a Java static - see the
// file doc above for why that distinction is the entire point of this file.
static ANativeWindow *g_turtle_sdl_window = NULL;
static _Atomic bool g_turtle_sdl_hook_installed = false;

static int custom_sdl_init_subsystem(uint32_t flags) {
    if ((flags & SDL_INIT_VIDEO) != 0u) {
        if (g_turtle_sdl_window == NULL) {
            LOG_TO_W("sdl_hook: %s",
                     "SDL_InitSubSystem(VIDEO) reached with no native window "
                     "registered yet - if this is followed by a SIGSEGV, that's the "
                     "original crash this hook could not prevent this time.");
        } else {
            LOG_TO_D("sdl_hook: SDL_InitSubSystem(VIDEO) reached, native window %p registered",
                     (void *) g_turtle_sdl_window);
        }
    }
    int ret = BYTEHOOK_CALL_PREV(custom_sdl_init_subsystem, sdl_init_subsystem_func, flags);
    BYTEHOOK_POP_STACK();
    return ret;
}

// Same "load bytehook via dlsym instead of linking against it directly"
// approach as exit_hook.c's init_exit_hook() - kept consistent rather than
// switching to a direct LOCAL_SHARED_LIBRARIES dependency, so both native
// hooks in this project fail the same predictable way (loud logcat line, no
// crash of their own) if libbytehook.so is ever missing or renamed.
static bool turtle_install_sdl_hook(void) {
    void *bytehook_handle = dlopen("libbytehook.so", RTLD_NOW);
    if (bytehook_handle == NULL) {
        goto dlerror;
    }

    bytehook_stub_t (*bytehook_hook_all_p)(const char *callee_path_name, const char *sym_name,
                                            void *new_func, bytehook_hooked_t hooked,
                                            void *hooked_arg);
    int (*bytehook_init_p)(int mode, bool debug);

    bytehook_hook_all_p = dlsym(bytehook_handle, "bytehook_hook_all");
    bytehook_init_p = dlsym(bytehook_handle, "bytehook_init");

    if (bytehook_hook_all_p == NULL || bytehook_init_p == NULL) {
        goto dlerror;
    }

    // BYTEHOOK_MODE_AUTOMATIC (matches exit_hook.c): also hooks libraries
    // loaded after this call, which matters here since this must be installed
    // before libSDL3.so itself is dlopen'd/loaded by SdlAndroidJniPrep.setup().
    int bhook_status = bytehook_init_p(/* BYTEHOOK_MODE_AUTOMATIC */ 0, false);
    if (bhook_status == BYTEHOOK_STATUS_CODE_OK) {
        // NULL callee_path_name: hook every loaded/future-loaded image's call
        // to this symbol, the same breadth exit_hook.c uses for "exit" - this
        // symbol is only exported by libSDL3.so, so nothing else can bind it.
        bytehook_stub_t stub = bytehook_hook_all_p(NULL, "SDL_InitSubSystem",
                                                    &custom_sdl_init_subsystem, NULL, NULL);
        LOG_TO_I("sdl_hook: hook installed, stub=%p", (void *) stub);
        return true;
    }

    LOG_TO_W("sdl_hook: bytehook_init failed (%i)", bhook_status);
    dlclose(bytehook_handle);
    return false;

    dlerror:
    if (bytehook_handle != NULL) dlclose(bytehook_handle);
    LOG_TO_E("sdl_hook: failed to load bytehook: %s", dlerror());
    return false;
}

JNIEXPORT void JNICALL
Java_com_movtery_zalithlauncher_launch_SdlAndroidJniPrep_nativeInstallSdlHook(
        JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    if (!g_turtle_sdl_hook_installed) {
        g_turtle_sdl_hook_installed = turtle_install_sdl_hook();
    }
}

// Registers (or replaces) the ANativeWindow the hook above hands to SDL. Safe
// to call more than once - SdlAndroidJniPrep calls this with a token Surface
// first, then MinecraftGLSurface calls it again with the real render Surface
// once one exists, exactly mirroring the existing
// SDLActivity.setDroidBridgeNativeSurface() call sites (kept alongside them,
// not replacing them - see both call sites' doc comments).
JNIEXPORT void JNICALL
Java_com_movtery_zalithlauncher_launch_SdlAndroidJniPrep_nativeSetSurface(
        JNIEnv *env, jclass clazz, jobject surface) {
    (void) clazz;
    ANativeWindow *window = surface != NULL ? ANativeWindow_fromSurface(env, surface) : NULL;
    ANativeWindow *previous = g_turtle_sdl_window;
    g_turtle_sdl_window = window;
    if (previous != NULL) {
        ANativeWindow_release(previous);
    }
    LOG_TO_D("sdl_hook: native window updated to %p", (void *) window);
}
