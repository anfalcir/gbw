package com.gbw.android.separation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemucsThreadPolicyTest {
    @Test
    fun `supported internal counts are exactly 1 and 2`() {
        assertEquals(setOf(1, 2), DemucsThreadPolicy.supportedThreadCounts)
        assertEquals(1, DemucsThreadPolicy.DEFAULT_THREADS)
    }

    @Test
    fun `internal override accepts 1 and 2`() {
        listOf(1, 2).forEach { threads ->
            assertEquals(threads, DemucsThreadPolicy.resolve(threads.toString()))
        }
    }

    @Test
    fun `invalid benchmark override falls back conservatively`() {
        listOf(null, "", "0", "3", "4", "8", "invalid").forEach { value ->
            assertEquals(1, DemucsThreadPolicy.resolve(value))
        }
        assertTrue(DemucsThreadPolicy.DEFAULT_THREADS <= 2)
    }
}
