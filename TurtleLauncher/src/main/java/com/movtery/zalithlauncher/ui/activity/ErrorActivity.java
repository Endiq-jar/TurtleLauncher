package com.movtery.zalithlauncher.ui.activity;

import android.content.Context;

import androidx.annotation.Keep;

/**
 * Crash-compatibility trampoline for the prebuilt native exit hook.
 *
 * <p>The shipped {@code libpojavexec.so} (all four ABIs under
 * {@code src/main/jniLibs}, built from the pre-rename ZalithLauncher tree) hardcodes the
 * JNI class path {@code com/movtery/zalithlauncher/ui/activity/ErrorActivity}:
 * {@code JREUtils.setupExitMethod()} does a {@code FindClass} on it and the game-exit
 * hook later calls its static {@code showExitMethod(Context, int, boolean)}.
 * There is no source for that native method left in this repo ({@code jni/} only
 * carries the newer ZLBridge-based sources, which the Gradle build doesn't even
 * compile - {@code externalNativeBuild} is disabled and the APK ships the prebuilt
 * {@code .so} files), so the stale path cannot be fixed on the native side without a
 * full NDK rebuild of the launcher natives.
 *
 * <p>When the Java sources were renamed to {@code com.endiq.*}, this class stopped
 * existing at the old path. Every game launch then died the same way: the
 * {@code FindClass} throws {@code ClassNotFoundException}, the very next JNI call
 * ({@code NewGlobalRef}) runs with that exception still pending, ART aborts with
 * "JNI DETECTED ERROR IN APPLICATION", and the whole process takes SIGABRT
 * (exit status 6) before the JVM even starts.
 *
 * <p>This class re-creates the exact old entry point and forwards to the real,
 * renamed {@code com.endiq.zalithlauncher.ui.activity.ErrorActivity}, restoring the
 * game exit/crash screen with zero behavior change. It is never started as an
 * Activity itself (no manifest entry needed) - native code only invokes the static
 * method. Kept verbatim by R8 via {@code @Keep} plus an explicit rule in
 * {@code proguard-rules.pro}, since nothing in Java references it.
 */
@Keep
public class ErrorActivity {

    /**
     * Exact signature the native exit hook looks up:
     * {@code showExitMessage(Landroid/content/Context;IZ)V}.
     */
    public static void showExitMessage(Context ctx, int code, boolean isSignal) {
        com.endiq.zalithlauncher.ui.activity.ErrorActivity.showExitMessage(ctx, code, isSignal);
    }
}
