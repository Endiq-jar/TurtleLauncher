//
// TurtleLauncher NATIVE BUILD FIX: logger.h declares zl_log() (used by utils.c,
// jre_launcher.c and input_bridge_v3.c via the LOG_TO_E/W/I/D macros) but no
// implementation of it existed anywhere in the tree - the native build only
// avoided "undefined reference to zl_log" at link time because
// externalNativeBuild was commented out in build.gradle.kts, so ndk-build never
// actually ran. Re-enabling the native build immediately surfaces the missing
// symbol in the pojavexec module's final link step. This file is that missing
// implementation, routed to logcat via __android_log_print.
//

#include "logger.h"
#include <android/log.h>
#include <stdarg.h>

#define ZL_LOG_TAG "TurtleLauncherNative"

static int zl_priority_for_level(const char *level) {
    // level is always one of the LOG_E/LOG_W/LOG_I/LOG_D string constants from
    // logger.h (compared by pointer value everywhere they're used, but done by
    // content here defensively in case a caller ever passes a different but
    // equal-content string literal).
    if (level == LOG_E || (level[0] == 'E')) return ANDROID_LOG_ERROR;
    if (level == LOG_W || (level[0] == 'W')) return ANDROID_LOG_WARN;
    if (level == LOG_I || (level[0] == 'I')) return ANDROID_LOG_INFO;
    return ANDROID_LOG_DEBUG;
}

void zl_log(const char *level, const char *fmt, ...) {
    va_list args;
    va_start(args, fmt);
    __android_log_vprint(zl_priority_for_level(level), ZL_LOG_TAG, fmt, args);
    va_end(args);
}
