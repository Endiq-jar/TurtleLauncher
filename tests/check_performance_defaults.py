#!/usr/bin/env python3
"""Pin the performance-regression fixes: no always-on debug/GC furnace.

Three separate defaults used to tax every game session:

  1. GameFileDialogs enabled LWJGL Debug + DebugLoader unconditionally.
     LWJGL debug mode adds per-call validation and captures a stack trace
     per native allocation - a large constant FPS/heat cost. It must stay
     behind the turtlelauncher.lwjglDebug opt-in (asserted in detail by
     check_guest_bootstrap.py; LaunchArgs half asserted here).
  2. The stock javaArgs preset pinned -XX:MaxGCPauseMillis=4, so G1 cycled
     almost constantly chasing a 4ms pause target, and forced
     -XX:+AlwaysPreTouch, bypassing LaunchGame's heap-share safety guard.
     Both tokens are gone from the default (PerformancePolicy supplies the
     pause target; framePacing adds pre-touch only when safe), and stored
     copies of the exact old preset are migrated on startup.
"""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
main = root / "TurtleLauncher/src/main/java"

settings = (main / "com/endiq/turtlelauncher/setting/AllSettings.kt").read_text()
javaargs = settings.index("val javaArgs = StringSettingUnit(")
javaargs_block = settings[javaargs:javaargs + 1200]
for token in ("-XX:MaxGCPauseMillis=4", "-XX:+AlwaysPreTouch"):
    assert token not in javaargs_block, f"stock javaArgs must not ship {token}"

prefs = (main / "net/endiq/launcher/prefs/LauncherPreferences.java").read_text()
assert '"-XX:MaxGCPauseMillis=4"' in prefs and '"-XX:+AlwaysPreTouch"' in prefs, \
    "stock-preset migration tokens missing from LauncherPreferences"
assert "getDefaultValue()" in prefs and "oldStockArgs" in prefs, \
    "exact-match stock preset migration missing"

launch_args = (main / "com/endiq/turtlelauncher/launch/LaunchArgs.kt").read_text()
assert "rendererDebugLogging.getValue()" in launch_args
assert '"-Dturtlelauncher.lwjglDebug=true"' in launch_args

policy = (main / "com/endiq/turtlelauncher/launch/PerformancePolicy.kt").read_text()
assert "if (adaptive) 50 else 100" in policy, "GC pause targets must stay phone-sane"

print("Performance defaults passed: LWJGL debug opt-in, no 4ms GC furnace in stock args")
