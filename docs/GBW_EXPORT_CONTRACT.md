# GBW Export Contract

Producer: GBW
Schema version: 1

## Final product
The final product is a pair: backing + guitar.
Backing contains exactly drums + bass + other + vocals + piano.
Guitar is never mixed into backing.

## Shared gain
The Android renderer preserves the Linux 5.23 render_pair semantics:
1. mix the five backing stems in float32;
2. recombine backing + guitar;
3. measure the recombined peak;
4. use -1 dBFS as the safety target;
5. calculate one gain factor;
6. apply that exact same factor to backing and guitar.

Backing and guitar are never independently normalized. Their musical level relationship is therefore preserved when both are imported at 0 dB.

## Pitch
For the pitched variant:
- drums remains unchanged;
- guitar, bass, other and piano receive pitch;
- vocals receive pitch with the vocalFormants policy;
- Rubber Band R3 is used;
- the final stem is normalized to the exact original frame count after the R3 duration-tolerance gate.

If pitch is zero and the original pair is requested, GBW does not create a redundant pitched copy.

## Audio contract
Workflow sample rate: 44.1 kHz stereo.
Formats: WAV float32, WAV 24-bit, FLAC 24-bit.
Backing and guitar in one variant have identical sample rate, channel count and frame alignment.

## Layout
exports/<exportId>/original/backing.<ext>
exports/<exportId>/original/guitar.<ext>
exports/<exportId>/pitch_+Nst/backing.<ext>
exports/<exportId>/pitch_+Nst/guitar.<ext>
exports/<exportId>/export_manifest.json

Filenames are convenience only. Consumers should use manifest roles.

## Manifest
export_manifest.json records producer/schema, GBW projectId, artist/song, exportId/revision, target peak, shared-gain policy, variant, pitch semitones, role, relativePath, format, sampleRate, channels, durationFrames, size and SHA-256.

This file contract is the future interoperability boundary for GuitarLab. There is no bidirectional code dependency between the applications.