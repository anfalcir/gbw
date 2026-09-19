#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/.build/domain-smoke"
rm -rf "$OUT"
mkdir -p "$OUT"
kotlinc \
  "$ROOT/app/src/main/java/com/gbw/android/domain/TextNormalization.kt" \
  "$ROOT/app/src/main/java/com/gbw/android/domain/Models.kt" \
  "$ROOT/app/src/main/java/com/gbw/android/domain/Workflow.kt" \
  "$ROOT/app/src/main/java/com/gbw/android/domain/AudioQuality.kt" \
  "$ROOT/tools/DomainSmoke.kt" \
  -include-runtime -d "$OUT/domain-smoke.jar"
java -jar "$OUT/domain-smoke.jar"
