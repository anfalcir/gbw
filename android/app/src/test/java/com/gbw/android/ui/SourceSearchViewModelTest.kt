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
    fun `completed search keeps result state across configuration recreation`() {
        val vm = SourceSearchViewModel(SavedStateHandle())
        vm.beginSearch()
        assertTrue(vm.searching)

        val ranked = SourceSearchRules.rank(
            SourceSearchRequest("Wolves At The Gate", "Enemy"),
            listOf(
                SourceCandidateDraft(
                    provider = SourceProvider.APPLE_MUSIC,
                    title = "Enemy",
                    uploader = "Wolves At The Gate",
                    url = "https://music.apple.com/test",
                    durationSeconds = 197.0,
                    officialSignal = true,
                ),
            ),
        )
        vm.completeSearch(SourceDiscoveryResult(ranked))

        assertFalse(vm.searching)
        assertEquals(1, vm.results.size)
        assertTrue(vm.message.orEmpty().contains("1 resultado"))
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
}
