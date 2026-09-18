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



## ExecuTorch runtime

GBW Android uses ExecuTorch for the real-valued BS-RoFormer transformer/mask
core. Spectral DSP stays in the separately pinned PFFFT JNI boundary.

- Component: ExecuTorch Android
- Maven coordinate: org.pytorch:executorch-android:1.3.1
- Export/runtime version: 1.3.1
- Initial backend: XNNPACK / CPU
- Initial ABI consumed by GBW: arm64-v8a
- Upstream licence: BSD License at the 1.3.1 release

The generic Module Java/Kotlin API is experimental upstream. GBW therefore
keeps it behind BsRoformerExecuTorch and validates tensor dtype/shape on every
forward boundary. Large tensors use direct FloatBuffer input and a reusable
caller-owned FloatBuffer output rather than per-chunk float arrays.

## PFFFT — BS-RoFormer spectral DSP

GBW Android uses PFFFT for the native STFT/ISTFT boundary around the
BS-RoFormer ExecuTorch transformer core.

- Component: PFFFT
- Upstream: https://github.com/marton78/pffft
- Pinned source commit: e1dbebc9fbf74247d12f094accbbc470aaee8715
- Integration: C static library linked into libgbw_bsroformer_spectral.so
- Android ABI in this gate: arm64-v8a
- Contract: real FFT 2048, hop 512, periodic Hann, center reflect padding

The pinned LICENSE.txt permits redistribution in source and binary form with
conditions preserving copyright/conditions/disclaimer and forbids endorsement
using contributor/UCAR/NCAR names without permission. Upstream describes this
as BSD-like. Required notices must be carried into release documentation.

The Android spectral implementation is anchored by
android/tools/BsRoformerSpectralHostSmoke.cpp, whose committed reference bins
were generated from the exact torch.stft/torch.istft semantics used by the
BS-RoFormer 0.1.5 source. CI requires this host golden before assembling the APK.

## BS-RoFormer-SW source/checkpoint and production PTE

- `bs-roformer-infer 0.1.5` source: `244cddd4f7611956eb1cc4958e82b65b4891c019` (MIT code)
- model revision: `a443a2985534b3bc815ef54a5d446c6a0390f974`
- checkpoint: 699,412,152 bytes; SHA-256 `24e7d35ee9c64415673d3fd33e06a67cac2c103c5df6267ba1576459c775916e`
- packaged config SHA-256: `52df622c95ff3c1f4e1389f476ed737581a2c2dc12324d52c9763be9ccd2be2b`
- production PTE: `GBW-BS-RoFormer-SW-executorch-1.3.1-T1151.pte`
- PTE: 700,284,960 bytes; SHA-256 `8c3cc68404b7fadb2a41ec332b0493290d956f9490dc5c21c5120ee596807182`
- export: torch `2.12.1+cpu`, ExecuTorch `1.3.1`, XNNPACK
- provenance: BS-RoFormer Production PTE #3 / run `35289168951`

### Weight/PTE redistribution gate

The inference code licence does not establish the trained checkpoint licence. The current checkpoint host declares the model licence as **unknown**. GBW therefore does not publish/rehost the checkpoint or derived PTE as a public Release asset until redistribution rights are established. The Android public PTE URL is intentionally unset; development/homologation imports the exact CI-produced PTE through SAF and validates exact bytes + SHA-256.

## Reproducibility

`CMakeLists.txt` fetches exact runtime source commits. `android/scripts/validate_demucs_model.sh` fixes the external checkpoint revision, byte size, SHA-256, six-source magic and representative tensor names. Any future runtime/model upgrade must deliberately change the relevant pins, pass Android CI, update this file and update current-state/parity documentation.
