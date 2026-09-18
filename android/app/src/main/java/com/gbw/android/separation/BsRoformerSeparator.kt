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
import java.nio.ByteOrder
import kotlin.math.roundToInt

internal data class BsRoformerSeparationResult(
    val outputDirectory: File,
    val stemFiles: Map<String, File>,
    val frames: Long,
    val elapsedMillis: Long,
    val peakObservedPssKb: Long,
    val spectralIdentity: String,
    val pteSha256: String,
)

internal object BsRoformerSeparator {
    suspend fun separate(
        context: Context,
        inputUri: Uri,
        jobId: String,
        onProgress: (Int, String) -> Unit,
    ): BsRoformerSeparationResult = withContext(Dispatchers.Default) {
        require(jobId.isNotBlank()) { "BS-RoFormer job id is blank" }
        check(ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            "GBW BS-RoFormer WAV streaming requires little-endian Android ABI"
        }

        var lastReported = -1
        fun report(progress: Int, message: String) {
            val safe = progress.coerceIn(0, 100)
            if (safe != lastReported) {
                lastReported = safe
                onProgress(safe, message)
            }
        }

        val tempDir = File(context.cacheDir, "gbw-bs-roformer/$jobId")
        val preparedInput = File(tempDir, "input_44100_stereo_f32.wav")
        val outputDir = File(context.filesDir, "jobs/$jobId/separation/high-quality")
        tempDir.deleteRecursively()
        outputDir.deleteRecursively()
        tempDir.mkdirs()
        outputDir.mkdirs()

        var openWriters: List<FloatWavWriter> = emptyList()
        var success = false
        val startedAt = System.nanoTime()
        var peakPssKb = Debug.getPss()

        try {
            val modelFile = BsRoformerModelManager.ensureReady(context) { progress, message ->
                report((progress * 5.0 / 100.0).roundToInt(), message)
            }
            currentCoroutineContext().ensureActive()
            peakPssKb = maxOf(peakPssKb, Debug.getPss())

            report(6, "Preparando áudio estéreo 44,1 kHz para BS-RoFormer-SW…")
            val inputInfo = BsRoformerAudioIo.prepareInput(context, inputUri, preparedInput)
            currentCoroutineContext().ensureActive()

            val plans = BsRoformerChunking.plan(inputInfo.frames)
            val stemFiles = BsRoformerContract.stemNames.associateWith { stem ->
                File(outputDir, "$stem.wav")
            }
            openWriters = BsRoformerContract.stemNames.map { stem ->
                FloatWavWriter(
                    file = requireNotNull(stemFiles[stem]),
                    sampleRate = BsRoformerContract.SAMPLE_RATE,
                    channels = BsRoformerContract.CHANNELS,
                )
            }

            report(10, "Abrindo PTE BS-RoFormer-SW via mmap / ExecuTorch…")
            val inputChunk = FloatArray(
                BsRoformerContract.CHUNK_FRAMES * BsRoformerContract.CHANNELS
            )
            val stftBuffer = BsRoformerSpectralNative.allocateStftBuffer()
            val maskBuffer = BsRoformerSpectralNative.allocateMaskBuffer()
            val outputBuffer = BsRoformerSpectralNative.allocateOutputBuffer()
            val spectralIdentity = BsRoformerSpectralNative.identity()

            BsRoformerExecuTorch.open(modelFile).use { model ->
                peakPssKb = maxOf(peakPssKb, Debug.getPss())
                FloatWavReader(preparedInput).use { reader ->
                    val overlap = BsRoformerOverlapAdd { stem, outputStart, bytes, frames ->
                        val writer = openWriters[stem]
                        check(writer.framesWritten == outputStart) {
                            "BS-RoFormer perdeu continuidade no stem " +
                                BsRoformerContract.stemNames[stem]
                        }
                        writer.write(bytes, frames)
                    }

                    for (plan in plans) {
                        currentCoroutineContext().ensureActive()
                        BsRoformerChunkInput.fill(
                            reader = reader,
                            totalFrames = inputInfo.frames,
                            plan = plan,
                            destination = inputChunk,
                        )

                        val base = 10 + (
                            (plan.index.toDouble() / plans.size.toDouble()) * 84.0
                        ).roundToInt()
                        report(base, "Trecho ${plan.index + 1}/${plans.size}: STFT…")
                        BsRoformerSpectralNative.stft(inputChunk, stftBuffer)
                        currentCoroutineContext().ensureActive()

                        report(
                            minOf(94, base + 1),
                            "Trecho ${plan.index + 1}/${plans.size}: XNNPACK…",
                        )
                        model.forwardMasks(stftBuffer, maskBuffer)
                        currentCoroutineContext().ensureActive()

                        report(
                            minOf(94, base + 2),
                            "Trecho ${plan.index + 1}/${plans.size}: ISTFT…",
                        )
                        BsRoformerSpectralNative.applyMasksAndIstft(
                            stft = stftBuffer,
                            masks = maskBuffer,
                            output = outputBuffer,
                        )
                        currentCoroutineContext().ensureActive()

                        overlap.consume(plan, outputBuffer)
                        peakPssKb = maxOf(peakPssKb, Debug.getPss())
                        val completed = 10 + (
                            ((plan.index + 1).toDouble() / plans.size.toDouble()) * 84.0
                        ).roundToInt()
                        report(
                            completed.coerceAtMost(94),
                            "Trecho ${plan.index + 1}/${plans.size} concluído.",
                        )
                    }
                    overlap.finish(plans.size)
                }
            }

            openWriters.forEach { writer ->
                check(writer.framesWritten == inputInfo.frames) {
                    "Stem BS-RoFormer com ${writer.framesWritten} frames; " +
                        "esperado ${inputInfo.frames}"
                }
                writer.close()
            }
            openWriters = emptyList()

            report(96, "Validando os seis stems BS-RoFormer-SW…")
            stemFiles.forEach { (stem, file) ->
                check(file.isFile && file.length() > 44L) {
                    "Stem BS-RoFormer $stem não foi gerado"
                }
                FloatWavReader(file).use { reader ->
                    val info = reader.info
                    check(info.sampleRate == BsRoformerContract.SAMPLE_RATE)
                    check(info.channels == BsRoformerContract.CHANNELS)
                    check(info.frames == inputInfo.frames) {
                        "Stem $stem com duração desalinhada"
                    }
                }
            }

            val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L
            peakPssKb = maxOf(peakPssKb, Debug.getPss())
            report(100, "Alta qualidade concluída: 6 stems validados.")
            success = true
            BsRoformerSeparationResult(
                outputDirectory = outputDir,
                stemFiles = stemFiles,
                frames = inputInfo.frames,
                elapsedMillis = elapsedMillis,
                peakObservedPssKb = peakPssKb,
                spectralIdentity = spectralIdentity,
                pteSha256 = BsRoformerContract.PTE_SHA256,
            )
        } finally {
            openWriters.forEach { writer -> runCatching { writer.close() } }
            preparedInput.delete()
            tempDir.deleteRecursively()
            if (!success) outputDir.deleteRecursively()
        }
    }
}
