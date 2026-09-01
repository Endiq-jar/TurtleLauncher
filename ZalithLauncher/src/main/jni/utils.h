#pragma once

#include <stdbool.h>
#include "environ/environ.h"
#include "logger/logger.h"

#define CLIPBOARD_COPY 2000
#define CLIPBOARD_PASTE 2001
#define CLIPBOARD_OPEN 2002

// TurtleLauncher SDL3 fix: notifyLauncher() notification type/action codes, mirrored
// exactly on the Java side in CallbackBridge (NOTIF_TYPE_SDL / ACTION_INIT_LAUNCHER_INTEGRATION).
#define NOTIF_TYPE_SDL 0
#define ACTION_INIT_LAUNCHER_INTEGRATION 0

// TurtleLauncher SDL3 fix: small dlsym helper so sdl_hook.c can call the real
// (un-hooked) libSDL3.so functions it needs (SDL_SetError/SDL_GetError/SDL_SetHint)
// without linking directly against libSDL3.so at build time. Fills a file-scope
// static pointer previously declared by the caller, e.g.:
//   static SDL_SetError_t SDL_SetError_p = NULL;
//   SET_DLSYM_PTR(SDL_SetError_p, handle, SDL_SetError);
// The function symbols are deliberately never referenced directly here - only via
// the string. The old DECL_DLSYM macro used `typeof(&fn)` on the untouched symbol
// names, which cannot compile: the SDL functions are never declared (only their
// _t typedefs exist), so `typeof(&SDL_SetError)` is an "undeclared identifier" error.
#define SET_DLSYM_PTR(var, handle, fn)                \
    do {                                               \
        dlerror();                                     \
        void *_p = dlsym((handle), #fn);               \
        const char *_e = dlerror();                    \
        if (_e || !_p) {                                \
            LOG_TO_E("dlsym(%s) failed: %s\n",          \
                 #fn, _e ? _e : "unknown error");       \
        }                                                \
        var = (__typeof__(var))_p;                     \
    } while (0)

// TurtleLauncher SDL3 fix: attaches the calling native thread to the real (Dalvik)
// JVM given a JavaVM* - needed because sdl_hook.c's bytehook fires from whatever
// native thread/JVM context Minecraft's SDL_InitSubSystem call happens to run on
// (the embedded JVM's own thread), which is NOT the app's real Activity/JVM. Runs
// `then` and bails out of the calling function if attaching fails.
#define TRY_ATTACH_ENV(env_name, vm, error_message, then) JNIEnv* env_name;\
do {                                                                        \
    env_name = get_attached_env(vm);                                       \
    if(env_name == NULL) {                                                 \
        LOG_TO_E("%s", error_message);                                     \
        then                                                               \
    }                                                                      \
} while(0)

char** convert_to_char_array(JNIEnv *env, jobjectArray jstringArray);
jobjectArray convert_from_char_array(JNIEnv *env, char **charArray, int num_rows);
void free_char_array(JNIEnv *env, jobjectArray jstringArray, const char **charArray);
jstring convertStringJVM(JNIEnv* srcEnv, JNIEnv* dstEnv, jstring srcStr);

void hookExec();
void installLwjglDlopenHook();
void installEMUIIteratorMititgation();
JNIEnv* get_attached_env(JavaVM* jvm);
JNIEXPORT jstring JNICALL Java_org_lwjgl_glfw_CallbackBridge_nativeClipboard(JNIEnv* env, jclass clazz, jint action, jbyteArray copySrc);

// TurtleLauncher SDL3 fix: calls CallbackBridge.notifyLauncher(type, actions...) on
// the real Dalvik-side JNIEnv (obtained via TRY_ATTACH_ENV above), so Java code that
// only exists/works correctly on the main app JVM (like org.libsdl.app.SDL.setupJNI())
// can be triggered from native code running in the embedded JVM's context.
static bool notifyLauncher(JNIEnv *dvm_env, int type, int actions[], int len) {
    jintArray actionArray = (*dvm_env)->NewIntArray(dvm_env, len);
    (*dvm_env)->SetIntArrayRegion(dvm_env, actionArray, 0, len, actions);
    return (*dvm_env)->CallStaticBooleanMethod(dvm_env, pojav_environ->bridgeClazz,
            pojav_environ->method_notifyLauncher, type, actionArray);
}