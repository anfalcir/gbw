package com.gbw.android.audio

import com.gbw.android.domain.OutputFormat
import kotlin.math.ceil

internal object FilePitchStorageBudget {
    private const val SAFETY_RESERVE_BYTES = 64L * 1024L * 1024L

    fun requiredBytes(
        durationSeconds: Double?,
        sampleRate: Int,
        channels: Int,
        outputFormat: OutputFormat,
    ): Long? {
        if (durationSeconds == null || !durationSeconds.isFinite() || durationSeconds <= 0.0) return null
        if (sampleRate <= 0 || channels <= 0) return null
        return runCatching {
            val frames = ceil(durationSeconds * sampleRate.toDouble()).toLong()
            val samples = Math.multiplyExact(frames, channels.toLong())
            val floatBytes = Math.multiplyExact(samples, 4L)
            val concurrentCopies = if (outputFormat == OutputFormat.WAV_FLOAT32) 2L else 3L
            Math.addExact(Math.multiplyExact(floatBytes, concurrentCopies), SAFETY_RESERVE_BYTES)
        }.getOrNull()
    }
}
