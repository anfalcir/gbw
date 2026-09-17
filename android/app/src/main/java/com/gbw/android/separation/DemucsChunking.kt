package com.gbw.android.separation

internal data class DemucsChunkPlan(
    val index: Int,
    val coreStartFrame: Long,
    val coreFrames: Int,
    val windowStartFrame: Long,
    val readStartFrame: Long,
    val readFrames: Int,
    val inputDestinationFrame: Int,
    val cropStartFrame: Int,
)

/**
 * Bounds every native call to one 7.8 s Demucs window while giving each
 * written core 1.15 s of neighbouring context on both sides.
 */
internal object DemucsChunking {
    const val WINDOW_FRAMES = 343_980 // 7.8 s @ 44.1 kHz
    const val CORE_FRAMES = 242_550 // 5.5 s @ 44.1 kHz
    const val CONTEXT_FRAMES = 50_715 // 1.15 s @ 44.1 kHz

    init {
        check(CORE_FRAMES + 2 * CONTEXT_FRAMES == WINDOW_FRAMES)
    }

    fun plan(totalFrames: Long): List<DemucsChunkPlan> {
        require(totalFrames > 0L) { "Demucs input must contain audio frames" }
        val result = mutableListOf<DemucsChunkPlan>()
        var coreStart = 0L
        var index = 0
        while (coreStart < totalFrames) {
            val coreFrames = minOf(CORE_FRAMES.toLong(), totalFrames - coreStart).toInt()
            val windowStart = coreStart - CONTEXT_FRAMES.toLong()
            val windowEndExclusive = windowStart + WINDOW_FRAMES.toLong()
            val readStart = maxOf(0L, windowStart)
            val readEndExclusive = minOf(totalFrames, windowEndExclusive)
            val readFrames = maxOf(0L, readEndExclusive - readStart).toInt()
            val inputDestination = (readStart - windowStart).toInt()
            check(inputDestination >= 0)
            check(inputDestination + readFrames <= WINDOW_FRAMES)
            check(CONTEXT_FRAMES + coreFrames <= WINDOW_FRAMES)
            result += DemucsChunkPlan(
                index = index,
                coreStartFrame = coreStart,
                coreFrames = coreFrames,
                windowStartFrame = windowStart,
                readStartFrame = readStart,
                readFrames = readFrames,
                inputDestinationFrame = inputDestination,
                cropStartFrame = CONTEXT_FRAMES,
            )
            coreStart += coreFrames.toLong()
            index += 1
        }
        return result
    }
}
