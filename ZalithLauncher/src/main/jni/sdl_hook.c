//
// TurtleLauncher SDL3 fix (MC 26.3+ SIGSEGV inside libSDL3.so).
//
// Ported from AngelAuraMC/Amethyst-Android's native_hooks/sdl_hook.c (public,
// LGPLv3, github.com/AngelAuraMC/Amethyst-Android), which already ships this
// working on real devices for the exact same lwjgl-sdl-based MC versions.
//
// ── Why the earlier attempt (SdlAndroidJniPrep.kt) wasn't enough ──
//
// Minecraft's SDL_Init() runs inside a *separate embedded JVM* spun up by
// VMLauncher/JNI_CreateJavaVM - not the launcher's own real Activity/JVM. Any
// Java-side setup done ahead of time on the real JVM (calling SDL.setupJNI()
// before launch, as SdlAndroidJniPrep did) simply isn't visible to that other
// JVM's copy of the same Java classes - each JVM instance has its own static
// state, even for identically-named classes.
//
// ── How this actually works ──
//
// Native (C) global state, unlike Java static fields, IS visible process-wide
// regardless of which JVM a given thread happens to be attached to. This
// launcher already captures exactly such a global - pojav_environ->dalvikJavaVMPtr,
// the real JavaVM* for the main app's own JVM, set once in input_bridge_v3.c's
// JNI_OnLoad() the moment libpojavexec.so is first loaded into that JVM.
//
// This file uses ByteHook (already bundled for the exit-code hook in
// exit_hook.c) to intercept SDL_InitSubSystem - a normal exported SDL function,
// not an internal implementation detail, so it's a stable, easy PLT/GOT hook
// target. Whichever JVM/thread ends up calling SDL_InitSubSystem (the embedded
// one, in Minecraft's case), the hook fires in that same native process, and
// can freely attach to the *real* dalvikJavaVMPtr and call back into
// CallbackBridge.notifyLauncher() - which runs org.libsdl.app.SDL.setupJNI()
// on the correct (main JVM) side of the boundary, before letting the real
// SDL_InitSubSystem proceed.
//
#include "environ/environ.h"
#include "utils.h"
#include "logger/logger.h"

#include <bytehook.h>
#include <dlfcn.h>
#include <jni.h>
#include <stdint.h>
#include <string.h>

// SDL_InitFlags is just a uint32_t bitmask (see SDL3/SDL_init.h) - declared
// locally so this file doesn't need the real SDL3 headers bundled/on the
// include path, matching how the rest of this launcher's native code treats
// bundled renderer .so's as opaque, dlsym'd dependencies rather than link-time
// ones (see ctxbridges/*).
typedef uint32_t SDL_InitFlags;
typedef bool (*SDL_InitSubSystem_t)(SDL_InitFlags flags);
typedef void (*SDL_SetHint_t)(const char *name, const char *value);
typedef bool (*SDL_SetError_t)(const char *fmt, ...);
typedef const char* (*SDL_GetError_t)(void);

DECL_DLSYM(SDL_InitSubSystem)
DECL_DLSYM(SDL_SetHint)
DECL_DLSYM(SDL_SetError)
DECL_DLSYM(SDL_GetError)

static bool custom_SDL_InitSubSystem_Func(SDL_InitFlags flags) {
    // Run CallbackBridge.notifyLauncher() on the real Dalvik JNIEnv before
    // letting SDL_Init proceed - this is what actually sets up SDL's Android
    // JNI state (SDL.setupJNI()) from the correct JVM.
    TRY_ATTACH_ENV(dvm_env, pojav_environ->dalvikJavaVMPtr, "SDL_InitSubSystem: failed to attach to the launcher's JVM, SDL launcher-integration setup was skipped",
            void* sdl3_handle = dlopen("libSDL3.so", RTLD_NOLOAD);
            if (sdl3_handle) {
                SET_DLSYM_PTR(sdl3_handle, SDL_SetError);
                if (SDL_SetError_p) SDL_SetError_p("Failed to attach to the launcher's JVM for SDL setup - this is not an SDL bug, please report it to the launcher developer.");
            }
            return false;
    );

    jint safeFlags = (flags > (SDL_InitFlags)INT32_MAX) ? -1 : (jint)flags;
    notifyLauncher(dvm_env, NOTIF_TYPE_SDL, (int[]){ACTION_INIT_LAUNCHER_INTEGRATION, safeFlags}, 2);

    // Matches this launcher's touch-keyboard behavior rather than SDL's own
    // (false) default.
    void* sdl3_handle = dlopen("libSDL3.so", RTLD_NOLOAD);
    if (sdl3_handle) {
        SET_DLSYM_PTR(sdl3_handle, SDL_SetHint);
        if (SDL_SetHint_p) SDL_SetHint_p("SDL_RETURN_KEY_HIDES_IME", "true");
    }

    bool r = BYTEHOOK_CALL_PREV(custom_SDL_InitSubSystem_Func, SDL_InitSubSystem_t, flags);
    if (!r && sdl3_handle) {
        SET_DLSYM_PTR(sdl3_handle, SDL_GetError);
        if (SDL_GetError_p) LOG_TO_E("SDL_InitSubSystem error: %s", SDL_GetError_p());
    }
    BYTEHOOK_POP_STACK();
    return r;
}

void create_sdl_hooks(bytehook_stub_t (*bytehook_hook_all_p)(const char *callee_path_name, const char *sym_name, void *new_func,
                                                              bytehook_hooked_t hooked, void *hooked_arg)) {
    // callee_path_name must stay NULL, or the symbol lookup fails to find
    // SDL_InitSubSystem inside libSDL3.so.
    bytehook_stub_t stub_SDL_InitSubSystem = bytehook_hook_all_p(NULL, "SDL_InitSubSystem", &custom_SDL_InitSubSystem_Func, NULL, NULL);
    LOG_TO_I("Successfully initialized SDL hook, stub: %p", stub_SDL_InitSubSystem);
}
