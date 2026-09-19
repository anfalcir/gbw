package com.gbw.android.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioStorageBudgetTest {
    @Test fun demucsBudgetScalesWithSixStereoFloatStems() {
        val frames = 44_100L * 60L
        val required = AudioStorageBudget.demucsOutputRequiredBytes(frames)
        val rawStems = frames * 2L * 4L * 6L
        assertTrue(required > rawStems)
    }

    @Test fun exportBudgetIncludesWorkingHeadroom() {
        val frames = 44_100L * 60L
        assertTrue(AudioStorageBudget.exportWorkingRequiredBytes(frames) > frames * 32L)
    }

    @Test fun sourceBudgetRequiresKnownPositiveDuration() {
        assertNull(AudioStorageBudget.sourcePrepareRequiredBytes(0.0))
        assertNull(AudioStorageBudget.sourcePrepareRequiredBytes(Double.NaN))
        assertTrue(requireNotNull(AudioStorageBudget.sourcePrepareRequiredBytes(300.0)) > 0L)
    }

    @Test fun arithmeticSaturatesInsteadOfOverflowing() {
        assertEquals(Long.MAX_VALUE, AudioStorageBudget.multiplySaturated(Long.MAX_VALUE, 2L))
        assertEquals(Long.MAX_VALUE, AudioStorageBudget.addSaturated(Long.MAX_VALUE, 1L))
    }
}
