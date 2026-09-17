package com.gbw.android.audio

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.gbw.android.domain.AudioKind
import com.gbw.android.domain.FilePitchRules
import com.gbw.android.domain.OutputFormat
import com.gbw.android.domain.QualityStatus
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.CancellationException
import kotlin.math.roundToLong

internal data class FilePitchRenderRequest(
    val inputUri: Uri,
    val outputUri: Uri,
    val semitones: Int,
    val audioKind: AudioKind,
    val outputFormat: OutputFormat,
    val cautionAccepted: Boolean,
    val jobId: String,
)

internal data class FilePitchRenderResult(
    val sampleRate: Int,
    val channels: Int,
    val inputFrames: Long,
    val outputFrames: Long,
    val outputBytes: Long,
    val rubberBandIdentity: String,
)

internal object FilePitchRenderer {
    suspend fun render(
        context: Context,
        request: FilePitchRenderRequest,
        onProgress: (Int, String) -> Unit,
    ): FilePitchRenderResult {
        require(FilePitchRules.validateSemitones(request.semitones)) { "Pitch fora do intervalo permitido pelo GBW." }
        val workDir = File(context.cacheDir, "file-pitch/${request.jobId}")
        if (workDir.exists()) workDir.deleteRecursively()
        require(workDir.mkdirs()) { "Não foi possível criar a área temporária do processamento." }

        var destinationCommitted = false
        try {
            currentCoroutineContext().ensureActive()
            onProgress(2, "Validando arquivo de entrada…")
            val inspection = AudioInspectionDispatcher.inspect(context, request.inputUri)
            if (inspection.status == QualityStatus.CAUTION && !request.cautionAccepted) {
                throw IllegalArgumentException("A entrada possui ressalvas e exige confirmação explícita antes do processamento.")
            }
            val expectedRate = requireNotNull(inspection.sampleRate) { "Sample rate da entrada não pôde ser determinado." }
            val expectedChannels = requireNotNull(inspection.channels) { "Número de canais da entrada não pôde ser determinado." }

            val prepared = File(workDir, "prepared-f32.wav")
            onProgress(8, "Preparando áudio float32 sem alterar sample rate/canais…")
            FfmpegPitchIo.prepareFloatWav(context, request.inputUri, inspection, prepared)
            currentCoroutineContext().ensureActive()

            val preparedInfo = FloatWavReader(prepared).use { it.info }
            require(preparedInfo.sampleRate == expectedRate) { "A preparação alterou o sample rate inesperadamente." }
            require(preparedInfo.channels == expectedChannels) { "A preparação alterou o número de canais inesperadamente." }
            require(preparedInfo.frames > 0L) { "O arquivo não contém frames de áudio processáveis." }

            val rendered = File(workDir, "rendered-r3-f32.wav")
            val preserveFormants = request.audioKind == AudioKind.VOCAL
            var identity = "Rubber Band R3"
            var renderedFrames = 0L

            RubberBandR3Session(
                sampleRate = preparedInfo.sampleRate,
                channels = preparedInfo.channels,
                semitones = request.semitones.toDouble(),
                preserveFormants = preserveFormants,
                expectedFrames = preparedInfo.frames,
            ).use { r3 ->
                identity = r3.identity
                try {
                    FloatWavReader(prepared).use { source ->
                        var studied = 0L
                        while (source.remainingFrames() > 0L) {
                            currentCoroutineContext().ensureActive()
                            val block = source.readBlock(RubberBandR3Contract.MAX_BLOCK_FRAMES)
                            val frames = block.size / preparedInfo.channels
                            val finalBlock = source.remainingFrames() == 0L
                            r3.study(block, frames, finalBlock)
                            studied += frames.toLong()
                            onProgress(
                                15 + ((studied * 25L) / preparedInfo.frames).toInt().coerceIn(0, 25),
                                "R3: analisando o áudio (1/2)…",
                            )
                            yield()
                        }

                        source.rewind()
                        FloatWavWriter(rendered, preparedInfo.sampleRate, preparedInfo.channels).use { writer ->
                            var processed = 0L
                            var terminalAvailability = 0
                            while (source.remainingFrames() > 0L) {
                                currentCoroutineContext().ensureActive()
                                val block = source.readBlock(RubberBandR3Contract.MAX_BLOCK_FRAMES)
                                val frames = block.size / preparedInfo.channels
                                val finalBlock = source.remainingFrames() == 0L
                                r3.process(block, frames, finalBlock)
                                terminalAvailability = drainAvailable(r3, writer)
                                processed += frames.toLong()
                                onProgress(
                                    40 + ((processed * 42L) / preparedInfo.frames).toInt().coerceIn(0, 42),
                                    "R3: aplicando pitch (2/2)…",
                                )
                                yield()
                            }
                            terminalAvailability = drainAvailable(r3, writer)
                            require(terminalAvailability == -1) { "Rubber Band não sinalizou finalização completa do render." }
                            renderedFrames = writer.framesWritten
                        }
                    }
                } catch (cancelled: CancellationException) {
                    r3.cancel()
                    throw cancelled
                }
            }

            require(
                RubberBandR3Contract.durationWithinTolerance(
                    preparedInfo.frames,
                    renderedFrames,
                    preparedInfo.sampleRate,
                )
            ) { "O render R3 excedeu a tolerância de duração/sincronismo." }

            onProgress(84, "Validando render R3…")
            val renderedInfo = FloatWavReader(rendered).use { it.info }
            require(renderedInfo.sampleRate == preparedInfo.sampleRate) { "O render alterou o sample rate." }
            require(renderedInfo.channels == preparedInfo.channels) { "O render alterou o número de canais." }
            require(renderedInfo.frames == renderedFrames) { "O cabeçalho WAV não corresponde ao total de frames renderizados." }

            val encoded = when (request.outputFormat) {
                OutputFormat.WAV_FLOAT32 -> rendered
                OutputFormat.WAV_24 -> File(workDir, "final.wav").also {
                    onProgress(87, "Gerando WAV 24-bit…")
                    FfmpegPitchIo.encodeOutput(rendered, it, request.outputFormat)
                }
                OutputFormat.FLAC_24 -> File(workDir, "final.flac").also {
                    onProgress(87, "Gerando FLAC 24-bit…")
                    FfmpegPitchIo.encodeOutput(rendered, it, request.outputFormat)
                }
            }

            currentCoroutineContext().ensureActive()
            onProgress(92, "Verificando metadados e duração da saída…")
            val finalProbe = FfmpegPitchIo.probe(encoded)
            require(finalProbe.sampleRate == preparedInfo.sampleRate) { "A saída final alterou o sample rate." }
            require(finalProbe.channels == preparedInfo.channels) { "A saída final alterou o número de canais." }
            val probedFrames = (finalProbe.durationSeconds * finalProbe.sampleRate.toDouble()).roundToLong()
            require(
                RubberBandR3Contract.durationWithinTolerance(
                    preparedInfo.frames,
                    probedFrames,
                    preparedInfo.sampleRate,
                )
            ) { "A duração do arquivo final excede a tolerância permitida." }
            validateCodec(request.outputFormat, finalProbe)

            currentCoroutineContext().ensureActive()
            onProgress(96, "Gravando arquivo validado no destino…")
            commitToSaf(context, encoded, request.outputUri)
            destinationCommitted = true
            onProgress(100, "Pitch concluído e arquivo validado.")

            return FilePitchRenderResult(
                sampleRate = preparedInfo.sampleRate,
                channels = preparedInfo.channels,
                inputFrames = preparedInfo.frames,
                outputFrames = renderedFrames,
                outputBytes = encoded.length(),
                rubberBandIdentity = identity,
            )
        } finally {
            if (!destinationCommitted) cleanupDestination(context, request.outputUri)
            workDir.deleteRecursively()
        }
    }

    private fun drainAvailable(r3: RubberBandR3Session, writer: FloatWavWriter): Int {
        while (true) {
            r3.throwIfCancelled()
            val available = r3.availableFrames()
            if (available <= 0) return available
            val output = r3.retrieve(minOf(available, RubberBandR3Contract.MAX_BLOCK_FRAMES))
            require(output.isNotEmpty()) { "Rubber Band anunciou frames disponíveis, mas não retornou áudio." }
            writer.write(output)
        }
    }

    private fun validateCodec(format: OutputFormat, probe: EncodedAudioProbe) {
        when (format) {
            OutputFormat.WAV_FLOAT32 -> {
                require(probe.codec.equals("pcm_f32le", true)) { "WAV float32 final usa codec inesperado: ${probe.codec}" }
                require(probe.bitDepth == null || probe.bitDepth == 32) { "WAV float32 final possui profundidade inesperada." }
            }
            OutputFormat.WAV_24 -> {
                require(probe.codec.equals("pcm_s24le", true)) { "WAV 24-bit final usa codec inesperado: ${probe.codec}" }
                require(probe.bitDepth == null || probe.bitDepth == 24) { "WAV final não foi confirmado como 24-bit." }
            }
            OutputFormat.FLAC_24 -> {
                require(probe.codec.equals("flac", true)) { "FLAC final usa codec inesperado: ${probe.codec}" }
                require(probe.bitDepth == null || probe.bitDepth == 24) { "FLAC final não foi confirmado como 24-bit." }
            }
        }
    }

    private fun commitToSaf(context: Context, source: File, destination: Uri) {
        val resolver = context.contentResolver
        resolver.openOutputStream(destination, "wt")?.use { output ->
            FileInputStream(source).buffered().use { input -> input.copyTo(output, 256 * 1024) }
            output.flush()
        } ?: throw IllegalStateException("Não foi possível abrir o arquivo de destino para escrita.")
    }

    private fun cleanupDestination(context: Context, destination: Uri) {
        val resolver = context.contentResolver
        try {
            if (DocumentsContract.isDocumentUri(context, destination)) {
                if (DocumentsContract.deleteDocument(resolver, destination)) return
            }
        } catch (_: Exception) {
            // Some providers do not permit delete through DocumentsContract. Fall back to truncation.
        }
        try {
            resolver.openOutputStream(destination, "wt")?.close()
        } catch (_: Exception) {
            // Best effort: the validated file is never committed before this cleanup path.
        }
    }
}
