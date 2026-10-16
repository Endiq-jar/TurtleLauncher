#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
: "${JAVA_HOME:?Set JAVA_HOME to a JDK with JNI headers}"
TEMP=$(mktemp -d)
trap 'rm -rf "$TEMP"' EXIT
${CC:-cc} -std=gnu11 -g -fsanitize=address,undefined -Wall -Wextra -Werror \
  -Wno-unused-parameter -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
  tests/native/file_dialog_bridge_test.c -lpthread -o "$TEMP/file-dialog-test"
"$TEMP/file-dialog-test"
