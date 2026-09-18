#!/usr/bin/env python3
import argparse
import pathlib
import sys
import zipfile

FORBIDDEN = (
    "bsroformer",
    "bs-roformer",
    "alta qualidade",
    "comparar",
    "executorch",
    "xnnpack",
    "pffft",
    "action_bsroformer",
    ".pte",
)
TEXT_SUFFIXES = {".kt", ".kts", ".cpp", ".c", ".h", ".hpp", ".xml", ".py", ".sh"}

def source_gate(root: pathlib.Path) -> None:
    candidates = [root / "app" / "src" / "main", root / "app" / "build.gradle.kts",
                  root / "tools", root / "scripts"]
    failures = []
    self_path = pathlib.Path(__file__).resolve()
    for candidate in candidates:
        paths = [candidate] if candidate.is_file() else (
            [p for p in candidate.rglob("*") if p.is_file()] if candidate.exists() else []
        )
        for path in paths:
            if path.resolve() == self_path or path.suffix.lower() not in TEXT_SUFFIXES:
                continue
            text = path.read_text(encoding="utf-8", errors="ignore").lower()
            for token in FORBIDDEN:
                if token in text:
                    failures.append(f"{path.relative_to(root)} contains forbidden active token {token!r}")
    if failures:
        raise SystemExit("Demucs-only source gate failed:\n" + "\n".join(failures))
    print("Demucs-only active source architecture: PASS")

def apk_gate(apk: pathlib.Path) -> None:
    with zipfile.ZipFile(apk) as archive:
        names = [name.lower() for name in archive.namelist()]
        forbidden_names = [
            name for name in names
            if any(token in name for token in ("bsroformer", "executorch", "xnnpack", "pffft", ".pte"))
        ]
        if forbidden_names:
            raise SystemExit("Demucs-only APK gate failed; dead components remain:\n" +
                             "\n".join(forbidden_names))
        native_names = [name for name in names if name.startswith("lib/arm64-v8a/")]
        expected = {"lib/arm64-v8a/libgbw_demucs.so", "lib/arm64-v8a/libgbw_rubberband.so"}
        missing = sorted(expected.difference(native_names))
        if missing:
            raise SystemExit("Demucs-only APK gate missing required native runtime: " + ", ".join(missing))
    print("Demucs-only APK contents: PASS")

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=pathlib.Path)
    parser.add_argument("--apk", type=pathlib.Path)
    args = parser.parse_args()
    if args.source is None and args.apk is None:
        parser.error("provide --source and/or --apk")
    if args.source is not None:
        source_gate(args.source.resolve())
    if args.apk is not None:
        apk_gate(args.apk.resolve())

if __name__ == "__main__":
    main()
