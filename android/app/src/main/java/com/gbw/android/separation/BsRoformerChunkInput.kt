package com.gbw.android.separation

import com.gbw.android.audio.FloatWavReader
import java.util.Arrays

/**
 * Materializes one fixed 588800-frame stereo model chunk from the virtual
 * upstream track:
 *
 * optional reflect border -> original track -> optional reflect border,
 * followed by the per-chunk right padding used by demix_track.
 */
internal object BsRoformerChunkInput {
    fun fill(
        reader: FloatWavReader,
        totalFrames: Long,
        plan: BsRoformerChunkPlan,
        destination: FloatArray,
    ) {
        require(reader.info.sampleRate == BsRoformerContract.SAMPLE_RATE)
        require(reader.info.channels == BsRoformerContract.CHANNELS)
        require(reader.info.frames == totalFrames)
        require(destination.size == BsRoformerContract.CHUNK_FRAMES * BsRoformerContract.CHANNELS)

        Arrays.fill(destination, 0f)
        val border = BsRoformerChunking.outerBorderFrames(totalFrames).toLong()
        var virtualFrame = plan.virtualStartFrame
        var destinationFrame = 0
        var remaining = plan.validFrames

        while (remaining > 0) {
            val prefixEnd = border
            val bodyEnd = border + totalFrames
            val count = when {
                virtualFrame < prefixEnd ->
                    minOf(remaining.toLong(), prefixEnd - virtualFrame).toInt()
                virtualFrame < bodyEnd ->
                    minOf(remaining.toLong(), bodyEnd - virtualFrame).toInt()
                else ->
                    remaining
            }

            when {
                virtualFrame < prefixEnd -> copyDescending(
                    reader = reader,
                    firstOriginalFrame = border - virtualFrame,
                    frames = count,
                    destination = destination,
                    destinationFrame = destinationFrame,
                )
                virtualFrame < bodyEnd -> copyAscending(
                    reader = reader,
                    originalStartFrame = virtualFrame - border,
                    frames = count,
                    destination = destination,
                    destinationFrame = destinationFrame,
                )
                else -> {
                    val rightOffset = virtualFrame - bodyEnd
                    copyDescending(
                        reader = reader,
                        firstOriginalFrame = totalFrames - 2L - rightOffset,
                        frames = count,
                        destination = destination,
                        destinationFrame = destinationFrame,
                    )
                }
            }

            virtualFrame += count.toLong()
            destinationFrame += count
            remaining -= count
        }

        check(destinationFrame == plan.validFrames)
        applyRightPadding(plan, destination)
    }

    /**
     * Pure mapping helper used by tests to lock PyTorch reflect-pad semantics.
     * Valid only inside the virtual track, before per-chunk right padding.
     */
    fun originalFrameForVirtual(totalFrames: Long, virtualFrame: Long): Long {
        require(totalFrames > 0L)
        val border = BsRoformerChunking.outerBorderFrames(totalFrames).toLong()
        val virtualTotal = totalFrames + 2L * border
        require(virtualFrame in 0 until virtualTotal)
        return when {
            virtualFrame < border -> border - virtualFrame
            virtualFrame < border + totalFrames -> virtualFrame - border
            else -> {
                val rightOffset = virtualFrame - (border + totalFrames)
                totalFrames - 2L - rightOffset
            }
        }
    }

    private fun copyAscending(
        reader: FloatWavReader,
        originalStartFrame: Long,
        frames: Int,
        destination: FloatArray,
        destinationFrame: Int,
    ) {
        if (frames == 0) return
        reader.seekFrame(originalStartFrame)
        val read = reader.readBlock(frames)
        check(read.size == frames * BsRoformerContract.CHANNELS) {
            "Truncated BS-RoFormer source read"
        }
        System.arraycopy(
            read,
            0,
            destination,
            destinationFrame * BsRoformerContract.CHANNELS,
            read.size,
        )
    }

    private fun copyDescending(
        reader: FloatWavReader,
        firstOriginalFrame: Long,
        frames: Int,
        destination: FloatArray,
        destinationFrame: Int,
    ) {
        if (frames == 0) return
        val lowest = firstOriginalFrame - frames + 1L
        require(lowest >= 0L) { "Invalid BS-RoFormer reflect range" }

        reader.seekFrame(lowest)
        val read = reader.readBlock(frames)
        check(read.size == frames * BsRoformerContract.CHANNELS) {
            "Truncated BS-RoFormer reflected source read"
        }

        for (offset in 0 until frames) {
            val sourceFrame = frames - 1 - offset
            for (channel in 0 until BsRoformerContract.CHANNELS) {
                destination[
                    (destinationFrame + offset) * BsRoformerContract.CHANNELS + channel
                ] = read[sourceFrame * BsRoformerContract.CHANNELS + channel]
            }
        }
    }

    private fun applyRightPadding(
        plan: BsRoformerChunkPlan,
        destination: FloatArray,
    ) {
        when (plan.rightPadMode) {
            BsRoformerRightPadMode.NONE -> check(plan.rightPadFrames == 0)
            BsRoformerRightPadMode.ZERO -> {
                // Destination was zero-filled before the valid region was copied.
            }
            BsRoformerRightPadMode.REFLECT -> {
                check(plan.validFrames > BsRoformerContract.CHUNK_FRAMES / 2 + 1)
                check(plan.rightPadFrames < plan.validFrames)
                for (padFrame in 0 until plan.rightPadFrames) {
                    val sourceFrame = plan.validFrames - 2 - padFrame
                    val destinationFrame = plan.validFrames + padFrame
                    check(sourceFrame >= 0)
                    for (channel in 0 until BsRoformerContract.CHANNELS) {
                        destination[
                            destinationFrame * BsRoformerContract.CHANNELS + channel
                        ] = destination[
                            sourceFrame * BsRoformerContract.CHANNELS + channel
                        ]
                    }
                }
            }
        }
    }
}
