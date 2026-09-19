package com.gbw.android.domain

import java.text.Normalizer
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

enum class SourceProvider(val publicLabel: String) {
    BANDCAMP("Bandcamp"),
    SOUNDCLOUD("SoundCloud"),
    YOUTUBE("YouTube"),
    OTHER("Outra"),
}

enum class SourceSearchDepth {
    ROBUST,
    MAXIMUM,
}

data class SourceSearchRequest(
    val artist: String,
    val song: String,
    val depth: SourceSearchDepth = SourceSearchDepth.ROBUST,
)

data class SourceCandidateDraft(
    val provider: SourceProvider,
    val title: String,
    val uploader: String = "",
    val url: String,
    val quality: String = "áudio disponível",
    val formatId: String = "",
    val qualityBonus: Int = 0,
    val durationSeconds: Double = 0.0,
    val previewOnly: Boolean = false,
    val officialSignal: Boolean = false,
    val automaticDownloadSupported: Boolean = false,
)

data class SourceSearchLink(
    val label: String,
    val url: String,
)

object SourceSearchLinks {
    fun forRequest(request: SourceSearchRequest): List<SourceSearchLink> {
        val query = listOf(request.artist.trim(), request.song.trim())
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (query.isBlank()) return emptyList()
        val encoded = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8.name())
        return listOf(
            SourceSearchLink("Pesquisar no YouTube", "https://www.youtube.com/results?search_query=$encoded"),
            SourceSearchLink("Pesquisar no SoundCloud", "https://soundcloud.com/search/sounds?q=$encoded"),
            SourceSearchLink("Pesquisar no Bandcamp", "https://bandcamp.com/search?q=$encoded"),
        )
    }
}

data class RankedSourceCandidate(
    val provider: SourceProvider,
    val title: String,
    val uploader: String,
    val url: String,
    val quality: String,
    val formatId: String,
    val qualityBonus: Int,
    val durationSeconds: Double,
    val previewOnly: Boolean,
    val durationWarning: Boolean,
    val official: Boolean,
    val score: Int,
    val reason: String,
    val automaticDownloadSupported: Boolean = true,
)

interface SourceSearchProviderClient {
    suspend fun search(request: SourceSearchRequest): List<SourceCandidateDraft>
}

/**
 * Stable Android port of the Linux 5.23 source-selection contract.
 *
 * Network/provider extraction is intentionally kept outside this object. That
 * allows provider implementations to change without changing the ranking and
 * identity rules already homologated by the desktop baseline.
 */
object SourceSearchRules {
    private val stopWords = setOf(
        "the", "a", "an", "of", "and", "or", "to", "in", "on", "at", "for", "from", "with",
        "feat", "ft", "featuring", "de", "da", "do", "das", "dos", "e", "o", "os", "as", "um", "uma",
    )

    private val undesirableDescriptors = mapOf(
        "cover" to 18,
        "karaoke" to 18,
        "reaction" to 18,
        "tutorial" to 18,
        "slowed" to 18,
        "sped" to 18,
        "nightcore" to 18,
        "8d" to 18,
        "live" to 10,
    )

    fun normalize(value: String): String {
        val ascii = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
        return ascii.replace(Regex("[^a-z0-9]+"), " ").trim()
    }

    fun meaningfulTokens(value: String): List<String> {
        val tokens = normalize(value).split(' ').filter { it.isNotBlank() }
        val meaningful = tokens.filterNot { it in stopWords }
        return meaningful.ifEmpty { tokens }
    }

    fun titleMatchesSong(song: String, title: String): Boolean {
        val wanted = meaningfulTokens(song)
        val got = normalize(title).split(' ').filter { it.isNotBlank() }.toSet()
        if (wanted.isEmpty() || got.isEmpty()) return false
        return if (wanted.size == 1) wanted.first() in got else wanted.all { it in got }
    }

    fun artistMatchesRequest(artist: String, title: String, uploader: String): Boolean {
        val wanted = meaningfulTokens(artist)
        if (wanted.isEmpty()) return true

        val combinedTokens = normalize("$title $uploader").split(' ').filter { it.isNotBlank() }.toSet()
        if (wanted.all { it in combinedTokens }) return true

        val uploaderN = normalize(uploader)
        val artistN = normalize(artist)
        if (uploaderN.isNotBlank() && (artistN in uploaderN || uploaderN in artistN)) return true

        val coverage = wanted.count { it in combinedTokens }.toDouble() / max(1, wanted.size)
        return coverage >= 0.67
    }

    fun textSimilarity(a: String, b: String): Double {
        val aa = normalize(a)
        val bb = normalize(b)
        if (aa.isBlank() || bb.isBlank()) return 0.0
        if (aa == bb) return 1.0
        if (aa in bb || bb in aa) {
            val shorter = min(aa.length, bb.length).toDouble()
            val longer = max(aa.length, bb.length).toDouble()
            return (0.85 + 0.15 * (shorter / longer)).coerceAtMost(0.99)
        }

        val distance = levenshtein(aa, bb)
        return (1.0 - distance.toDouble() / max(aa.length, bb.length).toDouble()).coerceIn(0.0, 1.0)
    }

    fun durationLabel(seconds: Double): String {
        val rounded = seconds.toInt()
        if (rounded <= 0) return "duração desconhecida"
        return "${rounded / 60}:${(rounded % 60).toString().padStart(2, '0')}"
    }

    fun rank(
        request: SourceSearchRequest,
        drafts: List<SourceCandidateDraft>,
        limit: Int = 15,
    ): List<RankedSourceCandidate> {
        val accepted = drafts
            .filter { titleMatchesSong(request.song, it.title) }
            .filter { request.artist.isBlank() || artistMatchesRequest(request.artist, it.title, it.uploader) }
            .distinctBy { it.url }
            .map { score(request, it) }
            .toMutableList()

        applyDurationConsensus(accepted, request.song)

        return accepted
            .sortedWith(
                compareByDescending<RankedSourceCandidate> { !it.previewOnly }
                    .thenByDescending { it.score }
                    .thenByDescending { it.qualityBonus }
                    .thenByDescending { it.official }
                    .thenBy { it.provider.publicLabel }
                    .thenBy { it.title },
            )
            .take(limit.coerceAtLeast(1))
    }

    private fun score(request: SourceSearchRequest, draft: SourceCandidateDraft): RankedSourceCandidate {
        val titleN = normalize(draft.title)
        val requestN = normalize(request.song)
        val uploaderN = normalize(draft.uploader)

        var songSimilarity = textSimilarity(request.song, draft.title)
        var artistSimilarity = textSimilarity(request.artist, draft.uploader)
        if (requestN.isNotBlank() && requestN in titleN) songSimilarity = max(songSimilarity, 0.95)
        val artistN = normalize(request.artist)
        if (artistN.isNotBlank() && artistN in uploaderN) artistSimilarity = max(artistSimilarity, 0.95)

        var score = when (draft.provider) {
            SourceProvider.BANDCAMP -> 45
            SourceProvider.SOUNDCLOUD -> 48
            SourceProvider.YOUTUBE -> 50
            SourceProvider.OTHER -> 45
        }
        score += draft.qualityBonus
        score += (songSimilarity * 12).toInt()
        score += (artistSimilarity * 10).toInt()

        undesirableDescriptors.forEach { (token, penalty) ->
            if (token in titleN && token !in requestN) score -= penalty
        }
        if ("remix" in titleN && "remix" !in requestN) score -= 14

        val shortTitleExempt = listOf("intro", "interlude", "outro", "short").any { it in requestN }
        var durationWarning = draft.durationSeconds in 0.000001..59.999999 && !shortTitleExempt
        if (durationWarning) score -= 30

        var official = draft.officialSignal
        val reasons = mutableListOf<String>()

        when {
            draft.qualityBonus >= 40 -> reasons += "áudio de boa qualidade"
            draft.qualityBonus >= 30 -> reasons += "fonte direta"
        }

        if (draft.previewOnly) {
            reasons += "trecho curto"
            score = min(score, 5)
        } else if (durationWarning) {
            reasons += "duração curta (${durationLabel(draft.durationSeconds)})"
        }

        when (draft.provider) {
            SourceProvider.YOUTUBE -> {
                if (draft.officialSignal || "topic" in uploaderN || "official audio" in titleN || "official music" in titleN) {
                    score += 10
                    official = true
                    reasons += "canal oficial"
                } else if (artistSimilarity >= 0.82) {
                    score += 6
                    reasons += "canal compatível"
                }
            }
            SourceProvider.BANDCAMP, SourceProvider.SOUNDCLOUD, SourceProvider.OTHER -> {
                if (draft.officialSignal || artistSimilarity >= 0.80) {
                    score += 6
                    official = true
                    reasons += "perfil compatível"
                }
            }
        }

        if (songSimilarity >= 0.82) reasons += "título compatível"
        if (draft.durationSeconds > 0 && !draft.previewOnly) {
            reasons += "duração ${durationLabel(draft.durationSeconds)}"
        }

        return RankedSourceCandidate(
            provider = draft.provider,
            title = draft.title,
            uploader = draft.uploader,
            url = draft.url,
            quality = draft.quality,
            formatId = draft.formatId,
            qualityBonus = draft.qualityBonus,
            durationSeconds = draft.durationSeconds,
            previewOnly = draft.previewOnly,
            durationWarning = durationWarning,
            official = official,
            score = (if (draft.previewOnly) min(score, 5) else score).coerceIn(0, 100),
            reason = reasons.joinToString(", ").ifBlank { "melhor resultado encontrado" },
            automaticDownloadSupported = draft.automaticDownloadSupported,
        )
    }

    private fun applyDurationConsensus(candidates: MutableList<RankedSourceCandidate>, song: String) {
        val plausible = candidates.filter {
            !it.previewOnly &&
                it.durationSeconds >= 60.0 &&
                textSimilarity(song, it.title) >= 0.72
        }
        if (plausible.size < 2) return

        val median = median(plausible.map { it.durationSeconds })
        if (median <= 0) return

        candidates.indices.forEach { index ->
            val candidate = candidates[index]
            if (candidate.previewOnly || candidate.durationSeconds <= 0) return@forEach
            val ratio = candidate.durationSeconds / median
            candidates[index] = when {
                ratio < 0.68 -> candidate.copy(
                    score = (candidate.score - 35).coerceAtLeast(0),
                    durationWarning = true,
                    reason = candidate.reason + ", duração muito menor que versões equivalentes (mediana ${durationLabel(median)})",
                )
                ratio > 1.55 -> candidate.copy(
                    score = (candidate.score - 15).coerceAtLeast(0),
                    durationWarning = true,
                    reason = candidate.reason + ", duração muito maior que versões equivalentes (mediana ${durationLabel(median)})",
                )
                ratio in 0.92..1.08 -> candidate.copy(score = (candidate.score + 3).coerceAtMost(100))
                else -> candidate
            }
        }
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                current[j + 1] = min(
                    min(current[j] + 1, previous[j + 1] + 1),
                    previous[j] + cost,
                )
            }
            previous = current
        }
        return previous[b.length]
    }
}
