package com.endiq.turtlelauncher.bridge;

import java.io.File;
import java.lang.reflect.InvocationTargetException;

/**
 * Java-8-compatible guest entry point, independent of GLFW and LWJGL ABI versions.
 *
 * <p>This class is the guest JVM's main class for every launch (see LaunchArgs.kt);
 * it runs under the plain system classloader and then hands over to the real main
 * class named by {@code -Dturtlelauncher.realMainClass}.
 *
 * <p><b>Classloader rule this class must obey:</b> never load a game native library
 * (libpojavexec, liblwjgl*, liblwjgl_tinyfd) or touch an LWJGL class from here unless
 * the game itself will also run under this same system classloader. The JVM binds
 * every {@code System.load}/{@code System.loadLibrary} to the <i>caller's</i>
 * classloader and refuses to load the same library into a second one
 * ("Native Library ... already loaded in another classloader"). Mod loaders
 * (Fabric/Quilt Knot, Forge/NeoForge BootstrapLauncher, legacy Launchwrapper) load
 * their own copy of LWJGL in a child classloader, so anything loaded here first is
 * poison for them: their GLFW static init fails on libpojavexec, and LWJGL's
 * {@code Library} then reports the misleading "Failed to locate library:
 * liblwjgl-legacy.so" (it discards the real UnsatisfiedLinkError) even though the
 * file sits right there on java.library.path. That was the deterministic
 * Fabric 1.21.11 startup crash this gating fixes.
 *
 * <p>No preload is needed for those launches: {@code System.loadLibrary} from a
 * child classloader still searches java.library.path (OpenJDK's
 * {@code ClassLoader.loadLibrary} falls back to the system/user library paths after
 * {@code findLibrary}), so the game classloader's own GLFW static initializer loads
 * libpojavexec itself - which is also the only way libpojavexec's JNI_OnLoad can
 * cache the <i>game's</i> GLFW copy (input buffers/callbacks) and install the
 * dlopen/forkAndExec hooks against the classes the game actually uses.
 */
public final class GameFileDialogs {
    private static native void nativeInstallFileDialogs();
    private GameFileDialogs() { }

    public static void main(String[] args) throws Throwable {
        // LWJGL's loader diagnostics print the exact paths tried and the exact
        // underlying exception for a native-load failure, where Library would
        // otherwise only report a generic cause-discarding "Failed to locate
        // library: X". But org.lwjgl.util.Debug is not free: it switches on
        // per-call validation across the bindings and a MemoryUtil allocator
        // that captures a stack trace for every native allocation - a heavy,
        // constant FPS/heat tax on the render thread. It used to be enabled
        // unconditionally here (left over from native-load crash hunting);
        // now it only turns on when the launcher's renderer debug logging
        // setting passes -Dturtlelauncher.lwjglDebug=true. Plain system
        // properties either way - classloader-neutral, safe for every launch.
        if (Boolean.getBoolean("turtlelauncher.lwjglDebug")) {
            System.setProperty("org.lwjgl.util.Debug", "true");
            System.setProperty("org.lwjgl.util.DebugLoader", "true");
        }

        String realMainClass = System.getProperty("turtlelauncher.realMainClass");
        if (runsOnSystemClassLoader(realMainClass)) {
            installSystemClassLoaderNatives();
        } else {
            // Mod-loader launch: stay away from LWJGL entirely (see class doc). The
            // loader's own GLFW copy loads libpojavexec/LWJGL natives when the game
            // first touches it, bound to the correct classloader.
            System.err.println("TurtleLauncher: " + realMainClass
                + " loads the game in a child classloader; leaving LWJGL natives "
                + "for that classloader to load.");
        }

        String target = Boolean.getBoolean("turtlelauncher.sdlBootstrap")
            ? "com.endiq.turtlelauncher.launch.SdlMainReadyBootstrap"
            : realMainClass;
        if (target == null || target.isEmpty()) throw new IllegalStateException("Missing game main class");
        try {
            Class.forName(target).getMethod("main", String[].class).invoke(null, (Object)args);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }

    /**
     * True only when the game's LWJGL classes will be loaded by this same system
     * classloader - i.e. an unmodified vanilla entry point. Everything else
     * (Fabric/Quilt {@code KnotClient}, Forge/NeoForge {@code BootstrapLauncher},
     * {@code net.minecraft.launchwrapper.Launch}, unknown mains) re-loads the game
     * in a child classloader and must return false. Defaulting unknown mains to
     * false is the fail-safe direction: the worst case is losing the TinyFD dialog
     * overrides below, never a poisoned native library.
     */
    private static boolean runsOnSystemClassLoader(String realMainClass) {
        return "net.minecraft.client.main.Main".equals(realMainClass)
            || "net.minecraft.client.Minecraft".equals(realMainClass);
    }

    /**
     * Vanilla-only early native setup, correct here solely because the game shares
     * this classloader: loading libpojavexec runs its JNI_OnLoad, which caches this
     * classloader's GLFW class for the input bridge and installs the LWJGL dlopen
     * hook + forkAndExec hook; libturtlefiledialogs then registers the Android
     * file-picker overrides on this classloader's TinyFileDialogs. Later game-side
     * loads of the same libraries hit the same-classloader cache and succeed.
     * (Under a mod loader none of this may run - see the class doc.)
     */
    private static void installSystemClassLoaderNatives() {
        try {
            System.loadLibrary("pojavexec");
            System.loadLibrary("turtlefiledialogs");
            nativeInstallFileDialogs();
        } catch (UnsatisfiedLinkError error) {
            System.err.println("TurtleFileDialogs: native bridge unavailable: " + error);
        }
        preloadLwjglCoreLibrary();
    }

    /**
     * Vanilla-only diagnostic aid (same-classloader launches): loads the LWJGL core
     * library (named by -Dorg.lwjgl.libname) by absolute path up front, so if it is
     * genuinely missing/unloadable the log shows the real path tried and the real
     * UnsatisfiedLinkError instead of LWJGL Library's cause-discarding "Failed to
     * locate library". This must never run for child-classloader launches: the
     * System.load would bind the library to the system classloader and the game
     * classloader's own load would then fail with "already loaded in another
     * classloader" - the exact crash the runsOnSystemClassLoader gate prevents.
     */
    private static void preloadLwjglCoreLibrary() {
        String libName = System.getProperty("org.lwjgl.libname", "lwjgl");
        String fileName = "lib" + libName + ".so";
        String libraryPath = System.getProperty("java.library.path", "");
        System.err.println("TurtleLauncher: looking for " + fileName + " on java.library.path=" + libraryPath);
        for (String dir : libraryPath.split(File.pathSeparator)) {
            if (dir.isEmpty()) continue;
            File candidate = new File(dir, fileName);
            boolean exists = candidate.isFile();
            System.err.println("TurtleLauncher:   " + candidate + " exists=" + exists);
            if (!exists) continue;
            try {
                System.load(candidate.getAbsolutePath());
                System.err.println("TurtleLauncher: preloaded " + candidate + " successfully");
                return;
            } catch (UnsatisfiedLinkError error) {
                System.err.println("TurtleLauncher: failed to preload " + candidate + ": " + error);
            }
        }
        System.err.println("TurtleLauncher: " + fileName + " not found on java.library.path (" + libraryPath
            + "); LWJGL/GLFW init will likely crash. Reinstall the app if this persists.");
    }
}
