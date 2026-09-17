# Native third-party components

This file records source identity and licence boundaries for native/DSP/ML dependencies used by GBW Android. Exact source pins are part of the reproducibility contract; dynamic/latest revisions are not accepted.

## Rubber Band Library

GBW Android's pitch engine uses the official Rubber Band Library source mirror.

- Component: Rubber Band Library
- Upstream: `https://github.com/breakfastquay/rubberband`
- Version: `4.0.0`
- Pinned source commit: `1d95888bec3ae0a17c0c4af791810d5a63f6bc35`
- Integration: NDK/CMake, `single/RubberBandSingle.cpp`
- Engine selected by GBW: R3 / Finer (`OptionEngineFiner`)
- Android ABI in this gate: `arm64-v8a`

### Rubber Band licence

Rubber Band Library is offered upstream under GNU GPL v2-or-later, with a separate commercial-licensing option. **Do not treat the Android release as cleared for public distribution solely because the development integration builds.** Before an RC/public APK is distributed, the project must either satisfy a GPL-compatible distribution model for the complete combined work or obtain/document an appropriate commercial Rubber Band licence.

## demucs.cpp runtime

GBW Android's Rápida separation path uses the C++17 HT-Demucs implementation from `demucs.cpp`. The Android build populates the pinned source revision but deliberately does not import its desktop CLI stack; only `src/*.cpp` is compiled into `libgbw_demucs.so` with GBW's JNI boundary.

- Component: `demucs.cpp`
- Upstream: `https://github.com/sevagh/demucs.cpp`
- Pinned source commit: `f1206e9adeea103aef4a636b9e62297cf1f8e34e`
- Upstream source licence: MIT (`LICENSE` at the pinned revision)
- Android integration: NDK/CMake, C++17, `arm64-v8a`
- Model contract: HT-Demucs six-source (`htdemucs_6s`)
- Required model sample rate: 44,100 Hz
- Required GBW runtime channels: stereo
- Stem order fixed by GBW: `drums`, `bass`, `other`, `vocals`, `guitar`, `piano`

The JNI layer accepts bounded float32 stereo chunks rather than allowing the upstream runtime to own user-file decoding. This keeps SAF/codec handling in the controlled Android audio layer and makes mobile memory bounding explicit.

## Eigen

`demucs.cpp` uses Eigen. GBW fetches the exact revision pinned for this integration rather than following a moving branch.

- Component: Eigen
- Upstream: `https://gitlab.com/libeigen/eigen.git`
- Pinned source commit: `dd8c71e62852b2fe429edb6682ac91fd1c578a26`
- Licence family: MPL-2.0, subject to upstream file-level notices/exceptions
- Integration: headers only for the Demucs native target

## htdemucs_6s model weights

The large model is **not bundled in the APK or committed to this repository**.

GBW pins:

- Provider/repository: `Retrobear/demucs.cpp` on Hugging Face
- Immutable source revision: `5f5daffffcf06ad7b27a7285da327e18ea62068a`
- File: `ggml-model-htdemucs-6s-f16.bin`
- Expected bytes: `54,855,129`
- SHA-256: `09704f4ceae204e56e77d5eefd6ac71d7275be81fd507e6913371d59abcee856`
- Required serialization magic: `dmc6` / `0x646d6336`

The runtime downloads to a `.part` file in private app storage, validates size and SHA-256, then atomically promotes the artifact. CI independently downloads/reuses the checkpoint cache only after revalidating the same identity and checks architecture-relevant tensor names.

### Model provenance/licence note

The public Hugging Face dataset card declares **MIT** metadata and documents the upstream Demucs weight origins used for the converted ggml files. That is useful provenance evidence, but the project will still keep pretrained-weight provenance/licence review as an explicit RC audit item rather than inferring that runtime functionality alone clears every redistribution obligation. Keeping the model outside the APK reduces coupling between application distribution and model delivery.

## Reproducibility

`CMakeLists.txt` fetches exact runtime source commits. `android/scripts/validate_demucs_model.sh` fixes the external checkpoint revision, byte size, SHA-256, six-source magic and representative tensor names. Any future runtime/model upgrade must deliberately change the relevant pins, pass Android CI, update this file and update current-state/parity documentation.
