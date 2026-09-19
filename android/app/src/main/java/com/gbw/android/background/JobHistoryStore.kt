package com.gbw.android.background

import android.content.Context
import com.gbw.android.project.ProjectJobLinkStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal data class JobHistoryEntry(
    val jobId: String,
    val type: String,
    val label: String,
    val state: String,
    val progress: Int,
    val startedAt: Long,
    val updatedAt: Long,
    val projectId: String?,
    val message: String,
    val diagnostic: String?,
)

internal object JobHistoryPolicy {
    fun shouldRecord(previous: PersistedJob?, next: PersistedJob): Boolean =
        previous == null || previous.id != next.id || previous.state != next.state
}

internal class JobHistoryStore(context: Context) {
    private val appContext = context.applicationContext
    private val stateDir = File(appContext.filesDir, "state").apply { mkdirs() }
    private val historyFile = File(stateDir, "job_history.json")
    private val tempFile = File(stateDir, "job_history.json.tmp")
    private val lockFile = File(stateDir, "job_history.lock")
    private val projectLinks = ProjectJobLinkStore(appContext)

    fun record(job: PersistedJob, nowEpochMs: Long = System.currentTimeMillis()) {
        val projectId = projectLinks.projectId(job.id)
        withFileLock {
            val entries = readUnlocked().toMutableList()
            entries.removeAll { it.jobId == job.id }
            entries += JobHistoryEntry(
                jobId = job.id,
                type = job.type,
                label = job.label,
                state = job.state,
                progress = job.progress,
                startedAt = job.startedAt,
                updatedAt = nowEpochMs,
                projectId = projectId,
                message = job.message,
                diagnostic = job.diagnostic,
            )
            val compact = entries
                .sortedByDescending { it.updatedAt }
                .take(MAX_ENTRIES)
            writeUnlocked(compact)
        }
    }

    fun list(): List<JobHistoryEntry> = withFileLock {
        readUnlocked().sortedByDescending { it.updatedAt }
    }

    fun clear() = withFileLock {
        historyFile.delete()
        tempFile.delete()
    }

    private fun readUnlocked(): List<JobHistoryEntry> {
        if (!historyFile.isFile) return emptyList()
        return runCatching {
            val array = JSONArray(historyFile.readText(Charsets.UTF_8))
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.getJSONObject(index)
                    add(
                        JobHistoryEntry(
                            jobId = json.getString("jobId"),
                            type = json.optString("type", ""),
                            label = json.optString("label", ""),
                            state = json.optString("state", ""),
                            progress = json.optInt("progress", 0),
                            startedAt = json.optLong("startedAt", 0L),
                            updatedAt = json.optLong("updatedAt", 0L),
                            projectId =
                                if (!json.has("projectId") || json.isNull("projectId")) null
                                else json.optString("projectId").takeIf { it.isNotBlank() },
                            message = json.optString("message", ""),
                            diagnostic = json.optString("diagnostic", "").takeIf { it.isNotBlank() },
                        )
                    )
                }
            }
        }.getOrElse {
            val corrupt = File(
                stateDir,
                "job_history.corrupt.${System.currentTimeMillis()}.json",
            )
            runCatching { historyFile.renameTo(corrupt) }
            emptyList()
        }
    }

    private fun writeUnlocked(entries: List<JobHistoryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("jobId", entry.jobId)
                    .put("type", entry.type)
                    .put("label", entry.label)
                    .put("state", entry.state)
                    .put("progress", entry.progress)
                    .put("startedAt", entry.startedAt)
                    .put("updatedAt", entry.updatedAt)
                    .put("projectId", entry.projectId ?: JSONObject.NULL)
                    .put("message", entry.message)
                    .put("diagnostic", entry.diagnostic ?: JSONObject.NULL)
            )
        }
        FileOutputStream(tempFile).use { output ->
            output.write(array.toString().toByteArray(Charsets.UTF_8))
            output.flush()
            output.fd.sync()
        }
        try {
            Files.move(
                tempFile.toPath(),
                historyFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                tempFile.toPath(),
                historyFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun <T> withFileLock(block: () -> T): T =
        synchronized(LOCAL_LOCK) {
            RandomAccessFile(lockFile, "rw").channel.use { channel ->
                val lock = channel.lock()
                try {
                    block()
                } finally {
                    lock.release()
                }
            }
        }

    private companion object {
        const val MAX_ENTRIES = 250
        val LOCAL_LOCK = Any()
    }
}
