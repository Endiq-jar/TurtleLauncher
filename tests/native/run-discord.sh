#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
: "${JAVA_HOME:?Set JAVA_HOME to a JDK with JNI headers}"
TEMP=$(mktemp -d)
trap 'rm -rf "$TEMP"' EXIT
${CXX:-c++} -std=c++20 -g -pthread -fsanitize=address,undefined -Wall -Wextra -Werror \
  -Itests/native/discord_stub -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
  tests/native/discord_bridge_test.cpp -o "$TEMP/discord-test"
"$TEMP/discord-test"
