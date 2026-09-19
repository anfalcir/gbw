package com.gbw.android.source

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class YtDlpDiscoveryResilienceTest {
    @Test
    fun `unavailable individual result is skipped instead of aborting search`() {
        runBlocking {
            val value = YtDlpDiscoveryResilience.availableOrNull<String> {
                throw IllegalStateException("This video is not available")
            }
            assertNull(value)
        }
    }

    @Test
    fun `cancellation is never swallowed as an unavailable candidate`() {
        runBlocking {
            try {
                YtDlpDiscoveryResilience.availableOrNull<String> {
                    throw CancellationException("cancel")
                }
                fail("CancellationException should be propagated")
            } catch (_: CancellationException) {
                // Expected: navigation/app cancellation must remain cancellable.
            }
        }
    }

    @Test
    fun `healthy individual result is preserved`() {
        runBlocking {
            val value = YtDlpDiscoveryResilience.availableOrNull { "ok" }
            assertEquals("ok", value)
        }
    }
}
