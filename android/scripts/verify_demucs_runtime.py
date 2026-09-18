#!/usr/bin/env python3
import sys
import zipfile

apk = sys.argv[1] if len(sys.argv) > 1 else "app/build/outputs/apk/debug/app-debug.apk"
entry = "lib/arm64-v8a/libgbw_demucs.so"
required = [
    b"engine=direct-segment-v1",
    b"window_frames=343980",
    b"parallel=openblas",
    b"openblas@e0166008be8e466242aa76b2ff75ce3f0fbf574a",
    b"blas_threads=",
]
with zipfile.ZipFile(apk) as archive:
    data = archive.read(entry)
missing = [x.decode("ascii") for x in required if x not in data]
if missing:
    raise SystemExit("Demucs optimized runtime markers missing: " + ", ".join(missing))
print("Demucs/OpenBLAS optimized runtime markers: PASS")
