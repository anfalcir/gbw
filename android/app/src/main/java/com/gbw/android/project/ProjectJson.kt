package com.gbw.android.project

import org.json.JSONArray
import org.json.JSONObject

internal object ProjectJson {
    fun encode(project: ProjectManifest): String {
        val json = JSONObject()
            .put("schemaVersion", project.schemaVersion)
            .put("projectId", project.projectId)
            .put("name", project.name)
            .put("artist", project.artist)
            .put("song", project.song)
            .put("createdAtEpochMs", project.createdAtEpochMs)
            .put("updatedAtEpochMs", project.updatedAtEpochMs)
            .put("workflowStage", project.workflowStage)
            .put("pitch", JSONObject()
                .put("semitones", project.pitch.semitones)
                .put("vocalFormants", project.pitch.vocalFormants))
            .put("inventory", JSONArray().also { array ->
                project.inventory.sortedBy { it.relativePath }.forEach { a ->
                    array.put(JSONObject()
                        .put("relativePath", a.relativePath)
                        .put("size", a.size)
                        .put("sha256", a.sha256)
                        .put("modifiedAtEpochMs", a.modifiedAtEpochMs))
                }
            })
        project.lastSyncedRevisionId?.let { json.put("lastSyncedRevisionId", it) }
        project.source?.let { s ->
            json.put("source", JSONObject()
                .put("originalRelativePath", s.originalRelativePath)
                .put("preparedRelativePath", s.preparedRelativePath)
                .put("provenanceUri", s.provenanceUri)
                .put("sourceUrl", s.sourceUrl)
                .put("title", s.title)
                .put("formatId", s.formatId)
                .put("durationSeconds", s.durationSeconds))
        }
        project.separation?.let { s ->
            json.put("separation", JSONObject()
                .put("engine", s.engine)
                .put("jobId", s.jobId)
                .put("completedAtEpochMs", s.completedAtEpochMs)
                .put("frames", s.frames)
                .put("elapsedMillis", s.elapsedMillis)
                .put("runtimeIdentity", s.runtimeIdentity)
                .put("blasThreads", s.blasThreads)
                .put("stems", JSONObject().also { stems -> s.stems.toSortedMap().forEach { (k, v) -> stems.put(k, v) } }))
        }
        project.export?.let { e ->
            json.put("export", JSONObject()
                .put("exportId", e.exportId)
                .put("revision", e.revision)
                .put("pitchSemitones", e.pitchSemitones)
                .put("outputFormat", e.outputFormat)
                .put("manifestRelativePath", e.manifestRelativePath)
                .put("artifacts", JSONArray().also { array ->
                    e.artifacts.forEach { a ->
                        array.put(JSONObject()
                            .put("role", a.role)
                            .put("variant", a.variant)
                            .put("relativePath", a.relativePath)
                            .put("format", a.format)
                            .put("sampleRate", a.sampleRate)
                            .put("channels", a.channels)
                            .put("durationFrames", a.durationFrames)
                            .put("size", a.size)
                            .put("sha256", a.sha256))
                    }
                }))
        }
        return json.toString(2)
    }

    fun decode(text: String): ProjectManifest {
        val json = JSONObject(text)
        val source = json.optJSONObject("source")?.let { s ->
            ManagedSource(
                originalRelativePath = normalizeRelativePath(s.getString("originalRelativePath")),
                preparedRelativePath = s.optStringOrNull("preparedRelativePath")?.let(::normalizeRelativePath),
                provenanceUri = s.optStringOrNull("provenanceUri"),
                sourceUrl = s.optStringOrNull("sourceUrl"),
                title = s.optString("title", ""),
                formatId = s.optString("formatId", ""),
                durationSeconds = s.optDouble("durationSeconds", 0.0),
            )
        }
        val separation = json.optJSONObject("separation")?.let { s ->
            val stemsJson = s.optJSONObject("stems") ?: JSONObject()
            val stems = buildMap {
                stemsJson.keys().forEach { key -> put(key, normalizeRelativePath(stemsJson.getString(key))) }
            }
            SeparationState(
                engine = s.optString("engine", "htdemucs_6s"),
                jobId = s.getString("jobId"),
                completedAtEpochMs = s.optLong("completedAtEpochMs", 0L),
                frames = s.optLong("frames", 0L),
                elapsedMillis = s.optLong("elapsedMillis", 0L),
                runtimeIdentity = s.optString("runtimeIdentity", ""),
                blasThreads = s.optInt("blasThreads", 1),
                stems = stems,
            )
        }
        val pitchJson = json.optJSONObject("pitch") ?: JSONObject()
        val export = json.optJSONObject("export")?.let { e ->
            val artifacts = mutableListOf<ExportArtifact>()
            val array = e.optJSONArray("artifacts") ?: JSONArray()
            for (i in 0 until array.length()) {
                val a = array.getJSONObject(i)
                artifacts += ExportArtifact(
                    role = a.getString("role"),
                    variant = a.getString("variant"),
                    relativePath = normalizeRelativePath(a.getString("relativePath")),
                    format = a.getString("format"),
                    sampleRate = a.getInt("sampleRate"),
                    channels = a.getInt("channels"),
                    durationFrames = a.getLong("durationFrames"),
                    size = a.getLong("size"),
                    sha256 = a.getString("sha256"),
                )
            }
            ExportState(
                exportId = e.getString("exportId"),
                revision = e.optLong("revision", 1L),
                pitchSemitones = e.optInt("pitchSemitones", 0),
                outputFormat = e.optString("outputFormat", "FLAC_24"),
                artifacts = artifacts,
                manifestRelativePath = normalizeRelativePath(e.getString("manifestRelativePath")),
            )
        }
        val inventory = mutableListOf<DurableArtifact>()
        val inv = json.optJSONArray("inventory") ?: JSONArray()
        for (i in 0 until inv.length()) {
            val a = inv.getJSONObject(i)
            inventory += DurableArtifact(
                relativePath = normalizeRelativePath(a.getString("relativePath")),
                size = a.getLong("size"),
                sha256 = a.getString("sha256"),
                modifiedAtEpochMs = a.optLong("modifiedAtEpochMs", 0L),
            )
        }
        return ProjectManifest(
            schemaVersion = json.optInt("schemaVersion", 1),
            projectId = json.getString("projectId").lowercase(),
            name = sanitizeProjectName(json.optString("name", "Projeto sem nome")),
            artist = json.optString("artist", ""),
            song = json.optString("song", ""),
            createdAtEpochMs = json.getLong("createdAtEpochMs"),
            updatedAtEpochMs = json.getLong("updatedAtEpochMs"),
            workflowStage = json.optString("workflowStage", "SOURCE"),
            source = source,
            separation = separation,
            pitch = PitchState(
                semitones = pitchJson.optInt("semitones", 0),
                vocalFormants = pitchJson.optBoolean("vocalFormants", true),
            ),
            export = export,
            inventory = inventory,
            lastSyncedRevisionId = json.optStringOrNull("lastSyncedRevisionId"),
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}
