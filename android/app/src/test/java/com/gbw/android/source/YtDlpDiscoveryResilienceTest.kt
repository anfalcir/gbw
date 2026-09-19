package com.gbw.android.source

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YtDlpDiscoveryResilienceTest {
    @Test
    fun `unavailable individual result is skipped instead of aborting search`() = runBlocking {
        val value = YtDlpDiscoveryResilience.availableOrNull<String> {
            throw IllegalStateException("This video is not available")
        }
        assertNull(value)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is never swallowed as an unavailable candidate`() = runBlocking {
        YtDlpDiscoveryResilience.availableOrNull<String> {
            throw CancellationException("cancel")
        }
    }

    @Test
    fun `healthy individual result is preserved`() = runBlocking {
        val value = YtDlpDiscoveryResilience.availableOrNull { "ok" }
        assertEquals("ok", value)
    }
}
