//
// Created by movte on 2025/4/25.
//
// Implementation of zl_log(), declared in logger/logger.h and used by every
// LOG_TO_E/W/I/D call site in the jni tree (input_bridge_v3.c, jre_launcher.c,
// utils.c, sdl_hook.c). TurtleLauncher fix: this file was missing from the
// repo while logger.h was committed, so every module that called a LOG_TO_*
// macro failed to link with:
//     ld: error: undefined symbol: zl_log
//     >>> referenced by input_bridge_v3.c:39
//     clang++: error: linker command failed with exit code 1
// (see Actions run 33581296871, all four ABI jobs). Content matches upstream
// ZalithLauncher-derived forks byte for byte.
//
// Output goes to stderr because that is the stream the launcher already
// captures: stdio_is.c pipes stdout/stderr into the log the UI shows, and it
// calls setvbuf(stderr, 0, _IONBF, 0) so messages appear unbuffered.
//

#include <stdarg.h>
#include <stdio.h>

void zl_log(const char *level, const char *fmt, ...) {
    va_list args;
    char buffer[1024];
    va_start(args, fmt);
    vsnprintf(buffer, sizeof(buffer), fmt, args);
    va_end(args);

    fprintf(stderr, "[%s] %s\n", level, buffer);
}
