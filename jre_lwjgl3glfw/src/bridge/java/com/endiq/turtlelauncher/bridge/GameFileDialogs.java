package com.endiq.turtlelauncher.bridge;

import java.lang.reflect.InvocationTargetException;

/** Java-8-compatible guest entry point, independent of GLFW and LWJGL ABI versions. */
public final class GameFileDialogs {
    private static native void nativeInstallFileDialogs();
    private GameFileDialogs() { }

    public static void main(String[] args) throws Throwable {
        try {
            // Preserve SDL's existing early dlopen-hook setup before touching LWJGL classes.
            if (Boolean.getBoolean("turtlelauncher.sdlBootstrap")) System.loadLibrary("pojavexec");
            System.loadLibrary("turtlefiledialogs");
            nativeInstallFileDialogs();
        } catch (UnsatisfiedLinkError error) {
            System.err.println("TurtleFileDialogs: native bridge unavailable: " + error);
        }
        String target = Boolean.getBoolean("turtlelauncher.sdlBootstrap")
            ? "com.endiq.turtlelauncher.launch.SdlMainReadyBootstrap"
            : System.getProperty("turtlelauncher.realMainClass");
        if (target == null || target.isEmpty()) throw new IllegalStateException("Missing game main class");
        try {
            Class.forName(target).getMethod("main", String[].class).invoke(null, (Object)args);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }
}
