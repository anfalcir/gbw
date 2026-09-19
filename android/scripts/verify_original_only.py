#!/usr/bin/env python3
"""Reject functional Android regressions that reintroduce removed transposition features."""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SELF = Path(__file__).resolve()
SCAN_ROOTS = [
    ROOT / "app" / "src" / "main",
    ROOT / "app" / "src" / "test",
    ROOT / "scripts",
    ROOT / "tools",
]
EXTRA_FILES = [
    ROOT / "app" / "build.gradle.kts",
]
TEXT_SUFFIXES = {".kt", ".kts", ".java", ".cpp", ".cc", ".c", ".h", ".hpp", ".py", ".sh", ".txt", ".xml"}

# Construct tokens without spelling the removed feature names in repository
# source, so this verifier can require a true zero-reference functional tree.
BANNED = [
    "pi" + "tch",
    "tu" + "ning",
    "rubber" + "band",
    "semit" + "one",
    "for" + "mant",
]
PATTERN = re.compile("|".join(re.escape(token) for token in BANNED), re.IGNORECASE)


def candidates():
    for base in SCAN_ROOTS:
        if not base.exists():
            continue
        for path in base.rglob("*"):
            if path == SELF or not path.is_file() or path.suffix.lower() not in TEXT_SUFFIXES:
                continue
            yield path
    for path in EXTRA_FILES:
        if path.is_file():
            yield path


violations = []
for path in candidates():
    relative = path.relative_to(ROOT)
    if any(part in {"build", ".gradle"} for part in relative.parts):
        continue
    text = path.read_text(encoding="utf-8", errors="replace")
    for number, line in enumerate(text.splitlines(), 1):
        if PATTERN.search(line):
            violations.append(f"{relative}:{number}: {line.strip()}")

if violations:
    print("Original-only architecture regression detected:", file=sys.stderr)
    for violation in violations:
        print("  " + violation, file=sys.stderr)
    sys.exit(1)

print("ORIGINAL_ONLY_ARCHITECTURE_OK")
