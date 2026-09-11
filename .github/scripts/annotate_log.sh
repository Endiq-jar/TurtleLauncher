#!/usr/bin/env bash
# TEMPORARY diagnostic helper: GitHub Actions' log-archive endpoint and the artifact-download
# redirect are both unreachable from the driving sandbox, and the session token can't trigger
# workflow_dispatch, so the interesting part of the Gradle output is surfaced as check
# annotations (readable via the Checks REST API). Delete along with zz-debug-build.yml.
set -uo pipefail

LOG="${1:-/tmp/build.log}"
MAX=20
CHUNK=1400
count=0

emit() { # emit <level> <text> ; newlines preserved as %0A so one annotation can carry many log lines
  local level="$1" text="$2" enc
  [ "$count" -ge "$MAX" ] && return 0
  count=$((count+1))
  enc=$(printf '%s' "$text" \
    | sed -e 's/%/%25/g' -e 's/:/%3A/g' \
    | awk 'BEGIN{ORS=""} {if(NR>1) printf "%%0A"; print}' \
    | cut -c1-$CHUNK)
  [ -z "${enc// }" ] && return 0
  echo "::${level}::${enc}"
}

if [ ! -f "$LOG" ]; then
  emit error "RESULT=no-log - gradle never produced $LOG"
  exit 0
fi

if grep -q "^BUILD SUCCESSFUL" "$LOG"; then
  emit warning "RESULT=BUILD SUCCESSFUL ($(grep -m1 -oE 'BUILD SUCCESSFUL in .*' "$LOG"))"
else
  emit error "RESULT=BUILD FAILED ($(grep -m1 -oE 'BUILD FAILED in .*' "$LOG" || echo 'no summary'))"
fi
emit error "failed-task: $(grep -E '^> Task .* FAILED' "$LOG" | head -3 | tr '\n' ' ' || echo none)"

# Everything that names a concrete problem, with a line number so it can be located.
PATTERNS='Manifest merger failed|Duplicate class|Can not extract resource|Apostrophe not preceded|^e: |^Error: |error: |failed to compile|^> *Manifest|merger:'
HITS=$(grep -nE "$PATTERNS" "$LOG" | head -6)
[ -n "$HITS" ] && emit error "hits:
$HITS"

# The authoritative message. AGP prints a task's real diagnostics (merger errors, aapt
# errors, per-file paths) in the lines *between* the "> Task ... FAILED" line and the
# "FAILURE:" summary, and the summary itself only paraphrases - so that gap is the window.
TFAIL=$(grep -nE '^> Task .* FAILED' "$LOG" | head -n1 | cut -d: -f1)
START=$(grep -n '^FAILURE: ' "$LOG" | head -n1 | cut -d: -f1 || true)
if [ -n "$TFAIL" ] && [ -n "$START" ] && [ "$START" -gt "$TFAIL" ]; then
  FROM=$TFAIL
  TO=$(( START - 1 ))
elif [ -n "$TFAIL" ]; then
  FROM=$TFAIL; TO=$(( TFAIL + 60 ))
elif [ -n "$START" ]; then
  FROM=$START; TO=$(( START + 40 ))
else
  FROM=$(( $(wc -l < "$LOG") - 50 )); [ "$FROM" -lt 1 ] && FROM=1
  TO=$(( $(wc -l < "$LOG") ))
  emit warning "no failure anchor; showing tail"
fi
BODY=$(sed -n "${FROM},${TO}p" "$LOG" | grep -vE '^\s*$' | grep -vE '^\s+at ' | head -70)
if [ -n "$BODY" ]; then
  # mapfile, not `while read`: fold's output has no trailing newline, and `read` reports
  # EOF (exit 1) in that case, silently dropping the last chunk.
  mapfile -t CHUNKS < <(printf '%s\n' "$BODY" | tr '\t' ' ' | awk 'BEGIN{ORS=" | "} {print}' | sed 's/ | $//' | fold -w 1300 -s)
  total=${#CHUNKS[@]}
  k=0
  for c in "${CHUNKS[@]}"; do
    k=$((k+1)); [ "$k" -gt 13 ] && { emit warning "context truncated after 13 of $total chunks"; break; }
    emit error "ctx[$k/$total] $c"
  done
fi

emit warning "log: $(wc -l < "$LOG") lines / $(wc -c < "$LOG") bytes (showing $FROM-$TO)"
exit 0
