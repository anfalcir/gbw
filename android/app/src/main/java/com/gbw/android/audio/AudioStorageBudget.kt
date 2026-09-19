package com.gbw.android.audio

import android.os.StatFs
import java.io.File
import kotlin.math.ceil

internal object AudioStorageBudget {
    private const val CHANNELS = 2L
    private const val FLOAT_BYTES = 4L
    private const val DEMUCS_STEMS = 6L
    private const val SAMPLE_RATE = 44_100.0
    private const val RESERVE_BYTES = 96L * 1024L * 1024L

    fun demucsOutputRequiredBytes(frames: Long): Long =
        addSaturated(
            multiplySaturated(frames.coerceAtLeast(0L), CHANNELS * FLOAT_BYTES * DEMUCS_STEMS),
            RESERVE_BYTES,
        )

    fun exportWorkingRequiredBytes(frames: Long): Long =
        addSaturated(
            // backing mix + backing scaled + guitar scaled + two conservative final files
            multiplySaturated(frames.coerceAtLeast(0L), 48L),
            RESERVE_BYTES,
        )

    fun sourcePrepareRequiredBytes(durationSeconds: Double): Long? {
        if (!durationSeconds.isFinite() || durationSeconds <= 0.0) return null
        val frames = ceil(durationSeconds * SAMPLE_RATE).toLong()
        return addSaturated(
            // prepared stereo float32 plus conservative room for the acquired native file
            multiplySaturated(frames, CHANNELS * FLOAT_BYTES * 2L),
            RESERVE_BYTES,
        )
    }

    fun requireAvailable(directory: File, requiredBytes: Long, operation: String) {
        val available = StatFs(directory.absolutePath).availableBytes
        require(available >= requiredBytes) {
            val requiredMiB = requiredBytes / (1024L * 1024L)
            val availableMiB = available / (1024L * 1024L)
            "Espaço insuficiente para $operation: necessários cerca de $requiredMiB MiB; " +
                "disponíveis $availableMiB MiB."
        }
    }

    internal fun multiplySaturated(a: Long, b: Long): Long {
        if (a <= 0L || b <= 0L) return 0L
        return if (a > Long.MAX_VALUE / b) Long.MAX_VALUE else a * b
    }

    internal fun addSaturated(a: Long, b: Long): Long =
        if (a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b
}
