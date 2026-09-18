package com.gbw.android.separation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemucsContractTest {
    @Test
    fun `demucs model identity is immutable and six source`() {
        val spec = DemucsModelContract.quick
        assertEquals("htdemucs_6s", spec.id)
        assertEquals("ggml-model-htdemucs-6s-f16.bin", spec.fileName)
        assertEquals(54_855_129L, spec.expectedBytes)
        assertEquals(64, spec.sha256.length)
        assertTrue(spec.sha256.all { it in '0'..'9' || it in 'a'..'f' })
        assertTrue(spec.downloadUrl.contains(spec.sourceRevision))
        assertTrue(spec.downloadUrl.contains(spec.fileName))
        assertEquals(
            listOf("drums", "bass", "other", "vocals", "guitar", "piano"),
            DemucsModelContract.stemNames,
        )
        assertEquals(6, DemucsModelContract.stemNames.distinct().size)
        assertFalse(DemucsModelContract.RUNTIME_COMMIT.isBlank())
        assertEquals(DemucsChunking.WINDOW_FRAMES, DemucsNative.MODEL_WINDOW_FRAMES)
        assertEquals(setOf(1, 2, 4), DemucsThreadPolicy.supportedThreadCounts)
        assertEquals(1, DemucsThreadPolicy.DEFAULT_THREADS)
        assertEquals(4_000L, DemucsRuntimeMonitor.SAMPLE_INTERVAL_MS)
    }

    @Test
    fun `chunk planner covers source exactly with bounded context windows`() {
        val total = DemucsChunking.CORE_FRAMES.toLong() * 2L + 12_345L
        val plans = DemucsChunking.plan(total)
        assertEquals(3, plans.size)
        assertEquals(total, plans.sumOf { it.coreFrames.toLong() })

        var expectedCoreStart = 0L
        plans.forEachIndexed { index, plan ->
            assertEquals(index, plan.index)
            assertEquals(expectedCoreStart, plan.coreStartFrame)
            assertTrue(plan.coreFrames in 1..DemucsChunking.CORE_FRAMES)
            assertTrue(plan.inputDestinationFrame >= 0)
            assertTrue(plan.readFrames >= 0)
            assertTrue(plan.inputDestinationFrame + plan.readFrames <= DemucsChunking.WINDOW_FRAMES)
            assertEquals(DemucsChunking.CONTEXT_FRAMES, plan.cropStartFrame)
            assertTrue(plan.cropStartFrame + plan.coreFrames <= DemucsChunking.WINDOW_FRAMES)
            expectedCoreStart += plan.coreFrames.toLong()
        }
        assertEquals(total, expectedCoreStart)
    }

    @Test
    fun `short source is symmetrically padded around fixed core origin`() {
        val frames = 44_100L
        val plan = DemucsChunking.plan(frames).single()
        assertEquals(-DemucsChunking.CONTEXT_FRAMES.toLong(), plan.windowStartFrame)
        assertEquals(0L, plan.readStartFrame)
        assertEquals(DemucsChunking.CONTEXT_FRAMES, plan.inputDestinationFrame)
        assertEquals(frames.toInt(), plan.readFrames)
        assertEquals(frames.toInt(), plan.coreFrames)
    }
}
