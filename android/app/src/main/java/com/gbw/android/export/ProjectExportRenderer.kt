package com.gbw.android.export

import android.content.Context
import com.gbw.android.audio.AudioExportIo
import com.gbw.android.audio.FloatWavReader
import com.gbw.android.audio.FloatWavWriter
import com.gbw.android.domain.OutputFormat
import com.gbw.android.project.ExportArtifact
import com.gbw.android.project.ExportState
import com.gbw.android.project.ProjectHashing
import com.gbw.android.project.ProjectRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.math.abs
import kotlin.math.pow

internal data class ProjectExportResult(
    val state: ExportState,
    val sharedGainDb: Map<String, Double>,
)

internal object SharedGain {
    fun targetLinear(targetDbfs: Double): Double = 10.0.pow(targetDbfs / 20.0)

    fun factorForCombinedPeak(peakLinear: Double, targetDbfs: Double = -1.0): Double {
        if (!peakLinear.isFinite() || peakLinear <= 0.0) return 1.0
        val target = targetLinear(targetDbfs)
        return if (peakLinear > target) target / peakLinear else 1.0
    }

    fun gainDb(factor: Double): Double =
        if (factor >= 0.999999999) 0.0 else 20.0 * kotlin.math.log10(factor)
}

internal object ProjectExportRenderer {
    private const val SAMPLE_RATE = 44_100
    private const val CHANNELS = 2
    private const val BLOCK_FRAMES = 4096
    private val backingRoles = listOf("drums", "bass", "other", "vocals", "piano")

    suspend fun render(
        context: Context,
        projectId: String,
        outputFormat: OutputFormat,
        onProgress: (Int, String) -> Unit,
    ): ProjectExportResult {
        val repo = ProjectRepository(context)
        val project = repo.load(projectId)
        val separation = requireNotNull(project.separation) {
            "O projeto ainda não possui separação completa."
        }
        val required = (backingRoles + "guitar").associateWith { role ->
            val relative = separation.stems[role] ?: error("Stem ausente: $role")
            repo.resolveProjectPath(projectId, relative).also {
                require(it.isFile) { "Stem ausente: $role" }
            }
        }
        val baseInfo = FloatWavReader(required.getValue("guitar")).use { it.info }
        require(baseInfo.sampleRate == SAMPLE_RATE && baseInfo.channels == CHANNELS) {
            "Stems precisam estar em estéreo/44,1 kHz."
        }
        required.values.forEach { file ->
            FloatWavReader(file).use { reader ->
                require(reader.info.sampleRate == SAMPLE_RATE && reader.info.channels == CHANNELS)
                require(reader.info.frames == baseInfo.frames) {
                    "Stems perderam alinhamento de frames."
                }
            }
        }

        val exportId = "e_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().take(8)
        val stagingRoot = File(context.cacheDir, "project-export-staging/$projectId/$exportId")
        stagingRoot.deleteRecursively()
        require(stagingRoot.mkdirs())
        var published = false
        try {
            onProgress(5, "Renderizando backing + guitar no tom original…")
            val rendered = renderOriginal(
                inputs = required,
                outputDir = stagingRoot,
                outputFormat = outputFormat,
                expectedFrames = baseInfo.frames,
            )
            val artifacts = rendered.first.map { artifact ->
                artifact.copy(relativePath = "exports/$exportId/" + artifact.relativePath)
            }
            val gains = mapOf("original" to rendered.second)
            val manifestRelative = "exports/$exportId/export_manifest.json"
            val state = ExportState(
                exportId = exportId,
                revision = 1L,
                outputFormat = outputFormat.name,
                artifacts = artifacts,
                manifestRelativePath = manifestRelative,
            )
            File(stagingRoot, "export_manifest.json").writeText(
                exportManifest(projectId, project.artist, project.song, state, rendered.second)
            )
            currentCoroutineContext().ensureActive()
            onProgress(96, "Publicando exportação validada no projeto…")
            val updated = repo.publishExport(projectId, stagingRoot, state)
            published = true
            onProgress(100, "Exportação concluída: backing + guitar.")
            return ProjectExportResult(requireNotNull(updated.export), gains)
        } finally {
            if (!published) stagingRoot.deleteRecursively()
        }
    }

    private suspend fun renderOriginal(
        inputs: Map<String, File>,
        outputDir: File,
        outputFormat: OutputFormat,
        expectedFrames: Long,
    ): Pair<List<ExportArtifact>, Double> {
        val work = File(outputDir, ".work").apply { mkdirs() }
        try {
            val backingFloat = File(work, "backing-mix.wav")
            mixFloat(backingRoles.map { inputs.getValue(it) }, backingFloat, expectedFrames)
            val guitar = inputs.getValue("guitar")
            val peak = combinedPeak(backingFloat, guitar, expectedFrames)
            val factor = SharedGain.factorForCombinedPeak(peak, -1.0)
            val gainDb = SharedGain.gainDb(factor)

            val backingScaled = File(work, "backing-scaled.wav")
            val guitarScaled = File(work, "guitar-scaled.wav")
            scaleFloat(backingFloat, backingScaled, factor, expectedFrames)
            scaleFloat(guitar, guitarScaled, factor, expectedFrames)

            val backingOut = File(outputDir, "backing." + outputFormat.extension)
            val guitarOut = File(outputDir, "guitar." + outputFormat.extension)
            encode(backingScaled, backingOut, outputFormat)
            encode(guitarScaled, guitarOut, outputFormat)
            validateFinal(backingOut, outputFormat, expectedFrames)
            validateFinal(guitarOut, outputFormat, expectedFrames)

            val durationSeconds = expectedFrames.toDouble() / SAMPLE_RATE.toDouble()
            val artifacts = listOf(
                exportArtifact("backing", backingOut, outputFormat, expectedFrames, durationSeconds),
                exportArtifact("guitar", guitarOut, outputFormat, expectedFrames, durationSeconds),
            )
            return artifacts to gainDb
        } finally {
            work.deleteRecursively()
        }
    }

    private suspend fun mixFloat(inputs: List<File>, output: File, frames: Long) {
        val readers = inputs.map(::FloatWavReader)
        try {
            FloatWavWriter(output, SAMPLE_RATE, CHANNELS).use { writer ->
                var done = 0L
                while (done < frames) {
                    currentCoroutineContext().ensureActive()
                    val count = minOf(BLOCK_FRAMES.toLong(), frames - done).toInt()
                    val blocks = readers.map { it.readBlock(count) }
                    require(blocks.all { it.size == count * CHANNELS })
                    val mixed = FloatArray(count * CHANNELS)
                    for (i in mixed.indices) {
                        var sum = 0.0f
                        blocks.forEach { sum += it[i] }
                        mixed[i] = sum
                    }
                    writer.write(mixed)
                    done += count
                    yield()
                }
            }
        } finally {
            readers.forEach { it.close() }
        }
    }

    private suspend fun combinedPeak(backing: File, guitar: File, frames: Long): Double {
        var peak = 0.0
        FloatWavReader(backing).use { b ->
            FloatWavReader(guitar).use { g ->
                var done = 0L
                while (done < frames) {
                    currentCoroutineContext().ensureActive()
                    val count = minOf(BLOCK_FRAMES.toLong(), frames - done).toInt()
                    val bb = b.readBlock(count)
                    val gg = g.readBlock(count)
                    require(bb.size == gg.size)
                    for (i in bb.indices) {
                        peak = maxOf(peak, abs(bb[i].toDouble() + gg[i].toDouble()))
                    }
                    done += count
                    yield()
                }
            }
        }
        return peak
    }

    private suspend fun scaleFloat(input: File, output: File, factor: Double, frames: Long) {
        FloatWavReader(input).use { reader ->
            FloatWavWriter(output, SAMPLE_RATE, CHANNELS).use { writer ->
                var done = 0L
                while (done < frames) {
                    currentCoroutineContext().ensureActive()
                    val count = minOf(BLOCK_FRAMES.toLong(), frames - done).toInt()
                    val block = reader.readBlock(count)
                    if (factor != 1.0) {
                        for (i in block.indices) block[i] = (block[i] * factor).toFloat()
                    }
                    writer.write(block)
                    done += count
                    yield()
                }
            }
        }
    }

    private suspend fun encode(input: File, output: File, format: OutputFormat) {
        when (format) {
            OutputFormat.WAV_FLOAT32 ->
                Files.copy(input.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
            else -> AudioExportIo.encodeOutput(input, output, format)
        }
    }

    private suspend fun validateFinal(file: File, format: OutputFormat, frames: Long) {
        require(file.isFile && file.length() > 44L)
        val probe = AudioExportIo.probe(file)
        require(probe.sampleRate == SAMPLE_RATE && probe.channels == CHANNELS)
        val probedFrames = (probe.durationSeconds * SAMPLE_RATE).toLong()
        require(abs(probedFrames - frames) <= maxOf(2L, (SAMPLE_RATE * 0.002).toLong())) {
            "Export final perdeu sincronismo."
        }
        when (format) {
            OutputFormat.WAV_FLOAT32 -> require(probe.codec.equals("pcm_f32le", true))
            OutputFormat.WAV_24 -> require(probe.codec.equals("pcm_s24le", true))
            OutputFormat.FLAC_24 -> require(probe.codec.equals("flac", true))
        }
    }

    private fun exportArtifact(
        role: String,
        file: File,
        format: OutputFormat,
        frames: Long,
        durationSeconds: Double,
    ): ExportArtifact {
        require(durationSeconds > 0)
        return ExportArtifact(
            role = role,
            variant = "original",
            relativePath = file.name,
            format = format.name,
            sampleRate = SAMPLE_RATE,
            channels = CHANNELS,
            durationFrames = frames,
            size = file.length(),
            sha256 = ProjectHashing.sha256(file),
        )
    }

    private fun exportManifest(
        projectId: String,
        artist: String,
        song: String,
        state: ExportState,
        sharedGainDb: Double,
    ): String {
        val artifacts = JSONArray()
        state.artifacts.forEach { artifact ->
            artifacts.put(
                JSONObject()
                    .put("role", artifact.role)
                    .put("relativePath", artifact.relativePath)
                    .put("format", artifact.format)
                    .put("sampleRate", artifact.sampleRate)
                    .put("channels", artifact.channels)
                    .put("durationFrames", artifact.durationFrames)
                    .put("size", artifact.size)
                    .put("sha256", artifact.sha256)
            )
        }
        return JSONObject()
            .put("schemaVersion", 2)
            .put("producer", "GBW")
            .put("projectId", projectId)
            .put("artist", artist)
            .put("song", song)
            .put("exportId", state.exportId)
            .put("revision", state.revision)
            .put("targetPeakDbfs", -1.0)
            .put("sharedGainPolicy", "combined_backing_plus_guitar")
            .put("sharedGainDb", sharedGainDb)
            .put("artifacts", artifacts)
            .toString(2)
    }
}
