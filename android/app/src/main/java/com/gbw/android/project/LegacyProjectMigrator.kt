package com.gbw.android.project

import android.content.Context
import com.gbw.android.separation.SeparationResultFiles
import com.gbw.android.separation.SeparationResultStore
import com.gbw.android.source.PreparedSourceStore
import org.json.JSONObject
import java.io.File

internal class LegacyProjectMigrator(private val context: Context) {
    private val marker = File(context.filesDir, "state/project_migration_v1.json")

    fun migrateIfNeeded(): ProjectManifest? = synchronized(LOCK) {
        val repository = ProjectRepository(context)
        if (marker.isFile) {
            val id = runCatching { JSONObject(marker.readText()).optString("projectId") }.getOrNull()
            return@synchronized id?.takeIf { isCanonicalProjectId(it) }?.let { runCatching { repository.load(it) }.getOrNull() }
        }
        if (repository.list().isNotEmpty()) {
            writeMarker(null, "already-project-based")
            return@synchronized repository.active()
        }
        val prepared = PreparedSourceStore(context).load()
        val separationStore = SeparationResultStore(context)
        val separation = separationStore.load()?.let { SeparationResultFiles.validate(context.filesDir, it) }
        if (prepared == null && separation == null) {
            writeMarker(null, "nothing-to-migrate")
            return@synchronized null
        }
        val name = prepared?.title?.takeIf { it.isNotBlank() } ?: "Projeto recuperado"
        var project = repository.create(name)
        if (prepared != null && prepared.preparedFile().isFile) {
            project = repository.adoptPreparedSource(prepared, name, "", "")
        }
        if (separation != null) project = repository.publishSeparation(separation)
        writeMarker(project.projectId, "success")
        project
    }

    private fun writeMarker(projectId: String?, status: String) {
        marker.parentFile?.mkdirs()
        marker.writeText(JSONObject().put("schemaVersion",1).put("projectId",projectId).put("status",status).put("completedAtEpochMs",System.currentTimeMillis()).toString(2))
    }
    private companion object { val LOCK = Any() }
}
