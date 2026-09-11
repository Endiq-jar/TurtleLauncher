#!/usr/bin/env bash
# TEMPORARY diagnostic helper: GitHub Actions log downloads were unreachable from the
# driving sandbox, so this surfaces the interesting part of the Gradle output as check
# annotations (readable via the Checks REST API) instead of requiring the log archive.
set -uo pipefail

LOG="${1:-/tmp/build.log}"
MAX_ANNOTATIONS=12
CHUNK=1200

emit() {
  local level="$1" text="$2"
  # Workflow-command escaping: % -> %25, \r -> %0D, \n -> %0A, : -> %3A
  local enc
  enc=$(printf '%s' "$text" | sed -e 's/%/%25/g' -e 's/:/%3A/g' | awk 'BEGIN{ORS=""} {if(NR>1) printf "%%0A"; print}')
  echo "::${level}::${enc}"
}

if [ ! -f "$LOG" ]; then
  emit error "no build log produced at $LOG"
  exit 0
fi

echo "log size: $(wc -c < "$LOG") bytes, $(wc -l < "$LOG") lines"

# The Gradle failure block is "FAILURE: Build failed..." through "* What went wrong:" ...
# "* Try:" - grab a generous window around it; fall back to the tail.
START=$(grep -n '^FAILURE: ' "$LOG" | head -n1 | cut -d: -f1 || true)
if [ -z "$START" ]; then
  START=$(( $(wc -l < "$LOG") - 80 ))
  [ "$START" -lt 1 ] && START=1
  emit warning "no FAILURE block found; showing log tail from line $START"
fi

SECTION=$(tail -n +"$START" "$LOG" | head -n 90)

i=0
while IFS= read -r chunk; do
  [ -z "${chunk// }" ] && continue
  i=$((i+1))
  [ "$i" -gt "$MAX_ANNOTATIONS" ] && { emit warning "truncated at $MAX_ANNOTATIONS annotations"; break; }
  emit error "gradle[$i/$MAX_ANNOTATIONS] $chunk"
done < <(printf '%s\n' "$SECTION" | grep -v -E '^\s*$' | head -c 14000 | fold -w "$CHUNK" -s)

# Also surface the exception lines and any "Caused by" chain, which the window above can
# cut off when the failure is deep in a long stacktrace.
EXC=$(grep -nE '^\* What went wrong:|Caused by:|^\s*> (Run with|Execution failed|A problem occurred)' "$LOG" | head -n 25)
if [ -n "$EXC" ]; then
  j=0
  while IFS= read -r c; do
    [ -z "${c// }" ] && continue
    j=$((j+1)); [ "$j" -gt 6 ] && break
    emit warning "exc[$j] $c"
  done < <(printf '%s\n' "$EXC" | fold -w 900 -s)
fi

exit 0
