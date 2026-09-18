package com.gbw.android.separation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemucsThreadPolicyTest {
    @Test
    fun `supported benchmark counts are exactly 1 2 and 4`() {
        assertEquals(setOf(1, 2, 4), DemucsThreadPolicy.supportedThreadCounts)
        assertEquals(4, DemucsThreadPolicy.DEFAULT_THREADS)
    }

    @Test
    fun `benchmark override accepts 1 2 and 4`() {
        listOf(1, 2, 4).forEach { threads ->
            assertEquals(threads, DemucsThreadPolicy.resolve(threads.toString()))
        }
    }

    @Test
    fun `invalid benchmark override falls back conservatively`() {
        listOf(null, "", "0", "3", "8", "invalid").forEach { value ->
            assertEquals(4, DemucsThreadPolicy.resolve(value))
        }
        assertTrue(DemucsThreadPolicy.DEFAULT_THREADS <= 4)
    }
}
