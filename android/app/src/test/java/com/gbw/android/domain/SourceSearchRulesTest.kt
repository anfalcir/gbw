package com.gbw.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceSearchRulesTest {
    @Test
    fun `single word title uses exact token gate`() {
        assertTrue(SourceSearchRules.titleMatchesSong("Enemy", "Wolves At The Gate - Enemy (Official Audio)"))
        assertFalse(SourceSearchRules.titleMatchesSong("Enemy", "Wolves At The Gate - Enemies"))
        assertFalse(SourceSearchRules.titleMatchesSong("Enemy", "Eclipse - Enemyish"))
    }

    @Test
    fun `multi word title requires meaningful tokens but permits descriptors`() {
        assertTrue(SourceSearchRules.titleMatchesSong("All Around Me", "Flyleaf - All Around Me (Official Music Video)"))
        assertFalse(SourceSearchRules.titleMatchesSong("All Around Me", "Flyleaf - All I Need"))
    }

    @Test
    fun `artist can be proven by title or uploader`() {
        assertTrue(
            SourceSearchRules.artistMatchesRequest(
                "Wolves At The Gate",
                "Wolves At The Gate - Enemy",
                "Solid State Records",
            )
        )
        assertTrue(
            SourceSearchRules.artistMatchesRequest(
                "Wolves At The Gate",
                "Enemy",
                "Wolves At The Gate",
            )
        )
        assertFalse(
            SourceSearchRules.artistMatchesRequest(
                "Wolves At The Gate",
                "Enemy",
                "Unrelated Channel",
            )
        )
    }

    @Test
    fun `preview is never ranked ahead of complete candidates`() {
        val request = SourceSearchRequest("Wolves At The Gate", "Enemy")
        val ranked = SourceSearchRules.rank(
            request,
            listOf(
                SourceCandidateDraft(
                    provider = SourceProvider.SOUNDCLOUD,
                    title = "Wolves At The Gate - Enemy",
                    uploader = "Wolves At The Gate",
                    url = "https://example.test/preview",
                    quality = "PREVIEW",
                    qualityBonus = 40,
                    durationSeconds = 30.0,
                    previewOnly = true,
                ),
                SourceCandidateDraft(
                    provider = SourceProvider.YOUTUBE,
                    title = "Wolves At The Gate - Enemy (Official Audio)",
                    uploader = "Wolves At The Gate",
                    url = "https://example.test/full",
                    durationSeconds = 221.0,
                    officialSignal = true,
                ),
            ),
        )

        assertEquals("https://example.test/full", ranked.first().url)
        assertTrue(ranked.last().previewOnly)
        assertTrue(ranked.last().score <= 5)
    }

    @Test
    fun `cover and live descriptors are penalized when not requested`() {
        val request = SourceSearchRequest("Flyleaf", "All Around Me")
        val ranked = SourceSearchRules.rank(
            request,
            listOf(
                SourceCandidateDraft(
                    provider = SourceProvider.YOUTUBE,
                    title = "Flyleaf - All Around Me",
                    uploader = "Flyleaf",
                    url = "https://example.test/studio",
                    durationSeconds = 205.0,
                    officialSignal = true,
                ),
                SourceCandidateDraft(
                    provider = SourceProvider.YOUTUBE,
                    title = "Flyleaf - All Around Me Live Cover",
                    uploader = "Fan",
                    url = "https://example.test/cover",
                    durationSeconds = 205.0,
                ),
            ),
        )

        assertEquals("https://example.test/studio", ranked.first().url)
        assertTrue(ranked.first().score > ranked.last().score)
    }

    @Test
    fun `duration consensus demotes truncated outlier`() {
        val request = SourceSearchRequest("Artist", "Song")
        val ranked = SourceSearchRules.rank(
            request,
            listOf(
                SourceCandidateDraft(SourceProvider.BANDCAMP, "Artist - Song", "Artist", "https://a", durationSeconds = 210.0),
                SourceCandidateDraft(SourceProvider.SOUNDCLOUD, "Artist - Song", "Artist", "https://b", durationSeconds = 214.0),
                SourceCandidateDraft(SourceProvider.YOUTUBE, "Artist - Song", "Artist", "https://c", durationSeconds = 95.0),
            ),
        )

        val short = ranked.first { it.url == "https://c" }
        assertTrue(short.durationWarning)
        assertTrue(short.reason.contains("mediana"))
    }

    @Test
    fun `duplicates by url are collapsed and limit is honored`() {
        val request = SourceSearchRequest("", "Enemy")
        val drafts = (1..20).flatMap { index ->
            listOf(
                SourceCandidateDraft(SourceProvider.OTHER, "Enemy", "", "https://example.test/" + index),
                SourceCandidateDraft(SourceProvider.OTHER, "Enemy", "", "https://example.test/" + index),
            )
        }

        val ranked = SourceSearchRules.rank(request, drafts, limit = 15)
        assertEquals(15, ranked.size)
        assertEquals(15, ranked.map { it.url }.distinct().size)
    }
}
