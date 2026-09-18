package com.gbw.android.source

import com.gbw.android.domain.SourceCandidateDraft
import com.gbw.android.domain.SourceProvider
import com.gbw.android.domain.SourceSearchDepth
import com.gbw.android.domain.SourceSearchProviderClient
import com.gbw.android.domain.SourceSearchRequest
import com.gbw.android.domain.SourceSearchRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Metadata-only discovery against Apple's public iTunes Search API.
 *
 * GBW uses only catalog metadata/link for discovery and ranking. It does not
 * consume preview media or attempt to bypass store playback rules.
 */
class AppleMusicDiscoveryProvider(
    private val fetcher: suspend (String) -> String = ::fetchAppleSearch,
    private val countryProvider: () -> String = { Locale.getDefault().country },
) : SourceSearchProviderClient {
    override suspend fun search(request: SourceSearchRequest): List<SourceCandidateDraft> {
        val query = listOf(request.artist.trim(), request.song.trim())
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val limit = when (request.depth) {
            SourceSearchDepth.ROBUST -> 12
            SourceSearchDepth.MAXIMUM -> 25
        }

        val local = countryProvider().trim().uppercase(Locale.ROOT)
            .takeIf { it.length == 2 } ?: "US"
        val countries = linkedSetOf(local, "US")
        val collected = mutableListOf<SourceCandidateDraft>()

        for (country in countries) {
            val url =
                "https://itunes.apple.com/search?term=$encoded&country=$country&media=music&entity=song&limit=$limit"
            val parsed = AppleMusicSearchParser.parse(fetcher(url))
            collected += parsed

            val hasExactEnoughMatch = parsed.any { candidate ->
                SourceSearchRules.titleMatchesSong(request.song, candidate.title) &&
                    (request.artist.isBlank() ||
                        SourceSearchRules.artistMatchesRequest(
                            request.artist,
                            candidate.title,
                            candidate.uploader,
                        ))
            }
            if (hasExactEnoughMatch) break
        }

        return collected.distinctBy { it.url }
    }
}

internal object AppleMusicSearchParser {
    fun parse(json: String): List<SourceCandidateDraft> {
        if (json.isBlank()) return emptyList()
        val root = JSONObject(json)
        val results = root.optJSONArray("results") ?: return emptyList()
        val out = ArrayList<SourceCandidateDraft>(results.length())

        for (index in 0 until results.length()) {
            val item = results.optJSONObject(index) ?: continue
            val title = item.optString("trackName").trim()
            val artist = item.optString("artistName").trim()
            val url = item.optString("trackViewUrl").trim()
            if (title.isBlank() || artist.isBlank() || !url.startsWith("http")) continue

            val kind = item.optString("kind").trim()
            if (kind.isNotBlank() && kind != "song") continue

            val collection = item.optString("collectionName").trim()
            val millis = item.optLong("trackTimeMillis", 0L).coerceAtLeast(0L)
            val quality = buildString {
                append("Catálogo Apple Music")
                if (collection.isNotBlank()) {
                    append(" • ")
                    append(collection)
                }
            }

            out += SourceCandidateDraft(
                provider = SourceProvider.APPLE_MUSIC,
                title = title,
                uploader = artist,
                url = url,
                quality = quality,
                qualityBonus = 22,
                durationSeconds = millis / 1000.0,
                officialSignal = true,
            )
        }
        return out
    }
}

private suspend fun fetchAppleSearch(url: String): String = withContext(Dispatchers.IO) {
    val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
        connectTimeout = 12_000
        readTimeout = 15_000
        instanceFollowRedirects = true
        requestMethod = "GET"
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "GuitarBackingWizard/6.x Android")
    }
    try {
        val status = connection.responseCode
        if (status !in 200..299) {
            throw IllegalStateException("Apple Music indisponível (HTTP $status).")
        }
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } finally {
        connection.disconnect()
    }
}
