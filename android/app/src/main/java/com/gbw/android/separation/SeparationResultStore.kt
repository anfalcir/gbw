package com.gbw.android.separation

import android.content.Context
import com.gbw.android.audio.FloatWavReader
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal data class StoredSeparationResult(
    val jobId: String,
    val type: String,
    val completedAt: Long,
    val frames: Long,
    val elapsedMillis: Long,
    val peakPssKb: Long,
    val runtimeIdentity: String,
    val chunkCount: Int = 0,
    val medianChunkMillis: Long = 0L,
    val maxChunkMillis: Long = 0L,
    val maxThermalStatus: Int = DemucsRuntimeMonitor.THERMAL_UNAVAILABLE,
    val blasThreads: Int = 1,
)

internal data class SeparationStem(val name: String, val file: File)

internal data class ValidatedSeparationResult(
    val record: StoredSeparationResult,
    val stems: List<SeparationStem>,
    val totalBytes: Long,
)

internal object SeparationResultFiles {
    const val DEMUCS_TYPE = "separation-demucs"

    // Compatibility only: alpha7/alpha8 stored completed Demucs jobs under this type/path.
    // Keeping this reader preserves already-generated stems across an in-place alpha9 install.
    private const val LEGACY_ALPHA8_DEMUCS_TYPE = "separation-quick"

    val stemNames = listOf("drums", "bass", "other", "vocals", "guitar", "piano")

    fun isDemucsType(type: String): Boolean =
        type == DEMUCS_TYPE || type == LEGACY_ALPHA8_DEMUCS_TYPE

    fun outputDirectory(filesDir: File, jobId: String, type: String): File? {
        if (jobId.isBlank()) return null
        val leaf = when (type) {
            DEMUCS_TYPE -> "demucs"
            LEGACY_ALPHA8_DEMUCS_TYPE -> "quick"
            else -> return null
        }
        return File(filesDir, "jobs/$jobId/separation/$leaf")
    }

    fun validate(filesDir: File, record: StoredSeparationResult): ValidatedSeparationResult? =
        runCatching {
            val directory = requireNotNull(outputDirectory(filesDir, record.jobId, record.type))
            require(directory.isDirectory)
            var expectedFrames = record.frames.takeIf { it > 0L }
            val stems = stemNames.map { stem ->
                val file = File(directory, "$stem.wav")
                require(file.isFile && file.length() > 44L)
                FloatWavReader(file).use { reader ->
                    val info = reader.info
                    require(info.sampleRate == DemucsNative.REQUIRED_SAMPLE_RATE)
                    require(info.channels == DemucsNative.REQUIRED_CHANNELS)
                    if (expectedFrames == null) expectedFrames = info.frames
                    require(info.frames == expectedFrames)
                }
                SeparationStem(stem, file)
            }
            val normalized =
                if (record.frames > 0L) record else record.copy(frames = requireNotNull(expectedFrames))
            ValidatedSeparationResult(normalized, stems, stems.sumOf { it.file.length() })
        }.getOrNull()
}

internal class SeparationResultStore(context: Context) {
    private val appContext = context.applicationContext
    private val stateDir = File(appContext.filesDir, "state").apply { mkdirs() }
    private val stateFile = File(stateDir, "last_separation.json")
    private val tempFile = File(stateDir, "last_separation.json.tmp")
    private val lockFile = File(stateDir, "last_separation.lock")

    fun saveDemucs(jobId: String, result: DemucsSeparationResult) = save(
        StoredSeparationResult(
            jobId, SeparationResultFiles.DEMUCS_TYPE, System.currentTimeMillis(), result.frames,
            result.elapsedMillis, result.peakObservedPssKb, result.runtimeIdentity,
            result.chunkCount, result.medianChunkMillis, result.maxChunkMillis,
            result.maxThermalStatus, result.blasThreads,
        )
    )

    fun save(record: StoredSeparationResult) {
        requireNotNull(SeparationResultFiles.validate(appContext.filesDir, record))
        withFileLock { writeUnlocked(record) }
    }

    fun load(): StoredSeparationResult? = withFileLock { readUnlocked() }

    fun recoverExisting(
        jobId: String,
        type: String,
        startedAt: Long,
        message: String,
    ): ValidatedSeparationResult? {
        if (!SeparationResultFiles.isDemucsType(type)) return null
        val provisional = StoredSeparationResult(
            jobId = jobId,
            type = type,
            completedAt = System.currentTimeMillis(),
            frames = 0L,
            elapsedMillis = ELAPSED_SECONDS.find(message)?.groupValues?.getOrNull(1)
                ?.toLongOrNull()?.times(1_000L) ?: 0L,
            peakPssKb = PSS_MIB.find(message)?.groupValues?.getOrNull(1)
                ?.toLongOrNull()?.times(1_024L) ?: 0L,
            runtimeIdentity = "recovered-legacy-demucs;startedAt=$startedAt",
            blasThreads = 1,
        )
        val validated = SeparationResultFiles.validate(appContext.filesDir, provisional) ?: return null
        save(validated.record)
        return validated
    }

    private fun readUnlocked(): StoredSeparationResult? {
        if (!stateFile.isFile) return null
        return runCatching {
            val json = JSONObject(stateFile.readText(Charsets.UTF_8))
            StoredSeparationResult(
                json.getString("jobId"),
                json.getString("type"),
                json.optLong("completedAt", 0L),
                json.optLong("frames", 0L),
                json.optLong("elapsedMillis", 0L),
                json.optLong("peakPssKb", 0L),
                json.optString("runtimeIdentity", ""),
                json.optInt("chunkCount", 0),
                json.optLong("medianChunkMillis", 0L),
                json.optLong("maxChunkMillis", 0L),
                json.optInt("maxThermalStatus", DemucsRuntimeMonitor.THERMAL_UNAVAILABLE),
                json.optInt("blasThreads", 1),
            )
        }.getOrElse {
            val corrupt = File(
                stateDir,
                "last_separation.corrupt." + System.currentTimeMillis() + ".json",
            )
            runCatching { stateFile.renameTo(corrupt) }
            null
        }
    }

    private fun writeUnlocked(record: StoredSeparationResult) {
        val bytes = JSONObject()
            .put("jobId", record.jobId)
            .put("type", record.type)
            .put("completedAt", record.completedAt)
            .put("frames", record.frames)
            .put("elapsedMillis", record.elapsedMillis)
            .put("peakPssKb", record.peakPssKb)
            .put("runtimeIdentity", record.runtimeIdentity)
            .put("chunkCount", record.chunkCount)
            .put("medianChunkMillis", record.medianChunkMillis)
            .put("maxChunkMillis", record.maxChunkMillis)
            .put("maxThermalStatus", record.maxThermalStatus)
            .put("blasThreads", record.blasThreads)
            .toString().toByteArray(Charsets.UTF_8)
        FileOutputStream(tempFile).use { out ->
            out.write(bytes); out.flush(); out.fd.sync()
        }
        try {
            Files.move(tempFile.toPath(), stateFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tempFile.toPath(), stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun <T> withFileLock(block: () -> T): T =
        synchronized(LOCAL_PROCESS_LOCK) {
            RandomAccessFile(lockFile, "rw").channel.use { channel ->
                val lock = channel.lock()
                try { block() } finally { lock.release() }
            }
        }

    private companion object {
        val LOCAL_PROCESS_LOCK = Any()
        val ELAPSED_SECONDS = Regex("""(?:^|•)\s*(\d+)s(?:\s*•|$)""")
        val PSS_MIB = Regex("""PSS observado\s+(\d+)\s+MiB""")
    }
}
