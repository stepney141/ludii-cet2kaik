#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR="${LUDII_JAR:-$REPO_ROOT/Ludii/PlayerDesktop/build/Ludii.jar}"
SRCS=("$SCRIPT_DIR/ValidateLudiiGDL.java" "$SCRIPT_DIR/LudiiGDLRuntimeChecks.java")
BUILD_DIR="${TMPDIR:-/tmp}/validate-ludii-gdl-${USER:-user}"
STAMP="$BUILD_DIR/.compiled"
JAR_STAMP="$BUILD_DIR/.ludii-jar"

if [[ ! -f "$JAR" ]]; then
  echo "Missing Ludii jar: $JAR" >&2
  echo "Build it with: ant -f \"$REPO_ROOT/Ludii/PlayerDesktop/build.xml\" export_jar_public" >&2
  echo "Or set LUDII_JAR=/path/to/Ludii.jar to use a different build." >&2
  exit 2
fi

NEEDS_COMPILE=0
if [[ ! -f "$STAMP" || "$JAR" -nt "$STAMP" || "$(cat "$JAR_STAMP" 2>/dev/null || true)" != "$JAR" ]]; then
  NEEDS_COMPILE=1
else
  for SRC in "${SRCS[@]}"; do
    if [[ "$SRC" -nt "$STAMP" ]]; then
      NEEDS_COMPILE=1
      break
    fi
  done
fi

if [[ "$NEEDS_COMPILE" -eq 1 ]]; then
  mkdir -p "$BUILD_DIR"
  javac -Xlint:-options --release 8 -cp "$JAR" -d "$BUILD_DIR" "${SRCS[@]}"
  printf '%s\n' "$JAR" > "$JAR_STAMP"
  touch "$STAMP"
fi

exec java -XX:+PerfDisableSharedMem -cp "$BUILD_DIR:$JAR" ValidateLudiiGDL "$@"
