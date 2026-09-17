#!/usr/bin/env bash
set -euo pipefail

SOURCE_REVISION="5f5daffffcf06ad7b27a7285da327e18ea62068a"
FILE_NAME="ggml-model-htdemucs-6s-f16.bin"
EXPECTED_BYTES="54855129"
EXPECTED_SHA256="09704f4ceae204e56e77d5eefd6ac71d7275be81fd507e6913371d59abcee856"
URL="https://huggingface.co/datasets/Retrobear/demucs.cpp/resolve/${SOURCE_REVISION}/${FILE_NAME}?download=true"

CACHE_DIR="${GBW_MODEL_CACHE_DIR:-${HOME}/.cache/gbw/demucs}"
mkdir -p "$CACHE_DIR"
MODEL="$CACHE_DIR/${EXPECTED_SHA256}-${FILE_NAME}"
PART="$MODEL.part"

verify_file() {
  local file="$1"
  [[ -f "$file" ]] || return 1

  local bytes sha
  bytes="$(stat -c '%s' "$file")"
  [[ "$bytes" == "$EXPECTED_BYTES" ]] || return 1

  sha="$(sha256sum "$file" | awk '{print $1}')"
  [[ "$sha" == "$EXPECTED_SHA256" ]] || return 1

  python3 - "$file" <<'PY'
import struct
import sys

path = sys.argv[1]
with open(path, "rb") as handle:
    raw = handle.read(4)
    if len(raw) != 4:
        raise SystemExit("Demucs checkpoint is truncated before magic")
    magic = struct.unpack("<I", raw)[0]
    if magic != 0x646D6336:
        raise SystemExit(f"Expected six-source dmc6 magic, got 0x{magic:08x}")

    header = handle.read(8)
    if len(header) != 8:
        raise SystemExit("Demucs checkpoint is truncated before first tensor header")
    n_dims, name_len = struct.unpack("<ii", header)
    if not 1 <= n_dims <= 4:
        raise SystemExit(f"Invalid first tensor rank: {n_dims}")
    if not 1 <= name_len <= 512:
        raise SystemExit(f"Invalid first tensor name length: {name_len}")

    dims_raw = handle.read(4 * n_dims)
    if len(dims_raw) != 4 * n_dims:
        raise SystemExit("Demucs checkpoint is truncated in first tensor dimensions")
    dims = struct.unpack("<" + "i" * n_dims, dims_raw)
    if any(dim <= 0 for dim in dims):
        raise SystemExit(f"Invalid first tensor dimensions: {dims}")

    name_raw = handle.read(name_len)
    if len(name_raw) != name_len:
        raise SystemExit("Demucs checkpoint is truncated in first tensor name")
    try:
        name = name_raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise SystemExit(f"First tensor name is not UTF-8: {exc}")
    if not name.strip():
        raise SystemExit("First tensor name is empty")

print(f"DEMUCS_MODEL_HEADER_OK first_tensor={name} dims={dims}")
PY
}

if verify_file "$MODEL"; then
  echo "Using verified cached Demucs checkpoint: $MODEL"
else
  rm -f "$MODEL" "$PART"
  echo "Downloading pinned htdemucs_6s checkpoint..."
  curl \
    --fail \
    --location \
    --retry 3 \
    --retry-delay 2 \
    --connect-timeout 30 \
    --output "$PART" \
    "$URL"

  if ! verify_file "$PART"; then
    rm -f "$PART"
    echo "Pinned Demucs checkpoint verification failed" >&2
    exit 1
  fi

  mv "$PART" "$MODEL"
fi

# Assert a few architecture-relevant tensor names are present in the binary
# serialization. This complements magic/hash validation and catches a
# mismatched-but-six-source artifact contract before Android runtime use.
for tensor in \
  "encoder.0.conv.weight" \
  "decoder.3.conv_tr.weight" \
  "tdecoder.3.conv_tr.weight"; do
  if ! LC_ALL=C grep -aFq "$tensor" "$MODEL"; then
    echo "Expected tensor name not found in checkpoint: $tensor" >&2
    exit 1
  fi
done

echo "DEMUCS_MODEL_CHECKPOINT_OK"
echo "source_revision=$SOURCE_REVISION"
echo "bytes=$EXPECTED_BYTES"
echo "sha256=$EXPECTED_SHA256"
