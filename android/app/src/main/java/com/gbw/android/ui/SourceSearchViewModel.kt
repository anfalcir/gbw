package com.gbw.android.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.gbw.android.domain.RankedSourceCandidate
import com.gbw.android.domain.SourceSearchDepth
import com.gbw.android.source.SourceDiscoveryResult

internal class SourceSearchViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    companion object {
        private const val KEY_ARTIST = "source.search.artist"
        private const val KEY_SONG = "source.search.song"
        private const val KEY_DEPTH = "source.search.depth"
        private const val KEY_MANUAL_URL = "source.search.manualUrl"
    }

    var artist by mutableStateOf(savedStateHandle[KEY_ARTIST] ?: "")
        private set
    var song by mutableStateOf(savedStateHandle[KEY_SONG] ?: "")
        private set
    var depthName by mutableStateOf(
        savedStateHandle[KEY_DEPTH] ?: SourceSearchDepth.ROBUST.name,
    )
        private set
    var manualUrl by mutableStateOf(savedStateHandle[KEY_MANUAL_URL] ?: "")
        private set

    var searching by mutableStateOf(false)
        private set
    var results by mutableStateOf<List<RankedSourceCandidate>>(emptyList())
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun updateArtist(value: String) {
        artist = value
        savedStateHandle[KEY_ARTIST] = value
    }

    fun updateSong(value: String) {
        song = value
        savedStateHandle[KEY_SONG] = value
    }

    fun setDepth(depth: SourceSearchDepth) {
        depthName = depth.name
        savedStateHandle[KEY_DEPTH] = depth.name
    }

    fun updateManualUrl(value: String) {
        manualUrl = value
        savedStateHandle[KEY_MANUAL_URL] = value
    }

    fun updateMessage(value: String?) {
        message = value
    }

    fun beginSearch() {
        searching = true
        results = emptyList()
        message = "Pesquisando fontes…"
    }

    fun completeSearch(result: SourceDiscoveryResult) {
        results = result.candidates
        message = when {
            result.candidates.isEmpty() && result.warnings.isNotEmpty() ->
                "Nenhuma fonte direta encontrada. " + result.warnings.joinToString(" ")
            result.candidates.isEmpty() ->
                "Nenhuma fonte direta encontrada. Use a busca ampla abaixo."
            result.warnings.isNotEmpty() ->
                "${result.candidates.size} resultado(s). " + result.warnings.joinToString(" ")
            else ->
                "${result.candidates.size} resultado(s) encontrado(s)."
        }
        searching = false
    }

    fun failSearch(error: Throwable) {
        results = emptyList()
        message = error.message ?: "Falha na pesquisa online."
        searching = false
    }
}
