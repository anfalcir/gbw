package com.gbw.android.separation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BsRoformerContractTest {
    @Test
    fun sourceAndRuntimeIdentitiesArePinnedToLinux523Contract() {
        assertEquals("0.1.5", BsRoformerContract.INFERENCE_SOURCE_VERSION)
        assertEquals(699_412_152L, BsRoformerContract.SOURCE_CHECKPOINT_BYTES)
        assertEquals(686L, BsRoformerContract.PACKAGED_CONFIG_BYTES)
        assertEquals("1.3.1", BsRoformerContract.EXECUTORCH_VERSION)
        assertEquals(44_100, BsRoformerContract.SAMPLE_RATE)
        assertEquals(2, BsRoformerContract.CHANNELS)
        assertEquals(
            listOf("bass", "drums", "other", "vocals", "guitar", "piano"),
            BsRoformerContract.stemNames,
        )
        assertEquals(1_151, BsRoformerContract.PRODUCTION_STFT_TIME_FRAMES)
        assertEquals(1_025, BsRoformerContract.STFT_BINS)
        assertTrue(BsRoformerContract.SOURCE_CHECKPOINT_SHA256.matches(Regex("[0-9a-f]{64}")))
        assertTrue(BsRoformerContract.PACKAGED_CONFIG_SHA256.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun longSourceReproducesOuterReflectBorderAndCoversOriginalExactly() {
        val total = 44_100L * 180L
        val border = BsRoformerChunking.outerBorderFrames(total)
        assertEquals(BsRoformerContract.BORDER_FRAMES, border)

        val plans = BsRoformerChunking.plan(total)
        assertTrue(plans.size > 2)
        assertEquals(
            total + 2L * border,
            plans.sumOf { it.finalizedFrames.toLong() },
        )

        val writable = plans.filter { it.writeFrames > 0 }
        assertEquals(total, writable.sumOf { it.writeFrames.toLong() })
        var expectedOutput = 0L
        writable.forEach { plan ->
            assertEquals(expectedOutput, plan.outputStartFrame)
            expectedOutput += plan.writeFrames.toLong()
        }
        assertEquals(total, expectedOutput)
    }

    @Test
    fun oneExactChunkStillSchedulesOverlappingTailChunkLikeUpstream() {
        val plans = BsRoformerChunking.plan(BsRoformerContract.CHUNK_FRAMES.toLong())
        assertEquals(2, plans.size)

        val first = plans[0]
        assertTrue(first.forceFadeInUnity)
        assertFalse(first.forceFadeOutUnity)
        assertEquals(BsRoformerRightPadMode.NONE, first.rightPadMode)

        val second = plans[1]
        assertFalse(second.forceFadeInUnity)
        assertTrue(second.forceFadeOutUnity)
        assertEquals(BsRoformerContract.STEP_FRAMES, second.validFrames)
        assertEquals(BsRoformerRightPadMode.ZERO, second.rightPadMode)

        assertEquals(
            BsRoformerContract.CHUNK_FRAMES.toLong(),
            plans.sumOf { it.writeFrames.toLong() },
        )
    }

    @Test
    fun rightPaddingFollowsUpstreamReflectVersusZeroThreshold() {
        val reflectLength = BsRoformerContract.STEP_FRAMES + 2L
        val reflectPlans = BsRoformerChunking.plan(reflectLength)
        assertEquals(BsRoformerRightPadMode.REFLECT, reflectPlans.first().rightPadMode)

        val zeroLength = BsRoformerContract.STEP_FRAMES + 1L
        val zeroPlans = BsRoformerChunking.plan(zeroLength)
        assertEquals(BsRoformerRightPadMode.ZERO, zeroPlans.first().rightPadMode)
    }

    @Test
    fun linearOverlapWeightsReproduceEndpointSemanticsAndPositiveCounter() {
        val plans = BsRoformerChunking.plan(BsRoformerContract.CHUNK_FRAMES.toLong())
        val first = plans[0]
        val second = plans[1]

        assertEquals(1f, BsRoformerChunking.weightAt(first, 0), 0f)
        assertEquals(
            1f,
            BsRoformerChunking.weightAt(first, BsRoformerContract.FADE_FRAMES - 1),
            0f,
        )
        assertEquals(
            0f,
            BsRoformerChunking.weightAt(first, BsRoformerContract.CHUNK_FRAMES - 1),
            0f,
        )

        assertEquals(0f, BsRoformerChunking.weightAt(second, 0), 0f)
        assertEquals(
            1f,
            BsRoformerChunking.weightAt(second, BsRoformerContract.CHUNK_FRAMES - 1),
            0f,
        )

        for (offset in 0 until BsRoformerContract.STEP_FRAMES step 997) {
            val priorFrame = BsRoformerContract.STEP_FRAMES + offset
            val currentFrame = offset
            val counter =
                BsRoformerChunking.weightAt(first, priorFrame) +
                    BsRoformerChunking.weightAt(second, currentFrame)
            assertTrue("counter=$counter offset=$offset", counter > 0f)
            assertTrue("counter=$counter offset=$offset", counter <= 2f)
        }
    }
}
