package com.gbw.android.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RubberBandContractTest {
    @Test
    fun `drop B to drop D is plus three semitones`() {
        val scale = RubberBandR3Contract.pitchScale(3.0)
        assertEquals(1.189207115, scale, 1e-9)
    }

    @Test
    fun `drop D to drop B is minus three semitones`() {
        val up = RubberBandR3Contract.pitchScale(3.0)
        val down = RubberBandR3Contract.pitchScale(-3.0)
        assertEquals(1.0, up * down, 1e-9)
    }

    @Test
    fun `pitch only duration allows small rounding but rejects drift`() {
        val input = 48_000L * 60L
        assertTrue(RubberBandR3Contract.durationWithinTolerance(input, input + 100, 48_000))
        assertFalse(RubberBandR3Contract.durationWithinTolerance(input, input + 2_000, 48_000))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid sample rate is rejected before JNI`() {
        RubberBandR3Contract.validateRequest(7_999, 2, 3.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid channel count is rejected before JNI`() {
        RubberBandR3Contract.validateRequest(48_000, 0, 3.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non finite pitch is rejected before JNI`() {
        RubberBandR3Contract.validateRequest(48_000, 2, Double.NaN)
    }
}
