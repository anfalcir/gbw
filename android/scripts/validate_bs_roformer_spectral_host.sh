#!/usr/bin/env bash
set -euo pipefail

PFFFT_COMMIT="e1dbebc9fbf74247d12f094accbbc470aaee8715"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

SRC="$TMP/pffft"
mkdir -p "$SRC"
git -C "$SRC" init -q
git -C "$SRC" fetch -q --depth 1 https://github.com/marton78/pffft.git "$PFFFT_COMMIT"
git -C "$SRC" checkout -q --detach FETCH_HEAD
ACTUAL="$(git -C "$SRC" rev-parse HEAD)"
if [[ "$ACTUAL" != "$PFFFT_COMMIT" ]]; then
  echo "PFFFT source mismatch: expected $PFFFT_COMMIT, got $ACTUAL" >&2
  exit 1
fi

CFLAGS=(
  -O2
  -DPFFFT_STATIC_DEFINE
  -I"$SRC/include"
  -I"$SRC/include/pffft"
  -I"$SRC/src"
)

cc -std=c11 "${CFLAGS[@]}" -c "$SRC/src/pffft.c" -o "$TMP/pffft.o"
cc -std=c11 "${CFLAGS[@]}" -c "$SRC/src/pffft_common.c" -o "$TMP/pffft_common.o"

g++ \
  -std=c++17 \
  -O2 \
  -Wall \
  -Wextra \
  -Werror \
  -DPFFFT_STATIC_DEFINE \
  -I"$SRC/include" \
  "$ROOT/tools/BsRoformerSpectralHostSmoke.cpp" \
  "$TMP/pffft.o" \
  "$TMP/pffft_common.o" \
  -lm \
  -o "$TMP/bsroformer-spectral-host-smoke"

"$TMP/bsroformer-spectral-host-smoke"
