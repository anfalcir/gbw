package com.gbw.android.separation

import android.content.Context
import android.net.Uri
import android.os.Debug
import com.gbw.android.audio.FloatWavReader
import com.gbw.android.audio.FloatWavWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Arrays
import kotlin.math.roundToInt

internal data class DemucsSeparationResult(
    val outputDirectory: File,
    val stemFiles: Map<String, File>,
    val frames: Long,
    val elapsedMillis: Long,
    val peakObservedPssKb: Long,
    val runtimeIdentity: String,
    val modelSha256: String,
)

internal object DemucsSeparator {
    suspend fun separate(
        context: Context,
        inputUri: Uri,
        jobId: String,
        onProgress: (Int, String) -> Unit,
    ): DemucsSeparationResult = withContext(Dispatchers.Default) {
        require(jobId.isNotBlank()) { "Demucs job id is blank" }
        check(DemucsChunking.WINDOW_FRAMES == DemucsNative.MODEL_WINDOW_FRAMES) {
            "Demucs Kotlin/native window contract diverged"
        }
        check(DemucsModelContract.stemNames == DemucsNative.stemNames) {
            "Demucs stem order diverged between model and JNI contracts"
        }

        var lastReported = -1
        fun report(progress: Int, message: String) {
            val safe = progress.coerceIn(0, 100)
            if (safe != lastReported) {
                lastReported = safe
                onProgress(safe, message)
            }
        }

        val tempDir = File(context.cacheDir, "gbw-demucs/$jobId")
        val preparedInput = File(tempDir, "input_44100_stereo_f32.wav")
        val outputDir = File(context.filesDir, "jobs/$jobId/separation/quick")
        tempDir.deleteRecursively()
        outputDir.deleteRecursively()
        tempDir.mkdirs()
        outputDir.mkdirs()

        var modelHandle = 0L
        var openWriters: List<FloatWavWriter> = emptyList()
        var success = false
        val startedAt = System.nanoTime()
        var peakPssKb = Debug.getPss()

        try {
            val modelFile = DemucsModelManager.ensureReady(context) { progress, message ->
                report((progress / 10.0).roundToInt(), message)
            }
            currentCoroutineContext().ensureActive()
            peakPssKb = maxOf(peakPssKb, Debug.getPss())

            report(11, "Preparando áudio estéreo 44,1 kHz para htdemucs_6s…")
            val inputInfo = DemucsAudioIo.prepareInput(context, inputUri, preparedInput)
            currentCoroutineContext().ensureActive()
            report(14, "Carregando modelo htdemucs_6s…")

            modelHandle = DemucsNative.createModel(modelFile.absolutePath)
            peakPssKb = maxOf(peakPssKb, Debug.getPss())
            val runtimeIdentity = DemucsNative.identity()
            report(15, "Modelo carregado. Iniciando separação em seis stems…")

            val plans = DemucsChunking.plan(inputInfo.frames)
            val stemFiles = DemucsModelContract.stemNames.associateWith { stem -> File(outputDir, "$stem.wav") }
            openWriters = DemucsModelContract.stemNames.map { stem ->
                FloatWavWriter(
                    file = requireNotNull(stemFiles[stem]),
                    sampleRate = DemucsNative.REQUIRED_SAMPLE_RATE,
                    channels = DemucsNative.REQUIRED_CHANNELS,
                )
            }

            FloatWavReader(preparedInput).use { reader ->
                val inputWindow = FloatArray(DemucsChunking.WINDOW_FRAMES * DemucsNative.REQUIRED_CHANNELS)
                for (plan in plans) {
                    currentCoroutineContext().ensureActive()
                    Arrays.fill(inputWindow, 0f)
                    if (plan.readFrames > 0) {
                        reader.seekFrame(plan.readStartFrame)
                        val read = reader.readBlock(plan.readFrames)
                        check(read.size == plan.readFrames * DemucsNative.REQUIRED_CHANNELS) {
                            "Leitura Demucs truncada no trecho ${plan.index + 1}"
                        }
                        System.arraycopy(
                            read,
                            0,
                            inputWindow,
                            plan.inputDestinationFrame * DemucsNative.REQUIRED_CHANNELS,
                            read.size,
                        )
                    }

                    val nativeOutput = DemucsNative.separateChunk(
                        handle = modelHandle,
                        interleavedStereo = inputWindow,
                        progressListener = DemucsNativeProgressListener { fraction, _ ->
                            val within = fraction.coerceIn(0f, 1f)
                            val completed = plan.index.toDouble() + within.toDouble()
                            val progress = 15 + ((completed / plans.size.toDouble()) * 80.0).roundToInt()
                            report(progress, "Separando trecho ${plan.index + 1}/${plans.size}…")
                        },
                    )
                    currentCoroutineContext().ensureActive()

                    val samplesPerSourceWindow =
                        DemucsChunking.WINDOW_FRAMES * DemucsNative.REQUIRED_CHANNELS
                    val cropSamples = plan.cropStartFrame * DemucsNative.REQUIRED_CHANNELS
                    val coreSamples = plan.coreFrames * DemucsNative.REQUIRED_CHANNELS
                    for (source in 0 until DemucsNative.SOURCE_COUNT) {
                        val core = FloatArray(coreSamples)
                        val sourceOffset = source * samplesPerSourceWindow + cropSamples
                        System.arraycopy(nativeOutput, sourceOffset, core, 0, coreSamples)
                        openWriters[source].write(core)
                    }
                    peakPssKb = maxOf(peakPssKb, Debug.getPss())
                    val complete = 15 + (((plan.index + 1).toDouble() / plans.size.toDouble()) * 80.0).roundToInt()
                    report(complete, "Trecho ${plan.index + 1}/${plans.size} concluído.")
                }
            }

            openWriters.forEach { writer ->
                check(writer.framesWritten == inputInfo.frames) {
                    "Stem Demucs com ${writer.framesWritten} frames; esperado ${inputInfo.frames}"
                }
                writer.close()
            }
            openWriters = emptyList()

            report(97, "Validando os seis stems…")
            stemFiles.forEach { (stem, file) ->
                check(file.isFile && file.length() > 44L) { "Stem $stem não foi gerado" }
                FloatWavReader(file).use { stemReader ->
                    val info = stemReader.info
                    check(info.sampleRate == DemucsNative.REQUIRED_SAMPLE_RATE) { "Stem $stem com sample rate inválido" }
                    check(info.channels == DemucsNative.REQUIRED_CHANNELS) { "Stem $stem com canais inválidos" }
                    check(info.frames == inputInfo.frames) { "Stem $stem com duração desalinhada" }
                }
            }

            val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L
            peakPssKb = maxOf(peakPssKb, Debug.getPss())
            report(100, "Separação Rápida concluída: 6 stems validados.")
            success = true
            DemucsSeparationResult(
                outputDirectory = outputDir,
                stemFiles = stemFiles,
                frames = inputInfo.frames,
                elapsedMillis = elapsedMillis,
                peakObservedPssKb = peakPssKb,
                runtimeIdentity = runtimeIdentity,
                modelSha256 = DemucsModelContract.SHA256,
            )
        } finally {
            openWriters.forEach { writer -> runCatching { writer.close() } }
            if (modelHandle != 0L) DemucsNative.destroyModel(modelHandle)
            preparedInput.delete()
            tempDir.deleteRecursively()
            if (!success) outputDir.deleteRecursively()
        }
    }
}
