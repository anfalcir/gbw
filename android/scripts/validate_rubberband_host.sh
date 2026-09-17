#!/usr/bin/env bash
set -euo pipefail

COMMIT="1d95888bec3ae0a17c0c4af791810d5a63f6bc35"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

SRC="$TMP/rubberband"
mkdir -p "$SRC"
git -C "$SRC" init -q
git -C "$SRC" fetch -q --depth 1 https://github.com/breakfastquay/rubberband.git "$COMMIT"
git -C "$SRC" checkout -q --detach FETCH_HEAD
ACTUAL="$(git -C "$SRC" rev-parse HEAD)"
if [[ "$ACTUAL" != "$COMMIT" ]]; then
  echo "Rubber Band source mismatch: expected $COMMIT, got $ACTUAL" >&2
  exit 1
fi

g++ \
  -std=c++17 \
  -O2 \
  -pthread \
  -I"$SRC" \
  "$ROOT/tools/RubberBandHostSmoke.cpp" \
  "$SRC/single/RubberBandSingle.cpp" \
  -o "$TMP/rubberband-host-smoke"

"$TMP/rubberband-host-smoke"
