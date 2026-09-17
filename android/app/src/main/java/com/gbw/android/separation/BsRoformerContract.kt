package com.gbw.android.separation

/**
 * Immutable source/runtime contract for the Linux-5.23-compatible
 * BS-RoFormer-SW high-quality separator.
 *
 * The PyTorch checkpoint below is a conversion/provenance input only. Android
 * production inference will consume a separately generated and pinned
 * ExecuTorch .pte artifact; do not try to load the .ckpt in the app.
 */
internal object BsRoformerContract {
    const val INFERENCE_SOURCE_COMMIT = "244cddd4f7611956eb1cc4958e82b65b4891c019"
    const val INFERENCE_SOURCE_VERSION = "0.1.5"

    const val MODEL_REVISION = "a443a2985534b3bc815ef54a5d446c6a0390f974"
    const val SOURCE_CHECKPOINT_FILE = "BS-Rofo-SW-Fixed.ckpt"
    const val SOURCE_CHECKPOINT_BYTES = 699_412_152L
    const val SOURCE_CHECKPOINT_SHA256 =
        "24e7d35ee9c64415673d3fd33e06a67cac2c103c5df6267ba1576459c775916e"

    // bs-roformer-infer 0.1.5 prefers this packaged config over the remote
    // same-named YAML. These bytes therefore define the actual Linux runtime.
    const val PACKAGED_CONFIG_FILE = "BS-Rofo-SW-Fixed.yaml"
    const val PACKAGED_CONFIG_BYTES = 686L
    const val PACKAGED_CONFIG_SHA256 =
        "52df622c95ff3c1f4e1389f476ed737581a2c2dc12324d52c9763be9ccd2be2b"

    const val EXECUTORCH_VERSION = "1.3.1"
    const val PFFFT_COMMIT = "e1dbebc9fbf74247d12f094accbbc470aaee8715"

    const val SAMPLE_RATE = 44_100
    const val CHANNELS = 2
    const val STEM_COUNT = 6
    val stemNames = listOf("bass", "drums", "other", "vocals", "guitar", "piano")

    const val STFT_N_FFT = 2_048
    const val STFT_HOP = 512
    const val STFT_BINS = STFT_N_FFT / 2 + 1
    const val COMPLEX_COMPONENTS = 2

    const val CHUNK_FRAMES = 588_800
    const val NUM_OVERLAP = 2
    const val STEP_FRAMES = CHUNK_FRAMES / NUM_OVERLAP
    const val FADE_FRAMES = CHUNK_FRAMES / 10
    const val BORDER_FRAMES = CHUNK_FRAMES - STEP_FRAMES

    // torch.stft(center=true) with N=588800, n_fft=2048, hop=512:
    // padded length = N + n_fft and T = 1 + N / hop.
    const val PRODUCTION_STFT_TIME_FRAMES = 1 + CHUNK_FRAMES / STFT_HOP

    const val INPUT_FLOATS =
        CHANNELS * STFT_BINS * PRODUCTION_STFT_TIME_FRAMES * COMPLEX_COMPONENTS
    const val MASK_FLOATS =
        STEM_COUNT * (STFT_BINS * CHANNELS) * PRODUCTION_STFT_TIME_FRAMES * COMPLEX_COMPONENTS

    init {
        check(CHUNK_FRAMES % NUM_OVERLAP == 0)
        check(CHUNK_FRAMES % STFT_HOP == 0)
        check(STEP_FRAMES == 294_400)
        check(FADE_FRAMES == 58_880)
        check(BORDER_FRAMES == 294_400)
        check(PRODUCTION_STFT_TIME_FRAMES == 1_151)
        check(stemNames.size == STEM_COUNT)
        check(stemNames.distinct().size == STEM_COUNT)
    }
}
