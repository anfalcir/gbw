# Native third-party components

This file records source identity and licence boundaries for native/DSP dependencies used by GBW Android. Exact source pins are part of the reproducibility contract; dynamic/latest revisions are not accepted.

## Rubber Band Library

GBW Android's pitch engine uses the official Rubber Band Library source mirror.

- Component: Rubber Band Library
- Upstream: `https://github.com/breakfastquay/rubberband`
- Version: `4.0.0`
- Pinned source commit: `1d95888bec3ae0a17c0c4af791810d5a63f6bc35`
- Integration: NDK/CMake, `single/RubberBandSingle.cpp`
- Engine selected by GBW: R3 / Finer (`OptionEngineFiner`)
- FFT/resampler: upstream built-in implementations selected by the single-translation-unit build
- Android ABI in this gate: `arm64-v8a`

### Rubber Band licence

Rubber Band Library is offered upstream under GNU GPL v2-or-later, with a separate commercial-licensing option. The upstream source also documents the licences of bundled/supporting FFT and resampler implementations.

This repository is public, but public source visibility alone is not a substitute for a formal distribution/licensing decision. **Do not treat the Android release as cleared for public distribution solely because this development integration builds.** Before an RC/public APK is distributed, the project must either:

1. adopt and satisfy a GPL-compatible distribution model for the complete combined work, including corresponding-source obligations; or
2. obtain and document an appropriate commercial Rubber Band licence.

## demucs.cpp runtime

GBW Android's Rápida separation path uses the C++17 HT-Demucs implementation from `demucs.cpp`. The GBW build populates the source revision but deliberately does not import its desktop CMake project or CLI dependencies; only `src/*.cpp` is compiled into `libgbw_demucs.so` together with GBW's own JNI boundary.

- Component: `demucs.cpp`
- Upstream: `https://github.com/sevagh/demucs.cpp`
- Pinned source commit: `f1206e9adeea103aef4a636b9e62297cf1f8e34e`
- Upstream source licence: MIT (`LICENSE` at the pinned revision)
- Android integration: NDK/CMake, C++17, `arm64-v8a`
- Model contract: HT-Demucs six-source (`htdemucs_6s`)
- Required model sample rate: 44,100 Hz
- Required GBW runtime channels: stereo
- Stem order fixed by GBW: `drums`, `bass`, `other`, `vocals`, `guitar`, `piano`

The JNI layer accepts bounded float32 stereo chunks rather than allowing the upstream runtime to own user-file decoding. This keeps SAF/codec handling in the already controlled Android audio layer and makes mobile memory bounding explicit.

## Eigen

`demucs.cpp` uses Eigen. GBW fetches the exact Eigen revision pinned by the selected `demucs.cpp` source tree rather than following Eigen's moving default branch.

- Component: Eigen
- Upstream: `https://gitlab.com/libeigen/eigen.git`
- Pinned source commit: `dd8c71e62852b2fe429edb6682ac91fd1c578a26`
- Licence family: MPL-2.0 (with upstream file-level notices/exceptions as applicable)
- Integration: headers only for the Demucs native target

## htdemucs_6s model weights

The large model is **not bundled in the APK or committed to this repository**. The runtime model manager must pin an immutable source revision, expected byte size and SHA-256, download to private app storage, verify integrity before use, and use an atomic `.part` → final-file promotion.

The model-weight distribution/licensing status is intentionally kept as a separate release-audit item. A runtime/source implementation being technically functional does not by itself clear redistribution of pretrained weights. The model therefore remains outside the APK while provenance and licence obligations are finalized before RC/public distribution.

## Reproducibility

`CMakeLists.txt` fetches the exact commits recorded above. Any future source upgrade must change the pin deliberately, pass Android CI, and update this document and the current-state documentation. Large model weights remain runtime artifacts and are never part of the repository's source-integrity baseline.
