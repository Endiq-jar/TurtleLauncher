#!/usr/bin/env python3
"""Guard the jar-unsealing fix for `SecurityException: sealing violation: package org.lwjgl`.

LWJGL 2 (all Forge 1.8.9 / 1.12.2 instances) ships *sealed* jars: the manifest pins every
`org.lwjgl.*` package to one jar. The launcher has to put a second source of those classes on
the classpath - the lwjgl-glfw bridge jar plus the ABI override jars - so the JVM aborts as soon
as the game touches a class from the other source:

    Caused by: java.lang.SecurityException: sealing violation: package org.lwjgl is sealed
        at java.net.URLClassLoader.getAndVerifyPackage(...)
        at net.minecraft.client.Minecraft.func_71386_F(Minecraft.java:2756)

Sealing is only a manifest attribute, so the fix is to hand the JVM rewritten copies with
`Sealed` removed. This check keeps that wiring in place:

  * JarSealing must strip the attribute (whole-jar *and* per-package sections), drop signature
    files / the jar index it invalidates, keep every other entry, and cache the copies;
  * the game classpath must actually go through it, or the crash comes straight back;
  * preLaunch must warm the cache off the launch path;
  * the rewrite must be covered by a JVM unit test, since unit tests run in CI.
"""
import re
from pathlib import Path

repo = Path(__file__).resolve().parents[1]
main = repo / "TurtleLauncher/src/main"
java = main / "java"
tests = repo / "TurtleLauncher/src/test/java"

sealing = java / "com/endiq/turtlelauncher/launch/JarSealing.kt"
launch_args = java / "com/endiq/turtlelauncher/launch/LaunchArgs.kt"
launch_game = java / "com/endiq/turtlelauncher/launch/LaunchGame.kt"
unit_test = tests / "com/endiq/turtlelauncher/launch/JarSealingTest.kt"

errors = []


def read(path: Path) -> str:
    if not path.is_file():
        errors.append(f"missing file: {path.relative_to(repo)}")
        return ""
    return path.read_text()


source = read(sealing)
args = read(launch_args)
game = read(launch_game)
test = read(unit_test)

# ── The rewrite itself ────────────────────────────────────────────────────────────────────
for needle, what in (
    ("fun declaresSealing(jar: File): Boolean", "sealing detection"),
    ("fun unsealJarEntry(entry: String, cacheDir: File): String", "per-entry rewrite"),
    ("fun unsealClasspath(classpath: String, cacheDir: File): String", "classpath rewrite"),
    ("fun unsealedCopy(jar: File, cacheDir: File): File", "cached copy"),
    ("Attributes.Name.SEALED", "removal of the Sealed attribute"),
    ("manifest.mainAttributes.remove", "whole-jar sealing removal"),
    ("manifest.entries.values.forEach", "per-package sealing removal"),
    ("Manifest(input)", "manifest parsing"),
    ("JarFile.MANIFEST_NAME", "manifest entry handling"),
    ("INDEX.LIST", "stale jar index removal"),
    ('".SF", ".RSA", ".DSA", ".EC"', "signature stripping"),
    ("renameTo(target)", "atomic cache publication"),
):
    if needle not in source:
        errors.append(f"JarSealing.kt: missing {what} (`{needle}`)")

if "Sealed" not in source:
    errors.append("JarSealing.kt: must strip the Sealed manifest attribute")
if re.search(r"\.SEALED\)\s*=\s*\"true\"", source):
    errors.append("JarSealing.kt: must never *add* sealing")

# The launcher must not mutate installed libraries in place.
if "writeBytes" in source and "jar" in source and "replace(" in source:
    errors.append("JarSealing.kt: the source jars must not be rewritten in place")

# ── Wiring: the launch classpath has to pass through it ──────────────────────────────────
if "JarSealing.unsealClasspath(" not in args:
    errors.append("LaunchArgs.kt: the -cp value must go through JarSealing.unsealClasspath")
cp_block = args.split('argsList.add("-cp")', 1)[-1][:600] if 'argsList.add("-cp")' in args else ""
if "JarSealing.unsealClasspath(" not in cp_block:
    errors.append("LaunchArgs.kt: unsealing must wrap the exact string handed to -cp")
if "getLWJGL3ClassPath()" not in cp_block or "launchClassPath" not in cp_block:
    errors.append("LaunchArgs.kt: the unsealed classpath must still contain the bridge and game jars")

warm = game.split("fun preLaunch(", 1)[-1][:1200] if "fun preLaunch(" in game else ""
if "JarSealing.unsealClasspath(" not in warm:
    errors.append("LaunchGame.preLaunch must warm the unsealed-jar cache")
if "TaskExecutors.getDefault().execute" not in warm:
    errors.append("LaunchGame.preLaunch must warm the cache off the UI thread")

# ── Unit coverage (CI runs testDebugUnitTest) ────────────────────────────────────────────
for needle, what in (
    ("class JarSealingTest", "the unit test class"),
    ("JarSealing.declaresSealing(", "sealing assertions"),
    ("JarSealing.unsealJarEntry(", "rewrite assertions"),
    ("JarSealing.unsealClasspath(", "classpath assertions"),
    ("META-INF/LWJGL.SF", "signature-stripping coverage"),
    ("org/lwjgl/opengl/GL11.class", "entry-preservation coverage"),
):
    if needle not in test:
        errors.append(f"JarSealingTest.kt: missing {what} (`{needle}`)")

if "sealing violation" not in source:
    errors.append("JarSealing.kt: document the crash this prevents")

if errors:
    print("Jar sealing checks failed:", flush=True)
    for error in errors:
        print(f"  - {error}")
    raise SystemExit(1)

print("Jar sealing checks passed: sealed LWJGL jars are unsealed for the game classpath")
