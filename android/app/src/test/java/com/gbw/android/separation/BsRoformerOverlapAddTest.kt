package com.gbw.android.separation

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BsRoformerOverlapAddTest {
    @Test
    fun constantModelOutputRemainsConstantAfterCounterNormalization() {
        val totalFrames = BsRoformerContract.CHUNK_FRAMES.toLong()
        val plans = BsRoformerChunking.plan(totalFrames)
        val output = ByteBuffer
            .allocateDirect(BsRoformerContract.OUTPUT_BYTES)
            .order(ByteOrder.nativeOrder())
        val floats = output.asFloatBuffer()
        while (floats.hasRemaining()) {
            floats.put(1f)
        }

        val framesByStem = LongArray(BsRoformerContract.STEM_COUNT)
        val overlap = BsRoformerOverlapAdd { stem, outputStart, bytes, frames ->
            assertEquals(framesByStem[stem], outputStart)
            val values = bytes.duplicate().order(ByteOrder.nativeOrder()).asFloatBuffer()
            val expectedSamples = frames * BsRoformerContract.CHANNELS
            assertEquals(expectedSamples, values.remaining())
            while (values.hasRemaining()) {
                assertEquals(1f, values.get(), 1e-6f)
            }
            framesByStem[stem] += frames.toLong()
        }

        plans.forEach { overlap.consume(it, output) }
        overlap.finish(plans.size)

        framesByStem.forEach {
            assertEquals(totalFrames, it)
        }
    }

    @Test
    fun longTrackCropEmitsExactlyOriginalFramesPerStem() {
        val totalFrames = 44_100L * 14L
        val plans = BsRoformerChunking.plan(totalFrames)
        val output = ByteBuffer
            .allocateDirect(BsRoformerContract.OUTPUT_BYTES)
            .order(ByteOrder.nativeOrder())

        val framesByStem = LongArray(BsRoformerContract.STEM_COUNT)
        val overlap = BsRoformerOverlapAdd { stem, outputStart, _, frames ->
            assertEquals(framesByStem[stem], outputStart)
            framesByStem[stem] += frames.toLong()
        }

        plans.forEach { overlap.consume(it, output) }
        overlap.finish(plans.size)

        framesByStem.forEach {
            assertEquals(totalFrames, it)
        }
        assertTrue(plans.first().writeFrames < BsRoformerContract.STEP_FRAMES)
        assertTrue(plans.last().writeFrames < BsRoformerContract.STEP_FRAMES)
    }
}
