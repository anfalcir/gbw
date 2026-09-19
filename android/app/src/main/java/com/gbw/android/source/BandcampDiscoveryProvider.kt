package com.gbw.android.source

import android.content.Context
import com.gbw.android.domain.RankedSourceCandidate
import com.gbw.android.domain.SourceCandidateDraft
import com.gbw.android.domain.SourceProvider
import com.gbw.android.domain.SourceSearchDepth
import com.gbw.android.domain.SourceSearchProviderClient
import com.gbw.android.domain.SourceSearchRequest
import com.gbw.android.domain.SourceSearchRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class SourceDiscoveryResult(
    val candidates: List<RankedSourceCandidate>,
    val warnings: List<String> = emptyList(),
)

/**
 * Provider-neutral coordinator. Provider failures are isolated so one source
 * going offline never destroys the whole search experience.
 */
class SourceSearchCoordinator(
    private val providers: List<SourceSearchProviderClient>,
) {
    constructor(context: Context) : this(
        listOf(
            BandcampDiscoveryProvider(),
            YtDlpDiscoveryProvider(context.applicationContext),
        ),
    )

    suspend fun search(request: SourceSearchRequest): SourceDiscoveryResult {
        require(request.song.isNotBlank()) { "Informe o nome da música." }

        val drafts = mutableListOf<SourceCandidateDraft>()
        val warnings = mutableListOf<String>()
        for (provider in providers) {
            try {
                drafts += provider.search(request)
                    .filter { it.automaticDownloadSupported && !it.previewOnly }
            } catch (error: Exception) {
                warnings += error.message ?: "Uma fonte de pesquisa ficou indisponível."
            }
        }

        return SourceDiscoveryResult(
            candidates = SourceSearchRules.rank(request, drafts),
            warnings = warnings,
        )
    }
}

/**
 * Discovery-only Bandcamp provider.
 *
 * It reads the public search page and returns track links/metadata for GBW
 * ranking. It does not download or extract media.
 */
class BandcampDiscoveryProvider(
    private val fetcher: suspend (String) -> String = ::fetchBandcampSearchPage,
) : SourceSearchProviderClient {
    override suspend fun search(request: SourceSearchRequest): List<SourceCandidateDraft> {
        val query = listOf(request.artist.trim(), request.song.trim())
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val html = fetcher("https://bandcamp.com/search?q=$encoded")
        val limit = when (request.depth) {
            SourceSearchDepth.ROBUST -> 8
            SourceSearchDepth.MAXIMUM -> 15
        }
        return BandcampSearchParser.parse(html).take(limit)
    }
}

internal object BandcampSearchParser {
    private val searchResultBlock = Regex(
        """(?is)<li[^>]*class=["'][^"']*searchresult[^"']*["'][^>]*>(.*?)</li>""",
    )
    private val trackUrl = Regex(
        """https?://[a-z0-9._-]+\.bandcamp\.com/track/[a-z0-9._~%+\-/]+(?:\?[^"'<>\s]*)?""",
        RegexOption.IGNORE_CASE,
    )
    private val hrefTrackUrl = Regex(
        """(?is)href=["'](https?://[^"']+\.bandcamp\.com/track/[^"']+)["']""",
    )
    private val heading = Regex(
        """(?is)class=["'][^"']*heading[^"']*["'][^>]*>.*?<a[^>]*>(.*?)</a>""",
    )
    private val subhead = Regex(
        """(?is)class=["'][^"']*subhead[^"']*["'][^>]*>(.*?)</(?:div|span)>""",
    )

    fun parse(html: String): List<SourceCandidateDraft> {
        if (html.isBlank()) return emptyList()

        val blocks = searchResultBlock.findAll(html).map { it.groupValues[1] }.toList()
        val parsed = blocks.mapNotNull(::parseBlock)
        if (parsed.isNotEmpty()) return parsed.distinctBy { it.url }

        // Defensive fallback for markup changes: keep useful track URLs even if
        // Bandcamp changes its result-card structure.
        return trackUrl.findAll(decodeEntities(html))
            .map { match ->
                draftFromUrl(cleanUrl(match.value))
            }
            .distinctBy { it.url }
            .toList()
    }

    private fun parseBlock(block: String): SourceCandidateDraft? {
        val decoded = decodeEntities(block)
        val url = hrefTrackUrl.find(decoded)?.groupValues?.getOrNull(1)
            ?: trackUrl.find(decoded)?.value
            ?: return null

        val cleanUrl = cleanUrl(url)
        val title = heading.find(decoded)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::plainText)
            ?.takeIf { it.isNotBlank() }
            ?: titleFromUrl(cleanUrl)

        val uploader = subhead.find(decoded)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::plainText)
            ?.removePrefix("by ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: artistFromHost(cleanUrl)

        return SourceCandidateDraft(
            provider = SourceProvider.BANDCAMP,
            title = title,
            uploader = uploader,
            url = cleanUrl,
            quality = "Fonte direta no Bandcamp",
            qualityBonus = 30,
            automaticDownloadSupported = true,
        )
    }

    private fun draftFromUrl(url: String): SourceCandidateDraft =
        SourceCandidateDraft(
            provider = SourceProvider.BANDCAMP,
            title = titleFromUrl(url),
            uploader = artistFromHost(url),
            url = url,
            quality = "Fonte direta no Bandcamp",
            qualityBonus = 30,
            automaticDownloadSupported = true,
        )

    private fun titleFromUrl(url: String): String {
        val slug = runCatching {
            URI(url).path.substringAfter("/track/").substringBefore('/').trim()
        }.getOrDefault("")
        return slug.replace('-', ' ').trim().ifBlank { "Faixa no Bandcamp" }
    }

    private fun artistFromHost(url: String): String {
        val host = runCatching { URI(url).host.orEmpty() }.getOrDefault("")
        val artist = host.substringBefore(".bandcamp.com").replace('-', ' ').trim()
        return artist.ifBlank { "Bandcamp" }
    }

    private fun cleanUrl(url: String): String =
        url.substringBefore('#').trimEnd('.', ',', ')')

    private fun plainText(value: String): String =
        decodeEntities(value.replace(Regex("(?is)<[^>]+>"), " "))
            .replace(Regex("\\s+"), " ")
            .trim()

    internal fun decodeEntities(value: String): String = value
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#x27;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
}

private suspend fun fetchBandcampSearchPage(url: String): String = withContext(Dispatchers.IO) {
    val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 20_000
        instanceFollowRedirects = true
        requestMethod = "GET"
        setRequestProperty("User-Agent", "Mozilla/5.0 (Android) GuitarBackingWizard/6.x")
        setRequestProperty("Accept", "text/html,application/xhtml+xml")
    }

    try {
        val status = connection.responseCode
        if (status !in 200..299) {
            throw IllegalStateException("Bandcamp indisponível (HTTP $status).")
        }
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } finally {
        connection.disconnect()
    }
}
