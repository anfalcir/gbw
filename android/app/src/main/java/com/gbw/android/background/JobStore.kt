package com.gbw.android.background

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class PersistedJob(
    val id: String,
    val type: String,
    val label: String,
    val state: String,
    val progress: Int,
    val startedAt: Long,
    val message: String,
)

class JobStore(context: Context) {
    private val appContext = context.applicationContext
    private val stateDir = File(appContext.filesDir, "state").apply { mkdirs() }
    private val stateFile = File(stateDir, "current_job.json")
    private val tempFile = File(stateDir, "current_job.json.tmp")
    private val lockFile = File(stateDir, "current_job.lock")
    private val legacyPrefs =
        appContext.getSharedPreferences("gbw_jobs", Context.MODE_PRIVATE)
    private val historyStore = JobHistoryStore(appContext)

    fun save(job: PersistedJob) {
        var previous: PersistedJob? = null
        withFileLock {
            previous = readUnlocked()
            writeUnlocked(job)
            legacyPrefs.edit().clear().commit()
        }
        if (JobHistoryPolicy.shouldRecord(previous, job)) {
            historyStore.record(job)
        }
    }

    fun load(): PersistedJob? = withFileLock {
        migrateLegacyUnlocked()
        readUnlocked()
    }

    fun loadReconciled(): PersistedJob? {
        val current = load()
        return WorkerExitDiagnostics.reconcile(appContext, this, current)
    }

    fun clear() {
        withFileLock {
            stateFile.delete()
            tempFile.delete()
            legacyPrefs.edit().clear().commit()
        }
    }

    private fun migrateLegacyUnlocked() {
        if (stateFile.isFile) return
        val id = legacyPrefs.getString("id", null) ?: return
        val legacy = PersistedJob(
            id = id,
            type = legacyPrefs.getString("type", "") ?: "",
            label = legacyPrefs.getString("label", "") ?: "",
            state = legacyPrefs.getString("state", "") ?: "",
            progress = legacyPrefs.getInt("progress", 0),
            startedAt = legacyPrefs.getLong("startedAt", 0L),
            message = legacyPrefs.getString("message", "") ?: "",
        )
        val migrated =
            if (legacy.state == "RUNNING" || legacy.state == "CANCELLING") {
                legacy.copy(
                    state = "INTERRUPTED",
                    message =
                        "Job anterior interrompido durante atualização/reinício; inicie novamente.",
                )
            } else {
                legacy
            }
        writeUnlocked(migrated)
        historyStore.record(migrated)
        legacyPrefs.edit().clear().commit()
    }

    private fun readUnlocked(): PersistedJob? {
        if (!stateFile.isFile) return null
        return runCatching {
            val json = JSONObject(stateFile.readText(Charsets.UTF_8))
            PersistedJob(
                id = json.getString("id"),
                type = json.optString("type", ""),
                label = json.optString("label", ""),
                state = json.optString("state", ""),
                progress = json.optInt("progress", 0),
                startedAt = json.optLong("startedAt", 0L),
                message = json.optString("message", ""),
            )
        }.getOrElse {
            val corrupt = File(stateDir, "current_job.corrupt.${System.currentTimeMillis()}.json")
            runCatching { stateFile.renameTo(corrupt) }
            null
        }
    }

    private fun writeUnlocked(job: PersistedJob) {
        val json =
            JSONObject()
                .put("id", job.id)
                .put("type", job.type)
                .put("label", job.label)
                .put("state", job.state)
                .put("progress", job.progress)
                .put("startedAt", job.startedAt)
                .put("message", job.message)
                .toString()
                .toByteArray(Charsets.UTF_8)

        FileOutputStream(tempFile).use { output ->
            output.write(json)
            output.flush()
            output.fd.sync()
        }
        try {
            Files.move(
                tempFile.toPath(),
                stateFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                tempFile.toPath(),
                stateFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun <T> withFileLock(block: () -> T): T =
        synchronized(LOCAL_PROCESS_LOCK) {
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
        val LOCAL_PROCESS_LOCK = Any()
    }
}
