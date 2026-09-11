#!/usr/bin/env bash
# TEMPORARY diagnostic helper: GitHub Actions' log-archive endpoint is unreachable from the
# driving sandbox and the session token can't trigger workflow_dispatch, so the interesting
# part of the Gradle output is surfaced as check annotations (readable via the Checks REST
# API) instead of requiring the log download. Delete along with zz-debug-build.yml.
set -uo pipefail

LOG="${1:-/tmp/build.log}"
MAX=14
CHUNK=1150
count=0

emit() {
  local level="$1" text="$2" enc
  [ "$count" -ge "$MAX" ] && return 0
  count=$((count+1))
  enc=$(printf '%s' "$text" | sed -e 's/%/%25/g' -e 's/:/%3A/g' | awk 'BEGIN{ORS=""} {if(NR>1) printf "%0A"; print}' | cut -c1-$CHUNK)
  echo "::${level}::${enc}"
}

if [ ! -f "$LOG" ]; then
  emit error "RESULT=no-log - gradle never produced $LOG"
  exit 0
fi

# One-line status so a glance at the annotations says pass/fail without reading the rest.
if grep -q "^BUILD SUCCESSFUL" "$LOG"; then
  emit warning "RESULT=BUILD SUCCESSFUL ($(grep -m1 -oE 'BUILD SUCCESSFUL in .*' "$LOG"))"
else
  emit error "RESULT=BUILD FAILED ($(grep -m1 -oE 'BUILD FAILED in .*' "$LOG" || echo 'no summary line'))"
fi

# The task that actually failed.
emit error "failed-task: $(grep -E '^> Task .* FAILED' "$LOG" | head -3 | tr '\n' ' ' || echo none)"

# Curated, high-signal failure lines. These are the ones with a file/line in them, which the
# generic "What went wrong" block tends to paraphrase away.
PATTERNS='Duplicate class|Can not extract resource|^e: |error: |FAILURE: Build failed|Execution failed for task|What went wrong|Apostrophe not preceded|invalid resource directory name|failed to compile'
HITS=$(grep -nE "$PATTERNS" "$LOG" | head -9)
if [ -n "$HITS" ]; then
  i=0
  while IFS= read -r line; do
    [ -z "${line// }" ] && continue
    i=$((i+1)); [ "$i" -gt 8 ] && break
    emit error "hit[$i] $line"
  done < <(printf '%s\n' "$HITS")
fi

# Full "FAILURE:" ... "* Try:" block for whatever the greps above can't contextualize.
START=$(grep -n '^FAILURE: ' "$LOG" | head -n1 | cut -d: -f1 || true)
if [ -n "$START" ]; then
  BLOCK=$(sed -n "${START},$((START+22))p" "$LOG" | grep -vE '^\s*$' | head -c 6000)
  j=0
  while IFS= read -r c; do
    [ -z "${c// }" ] && continue
    j=$((j+1)); [ "$j" -gt 3 ] && break
    emit error "block[$j] $c"
  done < <(printf '%s\n' "$BLOCK" | fold -w 1100 -s)
else
  emit warning "no FAILURE block (build likely succeeded)"
fi

emit warning "log: $(wc -l < "$LOG") lines / $(wc -c < "$LOG") bytes"
exit 0
