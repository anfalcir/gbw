package com.gbw.android.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gbw.android.domain.RankedSourceCandidate
import com.gbw.android.domain.SourceSearchDepth
import com.gbw.android.source.SourceDiscoveryResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal enum class SourceNoticePlacement {
    LOCAL,
    SEARCH,
    PREPARATION,
    MANUAL_URL,
}

internal data class SourceUiNotice(
    val placement: SourceNoticePlacement,
    val message: String,
    val isError: Boolean,
)

internal data class SourceToastEvent(
    val id: Long,
    val message: String,
)

internal class SourceSearchViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    companion object {
        private const val KEY_ARTIST = "source.search.artist"
        private const val KEY_SONG = "source.search.song"
        private const val KEY_DEPTH = "source.search.depth"
        private const val KEY_MANUAL_URL = "source.search.manualUrl"
        private const val KEY_SELECTED_URL = "source.search.selectedUrl"
        private const val KEY_PROJECT_ID = "source.search.projectId"
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
    var selectedUrl by mutableStateOf(savedStateHandle[KEY_SELECTED_URL] ?: "")
        private set
    var searching by mutableStateOf(false)
        private set
    var results by mutableStateOf<List<RankedSourceCandidate>>(emptyList())
        private set
    var notices by mutableStateOf<Map<SourceNoticePlacement, SourceUiNotice>>(emptyMap())
        private set
    var toastEvent by mutableStateOf<SourceToastEvent?>(null)
        private set

    private var projectId: String? = savedStateHandle[KEY_PROJECT_ID]
    private var searchGeneration = 0L
    private var nextToastId = 0L

    fun bindProject(value: String?) {
        if (projectId == value) return
        projectId = value
        savedStateHandle[KEY_PROJECT_ID] = value
        searchGeneration += 1
        searching = false
        results = emptyList()
        selectedUrl = ""
        notices = emptyMap()
        savedStateHandle[KEY_SELECTED_URL] = ""
    }

    fun syncIdentity(artistValue: String, songValue: String) {
        updateArtist(artistValue)
        updateSong(songValue)
    }

    fun resetSession() {
        bindProject(null)
        artist = ""
        song = ""
        manualUrl = ""
        selectedUrl = ""
        searching = false
        results = emptyList()
        notices = emptyMap()
        toastEvent = null
        savedStateHandle[KEY_ARTIST] = ""
        savedStateHandle[KEY_SONG] = ""
        savedStateHandle[KEY_MANUAL_URL] = ""
        savedStateHandle[KEY_SELECTED_URL] = ""
    }

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

    fun postNotice(
        placement: SourceNoticePlacement,
        message: String,
        isError: Boolean = false,
        toast: Boolean = isError,
    ) {
        notices = notices + (placement to SourceUiNotice(placement, message, isError))
        if (toast) {
            nextToastId += 1
            toastEvent = SourceToastEvent(nextToastId, message)
        }
    }

    fun noticeFor(placement: SourceNoticePlacement): SourceUiNotice? = notices[placement]

    fun consumeToast(id: Long) {
        if (toastEvent?.id == id) toastEvent = null
    }

    fun selectCandidate(candidate: RankedSourceCandidate) {
        if (candidate.previewOnly || !candidate.automaticDownloadSupported) {
            postNotice(
                SourceNoticePlacement.PREPARATION,
                "Esse resultado não pode ser preparado automaticamente. Escolha outra fonte.",
                isError = true,
            )
            return
        }
        selectedUrl = candidate.url
        savedStateHandle[KEY_SELECTED_URL] = candidate.url
        postNotice(
            SourceNoticePlacement.PREPARATION,
            "Fonte selecionada: ${candidate.provider.publicLabel}. Pronta para preparação automática.",
        )
    }

    fun selectedCandidate(): RankedSourceCandidate? =
        results.firstOrNull { it.url == selectedUrl }

    internal fun beginSearch() {
        if (searching) return
        searchGeneration += 1
        searching = true
        results = emptyList()
        postNotice(SourceNoticePlacement.SEARCH, "Pesquisando fontes…")
    }

    fun search(block: suspend () -> SourceDiscoveryResult) {
        if (searching) return
        val generation = ++searchGeneration
        searching = true
        results = emptyList()
        postNotice(SourceNoticePlacement.SEARCH, "Pesquisando fontes…")
        viewModelScope.launch {
            try {
                val result = block()
                if (generation == searchGeneration) completeSearch(result)
            } catch (cancelled: CancellationException) {
                if (generation == searchGeneration) searching = false
                throw cancelled
            } catch (error: Exception) {
                if (generation == searchGeneration) failSearch(error)
            }
        }
    }

    internal fun completeSearch(result: SourceDiscoveryResult) {
        // Only automatically usable sources belong in the primary result list.
        results = result.candidates.filter { it.automaticDownloadSupported && !it.previewOnly }
        val safe = results.firstOrNull()
        if (safe != null && results.none { it.url == selectedUrl }) {
            selectedUrl = safe.url
            savedStateHandle[KEY_SELECTED_URL] = safe.url
        }
        val message = when {
            results.isEmpty() && result.warnings.isNotEmpty() ->
                "Nenhuma fonte utilizável encontrada. " + result.warnings.joinToString(" ")
            results.isEmpty() ->
                "Nenhuma fonte utilizável encontrada. Use a busca ampla abaixo."
            result.warnings.isNotEmpty() ->
                "${results.size} resultado(s) utilizável(is). Algumas fontes indisponíveis foram ignoradas."
            else ->
                "${results.size} resultado(s) utilizável(is) encontrado(s)."
        }
        postNotice(
            SourceNoticePlacement.SEARCH,
            message,
            isError = results.isEmpty(),
            toast = results.isEmpty(),
        )
        searching = false
    }

    internal fun failSearch(error: Throwable) {
        results = emptyList()
        postNotice(
            SourceNoticePlacement.SEARCH,
            error.message ?: "Falha na pesquisa online.",
            isError = true,
        )
        searching = false
    }
}
