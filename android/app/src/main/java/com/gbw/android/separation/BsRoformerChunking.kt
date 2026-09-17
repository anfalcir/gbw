package com.gbw.android.separation

internal enum class BsRoformerRightPadMode {
    NONE,
    REFLECT,
    ZERO,
}

internal data class BsRoformerChunkPlan(
    val index: Int,
    val virtualStartFrame: Long,
    val validFrames: Int,
    val rightPadFrames: Int,
    val rightPadMode: BsRoformerRightPadMode,
    val forceFadeInUnity: Boolean,
    val forceFadeOutUnity: Boolean,
    val finalizedFrames: Int,
    val writeOffsetInFinalized: Int,
    val writeFrames: Int,
    val outputStartFrame: Long,
)

/**
 * Reproduces bs-roformer-infer 0.1.5 demix_track geometry without allocating
 * complete song-sized result/counter tensors.
 *
 * Production can keep only one STEP_FRAMES overlap carry per stem/channel:
 * when chunk i is available, [i, i + STEP) is final after combining the prior
 * carry with the first half of the current chunk.
 */
internal object BsRoformerChunking {
    const val CHUNK_FRAMES = BsRoformerContract.CHUNK_FRAMES
    const val STEP_FRAMES = BsRoformerContract.STEP_FRAMES
    const val FADE_FRAMES = BsRoformerContract.FADE_FRAMES
    const val BORDER_FRAMES = BsRoformerContract.BORDER_FRAMES

    fun outerBorderFrames(totalFrames: Long): Int {
        require(totalFrames > 0L) { "BS-RoFormer input must contain audio frames" }
        return if (totalFrames > 2L * BORDER_FRAMES) BORDER_FRAMES else 0
    }

    fun virtualFrames(totalFrames: Long): Long {
        val border = outerBorderFrames(totalFrames)
        return Math.addExact(totalFrames, 2L * border)
    }

    fun plan(totalFrames: Long): List<BsRoformerChunkPlan> {
        require(totalFrames > 0L) { "BS-RoFormer input must contain audio frames" }

        val border = outerBorderFrames(totalFrames).toLong()
        val virtualTotal = Math.addExact(totalFrames, 2L * border)
        val keepStart = border
        val keepEnd = Math.addExact(border, totalFrames)

        val result = mutableListOf<BsRoformerChunkPlan>()
        var start = 0L
        var index = 0
        while (start < virtualTotal) {
            val available = minOf(CHUNK_FRAMES.toLong(), virtualTotal - start).toInt()
            val rightPad = CHUNK_FRAMES - available
            val padMode = when {
                rightPad == 0 -> BsRoformerRightPadMode.NONE
                available > CHUNK_FRAMES / 2 + 1 -> BsRoformerRightPadMode.REFLECT
                else -> BsRoformerRightPadMode.ZERO
            }

            val first = start == 0L
            // Upstream uses if i == 0 ... elif i + C >= total_length ...
            val fadeOutUnity = !first && start + CHUNK_FRAMES.toLong() >= virtualTotal

            val finalized = minOf(STEP_FRAMES.toLong(), virtualTotal - start).toInt()
            val finalizedEnd = start + finalized.toLong()
            val writeStart = maxOf(start, keepStart)
            val writeEnd = minOf(finalizedEnd, keepEnd)
            val writeFrames = maxOf(0L, writeEnd - writeStart).toInt()
            val writeOffset = if (writeFrames == 0) 0 else (writeStart - start).toInt()
            val outputStart = if (writeFrames == 0) 0L else writeStart - keepStart

            result += BsRoformerChunkPlan(
                index = index,
                virtualStartFrame = start,
                validFrames = available,
                rightPadFrames = rightPad,
                rightPadMode = padMode,
                forceFadeInUnity = first,
                forceFadeOutUnity = fadeOutUnity,
                finalizedFrames = finalized,
                writeOffsetInFinalized = writeOffset,
                writeFrames = writeFrames,
                outputStartFrame = outputStart,
            )
            start += STEP_FRAMES.toLong()
            index += 1
        }
        return result
    }

    /**
     * Exact torch.linspace linear window used by upstream demix_track.
     */
    fun weightAt(plan: BsRoformerChunkPlan, chunkFrame: Int): Float {
        require(chunkFrame in 0 until CHUNK_FRAMES)
        if (plan.forceFadeInUnity && chunkFrame < FADE_FRAMES) return 1f
        if (plan.forceFadeOutUnity && chunkFrame >= CHUNK_FRAMES - FADE_FRAMES) return 1f

        return when {
            chunkFrame < FADE_FRAMES ->
                chunkFrame.toFloat() / (FADE_FRAMES - 1).toFloat()
            chunkFrame >= CHUNK_FRAMES - FADE_FRAMES -> {
                val offset = chunkFrame - (CHUNK_FRAMES - FADE_FRAMES)
                1f - offset.toFloat() / (FADE_FRAMES - 1).toFloat()
            }
            else -> 1f
        }
    }
}
