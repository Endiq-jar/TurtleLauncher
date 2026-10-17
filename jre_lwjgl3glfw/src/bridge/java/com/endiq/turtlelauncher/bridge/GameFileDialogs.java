package com.endiq.turtlelauncher.bridge;

import java.lang.reflect.InvocationTargetException;

/** Java-8-compatible guest entry point, independent of GLFW and LWJGL ABI versions. */
public final class GameFileDialogs {
    private static native void nativeInstallFileDialogs();
    private GameFileDialogs() { }

    public static void main(String[] args) throws Throwable {
        try {
            // Load pojavexec here - under this process's plain system classloader, before Knot
            // exists - for EVERY launch, not just SDL ones. Its JNI_OnLoad installs the LWJGL
            // dlopen hook (installLwjglDlopenHook/DynamicLinkLoader.ndlopen) that native library
            // loads need to find .so files under Android's linker namespace restrictions. Legacy
            // (non-SDL) launches used to skip this and rely on GLFW's own static initializer to
            // load pojavexec instead - but by then GLFW is loaded through Knot's classloader,
            // where System.loadLibrary silently fails (caught, only stack-printed) and the hook
            // never installs, so the very next LWJGL native touch (LibFFI, needed by
            // GLFWErrorCallback right after in the same clinit) throws
            // NoClassDefFoundError/ExceptionInInitializerError on org.lwjgl.system.Library.
            System.loadLibrary("pojavexec");
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
