package com.gbw.android.backup

import android.content.Context
import com.gbw.android.project.ProjectHashing
import com.gbw.android.project.ProjectManifest
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal class BackupDirtyStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "state").apply { mkdirs() }
    private val file = File(dir, "backup_dirty.json")
    private val temp = File(dir, "backup_dirty.json.tmp")
    private val lockFile = File(dir, "backup_dirty.lock")

    fun markDirty(project: ProjectManifest, now: Long = System.currentTimeMillis()): DirtyProjectState =
        withLock {
            val all = readUnlocked().toMutableMap()
            val revision = ProjectHashing.revisionId(project)
            val previous = all[project.projectId]
            val next = DirtyProjectState(
                projectId = project.projectId,
                desiredRevisionId = revision,
                dirtySinceEpochMs = previous?.dirtySinceEpochMs ?: now,
                lastChangedAtEpochMs = now,
                lastBackedUpRevisionId = previous?.lastBackedUpRevisionId,
            )
            all[project.projectId] = next
            writeUnlocked(all)
            next
        }

    fun markBackedUp(projectId: String, revisionId: String) = withLock {
        val all = readUnlocked().toMutableMap()
        val current = all[projectId] ?: return@withLock
        val keepDirty = current.desiredRevisionId != revisionId
        if (keepDirty) {
            all[projectId] = current.copy(lastBackedUpRevisionId = revisionId)
        } else {
            all.remove(projectId)
        }
        writeUnlocked(all)
    }

    fun setLastBackedUp(projectId: String, revisionId: String?) = withLock {
        val all = readUnlocked().toMutableMap()
        val current = all[projectId]
        if (current != null) {
            all[projectId] = current.copy(lastBackedUpRevisionId = revisionId)
            writeUnlocked(all)
        }
    }

    fun list(): List<DirtyProjectState> = withLock { readUnlocked().values.sortedBy { it.dirtySinceEpochMs } }
    fun get(projectId: String): DirtyProjectState? = withLock { readUnlocked()[projectId] }

    fun clear(projectId: String) = withLock {
        val all = readUnlocked().toMutableMap()
        if (all.remove(projectId) != null) writeUnlocked(all)
    }

    private fun readUnlocked(): Map<String, DirtyProjectState> {
        if (!file.isFile) return emptyMap()
        return runCatching {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val projects = root.optJSONObject("projects") ?: JSONObject()
            buildMap {
                projects.keys().forEach { id ->
                    val j = projects.getJSONObject(id)
                    put(id, DirtyProjectState(
                        projectId = id,
                        desiredRevisionId = j.getString("desiredRevisionId"),
                        dirtySinceEpochMs = j.getLong("dirtySinceEpochMs"),
                        lastChangedAtEpochMs = j.getLong("lastChangedAtEpochMs"),
                        lastBackedUpRevisionId = if (j.has("lastBackedUpRevisionId") && !j.isNull("lastBackedUpRevisionId")) j.getString("lastBackedUpRevisionId") else null,
                    ))
                }
            }
        }.getOrElse { emptyMap() }
    }

    private fun writeUnlocked(states: Map<String, DirtyProjectState>) {
        val projects = JSONObject()
        states.toSortedMap().forEach { (id, state) ->
            projects.put(id, JSONObject()
                .put("desiredRevisionId", state.desiredRevisionId)
                .put("dirtySinceEpochMs", state.dirtySinceEpochMs)
                .put("lastChangedAtEpochMs", state.lastChangedAtEpochMs)
                .put("lastBackedUpRevisionId", state.lastBackedUpRevisionId))
        }
        val bytes = JSONObject().put("schemaVersion", 1).put("projects", projects)
            .toString(2).toByteArray(Charsets.UTF_8)
        FileOutputStream(temp).use { out -> out.write(bytes); out.flush(); out.fd.sync() }
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun <T> withLock(block: () -> T): T =
        synchronized(PROCESS_LOCK) {
            RandomAccessFile(lockFile, "rw").channel.use { channel ->
                val lock = channel.lock()
                try { block() } finally { lock.release() }
            }
        }

    private companion object { val PROCESS_LOCK = Any() }
}
