#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
: "${JAVA_HOME:?Set JAVA_HOME to a Java 17+ runtime}"
KOTLINC=${KOTLINC:-kotlinc}
TEMP=$(mktemp -d)
trap 'rm -rf "$TEMP"' EXIT
"$KOTLINC" \
  tests/kotlin/stubs/android/os/Process.kt \
  TurtleLauncher/src/main/java/com/endiq/turtlelauncher/feature/turtle/{HudRefreshPolicy,ServerEndpoint,PingTracker}.kt \
  TurtleLauncher/src/main/java/com/endiq/turtlelauncher/launch/{PerformancePolicy,GameFpsOptions,JvmArguments,FrameBudget}.kt \
  tests/kotlin/{LauncherPerformanceCheck,PingTrackerCheck,JvmArgumentsCheck}.kt \
  -include-runtime -d "$TEMP/launcher-performance.jar"
"$JAVA_HOME/bin/java" -jar "$TEMP/launcher-performance.jar"
