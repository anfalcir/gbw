package com.gbw.android.source

import android.content.Context
import com.gbw.android.domain.SourceCandidateDraft
import com.gbw.android.domain.SourceProvider
import com.gbw.android.domain.SourceSearchDepth
import com.gbw.android.domain.SourceSearchProviderClient
import com.gbw.android.domain.SourceSearchRequest
import com.gbw.android.domain.SourceSearchRules
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.util.UUID

class YtDlpDiscoveryProvider(
    private val context: Context,
) : SourceSearchProviderClient {
    override suspend fun search(request: SourceSearchRequest): List<SourceCandidateDraft> {
        val query = listOf(request.artist.trim(), request.song.trim())
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val maxSearch = if (request.depth == SourceSearchDepth.MAXIMUM) 9 else 6
        val maxInspect = if (request.depth == SourceSearchDepth.MAXIMUM) 8 else 5
        val out = mutableListOf<SourceCandidateDraft>()
        val seen = mutableSetOf<String>()
        var successfulSearches = 0
        var lastSearchFailure: Exception? = null

        val searches = listOf(
            Pair(SourceProvider.SOUNDCLOUD, "scsearch$maxSearch:$query"),
            Pair(SourceProvider.YOUTUBE, "ytsearch$maxSearch:$query official audio"),
            Pair(SourceProvider.YOUTUBE, "ytsearch$maxSearch:$query Topic"),
        )

        for ((provider, target) in searches) {
            currentCoroutineContext().ensureActive()
            val json = try {
                YtDlpRuntime.executeJson(
                    context = context,
                    target = target,
                    options = listOf(
                        "--skip-download" to null,
                        "--flat-playlist" to null,
                        "--dump-single-json" to null,
                    ),
                    processId = "search-" + UUID.randomUUID(),
                ).also { successfulSearches += 1 }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                lastSearchFailure = error
                continue
            }

            val root = JSONObject(json)
            val entries = root.optJSONArray("entries") ?: continue
            var inspected = 0
            for (i in 0 until entries.length()) {
                if (inspected >= maxInspect) break
                currentCoroutineContext().ensureActive()
                val entry = entries.optJSONObject(i) ?: continue
                val flatTitle = entry.optString("title").ifBlank { entry.optString("track") }
                if (flatTitle.isNotBlank() && !SourceSearchRules.titleMatchesSong(request.song, flatTitle)) continue
                val url = entryUrl(entry, provider)
                if (url.isBlank() || !seen.add(url)) continue

                // Search indexes routinely contain deleted, geo-blocked, private
                // or otherwise unavailable entries. A single stale video must
                // never abort the provider search or discard healthy candidates.
                val candidate = YtDlpDiscoveryResilience.availableOrNull {
                    inspectUrl(url, provider)
                } ?: continue
                if (!candidate.automaticDownloadSupported || candidate.previewOnly) continue
                if (!SourceSearchRules.titleMatchesSong(request.song, candidate.title)) continue
                if (
                    request.artist.isNotBlank() &&
                    !SourceSearchRules.artistMatchesRequest(request.artist, candidate.title, candidate.uploader)
                ) continue
                out += candidate
                inspected += 1
            }
        }

        if (out.isEmpty() && successfulSearches == 0 && lastSearchFailure != null) {
            throw lastSearchFailure
        }
        return out
    }

    suspend fun inspectUrl(url: String, provider: SourceProvider): SourceCandidateDraft {
        val json = YtDlpRuntime.executeJson(
            context = context,
            target = url,
            options = listOf(
                "--skip-download" to null,
                "--no-playlist" to null,
                "--dump-single-json" to null,
            ),
            processId = "inspect-" + UUID.randomUUID(),
        )
        return YtDlpInfoParser.toDraft(JSONObject(json), provider, url)
    }

    private fun entryUrl(entry: JSONObject, provider: SourceProvider): String {
        val direct = sequenceOf(
            entry.optString("webpage_url"),
            entry.optString("original_url"),
            entry.optString("url"),
        ).firstOrNull { it.startsWith("http") }.orEmpty()
        if (direct.isNotBlank()) return direct
        if (provider == SourceProvider.YOUTUBE) {
            val id = entry.optString("id").ifBlank { entry.optString("url") }
            if (id.isNotBlank()) return "https://www.youtube.com/watch?v=$id"
        }
        return ""
    }
}

internal object YtDlpDiscoveryResilience {
    suspend fun <T> availableOrNull(block: suspend () -> T): T? =
        try {
            block()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
}

internal object YtDlpInfoParser {
    private val losslessExts = setOf("flac", "wav", "alac", "ape", "aiff", "aif")

    fun toDraft(info: JSONObject, provider: SourceProvider, fallbackUrl: String): SourceCandidateDraft {
        val title = info.optString("track").ifBlank { info.optString("title") }
        val uploader = run {
            val artists = info.optJSONArray("artists")
            if (artists != null && artists.length() > 0) {
                buildList {
                    for (i in 0 until artists.length()) {
                        artists.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }.joinToString(", ")
            } else {
                info.optString("artist").ifBlank { info.optString("uploader") }
            }
        }
        val quality = chooseQuality(info)
        val duration = info.optDouble("duration", 0.0).takeIf { it.isFinite() } ?: 0.0
        val url = sequenceOf(
            info.optString("webpage_url"),
            info.optString("original_url"),
            fallbackUrl,
        ).firstOrNull { it.startsWith("http") }.orEmpty()
        val normalizedTitle = SourceSearchRules.normalize(title)
        val normalizedUploader = SourceSearchRules.normalize(uploader)
        val official =
            provider == SourceProvider.YOUTUBE &&
                ("topic" in normalizedUploader ||
                    "official audio" in normalizedTitle ||
                    "official music" in normalizedTitle)

        return SourceCandidateDraft(
            provider = provider,
            title = title,
            uploader = uploader,
            url = url,
            quality = quality.label,
            formatId = quality.formatId,
            qualityBonus = quality.bonus,
            durationSeconds = duration,
            previewOnly = quality.previewOnly,
            officialSignal = official,
            automaticDownloadSupported = true,
        )
    }

    fun chooseQuality(info: JSONObject): YtDlpQualityChoice {
        val formats = info.optJSONArray("formats")
        val audio = mutableListOf<JSONObject>()
        if (formats != null) {
            for (i in 0 until formats.length()) {
                val f = formats.optJSONObject(i) ?: continue
                val vcodec = f.optString("vcodec", "none")
                val acodec = f.optString("acodec", "none")
                if ((vcodec.isBlank() || vcodec == "none") && acodec != "none") audio += f
            }
        }
        val nonPreview = audio.filterNot(::isPreview)
        val previewOnly = audio.isNotEmpty() && nonPreview.isEmpty()
        val usable = if (nonPreview.isNotEmpty()) nonPreview else audio

        if (previewOnly) {
            val f = bestByBitrate(usable)
            return YtDlpQualityChoice(
                label = "PREVIEW/TRUNCADO " + f.optString("ext", "áudio").uppercase(),
                formatId = f.optString("format_id").ifBlank { "bestaudio/best" },
                bonus = -60,
                previewOnly = true,
            )
        }

        val lossless = usable.filter { f ->
            val ext = f.optString("ext").lowercase()
            val codec = f.optString("acodec").lowercase()
            ext in losslessExts || codec.startsWith("pcm") || codec in setOf("flac", "alac")
        }
        if (lossless.isNotEmpty()) {
            val pref = mapOf("flac" to 0, "alac" to 1, "wav" to 2, "aiff" to 3, "aif" to 3, "ape" to 4)
            val f = lossless.minWithOrNull(
                compareBy<JSONObject> { pref[it.optString("ext").lowercase()] ?: 9 }
                    .thenByDescending { it.optLong("filesize", 0L) }
            ) ?: lossless.first()
            return YtDlpQualityChoice(
                label = "LOSSLESS " + f.optString("ext").uppercase(),
                formatId = f.optString("format_id").ifBlank { "bestaudio/best" },
                bonus = 40,
                previewOnly = false,
            )
        }

        val original = usable.firstOrNull { f ->
            f.optString("format_id") == "download" ||
                "original" in f.optString("format_note").lowercase()
        }
        if (original != null) {
            return YtDlpQualityChoice(
                label = "download ORIGINAL (" + original.optString("ext", "arquivo").uppercase() + ")",
                formatId = original.optString("format_id").ifBlank { "download" },
                bonus = 32,
                previewOnly = false,
            )
        }

        if (usable.isNotEmpty()) {
            val f = bestByBitrate(usable)
            val br = maxOf(f.optDouble("abr", 0.0), f.optDouble("tbr", 0.0))
            val detail = if (br > 0) " ~" + br.toInt() + " kbps" else ""
            return YtDlpQualityChoice(
                label = "stream " + f.optString("ext", "áudio").uppercase() + detail,
                formatId = f.optString("format_id").ifBlank { "bestaudio/best" },
                bonus = 0,
                previewOnly = false,
            )
        }
        return YtDlpQualityChoice("áudio disponível", "bestaudio/best", 0, false)
    }

    private fun isPreview(f: JSONObject): Boolean {
        val blob = listOf("format_id", "format", "format_note", "url")
            .joinToString(" ") { f.optString(it) }
            .lowercase()
        return listOf("preview", "sample", "excerpt", "snippet").any { it in blob }
    }

    private fun bestByBitrate(items: List<JSONObject>): JSONObject =
        items.maxWithOrNull(
            compareBy<JSONObject> { it.optDouble("quality", -99.0) }
                .thenBy { maxOf(it.optDouble("abr", 0.0), it.optDouble("tbr", 0.0)) }
        ) ?: JSONObject()
}

internal data class YtDlpQualityChoice(
    val label: String,
    val formatId: String,
    val bonus: Int,
    val previewOnly: Boolean,
)
