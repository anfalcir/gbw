package com.gbw.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceScoreBandTest {
    @Test
    fun scoreBandsCoverNormalizedRangeAtDocumentedBoundaries() {
        assertEquals(SourceScoreBand.LOW, SourceScorePresentation.bandFor(0))
        assertEquals(SourceScoreBand.LOW, SourceScorePresentation.bandFor(54))
        assertEquals(SourceScoreBand.INTERMEDIATE, SourceScorePresentation.bandFor(55))
        assertEquals(SourceScoreBand.INTERMEDIATE, SourceScorePresentation.bandFor(74))
        assertEquals(SourceScoreBand.GOOD, SourceScorePresentation.bandFor(75))
        assertEquals(SourceScoreBand.GOOD, SourceScorePresentation.bandFor(100))
    }

    @Test
    fun strongOfficialCandidateLandsInGoodBand() {
        val ranked = SourceSearchRules.rank(
            SourceSearchRequest("Wolves At The Gate", "Enemy"),
            listOf(
                SourceCandidateDraft(
                    provider = SourceProvider.YOUTUBE,
                    title = "Wolves At The Gate - Enemy (Official Audio)",
                    uploader = "Wolves At The Gate",
                    url = "https://example.test/official",
                    durationSeconds = 220.0,
                    officialSignal = true,
                    automaticDownloadSupported = true,
                )
            ),
        ).single()

        assertTrue(ranked.score >= SourceScorePresentation.GOOD_MIN)
        assertEquals(SourceScoreBand.GOOD, SourceScorePresentation.bandFor(ranked.score))
    }
}
