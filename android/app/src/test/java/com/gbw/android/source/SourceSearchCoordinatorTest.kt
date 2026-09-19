package com.gbw.android.source

import com.gbw.android.domain.SourceCandidateDraft
import com.gbw.android.domain.SourceProvider
import com.gbw.android.domain.SourceSearchProviderClient
import com.gbw.android.domain.SourceSearchRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceSearchCoordinatorTest {
    @Test
    fun `one unavailable provider does not discard healthy provider results`() = runBlocking {
        val failing = object : SourceSearchProviderClient {
            override suspend fun search(request: SourceSearchRequest): List<SourceCandidateDraft> {
                error("Provider A indisponível")
            }
        }
        val healthy = object : SourceSearchProviderClient {
            override suspend fun search(request: SourceSearchRequest): List<SourceCandidateDraft> =
                listOf(
                    SourceCandidateDraft(
                        provider = SourceProvider.OTHER,
                        title = "Enemy",
                        uploader = "Wolves At The Gate",
                        url = "https://example.test/enemy",
                        durationSeconds = 220.0,
                    )
                )
        }

        val result = SourceSearchCoordinator(listOf(failing, healthy))
            .search(SourceSearchRequest("Wolves At The Gate", "Enemy"))

        assertEquals(1, result.candidates.size)
        assertEquals("https://example.test/enemy", result.candidates.single().url)
        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings.single().contains("Provider A"))
    }

    @Test
    fun `all unavailable providers return warnings without crashing the coordinator`() = runBlocking {
        val failing = object : SourceSearchProviderClient {
            override suspend fun search(request: SourceSearchRequest): List<SourceCandidateDraft> {
                throw IllegalStateException("offline")
            }
        }

        val result = SourceSearchCoordinator(listOf(failing))
            .search(SourceSearchRequest("", "Enemy"))

        assertTrue(result.candidates.isEmpty())
        assertEquals(listOf("offline"), result.warnings)
    }

    @Test
    fun `non-downloadable candidates are filtered before ranking`() = runBlocking {
        val catalog = object : SourceSearchProviderClient {
            override suspend fun search(request: SourceSearchRequest): List<SourceCandidateDraft> =
                listOf(
                    SourceCandidateDraft(
                        provider = SourceProvider.OTHER,
                        title = "Enemy",
                        uploader = "Wolves At The Gate",
                        url = "https://music.apple.com/test",
                        durationSeconds = 197.0,
                        automaticDownloadSupported = false,
                    )
                )
        }

        val result = SourceSearchCoordinator(listOf(catalog))
            .search(SourceSearchRequest("Wolves At The Gate", "Enemy"))

        assertTrue(result.candidates.isEmpty())
    }
}
