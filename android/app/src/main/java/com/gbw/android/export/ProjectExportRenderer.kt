package com.gbw.android.export

import android.content.Context
import com.gbw.android.audio.FfmpegPitchIo
import com.gbw.android.audio.FloatWavReader
import com.gbw.android.audio.FloatWavWriter
import com.gbw.android.audio.RubberBandR3Contract
import com.gbw.android.audio.RubberBandR3Session
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
        includeOriginal: Boolean,
        includePitched: Boolean,
        outputFormat: OutputFormat,
        onProgress: (Int, String) -> Unit,
    ): ProjectExportResult {
        require(includeOriginal || includePitched) { "Selecione ao menos uma variante para exportar." }
        val repo = ProjectRepository(context)
        val project = repo.load(projectId)
        val separation = requireNotNull(project.separation) { "O projeto ainda não possui separação completa." }
        val required = (backingRoles + "guitar").associateWith { role ->
            val relative = separation.stems[role] ?: error("Stem ausente: $role")
            repo.resolveProjectPath(projectId, relative).also { require(it.isFile) { "Stem ausente: $role" } }
        }
        val baseInfo = FloatWavReader(required.getValue("guitar")).use { it.info }
        require(baseInfo.sampleRate == SAMPLE_RATE && baseInfo.channels == CHANNELS) { "Stems precisam estar em estéreo/44,1 kHz." }
        required.values.forEach { file ->
            FloatWavReader(file).use { info ->
                require(info.info.sampleRate == SAMPLE_RATE && info.info.channels == CHANNELS)
                require(info.info.frames == baseInfo.frames) { "Stems perderam alinhamento de frames." }
            }
        }

        val exportId = "e_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().take(8)
        val stagingRoot = File(context.cacheDir, "project-export-staging/$projectId/$exportId")
        stagingRoot.deleteRecursively()
        require(stagingRoot.mkdirs())
        val artifacts = mutableListOf<ExportArtifact>()
        val gains = linkedMapOf<String, Double>()
        var published = false
        try {
            var step = 3
            if (includeOriginal) {
                onProgress(step, "Renderizando par ORIGINAL…")
                val result = renderVariant(
                    inputs = required,
                    variant = "original",
                    variantDirectory = File(stagingRoot, "original"),
                    outputFormat = outputFormat,
                    expectedFrames = baseInfo.frames,
                    semitones = 0,
                    vocalFormants = project.pitch.vocalFormants,
                    pitchFirst = false,
                )
                gains["original"] = result.second
                artifacts += result.first.map { it.copy(relativePath = "exports/$exportId/" + it.relativePath) }
                step = 48
            }
            val pitch = project.pitch.semitones
            if (includePitched && (pitch != 0 || !includeOriginal)) {
                onProgress(step, "Aplicando pitch $pitch st e renderizando par AJUSTADO…")
                val folder = "pitch_" + (if (pitch >= 0) "+$pitch" else "$pitch") + "st"
                val result = renderVariant(
                    inputs = required,
                    variant = "pitched",
                    variantDirectory = File(stagingRoot, folder),
                    outputFormat = outputFormat,
                    expectedFrames = baseInfo.frames,
                    semitones = pitch,
                    vocalFormants = project.pitch.vocalFormants,
                    pitchFirst = pitch != 0,
                )
                gains["pitched"] = result.second
                artifacts += result.first.map { it.copy(relativePath = "exports/$exportId/" + it.relativePath) }
            }

            currentCoroutineContext().ensureActive()
            val manifestRelative = "exports/$exportId/export_manifest.json"
            val state = ExportState(
                exportId = exportId,
                revision = 1L,
                pitchSemitones = project.pitch.semitones,
                outputFormat = outputFormat.name,
                artifacts = artifacts,
                manifestRelativePath = manifestRelative,
            )
            val manifestFile = File(stagingRoot, "export_manifest.json")
            manifestFile.writeText(exportManifest(projectId, project.artist, project.song, state, gains))
            onProgress(96, "Publicando exportação validada no projeto…")
            val updated = repo.publishExport(projectId, stagingRoot, state)
            published = true
            onProgress(100, "Exportação concluída: backing + guitar.")
            return ProjectExportResult(requireNotNull(updated.export), gains)
        } finally {
            if (!published) stagingRoot.deleteRecursively()
        }
    }

    private suspend fun renderVariant(
        inputs: Map<String, File>,
        variant: String,
        variantDirectory: File,
        outputFormat: OutputFormat,
        expectedFrames: Long,
        semitones: Int,
        vocalFormants: Boolean,
        pitchFirst: Boolean,
    ): Pair<List<ExportArtifact>, Double> {
        variantDirectory.mkdirs()
        val work = File(variantDirectory, ".work").apply { mkdirs() }
        try {
            val effective = if (!pitchFirst) inputs else {
                val pitched = mutableMapOf<String, File>()
                inputs.forEach { (role, source) ->
                    currentCoroutineContext().ensureActive()
                    if (role == "drums") {
                        pitched[role] = source
                    } else {
                        val target = File(work, "$role-pitched.wav")
                        pitchExact(source, target, semitones, role == "vocals" && vocalFormants, expectedFrames)
                        pitched[role] = target
                    }
                }
                pitched
            }

            val backingFloat = File(work, "backing-mix.wav")
            mixFloat(backingRoles.map { effective.getValue(it) }, backingFloat, expectedFrames)
            val guitar = effective.getValue("guitar")
            val peak = combinedPeak(backingFloat, guitar, expectedFrames)
            val factor = SharedGain.factorForCombinedPeak(peak, -1.0)
            val gainDb = SharedGain.gainDb(factor)

            val backingScaled = File(work, "backing-scaled.wav")
            val guitarScaled = File(work, "guitar-scaled.wav")
            scaleFloat(backingFloat, backingScaled, factor, expectedFrames)
            scaleFloat(guitar, guitarScaled, factor, expectedFrames)

            val backingOut = File(variantDirectory, "backing." + outputFormat.extension)
            val guitarOut = File(variantDirectory, "guitar." + outputFormat.extension)
            encode(backingScaled, backingOut, outputFormat)
            encode(guitarScaled, guitarOut, outputFormat)
            validateFinal(backingOut, outputFormat, expectedFrames)
            validateFinal(guitarOut, outputFormat, expectedFrames)

            val durationSeconds = expectedFrames.toDouble() / SAMPLE_RATE.toDouble()
            val artifacts = listOf(
                exportArtifact(variant, "backing", backingOut, outputFormat, expectedFrames, durationSeconds),
                exportArtifact(variant, "guitar", guitarOut, outputFormat, expectedFrames, durationSeconds),
            ).map { artifact ->
                artifact.copy(relativePath = variantDirectory.name + "/" + File(artifact.relativePath).name)
            }
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
        } finally { readers.forEach { it.close() } }
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
                    for (i in bb.indices) peak = maxOf(peak, abs(bb[i].toDouble() + gg[i].toDouble()))
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
                    if (factor != 1.0) for (i in block.indices) block[i] = (block[i] * factor).toFloat()
                    writer.write(block)
                    done += count
                    yield()
                }
            }
        }
    }

    private suspend fun pitchExact(
        input: File,
        output: File,
        semitones: Int,
        preserveFormants: Boolean,
        expectedFrames: Long,
    ) {
        if (semitones == 0) {
            Files.copy(input.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
            return
        }
        val raw = File(output.parentFile, output.nameWithoutExtension + ".raw.wav")
        RubberBandR3Session(SAMPLE_RATE, CHANNELS, semitones.toDouble(), preserveFormants, expectedFrames).use { r3 ->
            FloatWavReader(input).use { source ->
                while (source.remainingFrames() > 0L) {
                    currentCoroutineContext().ensureActive()
                    val block = source.readBlock(RubberBandR3Contract.MAX_BLOCK_FRAMES)
                    r3.study(block, block.size / CHANNELS, source.remainingFrames() == 0L)
                }
                source.rewind()
                FloatWavWriter(raw, SAMPLE_RATE, CHANNELS).use { writer ->
                    while (source.remainingFrames() > 0L) {
                        currentCoroutineContext().ensureActive()
                        val block = source.readBlock(RubberBandR3Contract.MAX_BLOCK_FRAMES)
                        r3.process(block, block.size / CHANNELS, source.remainingFrames() == 0L)
                        drain(r3, writer)
                        yield()
                    }
                    var terminal = drain(r3, writer)
                    while (terminal > 0) terminal = drain(r3, writer)
                    require(terminal == -1) { "Rubber Band não finalizou o stem." }
                }
            }
        }
        val produced = FloatWavReader(raw).use { it.info.frames }
        require(RubberBandR3Contract.durationWithinTolerance(expectedFrames, produced, SAMPLE_RATE)) {
            "Pitch alterou a duração além da tolerância."
        }
        normalizeFrames(raw, output, expectedFrames)
        raw.delete()
    }

    private fun drain(r3: RubberBandR3Session, writer: FloatWavWriter): Int {
        while (true) {
            r3.throwIfCancelled()
            val available = r3.availableFrames()
            if (available <= 0) return available
            val block = r3.retrieve(minOf(available, RubberBandR3Contract.MAX_BLOCK_FRAMES))
            require(block.isNotEmpty())
            writer.write(block)
        }
    }

    private fun normalizeFrames(input: File, output: File, expectedFrames: Long) {
        FloatWavReader(input).use { reader ->
            FloatWavWriter(output, SAMPLE_RATE, CHANNELS).use { writer ->
                var remaining = expectedFrames
                while (remaining > 0L && reader.remainingFrames() > 0L) {
                    val count = minOf(BLOCK_FRAMES.toLong(), remaining, reader.remainingFrames()).toInt()
                    writer.write(reader.readBlock(count))
                    remaining -= count
                }
                if (remaining > 0L) {
                    val silence = FloatArray(BLOCK_FRAMES * CHANNELS)
                    while (remaining > 0L) {
                        val count = minOf(BLOCK_FRAMES.toLong(), remaining).toInt()
                        writer.write(silence, 0, count * CHANNELS)
                        remaining -= count
                    }
                }
            }
        }
    }

    private suspend fun encode(input: File, output: File, format: OutputFormat) {
        when (format) {
            OutputFormat.WAV_FLOAT32 -> Files.copy(input.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
            else -> FfmpegPitchIo.encodeOutput(input, output, format)
        }
    }

    private suspend fun validateFinal(file: File, format: OutputFormat, frames: Long) {
        require(file.isFile && file.length() > 44L)
        val probe = FfmpegPitchIo.probe(file)
        require(probe.sampleRate == SAMPLE_RATE && probe.channels == CHANNELS)
        val probedFrames = (probe.durationSeconds * SAMPLE_RATE).toLong()
        require(abs(probedFrames - frames) <= maxOf(2L, (SAMPLE_RATE * 0.002).toLong())) { "Export final perdeu sincronismo." }
        when (format) {
            OutputFormat.WAV_FLOAT32 -> require(probe.codec.equals("pcm_f32le", true))
            OutputFormat.WAV_24 -> require(probe.codec.equals("pcm_s24le", true))
            OutputFormat.FLAC_24 -> require(probe.codec.equals("flac", true))
        }
    }

    private fun exportArtifact(
        variant: String,
        role: String,
        file: File,
        format: OutputFormat,
        frames: Long,
        durationSeconds: Double,
    ): ExportArtifact {
        require(durationSeconds > 0)
        return ExportArtifact(
            role = role,
            variant = variant,
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
        gains: Map<String, Double>,
    ): String {
        val variants = JSONObject()
        gains.forEach { (variant, gain) -> variants.put(variant, JSONObject().put("sharedGainDb", gain)) }
        val artifacts = JSONArray()
        state.artifacts.forEach { a ->
            artifacts.put(JSONObject()
                .put("role", a.role)
                .put("variant", a.variant)
                .put("pitchSemitones", if (a.variant == "original") 0 else state.pitchSemitones)
                .put("relativePath", a.relativePath)
                .put("format", a.format)
                .put("sampleRate", a.sampleRate)
                .put("channels", a.channels)
                .put("durationFrames", a.durationFrames)
                .put("size", a.size)
                .put("sha256", a.sha256))
        }
        return JSONObject()
            .put("schemaVersion", 1)
            .put("producer", "GBW")
            .put("projectId", projectId)
            .put("artist", artist)
            .put("song", song)
            .put("exportId", state.exportId)
            .put("revision", state.revision)
            .put("targetPeakDbfs", -1.0)
            .put("sharedGainPolicy", "combined_backing_plus_guitar")
            .put("variants", variants)
            .put("artifacts", artifacts)
            .toString(2)
    }
}
