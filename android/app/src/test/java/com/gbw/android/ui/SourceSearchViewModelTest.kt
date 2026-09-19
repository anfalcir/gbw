package com.gbw.android.ui

import androidx.lifecycle.SavedStateHandle
import com.gbw.android.domain.SourceCandidateDraft
import com.gbw.android.domain.SourceProvider
import com.gbw.android.domain.SourceSearchDepth
import com.gbw.android.domain.SourceSearchRequest
import com.gbw.android.domain.SourceSearchRules
import com.gbw.android.source.SourceDiscoveryResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceSearchViewModelTest {
    @Test
    fun `query fields survive recreation through SavedStateHandle`() {
        val handle = SavedStateHandle()
        val first = SourceSearchViewModel(handle)
        first.updateArtist("Wolves At The Gate")
        first.updateSong("Enemy")
        first.setDepth(SourceSearchDepth.MAXIMUM)
        first.updateManualUrl("https://example.test/source")

        val recreated = SourceSearchViewModel(handle)
        assertEquals("Wolves At The Gate", recreated.artist)
        assertEquals("Enemy", recreated.song)
        assertEquals(SourceSearchDepth.MAXIMUM.name, recreated.depthName)
        assertEquals("https://example.test/source", recreated.manualUrl)
    }

    @Test
    fun `non-downloadable results are never exposed as primary online sources`() {
        val vm = SourceSearchViewModel(SavedStateHandle())
        vm.beginSearch()
        assertTrue(vm.searching)

        val ranked = SourceSearchRules.rank(
            SourceSearchRequest("Wolves At The Gate", "Enemy"),
            listOf(
                SourceCandidateDraft(
                    provider = SourceProvider.OTHER,
                    title = "Enemy",
                    uploader = "Wolves At The Gate",
                    url = "https://catalog.example.test/enemy",
                    durationSeconds = 197.0,
                    officialSignal = true,
                    automaticDownloadSupported = false,
                ),
            ),
        )
        vm.completeSearch(SourceDiscoveryResult(ranked))

        assertFalse(vm.searching)
        assertTrue(vm.results.isEmpty())
        assertTrue(
            vm.noticeFor(SourceNoticePlacement.SEARCH)
                ?.message
                .orEmpty()
                .contains("Nenhuma fonte utilizável")
        )
        assertEquals("", vm.selectedUrl)
    }

    @Test
    fun `downloadable safe result is preselected like Linux workflow`() {
        val vm = SourceSearchViewModel(SavedStateHandle())
        val ranked = SourceSearchRules.rank(
            SourceSearchRequest("Wolves At The Gate", "Enemy"),
            listOf(
                SourceCandidateDraft(
                    provider = SourceProvider.YOUTUBE,
                    title = "Enemy",
                    uploader = "Wolves At The Gate",
                    url = "https://youtube.test/enemy",
                    durationSeconds = 197.0,
                    automaticDownloadSupported = true,
                ),
            ),
        )

        vm.completeSearch(SourceDiscoveryResult(ranked))
        assertEquals("https://youtube.test/enemy", vm.selectedUrl)
        assertEquals("https://youtube.test/enemy", vm.selectedCandidate()?.url)
    }

    @Test
    fun `switching project clears prior online results and contextual notices`() {
        val vm = SourceSearchViewModel(SavedStateHandle())
        vm.bindProject("11111111-1111-1111-1111-111111111111")
        val ranked = SourceSearchRules.rank(
            SourceSearchRequest("Wolves At The Gate", "Enemy"),
            listOf(
                SourceCandidateDraft(
                    provider = SourceProvider.YOUTUBE,
                    title = "Enemy",
                    uploader = "Wolves At The Gate",
                    url = "https://youtube.test/enemy",
                    durationSeconds = 197.0,
                    automaticDownloadSupported = true,
                ),
            ),
        )
        vm.completeSearch(SourceDiscoveryResult(ranked))
        assertEquals(1, vm.results.size)

        vm.bindProject("22222222-2222-2222-2222-222222222222")

        assertTrue(vm.results.isEmpty())
        assertEquals("", vm.selectedUrl)
        assertNull(vm.noticeFor(SourceNoticePlacement.SEARCH))
    }
}
