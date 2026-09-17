# Native third-party component: Rubber Band Library

GBW Android's pitch engine uses the official Rubber Band Library source mirror.

- Component: Rubber Band Library
- Upstream: `https://github.com/breakfastquay/rubberband`
- Version: `4.0.0`
- Pinned source commit: `1d95888bec3ae0a17c0c4af791810d5a63f6bc35`
- Integration: NDK/CMake, `single/RubberBandSingle.cpp`
- Engine selected by GBW: R3 / Finer (`OptionEngineFiner`)
- FFT/resampler: upstream built-in implementations selected by the single-translation-unit build
- Android ABI in this gate: `arm64-v8a`

## Reproducibility

`CMakeLists.txt` fetches the exact commit above. Dynamic/latest tags are not used. A build therefore selects a fixed Rubber Band source revision even if the upstream default branch moves.

## Licence

Rubber Band Library is offered upstream under GNU GPL v2-or-later, with a separate commercial-licensing option. The upstream source also documents the licences of bundled/supporting FFT and resampler implementations.

This repository is public, but public source visibility alone is not a substitute for a formal distribution/licensing decision. **Do not treat the Android release as cleared for public distribution solely because this development integration builds.** Before an RC/public APK is distributed, the project must either:

1. adopt and satisfy a GPL-compatible distribution model for the complete combined work, including corresponding-source obligations; or
2. obtain and document an appropriate commercial Rubber Band licence.

The migration plan already contains a mandatory licence audit before public distribution. This file records the exact dependency selected for that future audit.
