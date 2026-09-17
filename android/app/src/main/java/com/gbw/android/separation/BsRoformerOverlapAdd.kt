package com.gbw.android.separation

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Arrays

/**
 * Streaming equivalent of upstream result/counter overlap-add.
 *
 * The sink is synchronous and must not retain the provided scratch buffer.
 * Only one STEP_FRAMES carry is retained, so RAM is bounded independently of
 * song duration.
 */
internal class BsRoformerOverlapAdd(
    private val sink: (
        stemIndex: Int,
        outputStartFrame: Long,
        interleavedFloat32: ByteBuffer,
        frames: Int,
    ) -> Unit,
) {
    private val carry = FloatArray(
        BsRoformerContract.STEM_COUNT *
            BsRoformerContract.STEP_FRAMES *
            BsRoformerContract.CHANNELS,
    )
    private val carryCounter = FloatArray(BsRoformerContract.STEP_FRAMES)
    private val scratchBytes = ByteBuffer
        .allocateDirect(
            BsRoformerContract.STEP_FRAMES *
                BsRoformerContract.CHANNELS *
                Float.SIZE_BYTES,
        )
        .order(ByteOrder.nativeOrder())
    private val scratchFloats = scratchBytes.asFloatBuffer()

    private var nextIndex = 0

    fun consume(plan: BsRoformerChunkPlan, chunkOutput: ByteBuffer) {
        require(plan.index == nextIndex) {
            "BS-RoFormer chunks must be consumed in order: expected " +
                nextIndex + ", got " + plan.index
        }
        require(chunkOutput.isDirect) { "BS-RoFormer chunk output must be direct" }
        require(chunkOutput.order() == ByteOrder.nativeOrder()) {
            "BS-RoFormer chunk output must use native byte order"
        }
        require(chunkOutput.capacity() >= BsRoformerSpectralNative.outputBytes) {
            "BS-RoFormer chunk output buffer is too small"
        }

        chunkOutput.clear()
        val model = chunkOutput.asFloatBuffer()

        if (plan.writeFrames > 0) {
            for (stem in 0 until BsRoformerContract.STEM_COUNT) {
                scratchFloats.clear()
                val from = plan.writeOffsetInFinalized
                val until = from + plan.writeFrames
                for (frame in from until until) {
                    val currentWeight = BsRoformerChunking.weightAt(plan, frame)
                    val counter = carryCounter[frame] + currentWeight
                    check(counter > 0f && counter.isFinite()) {
                        "Invalid BS-RoFormer overlap counter at chunk=" +
                            plan.index + " frame=" + frame
                    }

                    for (channel in 0 until BsRoformerContract.CHANNELS) {
                        val prior = carry[carryIndex(stem, frame, channel)]
                        val current = model.get(modelIndex(stem, frame, channel))
                        val normalized = (prior + current * currentWeight) / counter
                        check(normalized.isFinite()) {
                            "Non-finite BS-RoFormer overlap output"
                        }
                        scratchFloats.put(normalized)
                    }
                }

                val byteCount = Math.multiplyExact(
                    Math.multiplyExact(plan.writeFrames, BsRoformerContract.CHANNELS),
                    Float.SIZE_BYTES,
                )
                scratchBytes.position(0)
                scratchBytes.limit(byteCount)
                sink(stem, plan.outputStartFrame, scratchBytes, plan.writeFrames)
            }
        }

        Arrays.fill(carryCounter, 0f)
        Arrays.fill(carry, 0f)
        val nextFrames = maxOf(
            0,
            minOf(
                BsRoformerContract.STEP_FRAMES,
                plan.validFrames - BsRoformerContract.STEP_FRAMES,
            ),
        )
        for (frame in 0 until nextFrames) {
            val sourceFrame = BsRoformerContract.STEP_FRAMES + frame
            val weight = BsRoformerChunking.weightAt(plan, sourceFrame)
            carryCounter[frame] = weight
            for (stem in 0 until BsRoformerContract.STEM_COUNT) {
                for (channel in 0 until BsRoformerContract.CHANNELS) {
                    carry[carryIndex(stem, frame, channel)] =
                        model.get(modelIndex(stem, sourceFrame, channel)) * weight
                }
            }
        }

        nextIndex += 1
    }

    fun finish(expectedChunks: Int) {
        check(nextIndex == expectedChunks) {
            "BS-RoFormer overlap-add ended at chunk " +
                nextIndex + " / " + expectedChunks
        }
        check(carryCounter.all { it == 0f }) {
            "BS-RoFormer overlap-add ended with unflushed carry"
        }
    }

    private fun carryIndex(stem: Int, frame: Int, channel: Int): Int =
        ((stem * BsRoformerContract.STEP_FRAMES + frame) *
            BsRoformerContract.CHANNELS) + channel

    private fun modelIndex(stem: Int, frame: Int, channel: Int): Int =
        ((stem * BsRoformerContract.CHUNK_FRAMES + frame) *
            BsRoformerContract.CHANNELS) + channel
}
