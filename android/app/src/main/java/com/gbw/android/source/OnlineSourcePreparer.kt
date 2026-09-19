package com.gbw.android.source

import android.content.Context
import com.gbw.android.audio.AudioStorageBudget
import com.gbw.android.audio.LocalFfmpeg
import com.gbw.android.domain.SourceProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.File

data class OnlineSourcePrepareRequest(
    val url: String,
    val provider: SourceProvider,
    val formatId: String,
    val expectedDurationSeconds: Double,
    val title: String,
)

data class PreparedOnlineSource(
    val jobId: String,
    val sourceUrl: String,
    val title: String,
    val nativeFile: File,
    val preparedFile: File,
    val formatId: String,
    val durationSeconds: Double,
)

object OnlineSourcePreparer {
    private val audioExtensions =
        setOf("wav", "flac", "m4a", "mp3", "ogg", "opus", "webm", "aac", "aiff", "aif", "mka", "mp4", "mov")

    suspend fun prepare(
        context: Context,
        request: OnlineSourcePrepareRequest,
        jobId: String,
        onProgress: (Int, String) -> Unit,
    ): PreparedOnlineSource {
        require(request.url.startsWith("http://") || request.url.startsWith("https://")) {
            "A URL da fonte é inválida."
        }
        val workRoot = File(context.filesDir, "jobs/" + jobId + "/source")
        val nativeDir = File(workRoot, "native")
        val preparedDir = File(workRoot, "prepared")
        workRoot.deleteRecursively()
        nativeDir.mkdirs()
        preparedDir.mkdirs()

        var success = false
        try {
            onProgress(3, "Inspecionando a fonte selecionada…")
            val inspected = YtDlpDiscoveryProvider(context).inspectUrl(request.url, request.provider)
            require(!inspected.previewOnly) {
                "A fonte selecionada oferece apenas um trecho curto. Escolha outra fonte."
            }
            currentCoroutineContext().ensureActive()

            val freshFormat =
                inspected.formatId.takeIf { it.isNotBlank() }
                    ?: request.formatId.takeIf { it.isNotBlank() }
                    ?: "bestaudio/best"
            val expectedDuration =
                request.expectedDurationSeconds.takeIf { it > 0.0 }
                    ?: inspected.durationSeconds.takeIf { it > 0.0 }
                    ?: 0.0
            AudioStorageBudget.sourcePrepareRequiredBytes(expectedDuration)?.let { required ->
                AudioStorageBudget.requireAvailable(
                    context.filesDir,
                    required,
                    "preparar a fonte",
                )
            }

            // Discovery metadata is advisory only. At acquisition time the format is
            // re-inspected and each retry starts from a clean directory so an expired
            // YouTube media URL/format choice cannot leak into the next attempt.
            runCatching { YtDlpRuntime.updateNightly(context, force = false) }
            val attempts = YtDlpDownloadStrategy.attempts(request.provider, freshFormat)
            var chosenFormat = freshFormat
            var lastFailure: Throwable? = null
            for ((index, attempt) in attempts.withIndex()) {
                currentCoroutineContext().ensureActive()
                nativeDir.deleteRecursively()
                nativeDir.mkdirs()
                if (attempt.forceRuntimeUpdateBefore) {
                    onProgress(7, "Atualizando o motor de aquisição do YouTube…")
                    runCatching { YtDlpRuntime.updateNightly(context, force = true) }
                    // Re-inspect after update; yt-dlp can expose a different set of
                    // formats/clients than the one observed during ranking.
                    runCatching {
                        YtDlpDiscoveryProvider(context).inspectUrl(request.url, request.provider)
                    }.getOrNull()?.formatId?.takeIf { it.isNotBlank() }?.let {
                        if (attempt.name == "youtube-fresh-default") chosenFormat = it
                    }
                }
                val format = if (index == 0) freshFormat else attempt.format
                chosenFormat = format
                val processId = processId(jobId) + "-a" + index
                val template = File(nativeDir, "original.%(ext)s").absolutePath
                onProgress(7, "Baixando fonte • tentativa ${index + 1}/${attempts.size}…")
                try {
                    val options = mutableListOf<Pair<String, String?>>(
                        "-f" to format,
                        "--no-playlist" to null,
                        "--write-info-json" to null,
                        "--newline" to null,
                        "--no-part" to null,
                        "--retries" to "2",
                        "--fragment-retries" to "2",
                        "--socket-timeout" to "20",
                        "-o" to template,
                    )
                    attempt.extractorArgs?.let { options += "--extractor-args" to it }
                    YtDlpRuntime.execute(
                        context = context,
                        target = request.url,
                        options = options,
                        processId = processId,
                    ) { progress, eta, line ->
                        val mapped = (7 + progress.coerceIn(0f, 100f) * 0.63f).toInt().coerceIn(7, 70)
                        val etaText = if (eta > 0) " • ~" + eta + "s restantes" else ""
                        val detail = line.trim().takeIf { it.isNotBlank() }?.take(110)
                        onProgress(
                            mapped,
                            buildString {
                                append("Download ")
                                append(progress.coerceIn(0f, 100f).toInt())
                                append("%")
                                append(etaText)
                                if (!detail.isNullOrBlank()) {
                                    append(" • ")
                                    append(detail)
                                }
                            },
                        )
                    }
                    lastFailure = null
                    break
                } catch (error: Throwable) {
                    lastFailure = error
                    if (!YtDlpDownloadStrategy.retryable(error) || index == attempts.lastIndex) throw error
                    onProgress(7, "YouTube recusou esta rota; tentando uma rota compatível…")
                }
            }
            if (lastFailure != null) throw lastFailure
            currentCoroutineContext().ensureActive()

            val native =
                nativeDir.listFiles()
                    ?.filter { file ->
                        file.isFile &&
                            file.extension.lowercase() in audioExtensions &&
                            !file.name.endsWith(".part")
                    }
                    ?.maxByOrNull { it.length() }
                    ?: error("Download terminou, mas o áudio não foi localizado.")
            require(native.length() > 0L) { "O arquivo baixado ficou vazio." }

            onProgress(73, "Validando integridade e duração do download…")
            val nativeProbe = probeAudio(native)
            require(nativeProbe.durationSeconds > 0.0) {
                "O download não contém uma faixa de áudio válida."
            }
            if (expectedDuration > 0.0) {
                require(nativeProbe.durationSeconds >= maxOf(20.0, expectedDuration * 0.72)) {
                    "O download parece incompleto. Escolha outra fonte e tente novamente."
                }
            }

            onProgress(82, "Preparando o áudio para separação…")
            val prepared = File(preparedDir, "original_44100_f32.wav")
            val command =
                "-hide_banner -nostdin -y -v error -i " + quote(native.absolutePath) + " " +
                    "-map 0:a:0 -vn -ar 44100 -ac 2 -c:a pcm_f32le -f wav " + quote(prepared.absolutePath)
            LocalFfmpeg.execute(command, "Falha ao preparar a fonte baixada.")
            require(prepared.isFile && prepared.length() > 44L) {
                "A preparação da fonte não gerou um áudio válido."
            }

            onProgress(94, "Validando o áudio preparado…")
            val preparedProbe = probeAudio(prepared)
            require(preparedProbe.sampleRate == 44100 && preparedProbe.channels == 2) {
                "A fonte preparada não ficou em estéreo/44,1 kHz."
            }
            if (nativeProbe.durationSeconds > 0.0) {
                val tolerance = maxOf(0.15, nativeProbe.durationSeconds * 0.001)
                require(kotlin.math.abs(preparedProbe.durationSeconds - nativeProbe.durationSeconds) <= tolerance) {
                    "A duração mudou durante a preparação; o resultado foi descartado."
                }
            }

            onProgress(100, "Fonte baixada, validada e preparada.")
            success = true
            return PreparedOnlineSource(
                jobId = jobId,
                sourceUrl = request.url,
                title = request.title.ifBlank { inspected.title },
                nativeFile = native,
                preparedFile = prepared,
                formatId = chosenFormat,
                durationSeconds = preparedProbe.durationSeconds,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            if (!success) workRoot.deleteRecursively()
        }
    }

    fun cancel(jobId: String) {
        val base = processId(jobId)
        YtDlpRuntime.cancel(base)
        repeat(4) { YtDlpRuntime.cancel(base + "-a" + it) }
    }

    private suspend fun probeAudio(file: File): SourceAudioProbe {
        val jsonText =
            LocalFfmpeg.probe(
                "-v error -select_streams a:0 " +
                    "-show_entries stream=sample_rate,channels,codec_name:format=duration " +
                    "-of json " + quote(file.absolutePath),
                "Falha ao validar a fonte baixada.",
            )
        val json = JSONObject(jsonText.ifBlank { "{}" })
        val stream = json.optJSONArray("streams")?.optJSONObject(0)
            ?: error("Arquivo sem stream de áudio")
        val format = json.optJSONObject("format") ?: JSONObject()
        return SourceAudioProbe(
            sampleRate = stream.optString("sample_rate").toIntOrNull() ?: 0,
            channels = stream.optInt("channels", 0),
            durationSeconds = format.optString("duration").toDoubleOrNull() ?: 0.0,
        )
    }

    private fun processId(jobId: String) = "gbw-source-" + jobId

    private fun quote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}

private data class SourceAudioProbe(
    val sampleRate: Int,
    val channels: Int,
    val durationSeconds: Double,
)
