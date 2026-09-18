package com.gbw.android.separation

internal fun interface DemucsNativeProgressListener {
    fun onProgress(progress: Float, message: String)
}

/** Thin JNI boundary around the pinned demucs.cpp six-source runtime. */
internal object DemucsNative {
    const val REQUIRED_SAMPLE_RATE = 44_100
    const val REQUIRED_CHANNELS = 2
    const val SOURCE_COUNT = 6
    const val MODEL_WINDOW_FRAMES = 343_980 // 7.8 s at 44.1 kHz, matching demucs.cpp.

    val stemNames: List<String> = listOf(
        "drums",
        "bass",
        "other",
        "vocals",
        "guitar",
        "piano",
    )

    init {
        System.loadLibrary("gbw_demucs")
    }

    fun identity(): String = nativeIdentity()

    fun configureBlasThreads(threadCount: Int): Int {
        require(threadCount == 1 || threadCount == 2 || threadCount == 4) {
            "Demucs BLAS threads must be 1, 2, or 4"
        }
        return nativeConfigureBlasThreads(threadCount)
    }

    fun createModel(modelPath: String): Long {
        require(modelPath.isNotBlank()) { "Demucs model path is blank" }
        return nativeCreateModel(modelPath).also {
            check(it != 0L) { "Demucs native model handle was not created" }
        }
    }

    fun destroyModel(handle: Long) {
        if (handle != 0L) nativeDestroyModel(handle)
    }

    fun cancel() = nativeCancel()

    fun separateChunk(
        handle: Long,
        interleavedStereo: FloatArray,
        progressListener: DemucsNativeProgressListener? = null,
    ): FloatArray {
        require(handle != 0L) { "Demucs model handle is null" }
        require(interleavedStereo.size % REQUIRED_CHANNELS == 0) {
            "Demucs PCM must be interleaved stereo"
        }
        val frames = interleavedStereo.size / REQUIRED_CHANNELS
        require(frames > 1) { "Demucs chunk is empty" }
        val result = nativeSeparateChunk(handle, interleavedStereo, frames, progressListener)
        val expected = Math.multiplyExact(Math.multiplyExact(SOURCE_COUNT, frames), REQUIRED_CHANNELS)
        check(result.size == expected) {
            "Demucs JNI output has ${result.size} samples; expected $expected"
        }
        return result
    }

    private external fun nativeIdentity(): String
    private external fun nativeConfigureBlasThreads(threadCount: Int): Int
    private external fun nativeCreateModel(modelPath: String): Long
    private external fun nativeDestroyModel(handle: Long)
    private external fun nativeCancel()
    private external fun nativeSeparateChunk(
        handle: Long,
        interleavedStereo: FloatArray,
        frames: Int,
        progressListener: DemucsNativeProgressListener?,
    ): FloatArray
}
