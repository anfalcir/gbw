package com.gbw.android.separation

import org.junit.Assert.assertEquals
import org.junit.Test

class BsRoformerChunkInputTest {
    @Test
    fun virtualReflectMappingMatchesPyTorchEdgeExclusion() {
        val total = 1_000_000L
        val border = BsRoformerContract.BORDER_FRAMES.toLong()

        assertEquals(border, BsRoformerChunkInput.originalFrameForVirtual(total, 0L))
        assertEquals(1L, BsRoformerChunkInput.originalFrameForVirtual(total, border - 1L))
        assertEquals(0L, BsRoformerChunkInput.originalFrameForVirtual(total, border))
        assertEquals(total - 1L, BsRoformerChunkInput.originalFrameForVirtual(total, border + total - 1L))
        assertEquals(total - 2L, BsRoformerChunkInput.originalFrameForVirtual(total, border + total))
        assertEquals(
            total - 1L - border,
            BsRoformerChunkInput.originalFrameForVirtual(
                total,
                border + total + border - 1L,
            ),
        )
    }

    @Test
    fun shortTrackHasNoOuterBorder() {
        val total = BsRoformerContract.BORDER_FRAMES.toLong()
        assertEquals(0, BsRoformerChunking.outerBorderFrames(total))
        assertEquals(0L, BsRoformerChunkInput.originalFrameForVirtual(total, 0L))
        assertEquals(total - 1L, BsRoformerChunkInput.originalFrameForVirtual(total, total - 1L))
    }

    @Test
    fun outerBorderStartsOnlyAboveTwoBordersLikeUpstream() {
        val threshold = 2L * BsRoformerContract.BORDER_FRAMES
        assertEquals(0, BsRoformerChunking.outerBorderFrames(threshold))
        assertEquals(
            BsRoformerContract.BORDER_FRAMES,
            BsRoformerChunking.outerBorderFrames(threshold + 1L),
        )
    }
}
