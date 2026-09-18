package com.gbw.android.source

import com.gbw.android.domain.SourceProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpDownloadStrategyTest {
    @Test
    fun youtubeHasFreshThenCompatibilityFallbacks() {
        val attempts = YtDlpDownloadStrategy.attempts(SourceProvider.YOUTUBE, "251")
        assertEquals("251", attempts.first().format)
        assertTrue(attempts.size >= 3)
        assertTrue(attempts.any { it.forceRuntimeUpdateBefore })
        assertTrue(attempts.drop(1).any { it.extractorArgs?.contains("web_embedded") == true })
    }

    @Test
    fun forbiddenIsRetryable_butOrdinaryValidationErrorIsNot() {
        assertTrue(YtDlpDownloadStrategy.retryable(IllegalStateException("HTTP Error 403: Forbidden")))
        assertFalse(YtDlpDownloadStrategy.retryable(IllegalStateException("Arquivo local vazio")))
    }

    @Test
    fun nonYoutubeUsesSingleAttempt() {
        assertEquals(1, YtDlpDownloadStrategy.attempts(SourceProvider.SOUNDCLOUD, "http_mp3_128").size)
    }
}
