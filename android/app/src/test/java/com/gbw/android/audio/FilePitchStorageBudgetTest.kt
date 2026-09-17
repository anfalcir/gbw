package com.gbw.android.audio

import com.gbw.android.domain.OutputFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilePitchStorageBudgetTest {
    @Test
    fun `budget includes concurrent float files and reserve`() {
        val float32 = FilePitchStorageBudget.requiredBytes(60.0, 48_000, 2, OutputFormat.WAV_FLOAT32)
        val wav24 = FilePitchStorageBudget.requiredBytes(60.0, 48_000, 2, OutputFormat.WAV_24)
        assertEquals(113_188_864L, float32)
        assertEquals(136_228_864L, wav24)
        assertTrue(requireNotNull(wav24) > requireNotNull(float32))
    }

    @Test
    fun `unknown or invalid duration skips estimate instead of guessing`() {
        assertNull(FilePitchStorageBudget.requiredBytes(null, 48_000, 2, OutputFormat.WAV_FLOAT32))
        assertNull(FilePitchStorageBudget.requiredBytes(Double.NaN, 48_000, 2, OutputFormat.WAV_FLOAT32))
        assertNull(FilePitchStorageBudget.requiredBytes(0.0, 48_000, 2, OutputFormat.WAV_FLOAT32))
    }
}
