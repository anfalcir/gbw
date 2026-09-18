#!/usr/bin/env python3
"""Verify Java runtime classes that must be defined inside the final GBW APK."""

from __future__ import annotations

import struct
import sys
import zipfile
from pathlib import Path

REQUIRED = {
    "Lcom/arthenica/ffmpegkit/FFmpegKitConfig;",
    "Lcom/arthenica/smartexception/java/Exceptions;",
}


def read_uleb128(data: bytes, offset: int) -> tuple[int, int]:
    value = 0
    shift = 0
    while True:
        byte = data[offset]
        offset += 1
        value |= (byte & 0x7F) << shift
        if byte < 0x80:
            return value, offset
        shift += 7
        if shift > 35:
            raise ValueError("invalid uleb128")


def dex_classes(data: bytes) -> set[str]:
    if not data.startswith(b"dex\n"):
        raise ValueError("invalid DEX magic")

    string_ids_size, string_ids_off = struct.unpack_from("<II", data, 0x38)
    type_ids_size, type_ids_off = struct.unpack_from("<II", data, 0x40)
    class_defs_size, class_defs_off = struct.unpack_from("<II", data, 0x60)

    strings: list[str] = []
    for index in range(string_ids_size):
        (string_off,) = struct.unpack_from("<I", data, string_ids_off + index * 4)
        _, cursor = read_uleb128(data, string_off)
        end = data.index(0, cursor)
        strings.append(data[cursor:end].decode("utf-8", errors="replace"))

    types: list[str] = []
    for index in range(type_ids_size):
        (string_index,) = struct.unpack_from("<I", data, type_ids_off + index * 4)
        types.append(strings[string_index])

    classes: set[str] = set()
    for index in range(class_defs_size):
        (class_index,) = struct.unpack_from("<I", data, class_defs_off + index * 32)
        classes.add(types[class_index])
    return classes


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: verify_apk_java_runtime.py <apk>", file=sys.stderr)
        return 2

    apk = Path(sys.argv[1])
    if not apk.is_file():
        print(f"APK not found: {apk}", file=sys.stderr)
        return 2

    defined: set[str] = set()
    with zipfile.ZipFile(apk) as archive:
        dex_names = sorted(
            name for name in archive.namelist()
            if name.startswith("classes") and name.endswith(".dex")
        )
        if not dex_names:
            print("No classes*.dex entries found", file=sys.stderr)
            return 1
        for name in dex_names:
            defined.update(dex_classes(archive.read(name)))

    missing = sorted(REQUIRED - defined)
    if missing:
        print("Missing required runtime class definitions:", file=sys.stderr)
        for descriptor in missing:
            print(f"  - {descriptor}", file=sys.stderr)
        return 1

    for descriptor in sorted(REQUIRED):
        print(f"APK_RUNTIME_CLASS_OK {descriptor}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
