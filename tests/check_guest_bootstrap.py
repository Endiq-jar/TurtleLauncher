#!/usr/bin/env python3
"""Guard the guest bootstrap's classloader rule (Fabric "already loaded in
another classloader" startup crash, mclo.gs/n30hcJv).

GameFileDialogs is the guest JVM's main class and runs on the system
classloader. The JVM binds every System.load/loadLibrary to the caller's
classloader and refuses to load the same library into a second one, so this
class may only load game natives (libpojavexec, LWJGL core, file-dialog
overrides) when the game itself runs on that same classloader (vanilla).
For mod-loader mains (Fabric/Quilt Knot, Forge/NeoForge, Launchwrapper) it
must not touch LWJGL at all - the game classloader's own GLFW loads the
natives. These checks keep that gate from being accidentally removed.
"""
import re
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = (root / "jre_lwjgl3glfw/src/bridge/java/com/endiq/turtlelauncher/bridge"
          / "GameFileDialogs.java").read_text()

# Strip comments so prose mentioning the dangerous calls can't confuse checks.
code = re.sub(r"/\*.*?\*/", "", source, flags=re.S)
code = re.sub(r"//[^\n]*", "", code)


def method_body(code, signature):
    """Return the brace-delimited body following the given method signature."""
    start = code.index(signature)
    opening = code.index("{", start)
    depth = 0
    for pos in range(opening, len(code)):
        if code[pos] == "{":
            depth += 1
        elif code[pos] == "}":
            depth -= 1
            if depth == 0:
                return code[opening:pos + 1]
    raise AssertionError(f"Unbalanced braces after {signature!r}")


main = method_body(code, "public static void main(String[] args)")
natives = method_body(code, "private static void installSystemClassLoaderNatives()")
gate = method_body(code, "private static boolean runsOnSystemClassLoader(String realMainClass)")

# The bootstrap must stay independent of LWJGL/GLFW classes entirely.
assert "org.lwjgl" not in code.replace("org.lwjgl.util.Debug", "").replace(
    "org.lwjgl.libname", ""), "GameFileDialogs must not reference LWJGL classes"

# Only vanilla entry points run LWJGL on the system classloader.
assert '"net.minecraft.client.main.Main".equals(realMainClass)' in gate
assert '"net.minecraft.client.Minecraft".equals(realMainClass)' in gate

# main() must consult the gate before any native is loaded, and must not
# itself load a native library or call the native installer directly.
assert "runsOnSystemClassLoader(realMainClass)" in main
assert "installSystemClassLoaderNatives()" in main
assert "System.load" not in main, "main() must not load natives outside the gate"
assert "nativeInstallFileDialogs()" not in main

# The actual native loads live only in the gated vanilla-only helper.
for needle in ('System.loadLibrary("pojavexec")',
               'System.loadLibrary("turtlefiledialogs")',
               "nativeInstallFileDialogs()",
               "preloadLwjglCoreLibrary()"):
    assert needle in natives, f"{needle} missing from installSystemClassLoaderNatives()"

# The LWJGL core preload (System.load by absolute path) is called from exactly
# one place: the gated helper above. Count definition + that single call.
preload_calls = code.count("preloadLwjglCoreLibrary()")
assert preload_calls == 2, f"preload must have 1 def + 1 gated call, found {preload_calls} refs"
load_calls = [m.start() for m in re.finditer(r"System\.load(?:Library)?\(", code)]
preload_body = method_body(code, "private static void preloadLwjglCoreLibrary()")
allowed = natives.count("System.loadLibrary(") + preload_body.count("System.load(")
assert len(load_calls) == allowed, (
    f"{len(load_calls)} System.load* calls but only {allowed} inside gated helpers")

# LWJGL debug mode is a heavy per-call/allocation-trace tax on the render
# thread and must stay opt-in (renderer debug logging setting), never on by
# default again - it shipped enabled for every launch and tanked game FPS.
debug_gate = main.index('Boolean.getBoolean("turtlelauncher.lwjglDebug")')
for prop in ("org.lwjgl.util.Debug", "org.lwjgl.util.DebugLoader"):
    assert main.index(f'System.setProperty("{prop}"') > debug_gate, \
        f"{prop} must only be set inside the turtlelauncher.lwjglDebug gate"
assert code.count('System.setProperty("org.lwjgl.util.Debug"') == 1

# Crash analyzer must recognise this signature before the generic
# "Failed to locate library" rule (which wrongly suggests clearing caches).
analyzer = (root / "TurtleLauncher/src/main/java/com/endiq/turtlelauncher"
            / "feature/log/CrashAnalyzer.kt").read_text()
specific = analyzer.index('title = "lwjgl_native_wrong_classloader"')
generic = analyzer.index('title = "native_library_path"')
assert specific < generic, "wrong-classloader rule must precede native_library_path"
assert "already loaded in another classloader" in analyzer

print("Guest bootstrap classloader gate passed: natives only load for "
      "same-classloader (vanilla) launches")
