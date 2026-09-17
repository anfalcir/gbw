package com.gbw.android.audio

import java.io.Closeable
import java.util.concurrent.CancellationException
import kotlin.math.abs
import kotlin.math.pow

/** Pure contracts shared by unit tests and the native wrapper. */
object RubberBandR3Contract {
    const val VERSION = "4.0.0"
    const val SOURCE_COMMIT = "1d95888bec3ae0a17c0c4af791810d5a63f6bc35"
    const val ENGINE_VERSION = 3
    const val MAX_BLOCK_FRAMES = 4096

    fun pitchScale(semitones: Double): Double = 2.0.pow(semitones / 12.0)

    fun validateRequest(sampleRate: Int, channels: Int, semitones: Double) {
        require(sampleRate in 8_000..192_000) { "sampleRate must be between 8000 and 192000 Hz" }
        require(channels in 1..8) { "channels must be between 1 and 8" }
        require(semitones.isFinite() && semitones in -24.0..24.0) { "semitones must be finite and within +/-24" }
    }

    fun durationWithinTolerance(inputFrames: Long, outputFrames: Long, sampleRate: Int): Boolean {
        if (inputFrames < 0 || outputFrames < 0 || sampleRate <= 0) return false
        // Pitch-only processing is time ratio 1:1. Allow 20 ms or 1 frame, whichever is larger,
        // to absorb fixed algorithmic rounding while still detecting real sync drift.
        val toleranceFrames = maxOf(1L, (sampleRate * 0.020).toLong())
        return abs(outputFrames - inputFrames) <= toleranceFrames
    }
}

internal object RubberBandNative {
    init {
        System.loadLibrary("gbw_rubberband")
    }

    external fun libraryIdentity(): String
    external fun create(
        sampleRate: Int,
        channels: Int,
        semitones: Double,
        preserveFormants: Boolean,
        expectedFrames: Long,
    ): Long
    external fun engineVersion(handle: Long): Int
    external fun study(handle: Long, interleaved: FloatArray, frames: Int, finalBlock: Boolean)
    external fun process(handle: Long, interleaved: FloatArray, frames: Int, finalBlock: Boolean)
    external fun available(handle: Long): Int
    external fun retrieve(handle: Long, maxFrames: Int): FloatArray
    external fun cancel(handle: Long)
    external fun isCancelled(handle: Long): Boolean
    external fun destroy(handle: Long)
}

/**
 * Streaming two-pass wrapper around Rubber Band's offline R3 engine.
 * Input and output blocks are interleaved float PCM. The native engine itself receives planar floats.
 */
class RubberBandR3Session(
    val sampleRate: Int,
    val channels: Int,
    val semitones: Double,
    val preserveFormants: Boolean,
    expectedFrames: Long = 0L,
) : Closeable {
    private var handle: Long

    init {
        RubberBandR3Contract.validateRequest(sampleRate, channels, semitones)
        require(expectedFrames >= 0L) { "expectedFrames must be non-negative" }
        handle = RubberBandNative.create(sampleRate, channels, semitones, preserveFormants, expectedFrames)
        check(handle != 0L) { "Rubber Band native session creation failed" }
        check(RubberBandNative.engineVersion(handle) == RubberBandR3Contract.ENGINE_VERSION) {
            "Rubber Band R3 engine was not activated"
        }
    }

    val identity: String
        get() = RubberBandNative.libraryIdentity()

    val isCancelled: Boolean
        get() = handle == 0L || RubberBandNative.isCancelled(handle)

    fun study(interleaved: FloatArray, frames: Int, finalBlock: Boolean) {
        ensureOpen()
        validateBlock(interleaved, frames)
        RubberBandNative.study(handle, interleaved, frames, finalBlock)
    }

    fun process(interleaved: FloatArray, frames: Int, finalBlock: Boolean) {
        ensureOpen()
        validateBlock(interleaved, frames)
        RubberBandNative.process(handle, interleaved, frames, finalBlock)
    }

    fun availableFrames(): Int {
        ensureOpen()
        return RubberBandNative.available(handle)
    }

    fun retrieve(maxFrames: Int = RubberBandR3Contract.MAX_BLOCK_FRAMES): FloatArray {
        ensureOpen()
        require(maxFrames > 0) { "maxFrames must be positive" }
        return RubberBandNative.retrieve(handle, maxFrames)
    }

    fun cancel() {
        if (handle != 0L) RubberBandNative.cancel(handle)
    }

    fun throwIfCancelled() {
        if (isCancelled) throw CancellationException("Rubber Band processing cancelled")
    }

    override fun close() {
        val current = handle
        if (current != 0L) {
            handle = 0L
            RubberBandNative.destroy(current)
        }
    }

    private fun ensureOpen() {
        check(handle != 0L) { "Rubber Band session is closed" }
    }

    private fun validateBlock(interleaved: FloatArray, frames: Int) {
        require(frames >= 0) { "frames must be non-negative" }
        require(interleaved.size == frames * channels) {
            "interleaved block size must equal frames * channels"
        }
        require(frames <= RubberBandR3Contract.MAX_BLOCK_FRAMES) {
            "block exceeds ${RubberBandR3Contract.MAX_BLOCK_FRAMES} frames"
        }
    }
}
